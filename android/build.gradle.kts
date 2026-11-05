// Versions checked against developer.android.com/build/releases/gradle-plugin (AGP 8.13.2 supports compileSdk 36,
// needs Gradle 8.13+ and JDK 17+).
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.21" apply false
}
