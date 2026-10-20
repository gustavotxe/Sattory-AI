#include <jni.h>
#include "llama.h"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <mutex>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

namespace {
struct Message {
    std::string role;
    std::string content;
};

std::mutex engine_mutex;
llama_model * model = nullptr;
llama_context * context = nullptr;
llama_sampler * sampler = nullptr;
llama_model * embedding_model = nullptr;
llama_context * embedding_context = nullptr;
bool backend_initialized = false;
bool generation_active = false;
bool last_generation_token_limited = false;
int generated_tokens = 0;
int max_generation_tokens = 0;
int context_tokens = 0;
int context_size = 0;
size_t previous_formatted_length = 0;
std::string generation_prefix;
std::string generated_response;
std::string utf8_pending;
std::vector<Message> messages;

constexpr const char * SYSTEM_PROMPT =
    "You are Sattory, a helpful assistant. Answer in the same language as the user.";

void throw_java(JNIEnv * env, const std::string & message) {
    const jclass exception = env->FindClass("java/lang/IllegalStateException");
    env->ThrowNew(exception, message.c_str());
}

jfloatArray to_float_array(JNIEnv * env, const std::vector<float> & value) {
    auto result = env->NewFloatArray(static_cast<jsize>(value.size()));
    if (!value.empty()) {
        env->SetFloatArrayRegion(result, 0, static_cast<jsize>(value.size()), value.data());
    }
    return result;
}

void ensure_backend_initialized() {
    if (backend_initialized) return;
    llama_log_set([](ggml_log_level level, const char * text, void *) {
        if (level >= GGML_LOG_LEVEL_ERROR) fprintf(stderr, "%s", text);
    }, nullptr);
    llama_backend_init();
    backend_initialized = true;
}

std::string from_utf8_bytes(JNIEnv * env, jbyteArray value) {
    const auto size = env->GetArrayLength(value);
    std::string result(static_cast<size_t>(size), '\0');
    env->GetByteArrayRegion(value, 0, size, reinterpret_cast<jbyte *>(result.data()));
    return result;
}

jbyteArray to_utf8_bytes(JNIEnv * env, const std::string & value) {
    auto result = env->NewByteArray(static_cast<jsize>(value.size()));
    if (!value.empty()) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(value.size()),
                                reinterpret_cast<const jbyte *>(value.data()));
    }
    return result;
}

void free_generation() {
    if (sampler != nullptr) {
        llama_sampler_free(sampler);
        sampler = nullptr;
    }
    generation_active = false;
    generated_tokens = 0;
    max_generation_tokens = 0;
    generation_prefix.clear();
    generated_response.clear();
    utf8_pending.clear();
}

void free_context() {
    free_generation();
    if (context != nullptr) {
        llama_free(context);
        context = nullptr;
    }
    context_tokens = 0;
    context_size = 0;
    previous_formatted_length = 0;
}

void free_embedding_context() {
    if (embedding_context != nullptr) {
        llama_free(embedding_context);
        embedding_context = nullptr;
    }
}

std::string format_messages(bool add_assistant_prompt) {
    const char * chat_template = llama_model_chat_template(model, nullptr);
    if (chat_template == nullptr) {
        if (messages.empty()) return {};
        return messages.back().content;
    }

    std::vector<llama_chat_message> native_messages;
    native_messages.reserve(messages.size() + 1);
    native_messages.push_back({"system", SYSTEM_PROMPT});
    for (const auto & message : messages) {
        native_messages.push_back({message.role.c_str(), message.content.c_str()});
    }

    int32_t size = llama_chat_apply_template(
        chat_template, native_messages.data(), native_messages.size(), add_assistant_prompt, nullptr, 0
    );
    if (size < 0) throw std::runtime_error("O template de chat do modelo não é compatível");
    std::vector<char> buffer(static_cast<size_t>(size) + 1);
    size = llama_chat_apply_template(
        chat_template, native_messages.data(), native_messages.size(), add_assistant_prompt,
        buffer.data(), buffer.size()
    );
    if (size < 0) throw std::runtime_error("Falha ao aplicar o template de chat");
    return {buffer.data(), static_cast<size_t>(size)};
}

std::vector<llama_token> tokenize(const std::string & text, bool add_special) {
    const llama_vocab * vocab = llama_model_get_vocab(model);
    const int count = -llama_tokenize(vocab, text.c_str(), text.size(), nullptr, 0, add_special, true);
    if (count <= 0) throw std::runtime_error("Falha ao tokenizar o contexto da conversa");
    std::vector<llama_token> tokens(static_cast<size_t>(count));
    if (llama_tokenize(vocab, text.c_str(), text.size(), tokens.data(), count, add_special, true) < 0) {
        throw std::runtime_error("Falha ao tokenizar o contexto da conversa");
    }
    return tokens;
}

