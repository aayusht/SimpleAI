import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotlinSerialization)
}

room {
    schemaDirectory("$projectDir/schemas")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    val xcframeworksPath = File(project.rootProject.projectDir, "iosApp/build/ios_xcframework")
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        val platformSubdir = when {
            iosTarget.name.contains("Simulator", ignoreCase = true) -> "ios-arm64-simulator"
            else -> "ios-arm64"
        }
        
        // Configure the compilation to include C interop
        iosTarget.compilations.getByName("main") {
            val LiteRtLm by cinterops.creating {
                definitionFile.set(project.file("src/nativeInterop/cinterop/LiteRtLm.def"))
                
                val liteRtLmPath = File(xcframeworksPath, "LiteRtLm.xcframework/$platformSubdir")
                val gemmaPath = File(xcframeworksPath, "GemmaModelConstraintProvider.xcframework/$platformSubdir")
                
                compilerOpts(
                    "-F${liteRtLmPath.absolutePath}",
                    "-F${gemmaPath.absolutePath}"
                )
            }
        }
        
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            
            val liteRtLmPath = File(xcframeworksPath, "LiteRtLm.xcframework/$platformSubdir")
            val gemmaPath = File(xcframeworksPath, "GemmaModelConstraintProvider.xcframework/$platformSubdir")
            
            linkerOpts(
                "-F${liteRtLmPath.absolutePath}",
                "-F${gemmaPath.absolutePath}",
                "-framework", "LiteRtLm",
                "-framework", "GemmaModelConstraintProvider"
            )
            linkerOpts.add("-lsqlite3")
        }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.koin.android)
            implementation(libs.gson)
            implementation(files("./libs/litertlm-android-modified.aar"))
            implementation(kotlin("reflect"))  // Required by AAR's ToolManager for runtime reflection
            implementation(libs.androidx.room.sqlite.wrapper)
            implementation(libs.camera.camera2)
            implementation(libs.camera.lifecycle)
            implementation(libs.camera.view)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.preview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            
            implementation(libs.ksoup)
            implementation(libs.ktor.client.core)
            implementation(libs.okio)
            implementation(libs.kermit)
            
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.markdown)
            implementation(libs.markdown.code)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.serialization.json)
            
            // Media libraries
            implementation(libs.peekaboo.ui)
            implementation(libs.peekaboo.image.picker)
            implementation(libs.kmp.record)
            implementation(libs.gadulka)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "com.aayush.simpleai"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.aayush.simpleai"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(compose.uiTooling)
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
}
