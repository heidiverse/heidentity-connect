import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.android.kotlin.multiplatform.library)
	alias(libs.plugins.skie)
	alias(libs.plugins.vanniktech.publish)
	alias(libs.plugins.kotlinx.atomicfu)
}

kotlin {
	compilerOptions {
		freeCompilerArgs.add("-Xexpect-actual-classes")
	}

	android {
		namespace = "org.heidiverse.connect.sdk.extensions.ktor"
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
			baseName = "heidentity-connect-extensions-ktor"
			isStatic = true

			export(projects.libExtensions)
			export(projects.libKtor)
		}
	}

	sourceSets {
		commonMain.dependencies {
			api(projects.libExtensions)
			api(projects.libKtor)
			implementation(libs.koin.core)
			implementation(libs.ktor.client.cio)
			implementation(libs.ktor.client.content.negotiation)
			implementation(libs.ktor.serialization.json)
			implementation(libs.kotlinx.atomicfu)
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

atomicfu {
	// The post-compilation bytecode transform looks for a task ('androidMainClasses') that the Android KMP library
	// plugin does not create. atomicfu still works without it, as a plain library.
	transformJvm = false
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
