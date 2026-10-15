plugins {
    id("com.android.application") version "8.11.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
}


// Executa a suíte existente sem instalar outro compilador Kotlin no runner.
val protocolCompiler by configurations.creating
val protocolRuntime by configurations.creating
dependencies {
    protocolCompiler("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.2.20")
    protocolRuntime("org.jetbrains.kotlin:kotlin-stdlib:2.2.20")
}
val protocolClasses = layout.buildDirectory.dir("protocol-tests/classes")
val protocolSources = files(
    fileTree("tests") { include("**/*.kt") },
    listOf("NetUtil", "OnvifClient", "RtspProbe", "RtspCodec", "WsDiscovery", "RecordingFiles", "VideoGeometry", "OnvifIdentity", "PhoneCameraUrls", "Nv21Frames", "PhoneCameraServer", "PhoneBatteryReading", "PhoneStreamStats", "PhoneEcoPolicy", "PhoneAvcAnnexB").map {
        "app/src/main/java/com/minhacamera/$it.kt"
    }
)
val compileProtocolTests by tasks.registering(JavaExec::class) {
    classpath = protocolCompiler
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
    inputs.files(protocolSources)
    inputs.files(protocolRuntime)
    outputs.dir(protocolClasses)
    doFirst {
        delete(protocolClasses)
        args = listOf("-no-stdlib", "-no-reflect", "-jvm-target", "17",
            "-classpath", protocolRuntime.asPath, "-d", protocolClasses.get().asFile.absolutePath) +
            protocolSources.files.sortedBy { it.path }.map { it.absolutePath }
    }
}
tasks.register<JavaExec>("testProtocol") {
    dependsOn(compileProtocolTests)
    classpath = files(protocolClasses) + protocolRuntime
    mainClass.set("com.minhacamera.ProtocolSmokeKt")
}
