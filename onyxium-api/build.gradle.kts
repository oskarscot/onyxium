plugins {
    `java-library`
}

dependencies {
    api(libs.guava)
    api(libs.slf4j.api)

    testImplementation(libs.junit)
}
