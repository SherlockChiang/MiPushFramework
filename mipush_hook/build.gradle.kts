import io.freefair.gradle.plugins.aspectj.AspectjCompile

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("io.freefair.android.aspectj.post-compile-weaving")
}

val mipushLib = file("libs/miuipushsdkshared_3_7_9.jar")
extra["mipushLib"] = mipushLib

android {
    namespace = "com.nihility.mipush_hook"
    compileSdk = 34

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

}

val aspectjTools by configurations.creating

tasks.withType<AspectjCompile>().configureEach {
    aspectjClasspath.from(aspectjTools)
    // The legacy Xiaomi SDK is the only external inpath. Keep Android APIs on
    // ajc's boot class path instead of treating the entire dependency graph as
    // weave input.
    ajcOptions.bootclasspath.from(android.bootClasspath)
    ajcOptions.compilerArgs = listOf("-Xlint:ignore")
}

dependencies {
    inpath(files(mipushLib))
    compileOnly(files(mipushLib))
    implementation("org.aspectj:aspectjrt:1.9.22.1")
    // Keep the runtime jar visible to ajc's type resolver as well as the APK.
    aspectjTools("org.aspectj:aspectjrt:1.9.22.1")
    aspectjTools("org.aspectj:aspectjtools:1.9.22.1")
    implementation("androidx.startup:startup-runtime:1.1.1")

    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.8.0")
    implementation(project(":common"))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
