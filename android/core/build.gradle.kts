import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Capri's engine: pure Kotlin on the JVM, no Android classes, so it runs and is tested off the phone.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
