package org.heidiverse.connect.sdk.sample.feature.oidc

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.heidiverse.connect.sdk.dpop.DpopMode
import org.heidiverse.connect.sdk.sample.shared.IdpConfigState
import org.heidiverse.connect.sdk.sample.shared.JwtPayload
import org.heidiverse.connect.sdk.sample.shared.LoginState

@Composable
fun OidcScreen(
	idpConfigState: State<IdpConfigState>,
	loginState: State<LoginState>,
	onIdpConfigChanged: (IdpConfigState) -> Unit,
	onLoginClicked: (scope: String, prompt: String) -> Unit,
	onApp2AppLoginClicked: (scope: String, prompt: String) -> Unit,
	onRefreshClicked: () -> Unit,
	onLogoutClicked: () -> Unit,
	onRetryClicked: () -> Unit,
) {
	val scrollState = rememberScrollState()

	Column(
		modifier = Modifier
			.systemBarsPadding()
			.fillMaxSize()
			.verticalScroll(scrollState)
			.padding(horizontal = 16.dp, vertical = 12.dp)
	) {
		IdpConfigSection(idpConfigState, onIdpConfigChanged)

		HorizontalDivider(modifier = Modifier.padding(vertical = 15.dp))

		val loginStateValue = loginState.value
		val errorMessage = loginStateValue.errorMessage
		when {
			loginStateValue.isLoggedIn -> LoggedInState(loginStateValue, onRefreshClicked, onLogoutClicked)
			loginStateValue.isLoading -> AuthLoadingState()
			errorMessage != null -> AuthErrorState(errorMessage, onRetryClicked)
			else -> LoggedOutState(
				isApp2AppAvailable = idpConfigState.value.nativeCallbackUri.isNotBlank(),
				onLoginClicked = onLoginClicked,
				onApp2AppLoginClicked = onApp2AppLoginClicked,
			)
		}
	}
}

@Composable
private fun IdpConfigSection(
	idpConfigState: State<IdpConfigState>,
	onIdpConfigChanged: (IdpConfigState) -> Unit,
) {
	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		val idpConfig = idpConfigState.value

		var discoveryUrl by remember(idpConfig) { mutableStateOf(idpConfig.discoveryUrl) }
		var redirectUri by remember(idpConfig) { mutableStateOf(idpConfig.redirectUri) }
		var clientId by remember(idpConfig) { mutableStateOf(idpConfig.clientId) }
		var clientSecret by remember(idpConfig) { mutableStateOf(idpConfig.clientSecret) }
		var nativeCallbackUri by remember(idpConfig) { mutableStateOf(idpConfig.nativeCallbackUri) }
		var dpopEnabled by remember(idpConfig) { mutableStateOf(idpConfig.dpopEnabled) }
		var dpopMode by remember(idpConfig) {
			mutableStateOf(if (idpConfig.dpopEnforced) DpopMode.ENFORCED else DpopMode.OPPORTUNISTIC)
		}
		var allowSoftwareKeyFallback by remember(idpConfig) { mutableStateOf(idpConfig.dpopAllowSoftwareKeyFallback) }

		OutlinedTextField(
			value = discoveryUrl,
			onValueChange = { discoveryUrl = it.trim() },
			label = { Text("Discovery URL") },
			colors = OutlinedTextFieldDefaults.colors(
				focusedTextColor = Color.Black,
				unfocusedTextColor = Color.Black,
			),
			modifier = Modifier.fillMaxWidth(),
		)

		OutlinedTextField(
			value = redirectUri,
			onValueChange = { redirectUri = it.trim() },
			label = { Text("Redirect URI") },
			colors = OutlinedTextFieldDefaults.colors(
				focusedTextColor = Color.Black,
				unfocusedTextColor = Color.Black,
			),
			modifier = Modifier.fillMaxWidth(),
		)

		OutlinedTextField(
			value = clientId,
			onValueChange = { clientId = it.trim() },
			label = { Text("Client ID") },
			colors = OutlinedTextFieldDefaults.colors(
				focusedTextColor = Color.Black,
				unfocusedTextColor = Color.Black,
			),
			modifier = Modifier.fillMaxWidth(),
		)

		OutlinedTextField(
			value = clientSecret,
			onValueChange = { clientSecret = it.trim() },
			label = { Text("Client Secret") },
			colors = OutlinedTextFieldDefaults.colors(
				focusedTextColor = Color.Black,
				unfocusedTextColor = Color.Black,
			),
			modifier = Modifier.fillMaxWidth(),
		)

		OutlinedTextField(
			value = nativeCallbackUri,
			onValueChange = { nativeCallbackUri = it.trim() },
			label = { Text("Native Callback URI (App2App)") },
			supportingText = { Text("Leave empty to turn off the App2App login") },
			colors = OutlinedTextFieldDefaults.colors(
				focusedTextColor = Color.Black,
				unfocusedTextColor = Color.Black,
			),
			modifier = Modifier.fillMaxWidth(),
		)

		Spacer(Modifier.height(10.dp))

		DpopSettings(
			enabled = dpopEnabled,
			onEnabledChanged = { dpopEnabled = it },
			mode = dpopMode,
			onModeChanged = { dpopMode = it },
			allowSoftwareKeyFallback = allowSoftwareKeyFallback,
			onAllowSoftwareKeyFallbackChanged = { allowSoftwareKeyFallback = it },
			supportedAlgorithms = idpConfig.dpopAlgorithms,
		)

		Spacer(Modifier.height(10.dp))

		Text(
			"Note: The redirect URI and the native callback URI must be registered in the AndroidManifest.xml, and " +
				"for App2App the user-interacting app's deep link must be listed in its <queries>"
		)

		Spacer(Modifier.height(10.dp))

		Button(
			onClick = {
				val newIdpConfig = IdpConfigState(
					discoveryUrl = discoveryUrl,
					redirectUri = redirectUri,
					clientId = clientId,
					clientSecret = clientSecret,
					nativeCallbackUri = nativeCallbackUri,
					dpopEnabled = dpopEnabled,
					dpopEnforced = dpopMode == DpopMode.ENFORCED,
					dpopAllowSoftwareKeyFallback = allowSoftwareKeyFallback,
				)
				onIdpConfigChanged.invoke(newIdpConfig)
			}
		) {
			Text("Save IDP Config")
		}
	}
}

