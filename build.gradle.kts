// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

val externalBuildRoot = file(System.getProperty("user.home"))
    .resolve(".gradle-dairyflow2-build")
    .resolve(rootProject.name)

layout.buildDirectory.set(externalBuildRoot.resolve("root"))

subprojects {
    layout.buildDirectory.set(externalBuildRoot.resolve(path.removePrefix(":").replace(":", "-")))
}
