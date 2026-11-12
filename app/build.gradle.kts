import com.google.firebase.crashlytics.buildtools.gradle.CrashlyticsExtension
import java.util.Properties
import java.util.zip.ZipFile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.kover)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.play.publisher)
}

// The google-services plugin's own "File google-services.json is missing" error does not
// say where the file goes or where to get it. Fail earlier, with instructions.
require(file("google-services.json").exists()) {
    """
    app/google-services.json is missing — the build cannot configure Firebase.
    Register THIS app's applicationId in the Firebase console (Project settings > Your
    apps > Add app > Android), download the file and place it at app/google-services.json.
    See app/google-services.json.example for the shape.
    NEVER copy another app's file: Firebase matches on package_name, and a mismatched
    file sends this app's analytics into the other project.
    It is committed to VCS on purpose: it is configuration, not a credential — the API
    key inside is restricted by package name + signing certificate.
    """.trimIndent()
}

// Secrets kept out of VCS (UMP test device hash for EEA consent testing).
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// AdMob IDs (features.md > AdMob IDs). admob.properties is git-ignored; the committed
// admob.properties.example holds the placeholders. Debug always uses Google's test IDs.
// Release uses the real IDs; while one is still a XXXX placeholder it falls back to the
// test ID with a warning - such a build must never reach production (spec CLAUDE.md 8.6).
val admobProperties = Properties().apply {
    val real = rootProject.file("admob.properties")
    val example = rootProject.file("admob.properties.example")
    (if (real.exists()) real else example).takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val admobTestIds = mapOf(
    "ADMOB_APP_ID" to "ca-app-pub-3940256099942544~3347511713",
    "AD_NATIVE_LIBRARY" to "ca-app-pub-3940256099942544/2247696110",
    "AD_NATIVE_IMPORT" to "ca-app-pub-3940256099942544/2247696110",
    "AD_NATIVE_HOMEBREW" to "ca-app-pub-3940256099942544/2247696110",
    "AD_NATIVE_SETTINGS" to "ca-app-pub-3940256099942544/2247696110",
    "AD_NATIVE_SHEET" to "ca-app-pub-3940256099942544/2247696110",
    "AD_INTER_EXIT_GAME" to "ca-app-pub-3940256099942544/1033173712",
    "AD_REWARDED_SAVE_SLOT" to "ca-app-pub-3940256099942544/5224354917",
)
fun admobIsReal(key: String): Boolean =
    admobProperties.getProperty(key)?.trim()?.let { it.isNotEmpty() && !it.contains("XXXX") } == true
fun admobReleaseId(key: String): String =
    if (admobIsReal(key)) admobProperties.getProperty(key).trim() else admobTestIds.getValue(key)
val admobReleaseIdsReal = admobTestIds.keys.all(::admobIsReal)
if (!admobReleaseIdsReal) {
    logger.warn("RetroNova: AdMob release IDs are placeholders - release builds use Google TEST IDs. Do not ship.")
}

// Debug builds send nothing to production (analytics.md 1 and 6) unless explicitly asked.
val enableAnalyticsDebug = project.hasProperty("enableAnalyticsDebug")
val enableCrashlyticsDebug = project.hasProperty("enableCrashlyticsDebug")

// Unstripped PPSSPP core produced by tools/build_core.sh, for Crashlytics NDK symbols.
val unstrippedCoreDir = rootProject.file("tools/core-out/unstripped")

android {
    namespace = "app.letostudio.gameemulator"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.letostudio.gameemulator"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The emulation core is built for these ABIs (tech_stacks.md); debug adds x86_64
        // for emulator testing.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }

        buildConfigField(
            "String",
            "UMP_TEST_DEVICE_ID",
            "\"${localProperties.getProperty("UMP_TEST_DEVICE_ID", "")}\"",
        )
        // GMA test device hash (from the "To get test ads on this device" logcat line).
        // Registered via MobileAds.setRequestConfiguration so taps on locally-built
        // release artifacts never count as live clicks (invalid-traffic protection).
        buildConfigField(
            "String",
            "GMA_TEST_DEVICE_ID",
            "\"${localProperties.getProperty("GMA_TEST_DEVICE_ID", "")}\"",
        )
    }

    signingConfigs {
        // Real upload key, read from local.properties (git-ignored). When the four
        // RELEASE_* entries are present, CLI release builds are Play-uploadable;
        // otherwise release falls back to debug signing for local R8 smoke tests.
        create("release") {
            fun prop(name: String) =
                localProperties.getProperty(name)?.takeIf { it.isNotBlank() }
            val storePath = prop("RELEASE_STORE_FILE")
            val storePass = prop("RELEASE_STORE_PASSWORD")
            val alias = prop("RELEASE_KEY_ALIAS")
            val keyPass = prop("RELEASE_KEY_PASSWORD")
            // All four or nothing — a partial config would fail the signing step
            // instead of falling back to debug.
            if (storePath != null && storePass != null && alias != null && keyPass != null) {
                storeFile = file(storePath)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        debug {
            ndk { abiFilters += "x86_64" }
            manifestPlaceholders["admobAppId"] = admobTestIds.getValue("ADMOB_APP_ID")
            admobTestIds.filterKeys { it != "ADMOB_APP_ID" }.forEach { (key, id) ->
                buildConfigField("String", key, "\"$id\"")
            }
            buildConfigField("boolean", "ADMOB_IDS_ARE_TEST", "true")
            // Forces the UMP consent form to behave as if the device is in the EEA.
            buildConfigField("boolean", "FORCE_EEA_FOR_TESTING", "true")
            // Flip to false to silence banners/interstitials while developing.
            buildConfigField("boolean", "ADS_ENABLED", "true")
            // Flip to false to stop analytics while developing. Deliberately separate from
            // the consent gate, which stays meaningful in tests whatever this is set to.
            // RetroNova: debug sends nothing unless -PenableAnalyticsDebug (analytics.md 1).
            buildConfigField("boolean", "ANALYTICS_ENABLED", enableAnalyticsDebug.toString())
            // Same for crash reports: -PenableCrashlyticsDebug (analytics.md 6).
            buildConfigField("boolean", "CRASHLYTICS_ENABLED", enableCrashlyticsDebug.toString())
            // Debug is not minified: there is no mapping file to upload.
            configure<CrashlyticsExtension> { mappingFileUploadEnabled = false }
        }
        release {
            manifestPlaceholders["admobAppId"] = admobReleaseId("ADMOB_APP_ID")
            admobTestIds.keys.filter { it != "ADMOB_APP_ID" }.forEach { key ->
                buildConfigField("String", key, "\"${admobReleaseId(key)}\"")
            }
            buildConfigField("boolean", "ADMOB_IDS_ARE_TEST", (!admobReleaseIdsReal).toString())
            buildConfigField("boolean", "FORCE_EEA_FOR_TESTING", "false")
            buildConfigField("boolean", "ADS_ENABLED", "true")
            buildConfigField("boolean", "ANALYTICS_ENABLED", "true")
            buildConfigField("boolean", "CRASHLYTICS_ENABLED", "true")
            isMinifyEnabled = true
            // R8 is on, so stack traces are only readable once mapping.txt is uploaded
            // (proguard-rules.pro keeps SourceFile/LineNumberTable for it). Gated on the
            // real upload key, same as the signingConfig below: without the RELEASE_*
            // entries this is a local R8 smoke test that never reaches a user, and
            // uploading its mapping would fill the Crashlytics console with build ids no
            // crash can match, and make assembleRelease need the network.
            configure<CrashlyticsExtension> {
                mappingFileUploadEnabled =
                    signingConfigs.getByName("release").storeFile != null
                // Native stacks from the emulation core symbolicate (analytics.md 6).
                nativeSymbolUploadEnabled =
                    signingConfigs.getByName("release").storeFile != null
                if (unstrippedCoreDir.exists()) unstrippedNativeLibsDir = unstrippedCoreDir.path
            }
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Signed with the real upload key when local.properties carries the
            // RELEASE_* entries; debug-signed (local R8 smoke tests only, Play
            // rejects it) on machines without them.
            signingConfig =
                if (signingConfigs.getByName("release").storeFile != null) {
                    signingConfigs.getByName("release")
                } else {
                    signingConfigs.getByName("debug")
                }
        }
    }

    // 16KB page size rule: never repackage native libs with legacy 4KB alignment
    // RetroNova: the libretro core is dlopen()ed by path, so it must be extracted to
    // nativeLibraryDir (tech_stacks.md). Every .so is still built 16 KB-aligned (NDK r28).
    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

kover {
    reports {
        verify {
            rule("line coverage baseline") {
                // Baseline is raised at each milestone checkpoint; koverVerify runs with `check`.
                minBound(0)
            }
        }
    }
}

// Hilt 2.58 bundles kotlin-metadata-jvm that reads metadata <= 2.3.0, but Kotlin 2.3.21
// emits 2.4.0 — force the newer reader on every classpath (incl. Hilt's javac step).
configurations.configureEach {
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.0")
}

// Gradle Play Publisher: `gradlew :app:publishReleaseBundle` builds, signs and uploads the
// AAB to the internal track. The service-account key is shared by every app and lives
// outside the repo (PLAY_CREDENTIALS_FILE in local.properties). versionCode stays manual:
// the default FAIL strategy rejects an already-used code instead of rewriting it, and a
// code is burned by any committed upload, even a draft that is later discarded.
//
// COMPLETED: the release goes straight out to internal testers, as in the derived apps.
// Play rejects a completed release while the app itself is still a draft (never sent for
// review); for those uploads pass `--release-status draft`.
play {
    localProperties.getProperty("PLAY_CREDENTIALS_FILE")?.takeIf { it.isNotBlank() }?.let {
        serviceAccountCredentials.set(rootProject.file(it))
    }
    track.set("internal")
    defaultToAppBundles.set(true)
    releaseStatus.set(com.github.triplet.gradle.androidpublisher.ReleaseStatus.COMPLETED)
}

// Native debug symbols: zip the merged libs as <abi>/lib.so and upload the zip as the
// release's "Native debug symbols", what the Console's manual upload does. GPP only does
// this for APK publishing, so it goes through the Play API directly, with the client
// libraries GPP already puts on the classpath. The template's .so files (androidx
// graphics-path, datastore) are vendor-stripped, so this mostly silences Play's warning.
val zipReleaseNativeSymbols by tasks.registering(Zip::class) {
    from(tasks.named("mergeReleaseNativeLibs")) {
        include("lib/**/*.so")
        // out/lib/<abi>/x.so -> <abi>/x.so, the layout Play expects
        eachFile { relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray()) }
        includeEmptyDirs = false
    }
    archiveFileName.set("play-native-symbols.zip")
    destinationDirectory.set(layout.buildDirectory.dir("outputs/native-debug-symbols/release"))
}

// Play only accepts deobfuscation files inside the edit that added the bundle (a later
// edit gets FAILED_PRECONDITION "the Deobfuscation File can't be modified"), so this
// joins GPP's edit: it runs after publishReleaseBundle has uploaded the AAB, reads the
// edit id GPP keeps in build/gpp/<applicationId>.txt, uploads into it, and leaves the
// commit to GPP's commitEditFor… task. Not runnable on its own.
//
// The R8 mapping goes up explicitly: AGP embeds it in the AAB too, but Play ignores that
// copy on API uploads and flags the release "no deobfuscation file". (Crashlytics gets
// its own copy from the Crashlytics plugin during the release build.)
val releaseMappingFile = objects.fileProperty()
androidComponents {
    onVariants(selector().withName("release")) { variant ->
        releaseMappingFile.set(
            variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.OBFUSCATION_MAPPING_FILE)
        )
    }
}

val uploadReleaseDeobfuscationFiles by tasks.registering {
    description = "Attaches the R8 mapping and the native symbols zip to the bundle publishReleaseBundle just uploaded."
    dependsOn(zipReleaseNativeSymbols)
    inputs.file(releaseMappingFile)
    val credentialsFile = localProperties.getProperty("PLAY_CREDENTIALS_FILE")
        ?.takeIf { it.isNotBlank() }?.let { rootProject.file(it) }
    val packageName = android.defaultConfig.applicationId!!
    val versionCode = android.defaultConfig.versionCode!!
    val clientName = rootProject.name.lowercase().replace(Regex("[^a-z0-9]+"), "-") + "-gradle"
    val editIdFile = layout.buildDirectory.file("gpp/$packageName.txt")
    val zipFile = zipReleaseNativeSymbols.flatMap { it.archiveFile }
    doLast {
        requireNotNull(credentialsFile) { "PLAY_CREDENTIALS_FILE missing from local.properties" }
        val editId = editIdFile.get().asFile.takeIf { it.exists() }?.readText()?.trim()
        require(!editId.isNullOrEmpty()) {
            "No open Play edit: run this through :app:publishReleaseBundle, not on its own"
        }
        val credentials = com.google.auth.oauth2.ServiceAccountCredentials
            .fromStream(credentialsFile.inputStream())
            .createScoped(listOf(com.google.api.services.androidpublisher.AndroidPublisherScopes.ANDROIDPUBLISHER))
        val publisher = com.google.api.services.androidpublisher.AndroidPublisher.Builder(
            com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport(),
            com.google.api.client.json.gson.GsonFactory.getDefaultInstance(),
            com.google.api.client.http.HttpRequestInitializer { request ->
                com.google.auth.http.HttpCredentialsAdapter(credentials).initialize(request)
                // Mapping files run to ~100 MB: the client's 20 s read timeout is too short
                // for an upload chunk on a slow line ("Read timed out").
                request.connectTimeout = 5 * 60_000
                request.readTimeout = 5 * 60_000
            }
        ).setApplicationName(clientName).build()
        fun attach(type: String, file: java.io.File) {
            publisher.edits().deobfuscationfiles().upload(
                packageName, editId, versionCode, type,
                com.google.api.client.http.FileContent("application/octet-stream", file)
            ).execute()
            logger.lifecycle("Attached $type (${file.name}, ${file.length() / 1_048_576} MB) to $packageName:$versionCode (edit $editId)")
        }
        attach("proguard", releaseMappingFile.get().asFile)
        // An app without any .so gets an empty zip, which Play rejects; skip it.
        val symbols = zipFile.get().asFile
        if (symbols.exists() && ZipFile(symbols).use { it.size() } > 0) {
            attach("nativeCode", symbols)
        } else {
            logger.lifecycle("No native libraries in $packageName:$versionCode, nothing to attach as nativeCode")
        }
    }
}

// GPP registers its tasks after evaluation, so they are matched by name. The commit
// waits for the deobfuscation files; if that upload fails, GPP sees the failed build
// and does not commit, so the version code is not used up and the run can be retried.
// commitEditFor<AppId> lives on the ROOT project (":commitEditFor…", no ":app:"), so
// it has to be matched there — matching it on :app silently matches nothing and the
// commit can run first, leaving the release on Play without its mapping.
tasks.matching { it.name == "publishReleaseBundle" }.configureEach {
    finalizedBy(uploadReleaseDeobfuscationFiles)
}
rootProject.tasks.matching { it.name.startsWith("commitEditFor") }.configureEach {
    mustRunAfter(uploadReleaseDeobfuscationFiles)
}

// Play refuses an unsigned or debug-signed bundle, but only after the full build and
// upload. Fail before building instead.
gradle.taskGraph.whenReady {
    if (allTasks.any { it.path == ":app:publishReleaseBundle" }) {
        val store = android.buildTypes.getByName("release").signingConfig?.storeFile
        if (store == null || store.name == "debug.keystore") {
            throw GradleException(
                "publishReleaseBundle needs THIS app's upload key: fill the four RELEASE_* entries in local.properties"
            )
        }
    }
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    // Compose (versions via BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.okhttp.logging.interceptor)

    // Coil 3 (shares the OkHttpClient)
    implementation(libs.bundles.coil)

    // Google Play Billing (specs/002-google-billing-template)
    implementation(libs.billing.ktx)

    // Google Mobile Ads + UMP consent (no .so files — 16KB-safe; NO mediation adapters)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)

    // Google Play In-App Review (no .so files — 16KB-safe). Fired from onboarding's
    // last slide; see ui/onboarding/InAppReview.kt.
    implementation(libs.play.review)

    // Cold-start splash (system splash -> Compose splash with ads notice)
    implementation(libs.androidx.core.splashscreen)

    // Firebase (version via BOM, never pinned here). Allowed products are listed in
    // tech_stacks.md §1: Remote Config (ads kill-switches, ads/AdsRemoteConfig),
    // Analytics and Crashlytics. Nothing else.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)
    // Analytics (specs/006-analytics, in progress): collection gated on UMP consent.
    implementation(libs.firebase.analytics)
    // Crashlytics (crash/CrashReportingManager): gated on the SAME consent outcome as
    // Analytics. Base artifact only; firebase-crashlytics-ndk bundles .so files.
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.crashlytics.ndk)

    // Emulation frontend (GPL-3.0): GLRetroView + Oboe audio; core .so in src/main/jniLibs.
    implementation(libs.libretrodroid)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Debug
    debugImplementation(libs.leakcanary)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Unit tests (JVM)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.konsist)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockk.android)
}
