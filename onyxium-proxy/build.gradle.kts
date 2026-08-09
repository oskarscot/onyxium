plugins {
    application
}

dependencies {
    implementation(project(":onyxium-api"))
    implementation(libs.bouncycastle.prov)
    implementation(libs.bouncycastle.pkix)

    testImplementation(libs.junit)
}

application {
    mainClass = "dev.onyxium.proxy.AppBootstrap"
}
