# iOS sample app

A SwiftUI app that walks through the whole OpenID Connect login flow: provider discovery, login in an
`ASWebAuthenticationSession` or [App2App](#app2app), token exchange and logout.

The login logic lives in the shared KMP module [`examples/shared`](../shared). The Android sample uses the same
module. The module is bridged to Swift with [Kotlin Swift Export](https://kotlinlang.org/docs/native-swift-export.html).
The SwiftUI layer only renders the `StateFlow`s of `OidcSampleManager` and forwards user actions to it.

## Running

Requirements: Xcode and JDK 21.

1. Open `OidcSample.xcodeproj`.
2. Select the `OidcSample` scheme and a simulator, then run.

The **Compile KMP** build phase runs `./gradlew :examples:shared:embedSwiftExportForXcode`, which builds the
Kotlin code as part of the Xcode build. It looks up JDK 21 with `/usr/libexec/java_home`. To use a different JDK,
set `JAVA_HOME`. The first build takes a few minutes.

## HTTPS redirect URIs

With a custom scheme redirect URI (e.g. `org.heidiverse.connect.sdk.sample://oidc/callback`), the login works without any setup.

An HTTPS redirect URI is only delivered back to the app if its host is a `webcredentials` associated domain of the
app:

1. **Host:** the redirect host must serve `/.well-known/apple-app-site-association` and list the sample's app ID:

   ```json
   {
     "webcredentials": {
       "apps": ["<TEAM_ID>.org.heidiverse.connect.sdk.sample"]
     }
   }
   ```

2. **App:** set `OIDC_REDIRECT_HOST` in `project.yml` to the redirect host and regenerate the project. To run on a
   device, also set `DEVELOPMENT_TEAM`, because the associated domains capability needs a provisioning profile.

3. **Device:** enable **Settings → Developer → Associated Domains Development**. The Developer menu appears after the
   device has been connected to Xcode once.

The entitlement uses `?mode=developer`. With that setting enabled, iOS fetches the association file directly from
the host instead of through Apple's CDN. The host can then be an internal development server that Apple can't
reach, and changes to the file take effect immediately rather than after the CDN refreshes. The host still has to
serve the file, and the file must list the app ID. Release builds ignore developer mode.

## App2App

**Login with App2App** runs the
[browserless App2App flow](https://datatracker.ietf.org/doc/html/draft-zehavi-oauth-app2app-browserless) with
`BrowserlessAuthorizationController`. The app sends the authorization request itself, and the authorization server
answers with a deep link into the app the user authenticates with (the *user-interacting app*). Once the user is done
there, that app opens the sample's *native callback URI*. The sample then finishes the login and exchanges the code
for tokens. The logic is in `OidcSampleManager` (`startApp2AppLogin`, `handleApp2AppCallback`). The SwiftUI layer
forwards the callback from `.onOpenURL` to it.

The authorization server must support the flow, and the user-interacting app must be installed. Before building:

1. **Callback:** register the native callback URI for your client at the authorization server. The sample uses
   `org.heidiverse.connect.sdk.sample://app2app`, a custom scheme registered under `CFBundleURLTypes` in `project.yml`. To
   use another one, change `CFBundleURLTypes` and the default in
   [`SampleApp2AppConfig.kt`](../shared/src/commonMain/kotlin/org/heidiverse/connect/sdk/sample/shared/SampleApp2AppConfig.kt),
   or enter it in the app. The spec recommends an https callback (a universal link) instead. That needs an
   `applinks:` associated domain and an `apple-app-site-association` file that lists the app under `applinks`.
2. **User-interacting app:** before opening its deep link, the SDK checks with `canOpenURL` that an app can handle
   it. For a custom scheme deep link, list the scheme in `LSApplicationQueriesSchemes` in `project.yml`, otherwise the
   check always fails. `canOpenURL` is always true for an https deep link, because Safari can open it. To still detect
   a missing app, map the link's host to a custom scheme of the same app in `SampleApp2AppConfig.customSchemeLookup`,
   and list that scheme in `LSApplicationQueriesSchemes`.
3. **Trusted hosts:** the user-interacting app returns a `redirect_uri` that the sample then calls. The SDK only
   accepts URLs on the host of the discovery URL. Add any other host of a trusted authorization server, e.g. when it
   federates the login, to `SampleApp2AppConfig.additionalTrustedHosts`.

Regenerate the project after changing `project.yml`, see below.

The login in progress is saved in `UserDefaults` (`App2AppFlowStorage`) before the other app opens. If iOS
terminates the sample in the meantime, the callback still completes the login when it relaunches the app.

## Changing the project

`project.yml` is the source of truth for the Xcode project. Don't edit `OidcSample.xcodeproj` by hand. After
changing `project.yml`, regenerate the project with [XcodeGen](https://github.com/yonaskolb/XcodeGen):

```bash
brew install xcodegen
cd examples/ios
xcodegen generate
```

Swift files in `OidcSample/` are picked up automatically, so adding a file doesn't require regenerating.
