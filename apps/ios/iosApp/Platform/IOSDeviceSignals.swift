//
//  IOSDeviceSignals.swift
//  iosApp
//
//  What a contribution request (a food submission, a report) carries about
//  the phone, so a person barred from contributing can't start over by
//  deleting the app. Kotlin's IosContributionAttestation calls this only for
//  those requests; logging food never touches it.
//
//  The install key is a random UUID in the Keychain with
//  kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly: it stays on this phone
//  (never in a backup restored to another one) and usually survives the app
//  being deleted. Apple doesn't promise that, so the server treats it as one
//  signal among several. It is random, not derived from the device.
//
//  DeviceCheck is Apple's sanctioned per-device marker: the token lets the
//  server read and set two bits Apple keeps for this phone across reinstalls.
//  The simulator doesn't support it, which reads as "no token".
//
import ComposeApp
import DeviceCheck
import Foundation
import Security

final class IOSDeviceSignals: DeviceSignals {

    private let service = "com.opson.contribution-install-key"
    private let account = "install-key"
    private let logger = KLog.shared.withTag(tag: "IOSDeviceSignals")

    func keychainInstallId() -> String? {
        if let existing = read() { return existing }
        let fresh = UUID().uuidString.lowercased()
        var add = baseQuery()
        add[kSecValueData as String] = Data(fresh.utf8)
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let status = SecItemAdd(add as CFDictionary, nil)
        if status == errSecDuplicateItem { return read() }
        guard status == errSecSuccess else {
            logger.w { "Keychain install key write failed (status=\(status))" }
            return nil
        }
        return fresh
    }

    func deviceCheckToken(completion: @escaping (String?) -> Void) {
        guard DCDevice.current.isSupported else {
            completion(nil)
            return
        }
        DCDevice.current.generateToken { data, error in
            if let error {
                self.logger.w { "DeviceCheck token failed: \(error.localizedDescription)" }
            }
            completion(data?.base64EncodedString())
        }
    }

    private func read() -> String? {
        var query = baseQuery()
        query[kSecReturnData as String] = kCFBooleanTrue
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func baseQuery() -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
    }
}
