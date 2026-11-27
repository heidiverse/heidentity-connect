import Combine
import OidcSampleShared
import SwiftUI

struct OidcScreen: View {

    @StateObject private var model = OidcScreenModel()

    var body: some View {
        OidcContentView(
            idpConfig: model.idpConfig.value,
            loginState: model.loginState.value,
            onSaveIdpConfig: model.saveIdpConfig,
            onLogin: model.login,
            onApp2AppLogin: model.loginWithApp2App,
            onRefresh: model.refresh,
            onLogout: model.logout,
            onRetry: model.retry
        )
        // The App2App callback, opened by the user-interacting app. The manager ignores other URLs.
        .onOpenURL { url in
            model.handleOpenUrl(url)
        }
    }
}

/// Owns the shared KMP manager for the lifetime of the screen
@MainActor
final class OidcScreenModel: ObservableObject {

    let idpConfig: StateFlowObserver<IdpConfigState>
    let loginState: StateFlowObserver<LoginState>

    private let manager: OidcSampleManager
    private let presenter: IosAuthorizationPresenter
    private var cancellables: [Any] = []

    init() {
        manager = OidcSampleIos.shared.createManager()
        presenter = OidcSampleIos.shared.createAuthorizationPresenter(manager: manager)
        idpConfig = StateFlowObserver(manager.idpConfig)
        loginState = StateFlowObserver(manager.loginState)

        // Forward changes of the nested observers so the screen re-renders
        cancellables = [
            idpConfig.objectWillChange.sink { [weak self] in self?.objectWillChange.send() },
            loginState.objectWillChange.sink { [weak self] in self?.objectWillChange.send() },
        ]
    }

    func saveIdpConfig(_ config: IdpConfigState) {
        manager.setIdpConfig(config: config)
    }

    func login(scope: String, prompt: String) {
        presenter.startLogin(scope: scope, prompt: prompt)
    }

    func loginWithApp2App(scope: String, prompt: String) {
        manager.startApp2AppLogin(scope: scope, prompt: prompt)
    }

    func handleOpenUrl(_ url: URL) {
        manager.handleApp2AppCallback(url: url.absoluteString)
    }

    func refresh() {
        manager.refreshTokens()
    }

    func logout() {
        manager.logout()
    }

    func retry() {
        manager.startIdpDiscovery()
    }
}

private struct OidcContentView: View {

    let idpConfig: IdpConfigState
    let loginState: LoginState
    let onSaveIdpConfig: (IdpConfigState) -> Void
    let onLogin: (_ scope: String, _ prompt: String) -> Void
    let onApp2AppLogin: (_ scope: String, _ prompt: String) -> Void
    let onRefresh: () -> Void
    let onLogout: () -> Void
    let onRetry: () -> Void

    var body: some View {
        NavigationStack {
            Form {
                IdpConfigSection(idpConfig: idpConfig, onSave: onSaveIdpConfig)

                if loginState.isLoggedIn {
                    LoggedInSection(
                        tokens: loginState.tokens,
                        isRefreshing: loginState.isLoading,
                        refreshErrorMessage: loginState.errorMessage,
                        onRefresh: onRefresh,
                        onLogout: onLogout
                    )
                } else if loginState.isLoading {
                    Section {
                        ProgressView()
                            .frame(maxWidth: .infinity)
                    }
                } else if let errorMessage = loginState.errorMessage {
                    ErrorSection(errorMessage: errorMessage, onRetry: onRetry)
                } else {
                    LoginSection(
                        isApp2AppAvailable: !idpConfig.nativeCallbackUri
                            .trimmingCharacters(in: .whitespaces).isEmpty,
                        onLogin: onLogin,
                        onApp2AppLogin: onApp2AppLogin
                    )
                }
            }
            .navigationTitle("OIDC Sample")
        }
    }
}

private struct IdpConfigSection: View {

    let idpConfig: IdpConfigState
    let onSave: (IdpConfigState) -> Void

    @State private var discoveryUrl = ""
    @State private var redirectUri = ""
    @State private var clientId = ""
    @State private var clientSecret = ""
    @State private var nativeCallbackUri = ""
    @State private var dpopEnabled = false
    @State private var dpopEnforced = true
    @State private var dpopAllowSoftwareKeyFallback = false

    var body: some View {
        Section {
            ConfigField(title: "Discovery URL", text: $discoveryUrl)
            ConfigField(title: "Redirect URI", text: $redirectUri)
            ConfigField(title: "Client ID", text: $clientId)
            ConfigField(title: "Client Secret", text: $clientSecret)
            ConfigField(title: "Native Callback URI (App2App)", text: $nativeCallbackUri)

            DpopSettings(
                enabled: $dpopEnabled,
                enforced: $dpopEnforced,
                allowSoftwareKeyFallback: $dpopAllowSoftwareKeyFallback,
                algorithms: idpConfig.dpopAlgorithms
            )

            Button("Save IDP Config") {
                onSave(
                    IdpConfigState(
                        discoveryUrl: discoveryUrl,
                        redirectUri: redirectUri,
                        clientId: clientId,
                        clientSecret: clientSecret,
                        nativeCallbackUri: nativeCallbackUri,
                        dpopEnabled: dpopEnabled,
                        dpopEnforced: dpopEnforced,
                        dpopAllowSoftwareKeyFallback: dpopAllowSoftwareKeyFallback
                    )
                )
            }
        } header: {
            Text("Identity provider")
        } footer: {
            Text(
                "The login runs in an ASWebAuthenticationSession, which intercepts the redirect URI itself. The "
                    + "native callback URI of the App2App login must be a URL scheme of the app (see project.yml). "
                    + "Leave it empty to turn off the App2App login."
            )
        }
        .onAppear { reset(to: idpConfig) }
        .onChange(of: idpConfig) { _, newConfig in reset(to: newConfig) }
    }

