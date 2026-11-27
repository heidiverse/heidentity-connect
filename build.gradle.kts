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

// The public Maven Central workflow stages every library here, then uploads them all as one deployment, so Central
// publishes all of them or none.
providers.gradleProperty("stagingRepository").map { file(it) }.orNull?.let { stagingRepository ->
	subprojects {
		pluginManager.withPlugin("maven-publish") {
			extensions.configure<PublishingExtension> {
				repositories.maven {
					name = "staging"
					url = stagingRepository.toURI()
				}
			}
		}
	}
}

private fun getProjectVersion(): String {
	val versionFromGradleProperties = runCatching { property("ARTIFACT_VERSION").toString() }.getOrNull()
	val versionFromWorkflow = runCatching { property("githubRefName").toString().removePrefix("v") }.getOrNull()
	return versionFromWorkflow ?: versionFromGradleProperties ?: "untagged"
}