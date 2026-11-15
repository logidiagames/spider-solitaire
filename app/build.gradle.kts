plugins {
    id("com.android.application")
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    // Uploads the R8 mapping of minified builds, so crash reports have readable stack traces.
    apply(plugin = "com.google.firebase.crashlytics")
}

android {
    namespace = "com.nexusplaylabss.spider"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nexusplaylabss.spider"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "UMP_TEST_DEVICE_IDS", "\"\"")
        // Test tools: the DEV panel, Google's test ad units and the pretend store (debug and qa).
        buildConfigField("boolean", "TEST_TOOLS", "false")
        // Crashlytics collects in release and qa; never in debug.
        manifestPlaceholders["crashlyticsCollectionEnabled"] = "true"
    }

    buildFeatures {
        buildConfig = true
    }

    // Comma-separated UMP hashed device IDs (printed in logcat by UserMessagingPlatform) that get
    // the EEA debug geography in debug builds. Set it outside the repo, e.g. in
    // ~/.gradle/gradle.properties: freecellUmpTestDeviceIds=ABC123...,DEF456...
    val umpTestDeviceIds = providers.gradleProperty("freecellUmpTestDeviceIds").getOrElse("")
        .split(",").map { it.trim() }.filter { it.matches(Regex("[0-9A-Fa-f]+")) }
        .joinToString(",")

    val uploadKeystorePath = providers.environmentVariable("UPLOAD_KEYSTORE").orNull
    val uploadKeystorePassword = providers.environmentVariable("UPLOAD_KEYSTORE_PASSWORD").orNull
    val uploadKeyAlias = providers.environmentVariable("UPLOAD_KEY_ALIAS").orNull
    val uploadKeyPassword = providers.environmentVariable("UPLOAD_KEY_PASSWORD").orNull

    signingConfigs {
        if (listOf(uploadKeystorePath, uploadKeystorePassword, uploadKeyAlias, uploadKeyPassword).all { it != null }) {
            create("release") {
                storeFile = file(uploadKeystorePath!!)
                storePassword = uploadKeystorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "UMP_TEST_DEVICE_IDS", "\"$umpTestDeviceIds\"")
            buildConfigField("boolean", "TEST_TOOLS", "true")
            manifestPlaceholders["crashlyticsCollectionEnabled"] = "false"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        // A release build (R8, Crashlytics on) that keeps the test tools, signed with the local
        // debug key so it installs from a computer: for the Crashlytics test crash and the R8
        // launch check before an AAB. Never uploaded to Google Play.
        create("qa") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "TEST_TOOLS", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// The game's pure rules live in assets/freecell.html and are tested with Node's built-in test
// runner. Without Node the task is skipped, so the Java tests still run anywhere. Windows names it node.exe.
val nodeExecutable: File? = (System.getenv("PATH").orEmpty().split(File.pathSeparator).flatMap { listOf(File(it, "node"), File(it, "node.exe")) } +
    listOf(File("/opt/homebrew/bin/node"), File("/usr/local/bin/node"))).firstOrNull { it.canExecute() }
val jsUnitTest by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the JavaScript unit tests in src/test/js with Node."
    onlyIf("Node is installed") { nodeExecutable != null }
    workingDir = projectDir
    environment("TZ", "Europe/Lisbon")
    val testFiles = fileTree("src/test/js") { include("*.test.mjs") }
    inputs.files(testFiles, "src/main/assets/freecell.html", fileTree("src/main/assets") { include("*_defaults.json") })
    commandLine(listOf(nodeExecutable?.path ?: "node", "--test") + testFiles.files.map { it.path }.sorted())
}
tasks.withType<Test>().configureEach { dependsOn(jsUnitTest) }

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.android.gms:play-services-ads:25.5.0")
    implementation("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation("com.android.billingclient:billing:9.1.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

