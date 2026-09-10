plugins {
    alias(libs.plugins.ginco.hygradle)
}

repositories {
    maven("https://maven.hytale.com/pre-release")
}

dependencies {
    testImplementation(libs.junit)
}

hytale {
    group = "Onyxium"
    mainClass = "dev.onyxium.backend.BackendPlugin"
    serverVersion = libs.versions.hytale.server.get()

    name = "ProxyBackend"
    description = "Backend handler for the onyxium proxy"
    website = "https://onyxium.dev"
    author("oskarscot")
}
