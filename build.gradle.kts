import com.diffplug.gradle.spotless.SpotlessExtension
import com.diffplug.spotless.FormatterFunc
import com.diffplug.spotless.LineEnding
import io.spring.javaformat.formatter.StreamsFormatter
import java.io.Serializable
import java.io.StringReader

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        val springJavaFormatVersion = "0.0.48"
        classpath("io.spring.javaformat:spring-javaformat-formatter:$springJavaFormatVersion")
        classpath("io.spring.javaformat:spring-javaformat-formatter-eclipse-runtime:$springJavaFormatVersion")
    }
}

plugins {
    java
    alias(libs.plugins.spotless)
}

class SpringJavaFormatter : FormatterFunc, Serializable {
    override fun apply(source: String): String {
        return StreamsFormatter().format(StringReader(source), "\n").formattedContent
    }
}

allprojects {
    apply(plugin = "com.diffplug.spotless")

    repositories {
        mavenCentral()
        maven("https://repo.okaeri.cloud/releases") {
            content { includeGroup("eu.okaeri") }
        }
    }

    extensions.configure<SpotlessExtension> {
        lineEndings = LineEnding.UNIX
        java {
            target("src/*/java/**/*.java")
            shortenFullyQualifiedTypes()
            removeUnusedImports()
            importOrder("java", "javax", "", "dev.onyxium", "#")
            forbidWildcardImports()
            bumpThisNumberIfACustomStepChanges(1)
            custom("springJavaFormat-0.0.48", SpringJavaFormatter())
            trimTrailingWhitespace()
            endWithNewline()
        }
        format("buildFiles") {
            target("*.gradle.kts", "gradle.properties", "gradle/*.toml")
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}

tasks.register("lint") {
    group = "verification"
    description = "Checks formatting and import conventions in every module."
    dependsOn(allprojects.map { it.tasks.named("spotlessCheck") })
}

tasks.named("check") {
    dependsOn("lint")
}

subprojects {
    apply(plugin = "java")

    group = "dev.onyxium"
    version = rootProject.version

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

    // Command argument names come from reflection rather than duplicate annotation attributes.
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-parameters")
    }
}