std::vector<llama_token> tokenize_embedding_part(const llama_vocab * vocab, const std::string & text, bool add_special) {
    const int count = -llama_tokenize(vocab, text.c_str(), text.size(), nullptr, 0, add_special, false);
    if (count <= 0) return {};
    std::vector<llama_token> tokens(static_cast<size_t>(count));
    const int actual = llama_tokenize(vocab, text.c_str(), text.size(), tokens.data(), count, add_special, false);
    if (actual < 0) throw std::runtime_error("Falha ao tokenizar o texto para embeddings");
    tokens.resize(static_cast<size_t>(actual));
    return tokens;
}

std::vector<llama_token> tokenize_embedding_fallback(const llama_vocab * vocab, const std::string & text, llama_token unknown) {
    try {
        return tokenize_embedding_part(vocab, text, false);
    } catch (const std::out_of_range &) {
        size_t split = text.size() / 2;
        while (split > 0 && (static_cast<unsigned char>(text[split]) & 0xC0) == 0x80) --split;
        if (split == 0) return { unknown };
        auto left = tokenize_embedding_fallback(vocab, text.substr(0, split), unknown);
        auto right = tokenize_embedding_fallback(vocab, text.substr(split), unknown);
        left.insert(left.end(), right.begin(), right.end());
        return left;
    }
}

std::vector<llama_token> tokenize_with_model(llama_model * target_model, const std::string & text, bool add_special) {
    const llama_vocab * vocab = llama_model_get_vocab(target_model);
    try {
        return tokenize_embedding_part(vocab, text, add_special);
    } catch (const std::out_of_range &) {
        // E5 vocabularies may lack byte fallback. Only unsupported code points
        // become UNK in embeddings; the original document is preserved in storage.
        llama_token unknown = LLAMA_TOKEN_NULL;
        for (llama_token token = 0; token < llama_vocab_n_tokens(vocab); ++token) {
            if ((llama_vocab_get_attr(vocab, token) & LLAMA_TOKEN_ATTR_UNKNOWN) ||
                std::string(llama_vocab_get_text(vocab, token)) == "<unk>") {
                unknown = token;
                break;
            }
        }
        if (unknown == LLAMA_TOKEN_NULL) throw std::runtime_error("O vocabulario de embeddings nao suporta caracteres deste documento");
        auto tokens = tokenize_embedding_fallback(vocab, text, unknown);
        if (add_special && llama_vocab_get_add_bos(vocab)) tokens.insert(tokens.begin(), llama_vocab_bos(vocab));
        if (add_special && llama_vocab_get_add_eos(vocab)) tokens.push_back(llama_vocab_eos(vocab));
        return tokens;
    }
}
std::vector<float> normalize_embedding(const float * input, int size) {
    if (input == nullptr || size <= 0) throw std::runtime_error("Embedding vazio");
    double sum = 0.0;
    for (int index = 0; index < size; ++index) sum += static_cast<double>(input[index]) * input[index];
    const float norm = static_cast<float>(std::sqrt(sum));
    std::vector<float> result(static_cast<size_t>(size));
    if (norm <= 0.0f) {
        std::copy(input, input + size, result.begin());
    } else {
        for (int index = 0; index < size; ++index) result[index] = input[index] / norm;
    }
    return result;
}

void load_embedding_model(const std::string & model_path) {
    if (embedding_model != nullptr) return;
    ensure_backend_initialized();
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    embedding_model = llama_model_load_from_file(model_path.c_str(), model_params);
    if (embedding_model == nullptr) throw std::runtime_error("Nao foi possivel carregar o modelo de embeddings GGUF");

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = 512;
    context_params.n_batch = 512;
    context_params.n_ubatch = 512;
    context_params.embeddings = true;
    context_params.no_perf = true;
    embedding_context = llama_init_from_model(embedding_model, context_params);
    if (embedding_context == nullptr) {
        llama_model_free(embedding_model);
        embedding_model = nullptr;
        throw std::runtime_error("Nao foi possivel criar contexto de embeddings");
    }
}

