plugins {
    alias(libs.plugins.android).apply(false)
    alias(libs.plugins.kotlinAndroid).apply(false)
    alias(libs.plugins.kotlinSerialization).apply(false)
    alias(libs.plugins.ksp).apply(false)
    alias(libs.plugins.detekt).apply(false)

    // ✅ AGREGAR ESTO
    id("com.google.gms.google-services") version "4.4.0" apply false
}

tasks.register<Delete>("clean") {
    delete {
        rootProject.buildDir
    }
}
