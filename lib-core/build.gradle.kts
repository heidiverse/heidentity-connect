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
		namespace = "org.heidiverse.connect.sdk"
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
			baseName = "heidentity-connect"
			isStatic = true
		}
	}

	sourceSets {
		commonMain.dependencies {
			implementation(libs.kotlin.coroutines)
			implementation(libs.koin.core)
			implementation(libs.uri)
			implementation(libs.kotlin.serialization)
			implementation(libs.kotlin.datetime)
		}

		commonTest.dependencies {
			implementation(libs.kotlin.test)
			implementation(libs.kotlin.coroutines.test)
			implementation(libs.kotlin.serialization)
		}

		androidMain.dependencies {
			implementation(libs.koin.android)
			implementation(libs.androidx.browser)
		}

		iosMain.dependencies {
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
