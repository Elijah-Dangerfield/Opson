package com.opson.libraries.identity.impl.auth

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.flowroutines.DispatcherProvider
import com.opson.libraries.identity.auth.SecureSessionStorage
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Supabase [SessionManager] that keeps the session in OS-encrypted storage
 * (Keychain / EncryptedSharedPreferences via [SecureSessionStorage]) instead
 * of supabase-kt's default plaintext `multiplatform-settings` store (AUTH-16;
 * decisions.md 2026-05-18 accepted plaintext only until claim shipped).
 *
 * A session already sitting in the old plaintext store migrates on the first
 * load after the upgrade: read old once, write to the encrypted store, clear
 * the old — nobody gets signed out. [legacy] is the plaintext-store manager
 * pointed at supabase-kt's default key; [deleteSession] clears it too so
 * sign-out never leaves a resurrectable plaintext copy behind.
 *
 * **Anonymous sessions also keep a file-backed [mirror]** (AUTH-19): the
 * Keychain lost the owner's session across a TestFlight upgrade, and an
 * anonymous account has no credential to recover with — the mirror is the
 * difference between a self-healing boot and permanently stranded progression.
 * Every save refreshes it; a claimed session clears it (see [SessionMirror]
 * for the security trade). On load, the recovery order is: secure store →
 * legacy migration → mirror (restoring the secure copy when the mirror hits).
 *
 * **A new install starts signed out** ([freshInstall]): iOS keeps Keychain
 * items when the app is deleted, so without this a reinstall would come back
 * signed in with no onboarding. A signed-in session found on an install's first
 * load is dropped, and the person signs in again, which brings their account
 * back. An anonymous one is kept: a guest has no credential to sign back in
 * with, and the sign-in step links Apple or Google onto it, so their log moves
 * into the real account.
 *
 * `encodeDefaults = true` matches supabase-kt's own session serialization —
 * `UserSession.expiresAt` is a defaulted property, and dropping it would
 * reset the expiry window on every load.
 */
class SecureSessionManager(
    private val storage: SecureSessionStorage,
    private val key: String,
    private val legacy: SessionManager?,
    private val mirror: SessionMirror,
    private val freshInstall: FreshInstall,
    private val dispatchers: DispatcherProvider,
    private val json: Json = defaultJson,
) : SessionManager {

    private val logger = KLog.withTag("SecureSessionManager")

    override suspend fun saveSession(session: UserSession) = withContext(dispatchers.io) {
        // Marks the install as seen, so a session saved before the first load
        // (nothing stored yet) is never mistaken for an earlier install's.
        freshInstall.claim()
        val encoded = json.encodeToString(session)
        storage.write(key, encoded)
        if (session.isAnonymous()) mirror.write(key, encoded) else mirror.clear()
    }

    override suspend fun loadSession(): UserSession = withContext(dispatchers.io) {
        val firstLoad = freshInstall.claim()
        storage.read(key)?.let { stored ->
            val session = json.decodeFromString<UserSession>(stored)
            if (firstLoad && !session.isAnonymous()) {
                logger.i { "New install: dropping a signed-in session the OS store kept from an earlier install" }
                storage.delete(key)
                error("No session for this install under $key")
            }
            return@withContext session
        }

        legacy?.loadSessionOrNull()?.let { migrated ->
            storage.write(key, json.encodeToString(migrated))
            legacy.deleteSession()
            return@withContext migrated
        }

        val mirrored = mirror.read(key) ?: error("No session stored under $key")
        logger.w {
            "Secure store had no session under $key but the mirror does — " +
                "the OS store lost it (app upgrade?); restoring from the mirror"
        }
        storage.write(key, mirrored)
        json.decodeFromString<UserSession>(mirrored)
    }

    override suspend fun deleteSession(): Unit = withContext(dispatchers.io) {
        storage.delete(key)
        legacy?.deleteSession()
        mirror.clear()
    }

    /**
     * Anonymity via the JWT's `is_anonymous` claim (the signal the server
     * trusts — see [deriveIsAnonymous]), falling back to the identities
     * heuristic when the token can't be decoded.
     */
    private fun UserSession.isAnonymous(): Boolean =
        deriveIsAnonymous(
            accessToken = accessToken,
            hasNoIdentities = user?.identities.isNullOrEmpty(),
        )

    companion object {
        private val defaultJson = Json {
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        /**
         * The per-project storage key, byte-for-byte the key supabase-kt's
         * default `SettingsSessionManager` derives — the migration reads the
         * legacy plaintext entry under this exact name, and keying by project
         * URL keeps dev/prod sessions apart once the env split lands.
         */
        fun storageKeyFor(supabaseUrl: String): String {
            val host = supabaseUrl.split("//").last()
                .removeSuffix("/")
                .replace('/', '-')
                .replace('.', '-')
            return "sb-$host-session"
        }
    }
}
