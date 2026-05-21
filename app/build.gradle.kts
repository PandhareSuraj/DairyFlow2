import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun localString(key: String): String = localProperties.getProperty(key)
    ?: providers.gradleProperty(key).orNull
    ?: ""

android {
    namespace = "com.example.dairyflow2"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.dairyflow2"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["GOOGLE_MAPS_API_KEY"] = localString("GOOGLE_MAPS_API_KEY")
        buildConfigField("String", "SUPABASE_URL", "\"${localString("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localString("SUPABASE_ANON_KEY")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

fun registerIdeApkRedirectMirror(variantName: String) {
    val capitalizedVariant = variantName.replaceFirstChar { it.uppercaseChar() }
    val createRedirectTaskName = "create${capitalizedVariant}ApkListingFileRedirect"
    val mirrorTask = tasks.register("mirror${capitalizedVariant}IdeApkListing") {
        dependsOn(createRedirectTaskName)
        doLast {
            val generatedRedirectDir = layout.buildDirectory
                .dir("intermediates/apk_ide_redirect_file/$variantName/$createRedirectTaskName")
                .get()
                .asFile
            val generatedApkDir = layout.buildDirectory
                .dir("intermediates/apk/$variantName")
                .get()
                .asFile

            val ideRedirectDir = projectDir.resolve("build/intermediates/apk_ide_redirect_file/$variantName/$createRedirectTaskName")
            val ideApkDir = projectDir.resolve("build/intermediates/apk/$variantName")
            ideRedirectDir.mkdirs()
            ideApkDir.mkdirs()

            generatedRedirectDir.resolve("redirect.txt")
                .copyTo(ideRedirectDir.resolve("redirect.txt"), overwrite = true)
            generatedApkDir.listFiles()?.filter { it.isFile }?.forEach { file ->
                file.copyTo(ideApkDir.resolve(file.name), overwrite = true)
            }
        }
    }
    tasks.named(createRedirectTaskName).configure {
        finalizedBy(mirrorTask)
    }
}

afterEvaluate {
    registerIdeApkRedirectMirror("debug")
    registerIdeApkRedirectMirror("release")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(platform(libs.supabase.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.google.maps.compose)
    implementation(libs.ktor.client.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.supabase.auth)
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    ksp(libs.androidx.room.compiler)
    coreLibraryDesugaring(libs.android.desugar.jdk.libs)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