std::vector<float> embed_text(const std::string & text) {
    if (embedding_model == nullptr || embedding_context == nullptr) {
        throw std::runtime_error("O modelo de embeddings ainda nao foi carregado");
    }
    std::vector<llama_token> tokens;
    try {
        tokens = tokenize_with_model(embedding_model, text, true);
    } catch (const std::exception & error) {
        throw std::runtime_error(std::string("Tokenizacao de embeddings: ") + error.what());
    }
    if (tokens.size() > static_cast<size_t>(llama_n_ctx(embedding_context))) {
        throw std::runtime_error("Texto grande demais para o contexto de embeddings");
    }

    llama_memory_clear(llama_get_memory(embedding_context), false);
    llama_batch batch = llama_batch_init(static_cast<int32_t>(tokens.size()), 0, 1);
    try {
        batch.n_tokens = static_cast<int32_t>(tokens.size());
        for (int32_t index = 0; index < batch.n_tokens; ++index) {
            batch.token[index] = tokens[static_cast<size_t>(index)];
            batch.pos[index] = index;
            batch.n_seq_id[index] = 1;
            batch.seq_id[index][0] = 0;
            batch.logits[index] = true;
        }
        int decode_result;
        try {
            decode_result = llama_model_has_encoder(embedding_model)
                ? llama_encode(embedding_context, batch)
                : llama_decode(embedding_context, batch);
        } catch (const std::exception & error) {
            throw std::runtime_error(std::string("Codificacao de embeddings: ") + error.what());
        }
        if (decode_result != 0) {
            throw std::runtime_error("Falha ao gerar embeddings");
        }

        const int size = llama_model_n_embd(embedding_model);
        const float * pooled = llama_get_embeddings_seq(embedding_context, 0);
        if (pooled != nullptr) {
            auto result = normalize_embedding(pooled, size);
            llama_batch_free(batch);
            return result;
        }
        const float * last = llama_get_embeddings_ith(embedding_context, batch.n_tokens - 1);
        auto result = normalize_embedding(last, size);
        llama_batch_free(batch);
        return result;
    } catch (...) {
        llama_batch_free(batch);
        throw;
    }
}

void create_context(int requested_size) {
    llama_context_params params = llama_context_default_params();
    params.n_ctx = static_cast<uint32_t>(std::max(requested_size, 512));
    params.n_batch = std::min<uint32_t>(params.n_ctx, 512);
    params.no_perf = true;
    context = llama_init_from_model(model, params);
    if (context == nullptr) throw std::runtime_error("Não foi possível criar o contexto do modelo");
    context_size = static_cast<int>(llama_n_ctx(context));
    context_tokens = 0;
    previous_formatted_length = 0;
}

void decode(const std::vector<llama_token> & tokens) {
    if (tokens.empty()) return;
    const int batch_size = static_cast<int>(llama_n_batch(context));
    for (int offset = 0; offset < static_cast<int>(tokens.size()); offset += batch_size) {
        const int count = std::min(batch_size, static_cast<int>(tokens.size()) - offset);
        llama_batch batch = llama_batch_get_one(const_cast<llama_token *>(tokens.data()) + offset, count);
        if (llama_decode(context, batch) != 0) throw std::runtime_error("Falha ao processar o contexto");
        context_tokens += count;
    }
}

std::string token_piece(llama_token token) {
    const llama_vocab * vocab = llama_model_get_vocab(model);
    std::vector<char> piece(256);
    int size = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, true);
    if (size < 0) {
        piece.resize(static_cast<size_t>(-size));
        size = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, true);
    }
    if (size < 0) throw std::runtime_error("Falha ao converter token em texto");
    return {piece.data(), static_cast<size_t>(size)};
}

size_t complete_utf8_prefix(const std::string & value) {
    size_t index = 0;
    while (index < value.size()) {
        const auto lead = static_cast<unsigned char>(value[index]);
        size_t length = 1;
        if ((lead & 0x80U) == 0) length = 1;
        else if ((lead & 0xE0U) == 0xC0U) length = 2;
        else if ((lead & 0xF0U) == 0xE0U) length = 3;
        else if ((lead & 0xF8U) == 0xF0U) length = 4;
        if (index + length > value.size()) break;

        bool valid = true;
        for (size_t offset = 1; offset < length; ++offset) {
            if ((static_cast<unsigned char>(value[index + offset]) & 0xC0U) != 0x80U) {
                valid = false;
                break;
            }
        }
        index += valid ? length : 1;
    }
    return index;
}

void remove_oldest_turn() {
    if (messages.size() <= 1) return;
    messages.erase(messages.begin());
    if (!messages.empty() && messages.front().role == "assistant") {
        messages.erase(messages.begin());
    }
}