    private func reset(to config: IdpConfigState) {
        discoveryUrl = config.discoveryUrl
        redirectUri = config.redirectUri
        clientId = config.clientId
        clientSecret = config.clientSecret
        nativeCallbackUri = config.nativeCallbackUri
        dpopEnabled = config.dpopEnabled
        dpopEnforced = config.dpopEnforced
        dpopAllowSoftwareKeyFallback = config.dpopAllowSoftwareKeyFallback
    }
}

/// The DPoP part of the IDP config: a switch to turn it on, and the options that only make sense while it is on. The
/// algorithms are shown but not editable, since ES256 is the only one the SDK supports.
private struct DpopSettings: View {

    @Binding var enabled: Bool
    @Binding var enforced: Bool
    @Binding var allowSoftwareKeyFallback: Bool
    let algorithms: String

    var body: some View {
        Toggle(isOn: $enabled) {
            SettingLabel(title: "DPoP", description: "Sender-constrained tokens (RFC 9449)")
        }

        if enabled {
            VStack(alignment: .leading, spacing: 6) {
                Picker("Mode", selection: $enforced) {
                    Text("Enforced").tag(true)
                    Text("Opportunistic").tag(false)
                }
                .pickerStyle(.segmented)

                Text(
                    enforced
                        ? "Fail if the server does not issue DPoP-bound tokens"
                        : "Fall back to bearer tokens if DPoP is unavailable"
                )
                .font(.caption)
                .foregroundStyle(.secondary)
            }

            Toggle(isOn: $allowSoftwareKeyFallback) {
                SettingLabel(
                    title: "Allow software key fallback",
                    description: "Needed on the simulator, which has no Secure Enclave"
                )
            }

            LabeledContent("Algorithms", value: algorithms)
        }
    }
}

private struct SettingLabel: View {

    let title: String
    let description: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
            Text(description)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}

private struct ConfigField: View {

    let title: String
    @Binding var text: String

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            TextField(title, text: $text)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
        }
    }
}

private struct LoginSection: View {

    let isApp2AppAvailable: Bool
    let onLogin: (_ scope: String, _ prompt: String) -> Void
    let onApp2AppLogin: (_ scope: String, _ prompt: String) -> Void

    @State private var scope = "openid offline_access"
    @State private var prompt = "login"

    var body: some View {
        Section {
            ConfigField(title: "Scope", text: $scope)
            ConfigField(title: "Prompt", text: $prompt)

            Button("Login") {
                onLogin(scope, prompt)
            }

            Button("Login with App2App") {
                onApp2AppLogin(scope, prompt)
            }
            .disabled(!isApp2AppAvailable)
        } header: {
            Text("Login")
        } footer: {
            Text(
                isApp2AppAvailable
                    ? "App2App logs in through the app the authorization server links to, without a browser."
                    : "Set a native callback URI to log in with App2App."
            )
        }
    }
}

private struct LoggedInSection: View {

    let tokens: SampleTokens?
    let isRefreshing: Bool
    let refreshErrorMessage: String?
    let onRefresh: () -> Void
    let onLogout: () -> Void

    @State private var showDecodedPayloads = false

    var body: some View {
        Section {
            if let tokens {
                Toggle("Decode JWT payloads", isOn: $showDecodedPayloads)
                if showDecodedPayloads {
                    Text(
                        "Payloads are only base64-decoded. No signature, issuer or expiry is checked — do not "
                            + "trust anything you read here 🕵️"
                    )
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                }

                TokenRow(title: "Access Token", token: tokens.accessToken, showDecodedPayload: showDecodedPayloads)
                if let idToken = tokens.idToken {
                    TokenRow(title: "ID Token", token: idToken, showDecodedPayload: showDecodedPayloads)
                }
                if let refreshToken = tokens.refreshToken {
                    TokenRow(title: "Refresh Token", token: refreshToken, showDecodedPayload: showDecodedPayloads)
                }
            }

            Button(action: onRefresh) {
                HStack {
                    Text("Refresh Tokens")
                    if isRefreshing {
                        Spacer()
                        ProgressView()
                    }
                }
            }
            .disabled(isRefreshing)

            if let refreshErrorMessage {
                Text("Refresh failed: \(refreshErrorMessage)")
                    .font(.footnote)
                    .foregroundStyle(.red)
            }

            Button("Logout", role: .destructive, action: onLogout)
        } header: {
            Text("Logged in")
        } footer: {
            Text("Tap a token to copy it. This always copies the raw token, also while its payload is shown decoded.")
        }
    }
}

/// One token, either as it was issued or as its decoded JWT payload
private struct TokenRow: View {

    let title: String
    let token: String
    let showDecodedPayload: Bool

    var body: some View {
        let decodedPayload = showDecodedPayload ? JwtPayload.shared.decode(token: token) : nil

        Button {
            UIPasteboard.general.string = token
        } label: {
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.headline)
                Text(decodedPayload ?? (showDecodedPayload ? "\(token)\n\n(not a JWT, nothing to decode)" : token))
                    .font(.caption.monospaced())
                    .lineLimit(decodedPayload != nil ? 40 : 5)
                    .truncationMode(.tail)
                    .foregroundStyle(.secondary)
            }
        }
        .buttonStyle(.plain)
    }
}

private struct ErrorSection: View {

    let errorMessage: String
    let onRetry: () -> Void

    var body: some View {
        Section("Error") {
            Text(errorMessage)
                .foregroundStyle(.red)
            Button("Retry", action: onRetry)
        }
    }
}
