plugins {
    `java-library`
}

dependencies {
    api(libs.guava)
    api(libs.slf4j.api)
    api(project(":onyxium-eventbus"))
    api(project(":onyxium-command"))

    testImplementation(libs.junit)
    testImplementation(libs.assertj)
}
