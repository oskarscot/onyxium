import gg.ginco.hygradle.tasks.GenerateManifestTask

plugins {
    alias(libs.plugins.ginco.hygradle)
}

repositories {
    maven("https://maven.hytale.com/pre-release")
}

dependencies {
    implementation(project(":onyxium-forwarding"))
    testImplementation(libs.junit)
    testImplementation(project(":onyxium-proxy"))
    testImplementation(project(":onyxium-api"))
    testRuntimeOnly("com.hypixel.hytale:Server:${providers.gradleProperty("hytaleServerVersion").orElse(libs.versions.hytale.server.get()).get()}")
}

// Hytale loads a single plugin jar, so include the shared forwarding implementation.
tasks.jar {
    dependsOn(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.map { files -> files.map { if (it.isDirectory) it else zipTree(it) } }) {
        exclude("META-INF/MANIFEST.MF", "module-info.class", "META-INF/versions/**/module-info.class")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Compatibility is checked against the running server's CRC by ForwardingFilter.
tasks.named<GenerateManifestTask>("generatePluginManifest") {
    serverVersion.set("*")
}

tasks.test {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty("java.util.logging.manager", "com.hypixel.hytale.logger.backend.HytaleLogManager")
}

hytale {
    group = "Onyxium"
    mainClass = "dev.onyxium.backend.BackendPlugin"
    serverVersion = providers.gradleProperty("hytaleServerVersion").orElse(libs.versions.hytale.server.get()).get()

    name = "ProxyBackend"
    description = "Backend handler for the onyxium proxy"
    website = "https://onyxium.dev"
    author("oskarscot")
}
