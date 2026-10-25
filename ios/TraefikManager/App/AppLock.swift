import LocalAuthentication
import SwiftUI

/// Face ID, Touch ID or the device passcode before anything is shown. Port of `AppLock` / `AppLockGate`.
enum AppLock {
    static let key = "app_lock"

    static var available: Bool {
        LAContext().canEvaluatePolicy(.deviceOwnerAuthentication, error: nil)
    }

    static var biometryName: String {
        let context = LAContext()
        _ = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil)
        switch context.biometryType {
        case .faceID: return "Face ID"
        case .touchID: return "Touch ID"
        case .opticID: return "Optic ID"
        default: return String(localized: "your passcode")
        }
    }

    /// Returns nil on success or the system's message.
    static func authenticate() async -> String? {
        let context = LAContext()
        do {
            try await context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: String(localized: "Confirm it is you before your servers are shown"))
            return nil
        } catch {
            return error.localizedDescription
        }
    }
}

struct AppLockGate<Content: View>: View {
    @ViewBuilder var content: Content

    @AppStorage(AppLock.key) private var enabled = false
    @Environment(\.scenePhase) private var scenePhase
    @State private var unlocked = false
    @State private var prompting = false
    @State private var lastError: String?

    private var locked: Bool { enabled && !unlocked }

    var body: some View {
        ZStack {
            content
            if locked || (enabled && scenePhase != .active) {
                lockScreen.transition(.opacity)
            }
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background && enabled { unlocked = false }
            if phase == .active && locked { prompt() }
        }
        .onAppear { if locked { prompt() } }
    }

    private var lockScreen: some View {
        VStack(spacing: Spacing.sm) {
            Image(systemName: "lock.fill").font(.system(size: 44)).foregroundStyle(Palette.muted)
            Text("Locked").font(.title2.weight(.semibold)).padding(.top, Spacing.sm)
            Text(lastError ?? String(localized: "Unlock to see your servers."))
                .font(.body)
                .foregroundStyle(Palette.muted)
                .multilineTextAlignment(.center)
            if locked {
                Button("Unlock", action: prompt).buttonStyle(.borderedProminent).padding(.top, Spacing.md)
            }
        }
        .padding(Spacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Palette.bg)
    }

    private func prompt() {
        guard !prompting, scenePhase == .active || scenePhase == .inactive else { return }
        prompting = true
        Task {
            let failure = await AppLock.authenticate()
            if failure == nil { unlocked = true }
            lastError = failure
            prompting = false
        }
    }
}
