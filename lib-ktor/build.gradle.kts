import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.android.kotlin.multiplatform.library)
	alias(libs.plugins.skie)
	alias(libs.plugins.vanniktech.publish)
}

kotlin {
	compilerOptions {
		freeCompilerArgs.add("-Xexpect-actual-classes")
	}

	android {
		namespace = "org.heidiverse.connect.sdk.ktor"
		compileSdk = libs.versions.android.compileSdk.get().toInt()
		minSdk = libs.versions.android.minSdk.get().toInt()

		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_21)
		}

		withHostTest {}
	}

	listOf(
		iosX64(),
		iosArm64(),
		iosSimulatorArm64()
	).forEach { iosTarget ->
		iosTarget.binaries.framework {
			baseName = "heidentity-connect-ktor"
			isStatic = true

			export(projects.libCore)
		}
	}

	sourceSets {
		commonMain.dependencies {
			api(projects.libCore)
			implementation(libs.koin.core)
			implementation(libs.ktor.client.cio)
			implementation(libs.ktor.client.content.negotiation)
			implementation(libs.ktor.serialization.json)
		}

		commonTest.dependencies {
			implementation(libs.kotlin.test)
		}

		androidMain.dependencies {
		}

		iosMain.dependencies {
			implementation(libs.ktor.client.darwin)
		}
	}
}

skie {
	analytics {
		enabled = false
		disableUpload = true
	}
}

mavenPublishing {
	coordinates(project.group.toString(), property("ARTIFACT_ID").toString(), project.version.toString())
}

publishing {
	repositories {
		val ubiqueMavenUrl = System.getenv("UB_ARTIFACTORY_URL_ANDROID") ?: providers.gradleProperty("ubiqueMavenUrl").orNull
		if (!ubiqueMavenUrl.isNullOrBlank()) {
			maven {
				url = uri(ubiqueMavenUrl)
				credentials {
					username = System.getenv("UB_ARTIFACTORY_USER") ?: providers.gradleProperty("ubiqueMavenUser").orNull ?: ""
					password = System.getenv("UB_ARTIFACTORY_PASSWORD") ?: providers.gradleProperty("ubiqueMavenPass").orNull ?: ""
				}
			}
		}
	}
}
