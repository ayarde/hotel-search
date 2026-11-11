plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    id("java")
}

group = "com.hotelsearch"
version = "0.0.1"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)

    testImplementation(libs.spring.boot.starter.test)
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
