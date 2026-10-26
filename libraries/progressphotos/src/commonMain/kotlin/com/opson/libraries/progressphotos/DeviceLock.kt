package com.opson.libraries.progressphotos

/** How asking for the phone's lock went. */
enum class DeviceLockResult {
    Unlocked,

    /** The person backed out, or the prompt was dismissed. */
    Cancelled,

    /** Too many tries, or the prompt could not show. */
    Failed,

    /** The phone has no passcode, PIN, pattern or biometric set, so there is nothing to ask for. */
    NotSet,
}

/**
 * The phone's own lock: Face ID, Touch ID or the passcode on iOS
 * (`LAPolicyDeviceOwnerAuthentication`), BiometricPrompt with the device
 * credential as fallback on Android. Opson never sees a fingerprint, face or
 * passcode; it only learns whether the person got past the lock.
 */
interface DeviceLock {
    /** Whether a passcode, PIN, pattern or biometric is set. */
    fun isSet(): Boolean

    /** Shows the system prompt, titled "Unlock your progress photos", and suspends until it is answered. */
    suspend fun authenticate(): DeviceLockResult
}
