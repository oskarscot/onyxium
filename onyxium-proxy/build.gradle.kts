plugins {
    application
}

dependencies {
    implementation(libs.guava)
    implementation(libs.kwik)

    testImplementation(libs.junit)
}

application {
    mainClass = "dev.onyxium.proxy.App"
}
