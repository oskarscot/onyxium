plugins {
    application
}

val quicNativePlatforms = listOf(
    "linux-x86_64",
    "linux-aarch_64",
    "osx-x86_64",
    "osx-aarch_64",
    "windows-x86_64",
)

dependencies {
    implementation(project(":onyxium-api"))
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)
    implementation(libs.fastutil)

    implementation(platform(libs.netty.bom))
    implementation(libs.netty.transport)
    implementation(libs.netty.handler)
    implementation(libs.netty.codec.quic)

    quicNativePlatforms.forEach { platform ->
        runtimeOnly(variantOf(libs.netty.codec.quic.native) { classifier(platform) })
    }

    runtimeOnly(libs.logback.classic)

    testImplementation(libs.junit)
}

val nativeAccess = listOf("--enable-native-access=ALL-UNNAMED")

application {
    mainClass = "dev.onyxium.proxy.AppBootstrap"
    applicationDefaultJvmArgs = nativeAccess
}

tasks.test {
    jvmArgs(nativeAccess)
}