std::vector<llama_token> prepare_prompt(int requested_context_size, int reserve_tokens) {
    if (context == nullptr || context_size != std::max(requested_context_size, 512)) {
        free_context();
        create_context(requested_context_size);
    }

    while (true) {
        generation_prefix = format_messages(true);
        const bool can_use_cache = previous_formatted_length <= generation_prefix.size() && context_tokens > 0;
        const std::string incremental = can_use_cache
            ? generation_prefix.substr(previous_formatted_length)
            : generation_prefix;
        auto tokens = tokenize(incremental, !can_use_cache);
        if (context_tokens + static_cast<int>(tokens.size()) + reserve_tokens <= context_size) {
            return tokens;
        }

        if (messages.size() <= 1) {
            throw std::runtime_error("A mensagem é grande demais para o contexto configurado");
        }
        remove_oldest_turn();
        free_context();
        create_context(requested_context_size);
    }
}

void finish_generation() {
    if (!generation_active) return;

    messages.push_back({"assistant", generated_response});
    const std::string final_formatted = format_messages(false);
    const std::string consumed = generation_prefix + generated_response;
    if (final_formatted.size() >= consumed.size() && final_formatted.compare(0, consumed.size(), consumed) == 0) {
        const std::string closing = final_formatted.substr(consumed.size());
        if (!closing.empty()) {
            auto closing_tokens = tokenize(closing, false);
            if (context_tokens + static_cast<int>(closing_tokens.size()) <= context_size) {
                decode(closing_tokens);
            } else {
                free_context();
            }
        }
    } else {
        free_context();
    }
    previous_formatted_length = final_formatted.size();
    free_generation();
}

void begin_generation(
    const std::string & user_prompt,
    int max_tokens,
    int requested_context_size,
    float temperature,
    float top_p,
    float repeat_penalty
) {
    if (model == nullptr) throw std::runtime_error("O modelo ainda não foi carregado");
    if (generation_active) throw std::runtime_error("Já existe uma geração em andamento");
    if (user_prompt.empty()) throw std::runtime_error("O prompt está vazio");

    last_generation_token_limited = false;
    messages.push_back({"user", user_prompt + "\n/no_think"});
    try {
        const int reserve = std::max(max_tokens, 1) + 16;
        auto prompt_tokens = prepare_prompt(requested_context_size, reserve);
        decode(prompt_tokens);

        sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        llama_sampler_chain_add(sampler, llama_sampler_init_penalties(96, std::max(repeat_penalty, 1.0f), 0.0f, 0.0f));
        if (temperature <= 0.0f) {
            llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
        } else {
            llama_sampler_chain_add(sampler, llama_sampler_init_top_p(std::clamp(top_p, 0.05f, 1.0f), 1));
            llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
            llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
        }
        max_generation_tokens = std::max(max_tokens, 1);
        generated_tokens = 0;
        generated_response.clear();
        generation_active = true;
    } catch (...) {
        if (!messages.empty() && messages.back().role == "user") messages.pop_back();
        free_generation();
        throw;
    }
}

bool next_token(std::string & piece) {
    if (!generation_active) return false;
    if (generated_tokens >= max_generation_tokens) {
        last_generation_token_limited = true;
        piece = std::move(utf8_pending);
        utf8_pending.clear();
        finish_generation();
        return !piece.empty();
    }

    const llama_vocab * vocab = llama_model_get_vocab(model);
    const llama_token token = llama_sampler_sample(sampler, context, -1);
    if (llama_vocab_is_eog(vocab, token)) {
        piece = std::move(utf8_pending);
        utf8_pending.clear();
        finish_generation();
        return !piece.empty();
    }

    const std::string raw_piece = token_piece(token);
    generated_response += raw_piece;
    utf8_pending += raw_piece;
    std::vector<llama_token> token_batch{token};
    decode(token_batch);
    ++generated_tokens;
    const size_t complete_size = complete_utf8_prefix(utf8_pending);
    piece = utf8_pending.substr(0, complete_size);
    utf8_pending.erase(0, complete_size);
    return true;
}

void reset_conversation() {
    messages.clear();
    last_generation_token_limited = false;
    free_context();
}

void truncate_conversation(int message_count) {
    if (message_count < 0) throw std::runtime_error("Quantidade de mensagens inválida");
    const auto keep = std::min(static_cast<size_t>(message_count), messages.size());
    messages.resize(keep);
    free_context();
}
} // namespace

