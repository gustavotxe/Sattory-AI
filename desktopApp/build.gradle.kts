import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "org.sattory.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "org.sattory.app"
            packageVersion = "1.0.0"
        }
    }
}

val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
val isMac = System.getProperty("os.name").startsWith("Mac", ignoreCase = true)
val nativeBuildDir = layout.buildDirectory.dir(if (isWindows) "native-msvc" else "native")
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}
val sdkDir = localProperties.getProperty("sdk.dir")?.let(::file)
val bundledCmake = sdkDir?.resolve("cmake/3.31.0/bin/cmake.exe")
val cmakeCommand = bundledCmake?.takeIf { it.isFile }?.absolutePath ?: "cmake"
val bundledNinja = sdkDir?.resolve("cmake/3.31.0/bin/ninja.exe")
val javaHome = file(System.getProperty("java.home")).let { home ->
    if (home.resolve("include/jni.h").isFile) home else home.parentFile
}.absolutePath.replace('\\', '/')
val nativeLibraryRelativePath = when {
    isWindows -> "Release/sattory_llm.dll"
    isMac -> "libsattory_llm.dylib"
    else -> "libsattory_llm.so"
}

val configureNative by tasks.registering(Exec::class) {
    val outputDir = nativeBuildDir.get().asFile
    inputs.file(rootProject.file("native/CMakeLists.txt"))
    inputs.file(rootProject.file("native/native_bridge.cpp"))
    inputs.dir(rootProject.file("third_party/llama.cpp"))
    outputs.file(outputDir.resolve("CMakeCache.txt"))
    doFirst { outputDir.mkdirs() }
    val generatorArguments = if (isWindows) {
        listOf("-G", "Visual Studio 17 2022", "-A", "x64")
    } else {
        listOf("-G", "Ninja", "-DCMAKE_MAKE_PROGRAM=${bundledNinja?.absolutePath ?: "ninja"}")
    }
    commandLine(listOf(
        cmakeCommand,
        "-S", rootProject.file("native").absolutePath,
        "-B", outputDir.absolutePath,
    ) + generatorArguments + listOf(
        "-DCMAKE_BUILD_TYPE=Release",
        "-DJAVA_HOME=$javaHome",
    ))
}

val buildNative by tasks.registering(Exec::class) {
    dependsOn(configureNative)
    val outputDir = nativeBuildDir.get().asFile
    inputs.file(outputDir.resolve("CMakeCache.txt"))
    inputs.file(rootProject.file("native/native_bridge.cpp"))
    inputs.dir(rootProject.file("third_party/llama.cpp"))
    outputs.file(outputDir.resolve(nativeLibraryRelativePath))
    commandLine(cmakeCommand, "--build", outputDir.absolutePath, "--config", "Release", "--target", "sattory_llm")
}

tasks.withType<JavaExec>().configureEach {
    dependsOn(buildNative)
    systemProperty("sattory.native.library", nativeBuildDir.get().file(nativeLibraryRelativePath).asFile.absolutePath)
    systemProperty(
        "sattory.model.path",
        providers.gradleProperty("sattoryModelPath")
            .getOrElse(rootProject.file("local-models/qwen/qwen3-1.7b-q4_k_m.gguf").absolutePath),
    )
}

tasks.register<JavaExec>("nativeSmokeTest") {
    group = "verification"
    description = "Carrega o GGUF local e gera uma resposta curta usando o binding Desktop."
    dependsOn("classes", buildNative)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.sattory.app.MainKt")
    args("--smoke-test")
}
