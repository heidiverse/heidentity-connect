package org.heidiverse.connect.sdk.sample.shared

/**
 * Build-time configuration of the App2App login, the
 * [browserless App2App flow](https://datatracker.ietf.org/doc/html/draft-zehavi-oauth-app2app-browserless).
 *
 * In that flow, the authorization request is sent by the sample app itself rather than by a browser. The authorization
 * server answers with a deep link into the app the user authenticates with (the "user-interacting app"). Once the
 * user is done there, that app opens the native callback URI of the sample app, which then completes the login.
 *
 * Everything here has a counterpart in the platform projects that has to be kept in sync, see the "App2App" sections
 * of the README and of examples/ios/README.md.
 */
internal object SampleApp2AppConfig {

	/**
	 * The native callback URI the sample apps start with. It can be changed in the app, in the IDP config.
	 *
	 * It must be registered with the authorization server for the client, and as a deep link of the app: in the
	 * intent filter of MainActivity in AndroidManifest.xml, and in CFBundleURLTypes in examples/ios/project.yml.
	 *
	 * A custom scheme works without any further setup. The spec recommends an https deep link instead (an Android App
	 * Link or an iOS universal link), because a custom scheme can be registered by any other app as well.
	 */
	const val DEFAULT_NATIVE_CALLBACK_URI = "org.heidiverse.connect.sdk.sample://app2app"

	/**
	 * The hosts the redirect_uri handed back by the user-interacting app may point to, in addition to the host of the
	 * discovery URL. The app calls that URL to continue the flow, so it must only accept URLs of authorization servers
	 * it trusts. Add a host here if the authorization server federates the login to another one.
	 *
	 * Passed to the BrowserlessAuthorizationController, which rejects the callback if its host isn't trusted.
	 */
	val additionalTrustedHosts: Set<String> = emptySet()

	/**
	 * Only used on iOS: maps the host of an https (universal link) deep link of a user-interacting app to a custom
	 * scheme of the same app, e.g. `"authenticator.example.com" to "com.example.authenticator://"`.
	 *
	 * iOS reports every https URL as openable, since Safari can always open it. To still find out whether the
	 * user-interacting app is installed, the SDK checks whether this custom scheme can be opened. The scheme must then
	 * be listed in LSApplicationQueriesSchemes in examples/ios/project.yml.
	 */
	val customSchemeLookup: Map<String, String> = emptyMap()

}