extern "C" JNIEXPORT void JNICALL
Java_org_sattory_app_llm_NativeLlmBridge_nativeLoad(JNIEnv * env, jobject, jstring model_path) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    try {
        if (model != nullptr) return;
        ensure_backend_initialized();
        const char * path = env->GetStringUTFChars(model_path, nullptr);
        llama_model_params params = llama_model_default_params();
        params.n_gpu_layers = 0;
        model = llama_model_load_from_file(path, params);
        env->ReleaseStringUTFChars(model_path, path);
        if (model == nullptr) throw std::runtime_error("Não foi possível carregar o arquivo GGUF");
    } catch (const std::exception & error) {
        throw_java(env, error.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_org_sattory_app_llm_NativeLlmBridge_nativeBeginGeneration(
    JNIEnv * env,
    jobject,
    jbyteArray prompt,
    jint max_tokens,
    jint requested_context_size,
    jfloat temperature,
    jfloat top_p,
    jfloat repeat_penalty,
    jboolean isolated_context
) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    try {
        if (isolated_context) {
            if (generation_active) throw std::runtime_error("Ja existe uma geracao em andamento");
            messages.clear();
            free_context();
        }
        begin_generation(
            from_utf8_bytes(env, prompt),
            max_tokens,
            requested_context_size,
            temperature,
            top_p,
            repeat_penalty
        );
    } catch (const std::exception & error) {
        throw_java(env, error.what());
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_org_sattory_app_llm_NativeLlmBridge_nativeCountPromptTokens(JNIEnv * env, jobject, jbyteArray prompt) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    auto saved_messages = std::move(messages);
    try {
        if (model == nullptr) throw std::runtime_error("Modelo local nao carregado");
        messages = {{"user", from_utf8_bytes(env, prompt) + "\n/no_think"}};
        const auto formatted = format_messages(true);
        const auto count = static_cast<jint>(tokenize(formatted, true).size());
        messages = std::move(saved_messages);
        return count;
    } catch (const std::exception & error) {
        messages = std::move(saved_messages);
        throw_java(env, error.what());
        return 0;
    }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_org_sattory_app_llm_NativeLlmBridge_nativeNextToken(JNIEnv * env, jobject) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    try {
        std::string piece;
        return next_token(piece) ? to_utf8_bytes(env, piece) : nullptr;
    } catch (const std::exception & error) {
        reset_conversation();
        throw_java(env, error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_org_sattory_app_llm_NativeLlmBridge_nativeCancelGeneration(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    last_generation_token_limited = false;
    finish_generation();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_sattory_app_llm_NativeLlmBridge_nativeWasLastGenerationTokenLimited(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    return last_generation_token_limited ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_org_sattory_app_llm_NativeLlmBridge_nativeResetConversation(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    reset_conversation();
}

extern "C" JNIEXPORT void JNICALL
Java_org_sattory_app_llm_NativeLlmBridge_nativeTruncateConversation(
    JNIEnv * env, jobject, jint message_count
) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    try {
        truncate_conversation(message_count);
    } catch (const std::exception & error) {
        throw_java(env, error.what());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_org_sattory_app_llm_NativeLlmBridge_nativeClose(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    reset_conversation();
    if (model != nullptr) {
        llama_model_free(model);
        model = nullptr;
    }
    if (backend_initialized && embedding_model == nullptr) {
        llama_backend_free();
        backend_initialized = false;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_org_sattory_app_rag_NativeEmbeddingBridge_nativeLoadEmbedding(
    JNIEnv * env, jobject, jstring model_path
) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    try {
        const char * path = env->GetStringUTFChars(model_path, nullptr);
        const std::string path_string(path);
        env->ReleaseStringUTFChars(model_path, path);
        load_embedding_model(path_string);
    } catch (const std::exception & error) {
        throw_java(env, error.what());
    }
}

extern "C" JNIEXPORT jint JNICALL
Java_org_sattory_app_rag_NativeEmbeddingBridge_nativeCountEmbeddingTokens(JNIEnv * env, jobject, jbyteArray text) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    try {
        if (embedding_model == nullptr) throw std::runtime_error("Modelo de embeddings nao carregado");
        return static_cast<jint>(tokenize_with_model(embedding_model, from_utf8_bytes(env, text), true).size());
    } catch (const std::exception & error) {
        throw_java(env, error.what());
        return 0;
    }
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_org_sattory_app_rag_NativeEmbeddingBridge_nativeEmbed(
    JNIEnv * env, jobject, jbyteArray text
) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    try {
        return to_float_array(env, embed_text(from_utf8_bytes(env, text)));
    } catch (const std::exception & error) {
        throw_java(env, error.what());
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_org_sattory_app_rag_NativeEmbeddingBridge_nativeCloseEmbedding(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(engine_mutex);
    free_embedding_context();
    if (embedding_model != nullptr) {
        llama_model_free(embedding_model);
        embedding_model = nullptr;
    }
    if (backend_initialized && model == nullptr) {
        llama_backend_free();
        backend_initialized = false;
    }
}
