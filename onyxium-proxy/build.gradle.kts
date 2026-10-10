plugins {
    alias(libs.plugins.shadow)
    id("com.gorylenko.gradle-git-properties") version "4.0.1"
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
    implementation(libs.picocli)
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
    testImplementation(libs.assertj)
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

gitProperties {
    keys = listOf(
        "git.build.version",
        "git.commit.id",
        "git.commit.id.abbrev",
        "git.dirty"
    )
}

val proxyRunDirectory = rootProject.layout.projectDirectory.dir("run/proxy")

val prepareProxyRunDirectory = tasks.register("prepareProxyRunDirectory") {
    val directory = proxyRunDirectory
    outputs.dir(directory)
    doLast {
        directory.asFile.mkdirs()
    }
}

tasks.register<JavaExec>("runProxy") {
    group = "application"
    description = "Builds and runs the proxy from run/proxy."
    dependsOn(prepareProxyRunDirectory)
    classpath(tasks.shadowJar)
    mainClass = "dev.onyxium.proxy.AppBootstrap"
    javaLauncher = javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(25)
    }
    workingDir(proxyRunDirectory)
    standardInput = System.`in`
    jvmArgs(nativeAccess)
}
