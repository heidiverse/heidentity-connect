<div align="center">

# Heidentity Connect

**An OpenID Connect client for Kotlin Multiplatform: one API for Android and iOS.**

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF.svg?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Platforms](https://img.shields.io/badge/Platforms-Android%20%7C%20iOS-lightgrey.svg)](#requirements)
[![Ktor](https://img.shields.io/badge/HTTP-Ktor%20%7C%20OkHttp-087CFA.svg)](#modules)

[Features](#features) •
[Installation](#installation) •
[Quick start](#quick-start) •
[Authorized HTTP requests](#authorized-http-requests) •
[DPoP](#dpop) •
[Platform setup](#platform-setup) •
[Error codes](#error-codes)

</div>

---

Heidentity Connect implements the OpenID Connect **Authorization Code Flow with PKCE** for native mobile apps.
It covers the whole login lifecycle: provider discovery, the browser-based login, the token exchange, secure token
storage, and automatic token refreshes. Optionally, it binds the tokens to a hardware-backed key on the device with
DPoP. The core is written once in Kotlin and shipped to Android as a library and to iOS as a framework.

## Features

- **Authorization Code Flow with PKCE** (`S256` only), including `state` and `nonce` generated from a
  cryptographically secure random source
- **DPoP sender-constrained tokens** ([RFC 9449](https://datatracker.ietf.org/doc/html/rfc9449)), opt-in per
  provider: proofs are signed with a non-extractable key in the secure element (Android StrongBox or TEE, iOS Secure
  Enclave), with server nonce and clock-skew handling
- **Provider discovery** through `/.well-known/openid-configuration`, with cached metadata
- **Secure token storage**: Keychain on iOS and app-private storage on Android, or bring your own `AuthStorage`
- **Automatic token refresh** before tokens expire and after `401`/`403` responses. Concurrent requests share a
  single refresh.
- **Session handling**: observe the authorization state, the current tokens and session expiration as Kotlin `Flow`s
- **Drop-in HTTP integration**: a Ktor client plugin and an OkHttp interceptor that attach bearer or DPoP tokens
  only to the base URLs you allow
- **Native login UIs**:
  - Android: [Auth Tab](https://developer.chrome.com/docs/android/custom-tabs/guide-auth-tab), falling back to a Custom Tab
  - iOS: `ASWebAuthenticationSession` or `SFSafariViewController`
  - Your own UI, through a callback-based launcher
- **Login verification hook**: validate freshly issued tokens against your backend before the login counts as successful
- **Stable numeric error codes** for app-side handling, telemetry and support
- **Pluggable networking**: pick Ktor (Android and iOS) or OkHttp (Android)

## Modules

The SDK is split into a core module and one module per HTTP client. Add the core module and **one** networking
module.

| Artifact                 | Platforms     | Description                                                          |
|--------------------------|---------------|----------------------------------------------------------------------|
| `oidc-core`              | Android, iOS  | Authorization flow, token handling, storage, models and exceptions   |
| `oidc-ktor`              | Android, iOS  | Ktor networking and the `OidcAuth` client plugin (includes core)     |
| `oidc-okhttp`            | Android       | OkHttp networking and the `AuthorizationHeaderInterceptor`           |
| `oidc-extensions`        | Android, iOS  | Optional extensions beyond the OIDC standard (see [Extensions](#extensions)) |
| `oidc-extensions-ktor`   | Android, iOS  | Ktor wiring for the extensions                                       |
| `oidc-extensions-okhttp` | Android       | OkHttp wiring for the extensions                                     |

## Requirements

| Platform | Minimum                                          |
|----------|--------------------------------------------------|
| Android  | API 26 (Android 8.0), Java 21 bytecode target    |
| iOS      | `iosArm64`, `iosX64` and `iosSimulatorArm64` targets |
| Build    | JDK 21, Kotlin 2.4, Xcode for iOS builds         |

## Installation

All artifacts are published under the group `org.heidiverse.connect.sdk`.

```kotlin
// build.gradle.kts
dependencies {
    // Kotlin Multiplatform or Android with Ktor
    implementation("org.heidiverse.connect.sdk:oidc-ktor:<version>")

    // or Android with OkHttp
    implementation("org.heidiverse.connect.sdk:oidc-core:<version>")
    implementation("org.heidiverse.connect.sdk:oidc-okhttp:<version>")
}
```

For iOS, the `oidc-ktor` module builds a static framework named `heidentity-connect-ktor` that re-exports the core API.
[SKIE](https://skie.touchlab.co) is applied, so suspend functions and flows can be used from Swift with
`async`/`await`.

## Quick start

### 1. Initialize the SDK

Initialize the SDK once when your app starts: in `Application.onCreate()` on Android, in the app delegate or
`App` initializer on iOS.

```kotlin
// Android, Ktor
HeidentityConnectKtor(context).initialize()

// Android, OkHttp
HeidentityConnectOkHttp(context).initialize()

// iOS, Ktor
HeidentityConnectKtor().initialize()
```

`initialize` takes two optional parameters:

- an `OidcConfiguration`, to provide your own `AuthStorage` or `OidcEventLogger`
- an `HttpClientConfiguration` for the request timeout and user agent, or a preconfigured `HttpClient` or
  `OkHttpClient`

### 2. Configure your identity provider

```kotlin
val idpConfig = IdpConfig(
    discoveryUrl = "https://idp.example.com/realms/demo", // Issuer URL, without /.well-known/openid-configuration
    redirectUri = "myapp://oidc/callback",                // Must match the redirect URI registered at the IdP
    clientId = "my-mobile-app",
    clientSecret = null,                                  // Optional; public clients do not need one
)

val controller = AuthorizationController(idpConfig)
```

To bind the tokens to the device, also pass a `DpopConfig` (see [DPoP](#dpop)).

### 3. Log in

```kotlin
// Optional: fetch the provider metadata ahead of time. startAuthorization() fetches it if it is missing.
controller.discoverProvider(forceFetch = true)

controller.startAuthorization(
    authRequestLauncher = launcher,          // See "Platform setup" below
    scope = "openid profile email offline_access",
    prompt = "login",                        // Optional
    uiLocales = "en",                        // Optional
    loginHint = null,                        // Optional
    customQueryParameters = emptyMap(),      // Optional extra parameters for the authorization URL
)
```

The launcher opens the login page. When the identity provider redirects back to your app, pass the result to the
controller to exchange the authorization code for tokens:

```kotlin
controller.handleAuthorizationResponse(
    authResponse = response,
    tokenVerificationHook = { tokens ->
        // Optional: check the new tokens against your backend before the login counts as successful
        runCatching { api.fetchUserProfile() }.map { }
    },
).onSuccess {
    // Logged in
}.onFailure { error ->
    // CancelledLoginException, StateMismatchException, NetworkException, ...
}
```

If you receive the redirect as a URL yourself (for example with a custom launcher), convert it with
`AuthorizationResponse.from(redirectUrl)`.

### 4. Observe the session

```kotlin
controller.isAuthorized()                  // Boolean
controller.getAuthorizedState()            // Flow<Boolean>, emits the current state immediately
controller.getAuthTokens()                 // AuthTokens?
controller.getAuthTokenFlow()              // Flow<AuthTokens?>
controller.getSessionExpiration()          // Flow that emits when a refresh fails and the session has ended

controller.forceRefreshTokens()            // Result<AuthTokens>
controller.clearAuthState(keepMetadata = true) // Log out
```

## Authorized HTTP requests

`DefaultTokenHandler` hands out a valid bearer token and refreshes it when needed. Both HTTP integrations use it
to authorize your app's API requests. A token is only ever sent to URLs that match one of the
`authorizedBaseUrls` by scheme, host, port and path prefix. If the tokens are DPoP-bound, both integrations send
them with the `DPoP` authorization scheme and a fresh proof for each request.

### Ktor

```kotlin
val client = HttpClient {
    install(OidcAuth) {
        authorizedBaseUrls = setOf("https://api.example.com")
        tokenHandler = DefaultTokenHandler(
            idpConfig = idpConfig,
            bearerTokenProvider = { it.accessToken }, // Default; it.idToken sends the ID token (not with DPoP)
        )
        defaultRequestType = RequestType.OPPORTUNISTIC
    }
}
```

Each request can override the default request type:

| Request type                   | Behavior                                                                              |
|--------------------------------|---------------------------------------------------------------------------------------|
| `opportunisticAuthorization()` | *(default)* Sends the token if the user is logged in, otherwise sends the request without one |
| `alwaysAuthorized()`           | Requires a token and throws `UnauthorizedException` if none is available              |
| `alwaysUnauthorized()`         | Never sends a token and skips all OIDC handling                                       |

```kotlin
client.get("https://api.example.com/public/news") {
    alwaysUnauthorized()
}
```

Requests made inside the `tokenVerificationHook` must be marked with `usedDuringTokenVerification()`. They then
use the newly issued tokens before those tokens are stored.

### OkHttp

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(
        AuthorizationHeaderInterceptor(
            tokenHandler = DefaultTokenHandler(idpConfig),
            authorizedBaseUrls = setOf("https://api.example.com"),
            throwOnUnauthorized = true, // Fail requests with an UnauthorizedException (wrapped in an IOException) while logged out
        )
    )
    .build()
```

## DPoP

[DPoP](https://datatracker.ietf.org/doc/html/rfc9449) (Demonstrating Proof of Possession) binds the issued tokens to
a key pair held by the device. Every token and API request carries a short-lived proof signed with the private key,
so a leaked token is useless without that key.

DPoP is off by default. To enable it for a provider, set a `DpopConfig` on the `IdpConfig`:

```kotlin
val idpConfig = IdpConfig(
    discoveryUrl = "https://idp.example.com/realms/demo",
    redirectUri = "myapp://oidc/callback",
    clientId = "my-mobile-app",
    dpop = DpopConfig(), // Enforced, ES256, hardware-backed keys only
)
```

When an authorization flow starts, the SDK generates a P-256 key pair in the device's secure element. The private
key can't be extracted, and only an opaque key alias is stored next to the tokens. The Ktor plugin and the OkHttp
interceptor create the proofs and use the right authorization scheme on their own, so the rest of your app does not
change.

| Option                     | Default    | Description                                                                                                   |
|----------------------------|------------|---------------------------------------------------------------------------------------------------------------|
| `mode`                     | `ENFORCED` | Whether DPoP is required, or only used where possible. See [Enforced and opportunistic mode](#enforced-and-opportunistic-mode) |
| `supportedAlgorithms`      | `[ES256]`  | Signature algorithms in order of preference, negotiated against the provider's `dpop_signing_alg_values_supported`. ES256 is currently the only one, because it is the only algorithm the iOS Secure Enclave supports. |
| `allowSoftwareKeyFallback` | `false`    | Allow a software-backed key when no secure element is available. See [Hardware-backed keys](#hardware-backed-keys) |

### Enforced and opportunistic mode

`DpopConfig.mode` decides what happens when DPoP can't be used:

| Situation                                                                                  | `ENFORCED` (default)                                   | `OPPORTUNISTIC`                    |
|--------------------------------------------------------------------------------------------|--------------------------------------------------------|------------------------------------|
| The server answers with a `token_type` other than `DPoP`                                   | Rejected with `DpopException` (`TOKEN_NOT_DPOP_BOUND`) | Accepted and used as bearer tokens |
| No suitable key: no secure element with the software fallback off, or no algorithm in common with the provider | `startAuthorization()` fails with a `DpopException` | Continues with bearer tokens |
| The key of the stored tokens is gone                                                       | Session expiry                                         | Session expiry                     |

In enforced mode, a rejected login fails the authorization, and a rejected refresh keeps the previous tokens. The
client must therefore be configured on the server to issue DPoP-bound access tokens. In Keycloak, enable the
client's DPoP-bound access tokens setting.

Opportunistic mode is meant for rolling DPoP out before every server and client configuration is ready. Each
fallback to bearer tokens is logged as `OidcEvent.DpopNotUsed`. This does not make the app easier to attack: TLS
prevents a downgrade on the wire, so bearer tokens can only be the server's choice. Enforced mode additionally
protects against a misconfigured server.

To turn DPoP off, leave `IdpConfig.dpop` set to `null`. Sessions created before DPoP was enabled have no key and
keep using bearer tokens until the next login.

### Hardware-backed keys

On Android, keys are created in the Android Keystore, in StrongBox where available and in the TEE otherwise. On
iOS, they are created in the Secure Enclave.

`allowSoftwareKeyFallback` is `false` by default. A software-backed private key is not protected against
extraction, and preventing that is the point of binding the tokens to a key. When no secure element is available,
`startAuthorization()` fails with `DpopException` (`HARDWARE_BACKING_UNAVAILABLE`) before the browser opens. In
opportunistic mode, the login continues without DPoP instead.

Set `allowSoftwareKeyFallback = true` to allow a software-backed key. You need this to test DPoP on Android
emulators and on the iOS simulator, which has no Secure Enclave. Each software-backed key is logged as
`OidcEvent.DpopKeyNotHardwareBacked`.

The keys require neither user authentication nor an unlocked device. Token refreshes also run while the app is in
the background and the device is locked, and a key that required user presence would turn those refreshes into
session failures.

### Nonces and clock skew

A server can require a nonce in the proofs. It then rejects the request with a `use_dpop_nonce` error and sends the
nonce in the `DPoP-Nonce` header. The SDK keeps the nonce in memory, separately for the authorization server and each
resource server, and retries the request once.

Servers only accept proofs whose `iat` lies within a few seconds of their own time, so a wrong device clock breaks
DPoP. When a proof is rejected with `invalid_dpop_proof`, the SDK uses the time from the response's `Date` header for
all following proofs and retries the request once. Each correction is logged as `OidcEvent.DpopClockSkewCorrected`.

### Limitations

- A custom `bearerTokenProvider` that returns anything other than the access token does not work with DPoP. The
  proof's `ath` claim hashes the token sent in the `Authorization` header, and the server binds the key (`cnf.jkt`)
  to the access token only.
- If the key of the stored tokens is gone, for example because app data was restored onto another device or the
  keystore was invalidated, the tokens can't be used anymore. As soon as a request needs the key, the SDK reports a
  session expiry through `getSessionExpiration()`, so your app can start a new login.
- Binding the authorization code to the key with `dpop_jkt`
  ([RFC 9449, section 10](https://datatracker.ietf.org/doc/html/rfc9449#section-10)) is optional and not
  implemented.

## Platform setup

### Android

Register a launcher in your `ComponentActivity` or `Fragment`. You must register it before the activity or
fragment is created, for example as a property initializer:

```kotlin
private val authRequestLauncher = registerAuthorizationRequestLauncher(
    useEphemeralSession = false,
    onResponse = { response -> viewModel.handleAuthorizationResponse(response) },
)
```

The login opens in an **Auth Tab** when possible, and in a **Custom Tab** otherwise. An Auth Tab is preferable
because the browser itself intercepts the redirect and returns it as an activity result. The response never leaves
the browser as a deeplink that another app could claim. An Auth Tab is used when both of these are true:

- the browser supports Auth Tabs, for example Chrome 137 or later
- the redirect URI uses a custom scheme (`myapp://…`) or HTTPS. Plain `http` redirect URIs are never intercepted.

To check which mechanism will be used, for example for logging, call
`AuthTabAuthorizationRequestLauncher.canLaunchAuthTab(context, redirectUri)`.

#### Redirect URI intent filter

The app can fall back to a Custom Tab at any time, so the manifest **must always** declare the
`RedirectUriReceiverActivity` with an intent filter that matches your redirect URI:

```xml
<activity
    android:name="org.heidiverse.connect.sdk.browser.RedirectUriReceiverActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />

        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />

        <data android:scheme="myapp" />
        <data android:host="oidc" />
        <data android:pathPrefix="/callback" />
    </intent-filter>
</activity>
```

An **HTTPS redirect URI** also needs a
[Digital Asset Links](https://developer.android.com/training/app-links/verify-android-applinks) association
between your app and the redirect host. The SDK can't detect a missing association in advance. If it is missing,
the Auth Tab reports a failed verification and the login fails with an `AuthorizationException`. It does not fall
back to a Custom Tab, because the user has already signed in by then. Either publish an `assetlinks.json` for the
redirect host or use a custom scheme.

#### Storage

By default, tokens and provider metadata are stored in app-private `SharedPreferences`. To store them elsewhere,
implement `AuthStorage` and pass it with a custom `OidcConfiguration`.

#### Troubleshooting

<details>
<summary>The browser stays open after a successful login</summary>

- Make sure your `MainActivity` does not use `launchMode="singleInstance"`.
- Make sure the `RedirectUriReceiverActivity` intent filter matches your redirect URI exactly. The Custom Tab
  fallback needs it even when Auth Tabs are available.

</details>

### iOS

Two browser-based launchers are available:

```swift
// ASWebAuthenticationSession (recommended)
let launcher = WebAuthenticationSessionLauncher(
    presentationContextProvider: contextProvider, // ASWebAuthenticationPresentationContextProviding
    ephemeralBrowserSession: false,
    responseReceivedHandler: { response in
        Task { try await controller.handleAuthorizationResponse(authResponse: response) }
    },
    sessionFactory: nil
)

// SFSafariViewController
let launcher = SafariViewControllerLauncher(
    presentationContextProvider: viewController,
    controllerConfiguration: SFSafariViewController.Configuration(),
    responseReceivedHandler: { response in /* ... */ }
)
```

`WebAuthenticationSessionLauncher` supports both custom URL schemes and HTTPS (universal link) redirect URIs.

#### Storage

By default, tokens and provider metadata are stored in the **Keychain** with
`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`. They are not included in backups and are not moved to other
devices.

### Custom login UI

To present the authorization URL yourself, for example in an embedded web view, use
`CallbackAuthorizationRequestLauncher`:

```kotlin
val launcher = CallbackAuthorizationRequestLauncher(
    launchHandler = { authRequestUrl, idpConfig, customHeaders ->
        // Open authRequestUrl and, once the redirect arrives, call
        // controller.handleAuthorizationResponse(AuthorizationResponse.from(redirectUrl))
    },
    dismissHandler = { /* Close your UI */ },
)
```

## Extensions

The `oidc-extensions` modules add optional flows that go beyond the OpenID Connect standard. They are not loaded
by default. Initialize the ones you need after the SDK itself:

```kotlin
HeidentityConnectSessionTransferKtor.initialize()  // or HeidentityConnectSessionTransferOkHttp.initialize()
HeidentityConnectBrowserlessKtor.initialize()      // or HeidentityConnectBrowserlessOkHttp.initialize()
```

- **Session transfer** (`SessionTransferController`): carries the app's login over into a web session.
  `getSessionTransferUrl()` creates a one-time session transfer token with the user's access token and returns a URL
  that signs the user in when opened in a browser. Requires an identity provider such as Heidentity that publishes
  `stt_create_endpoint` and `stt_use_endpoint` in its discovery metadata.
- **Browserless** (`BrowserlessAuthorizationController`): runs the authorization request through the HTTP client
  instead of a browser. Cookies are persisted so the flow can continue across a deep link. This is the
  [browserless App2App flow](https://datatracker.ietf.org/doc/html/draft-zehavi-oauth-app2app-browserless). The
  [sample apps](#app2app-in-the-sample-apps) show how to integrate it.

## Error codes

Every SDK exception exposes a stable integer `errorCode` in the range `1000..99999`. Use it in your app's error
handling, telemetry and support diagnostics.

The code combines a **type prefix**, which says where in the flow the error happened, with either a **class
offset** or, for HTTP errors (`ResponseException`), the **HTTP status**:

```
errorCode = typePrefix + classOffset     // Non-HTTP errors
errorCode = typePrefix + httpStatus      // HTTP errors
```

<table>
<tr><th>Type prefix</th><th>Class offset</th></tr>
<tr valign="top"><td>

| Prefix  | Error type                            |
|---------|---------------------------------------|
| `1000`  | `GENERAL`                             |
| `2000`  | `DISCOVERY`                           |
| `3000`  | `AUTHORIZATION`                       |
| `4000`  | `AUTHORIZATION_CODE_EXCHANGE`         |
| `5000`  | `TOKEN_REFRESH`                       |
| `6000`  | `TOKEN_VERIFICATION_HOOK`             |
| `11000` | Session transfer (extension)          |
| `12000` | Browserless authorization (extension) |

</td><td>

| Offset | Exception                                         |
|--------|---------------------------------------------------|
| `+10`  | `AuthorizationException`                          |
| `+11`  | `BrowserNotFoundException`                        |
| `+12`  | `CancelledLoginException`                         |
| `+13`  | `MalformedResponseUriException`                   |
| `+14`  | `StateMismatchException`                          |
| `+15`  | `MissingRefreshTokenException`                    |
| `+16`  | `TokenVerificationException`                      |
| `+20`  | `NetworkException`                                |
| `+21`  | `NetworkTimeoutException`                         |
| `+22`  | `DeserializationException`                        |
| `+30`  | `MetadataException` (`NOT_AVAILABLE`)             |
| `+31`  | `MetadataException` (`DESERIALIZATION`)           |
| `+32`  | `MetadataException` (`MISSING_FIELD`)             |
| `+40`  | `DpopException` (`NO_SUPPORTED_ALGORITHM`)        |
| `+41`  | `DpopException` (`HARDWARE_BACKING_UNAVAILABLE`)  |
| `+42`  | `DpopException` (`KEY_GENERATION_FAILED`)         |
| `+43`  | `DpopException` (`KEY_MISSING`)                   |
| `+44`  | `DpopException` (`SIGNING_FAILED`)                |
| `+45`  | `DpopException` (`TOKEN_NOT_DPOP_BOUND`)          |
| `+50`  | `BrowserlessException` (`APP_NOT_INSTALLED`)      |
| `+51`  | `BrowserlessException` (`MALFORMED_URL`)          |
| `+52`  | `BrowserlessException` (`UNTRUSTED_REDIRECT_URI`) |

</td></tr>
</table>

A few exceptions use fixed codes: `UnauthorizedException` is `9700` and `SessionExpiredException` is `9710`.
An HTTP error with a status outside `100..599` is reported as `9600`.

**Examples**

| Situation                                          | Code                  |
|----------------------------------------------------|-----------------------|
| Discovery endpoint returns HTTP 404                | `2000 + 404 = 2404`   |
| Authorization `state` does not match               | `3000 + 14 = 3014`    |
| No secure element for a DPoP key                   | `3000 + 41 = 3041`    |
| Server issues bearer tokens while DPoP is enforced | `3000 + 45 = 3045`    |
| Token refresh times out                            | `5000 + 21 = 5021`    |
| Session transfer endpoint returns HTTP 404         | `11000 + 404 = 11404` |
| App2App: the user-interacting app is not installed | `12000 + 50 = 12050`  |

## Sample apps

[`examples/android`](examples/android) and [`examples/ios`](examples/ios) contain sample apps that show the full login
flow, including DPoP. Both use the shared KMP module in [`examples/shared`](examples/shared) for their logic, see the
[iOS sample's README](examples/ios/README.md) for how to build it. The initial DPoP configuration of both apps is in
[`SampleDpopConfig.kt`](examples/shared/src/commonMain/kotlin/org/heidiverse/connect/sdk/sample/shared/SampleDpopConfig.kt).
You can change it in the apps.

### App2App in the sample apps

Both apps also offer **Login with App2App**, the browserless App2App flow (see [Extensions](#extensions)). The app
sends the authorization request itself, and the authorization server answers with a deep link into the app the user
authenticates with (the *user-interacting app*). Once the user is done there, that app opens the sample's *native
callback URI* with a `redirect_uri`. The SDK checks that its host is trusted, calls it to get the authorization
code, and exchanges the code for tokens. The shared logic is in
[`OidcSampleManager.kt`](examples/shared/src/commonMain/kotlin/org/heidiverse/connect/sdk/sample/shared/OidcSampleManager.kt).
The flow in progress is saved before the other app opens, so the login still finishes if the OS kills the sample in
the meantime.

You need an authorization server that supports the flow and an installed user-interacting app. Before building:

- Register the native callback URI for your client at the authorization server. The default is
  `org.heidiverse.connect.sdk.sample://app2app`. It is set in
  [`SampleApp2AppConfig.kt`](examples/shared/src/commonMain/kotlin/org/heidiverse/connect/sdk/sample/shared/SampleApp2AppConfig.kt)
  and can be changed in the app. A custom scheme works without further setup. The spec recommends an https deep link
  (App Link or universal link) for production.
- Android, in [`AndroidManifest.xml`](examples/android/src/main/AndroidManifest.xml):
  - The native callback URI is an intent filter of `MainActivity`. `MainActivity` is `singleTask`, so that the
    callback returns to the running instance rather than starting a new one in the other app's task.
  - Replace the placeholder host in `<queries>` with the one of the user-interacting app's deep link. Without it,
    Android 11 and later hide that app, and the login fails with "No app found that can handle the deep link".
- iOS: see the [iOS sample's README](examples/ios/README.md#app2app).
- If the `redirect_uri` the user-interacting app returns is not on the host of the discovery URL, add its host to
  `SampleApp2AppConfig.additionalTrustedHosts`.

## Building from source

```bash
git clone https://github.com/heidiverse/heidentity-connect.git
cd heidentity-connect
./gradlew build
```

You need JDK 21. Android builds also need the Android SDK. iOS targets can only be built on macOS with Xcode.

## Contributing

Contributions are welcome. Please open an issue to discuss larger changes before you submit a pull request.
Before you submit, make sure `./gradlew build` passes and that new behavior is covered by tests.

## License

Heidentity Connect is licensed under the [Apache License 2.0](LICENSE).

<div align="center">
<sub>Built by <a href="https://www.ubique.ch">Ubique Innovation AG</a></sub>
</div>
