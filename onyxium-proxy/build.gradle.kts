plugins {
    alias(libs.plugins.shadow)
}

abstract class MockitoAgentProvider : CommandLineArgumentProvider {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val agentJar: ConfigurableFileCollection

    override fun asArguments(): Iterable<String> = listOf("-javaagent:${agentJar.singleFile.absolutePath}")
}

val mockitoAgent = configurations.create("mockitoAgent")

val quicNativePlatforms = listOf(
    "linux-x86_64",
    "linux-aarch_64",
    "osx-x86_64",
    "osx-aarch_64",
    "windows-x86_64",
)

dependencies {
    implementation(project(":onyxium-api"))
    implementation(project(":onyxium-forwarding"))
    implementation(libs.okaeri.configs.yaml)
    implementation(libs.okaeri.configs.validator)
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.fastutil)
    implementation(libs.nimbus.jose.jwt)

    implementation(platform(libs.netty.bom))
    implementation(libs.netty.transport)
    implementation(libs.netty.handler)
    implementation(libs.netty.codec.quic)

    quicNativePlatforms.forEach { platform ->
        runtimeOnly(variantOf(libs.netty.codec.quic.native) { classifier(platform) })
    }

    runtimeOnly(libs.logback.classic)

    testImplementation(libs.junit)
    testImplementation(libs.mockito)
    mockitoAgent(libs.mockito) { isTransitive = false }
}

val nativeAccess = listOf("--enable-native-access=ALL-UNNAMED")

tasks.jar {
    archiveClassifier = "plain"
}

tasks.shadowJar {
    archiveClassifier = ""
    manifest.attributes(
        "Main-Class" to "dev.onyxium.proxy.AppBootstrap",
        "Enable-Native-Access" to "ALL-UNNAMED",
    )
    mergeServiceFiles()
    filesMatching("META-INF/services/**") {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
    }
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.test {
    jvmArgs(nativeAccess)
    jvmArgumentProviders.add(objects.newInstance<MockitoAgentProvider>().apply {
        agentJar.from(mockitoAgent)
    })
}
