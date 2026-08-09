plugins {
    java
}

subprojects {
    apply(plugin = "java")

    group = "dev.onyxium"
    version = "0.0.1-SNAPSHOT"

    repositories {
        mavenCentral()
    }

    dependencies {
        compileOnly("org.jetbrains:annotations:26.1.0")
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }
}