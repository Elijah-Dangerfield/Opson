plugins {
    id("opson.kotlin.multiplatform")
}

moduleConfig {
    optIn("kotlin.time.ExperimentalTime")
}

android {
    namespace = "com.opson.libraries.identity.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.identity)
            implementation(projects.libraries.networking)
            implementation(projects.libraries.storage)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            // For AppEvent + AppEventBus — needed to dispatch UserChanged/SignedOut.
            implementation(projects.libraries.opson)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            // supabase-kt for sign-in and session storage + token refresh. The
            // matching Ktor engine comes from :libraries:networking's
            // platformHttpEngineFactory (OkHttp on Android, Darwin on iOS).
            //
            // `api` (not `implementation`): the merged anvil component in
            // `:apps:compose` references SupabaseClient via our @ContributesTo
            // interface, so the type has to be on the consumer's classpath.
            api(libs.supabase.auth)
        }
        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.identity)
            implementation(projects.libraries.networking)
            // ProfileCache depends on storage's Cache / CacheFactory;
            // AppEventBus is in :libraries:opson (impl ctor dep).
            // :libraries:core for AutoInit (the test compiler has to load
            // impl supertypes to type-check).
            implementation(projects.libraries.storage)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.core)
            implementation(libs.ktor.client.contentNegotiation)
            // MockEngine is the easiest way to synthesize a real Ktor
            // ClientRequestException with a given HTTP status — the impl's
            // outcome mapping branches on status codes, so we need real
            // exception shapes (not hand-rolled ones).
            implementation(libs.ktor.client.mock)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // EncryptedSharedPreferences for the OS-encrypted Supabase
            // session store. iOS uses a Keychain Swift twin.
            implementation(libs.androidx.security.crypto)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}
