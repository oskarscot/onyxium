pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("gg.ginco.hygradle.settings") version "0.3.1"
}

rootProject.name = "Onyxium"
include(
    "onyxium-proxy",
    "onyxium-backend"
)
