This is a Kotlin Multiplatform project targeting Android, Desktop (JVM).

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
  - Hot reload: `./gradlew :desktopApp:hotRun --auto`
  - Standard run: `./gradlew :desktopApp:run`

### Local LLM (llama.cpp)

Initialize the pinned native dependency after cloning:

```shell
git submodule update --init --recursive
```

Desktop uses `local-models/qwen/qwen3-1.7b-q4_k_m.gguf` by default. Override it when needed:

```shell
./gradlew :desktopApp:run -PsattoryModelPath=/absolute/path/model.gguf
```

Run a short, non-UI inference check with:

```shell
./gradlew :desktopApp:nativeSmokeTest
```

Android intentionally does not package the 1.2 GB model in the APK. On first use, WorkManager downloads it
to `files/models/qwen3-1.7b-q4_k_m.gguf`, verifies its SHA-256, and only then enables the chat. Missing or
invalid files are downloaded again. The APK builds native CPU libraries for `arm64-v8a` and `x86_64`.

Inference is streamed token by token through a cancellable Kotlin `Flow`. The native session retains the
conversation and KV cache between turns; starting a new conversation explicitly resets both.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:jvmTest`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
