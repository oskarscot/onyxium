plugins {
    `java-library`
}

dependencies {
    api(libs.guava)
    api(libs.slf4j.api)
    api(project(":onyxium-eventbus"))

    testImplementation(libs.junit)
}
