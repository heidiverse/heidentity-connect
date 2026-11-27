plugins {
	// Kotlin & KMP plugins
	alias(libs.plugins.kotlin.multiplatform) apply false
	alias(libs.plugins.kotlin.serialization) apply false
	alias(libs.plugins.compose.multiplatform) apply false
	alias(libs.plugins.kotlin.parcelize) apply false
	alias(libs.plugins.jetbrains.kotlin.jvm) apply false

	// Android specific plugins
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.android.library) apply false
	alias(libs.plugins.android.kotlin.multiplatform.library) apply false
	alias(libs.plugins.kotlin.android) apply false
	alias(libs.plugins.compose.compiler) apply false

	// iOS specific plugins
	alias(libs.plugins.skie) apply false

	// Ubique plugins
	alias(libs.plugins.ubique.alpaka) apply false
	alias(libs.plugins.ubique.preset) apply false

	// Library publishing plugins
	alias(libs.plugins.vanniktech.publish) apply false
}

allprojects {
	group = "org.heidiverse.connect.sdk"
	version = getProjectVersion()
}

private fun getProjectVersion(): String {
	val versionFromGradleProperties = runCatching { property("ARTIFACT_VERSION").toString() }.getOrNull()
	val versionFromWorkflow = runCatching { property("githubRefName").toString().removePrefix("v") }.getOrNull()
	return versionFromWorkflow ?: versionFromGradleProperties ?: "untagged"
}