/**
 * The DPoP part of the IDP config: a switch to turn it on, and the options that only make sense while it is on. The
 * algorithms are shown but not editable, since ES256 is the only one the SDK supports.
 */
@Composable
private fun DpopSettings(
	enabled: Boolean,
	onEnabledChanged: (Boolean) -> Unit,
	mode: DpopMode,
	onModeChanged: (DpopMode) -> Unit,
	allowSoftwareKeyFallback: Boolean,
	onAllowSoftwareKeyFallbackChanged: (Boolean) -> Unit,
	supportedAlgorithms: String,
) {
	Column(modifier = Modifier.fillMaxWidth()) {
		SettingSwitch(
			title = "DPoP",
			description = "Sender-constrained tokens (RFC 9449)",
			checked = enabled,
			onCheckedChange = onEnabledChanged,
		)

		AnimatedVisibility(visible = enabled) {
			Column(
				modifier = Modifier
					.fillMaxWidth()
					.padding(start = 16.dp, top = 8.dp),
			) {
				Text("Mode", style = MaterialTheme.typography.labelLarge)

				DpopMode.entries.forEach { entry ->
					Row(
						modifier = Modifier
							.fillMaxWidth()
							.clickable { onModeChanged(entry) },
						verticalAlignment = Alignment.CenterVertically,
					) {
						RadioButton(
							selected = mode == entry,
							onClick = { onModeChanged(entry) },
						)
						Column(modifier = Modifier.padding(vertical = 4.dp)) {
							Text(entry.name)
							Text(
								text = when (entry) {
									DpopMode.ENFORCED -> "Fail if the server does not issue DPoP-bound tokens"
									DpopMode.OPPORTUNISTIC -> "Fall back to bearer tokens if DPoP is unavailable"
								},
								style = MaterialTheme.typography.bodySmall,
							)
						}
					}
				}

				Spacer(Modifier.height(8.dp))

				SettingSwitch(
					title = "Allow software key fallback",
					description = "Needed on emulators without a secure element",
					checked = allowSoftwareKeyFallback,
					onCheckedChange = onAllowSoftwareKeyFallbackChanged,
				)

				Spacer(Modifier.height(8.dp))

				Text(
					text = "Algorithms: $supportedAlgorithms",
					style = MaterialTheme.typography.bodySmall,
				)
			}
		}
	}
}

@Composable
private fun SettingSwitch(
	title: String,
	description: String,
	checked: Boolean,
	onCheckedChange: (Boolean) -> Unit,
) {
	Row(
		modifier = Modifier.fillMaxWidth(),
		verticalAlignment = Alignment.CenterVertically,
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(title)
			Text(description, style = MaterialTheme.typography.bodySmall)
		}

		Switch(checked = checked, onCheckedChange = onCheckedChange)
	}
}

