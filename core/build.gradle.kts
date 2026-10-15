// Pure Kotlin: the nutrient catalog, Livsmedelsverket parsing and every nutrient rule.
// The sync CLI and the Android app both use it, so the rules are written and tested once.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin { jvmToolchain(17) }

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("junit:junit:4.13.2")
}