@Composable
private fun LoggedInState(
	loginState: LoginState,
	onRefreshClicked: () -> Unit,
	onLogoutClicked: () -> Unit,
) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		val tokens = loginState.tokens
		var showDecodedPayloads by remember { mutableStateOf(false) }

		if (tokens != null) {
			TextButton(onClick = { showDecodedPayloads = !showDecodedPayloads }) {
				Text(if (showDecodedPayloads) "Show raw tokens" else "Decode JWT payloads")
			}

			if (showDecodedPayloads) {
				Text(
					text = "Payloads are only base64-decoded. No signature, issuer or expiry is checked — do not " +
						"trust anything you read here \uD83D\uDD75\uFE0F",
					style = MaterialTheme.typography.bodySmall,
					modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
				)
			}
		}

		tokens?.accessToken?.let { token ->
			TokenEntry("Access Token", token, showDecodedPayloads)
		}

		tokens?.idToken?.let { token ->
			Spacer(Modifier.height(10.dp))
			TokenEntry("ID Token", token, showDecodedPayloads)
		}

		tokens?.refreshToken?.let { token ->
			Spacer(Modifier.height(10.dp))
			TokenEntry("Refresh Token", token, showDecodedPayloads)
		}

		Spacer(Modifier.height(10.dp))

		Button(
			onClick = onRefreshClicked,
			enabled = !loginState.isLoading,
		) {
			if (loginState.isLoading) {
				CircularProgressIndicator(
					strokeWidth = 2.dp,
					modifier = Modifier.size(16.dp),
				)
			} else {
				Text("Refresh Tokens")
			}
		}

		loginState.errorMessage?.let { errorMessage ->
			Spacer(Modifier.height(10.dp))
			Text("Refresh failed: $errorMessage", style = MaterialTheme.typography.labelMedium)
		}

		Spacer(Modifier.height(10.dp))

		Button(
			onClick = onLogoutClicked
		) {
			Text("Logout")
		}
	}
}

/**
 * One token, either as it was issued or as its decoded JWT payload. Tapping it always copies the raw token, which is
 * what can be pasted into jwt.io or a curl command.
 */
@Composable
private fun TokenEntry(
	label: String,
	token: String,
	showDecodedPayload: Boolean,
) {
	val clipboardManager = LocalClipboardManager.current
	val decodedPayload = remember(token, showDecodedPayload) {
		if (showDecodedPayload) JwtPayload.decode(token) else null
	}

	Text(label, style = MaterialTheme.typography.labelLarge)
	Text(
		text = decodedPayload
			?: if (showDecodedPayload) "$token\n\n(not a JWT, nothing to decode)" else token,
		style = MaterialTheme.typography.labelMedium,
		maxLines = if (decodedPayload != null) 40 else 5,
		overflow = TextOverflow.Ellipsis,
		modifier = Modifier
			.clip(RoundedCornerShape(10.dp))
			.background(Color.LightGray.copy(alpha = 0.5f))
			.clickable { clipboardManager.setText(AnnotatedString(token)) }
			.padding(5.dp)
	)
}

@Composable
private fun LoggedOutState(
	isApp2AppAvailable: Boolean,
	onLoginClicked: (scope: String, prompt: String) -> Unit,
	onApp2AppLoginClicked: (scope: String, prompt: String) -> Unit,
) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		var scope by remember { mutableStateOf("openid offline_access") }
		var prompt by remember { mutableStateOf("login") }

		OutlinedTextField(
			value = scope,
			onValueChange = { scope = it },
			label = { Text("Scope") },
			colors = OutlinedTextFieldDefaults.colors(
				focusedTextColor = Color.Black,
				unfocusedTextColor = Color.Black,
			),
			modifier = Modifier.fillMaxWidth(),
		)

		OutlinedTextField(
			value = prompt,
			onValueChange = { prompt = it },
			label = { Text("Prompt") },
			colors = OutlinedTextFieldDefaults.colors(
				focusedTextColor = Color.Black,
				unfocusedTextColor = Color.Black,
			),
			modifier = Modifier.fillMaxWidth(),
		)

		Spacer(Modifier.height(10.dp))

		Button(
			onClick = {
				onLoginClicked.invoke(scope.trim(), prompt.trim())
			}
		) {
			Text("Login")
		}

		OutlinedButton(
			onClick = {
				onApp2AppLoginClicked.invoke(scope.trim(), prompt.trim())
			},
			enabled = isApp2AppAvailable,
		) {
			Text("Login with App2App")
		}

		Text(
			text = if (isApp2AppAvailable) {
				"Logs in through the app the authorization server links to, without a browser"
			} else {
				"Set a native callback URI to log in with App2App"
			},
			style = MaterialTheme.typography.bodySmall,
		)
	}
}

@Composable
private fun AuthLoadingState() {
	Column(
		modifier = Modifier.fillMaxWidth(),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		CircularProgressIndicator()
	}
}

@Composable
private fun AuthErrorState(
	errorMessage: String,
	onRetryClicked: () -> Unit,
) {
	Column(
		modifier = Modifier.fillMaxWidth(),
		horizontalAlignment = Alignment.CenterHorizontally,
	) {
		Text(errorMessage)
		Button(
			onClick = onRetryClicked
		) {
			Text("Retry")
		}
	}
}