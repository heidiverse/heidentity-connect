import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.swiftexport.ExperimentalSwiftExportDsl

plugins {
	alias(libs.plugins.kotlin.multiplatform)
	alias(libs.plugins.android.kotlin.multiplatform.library)
}

@OptIn(ExperimentalSwiftExportDsl::class)
kotlin {
	compilerOptions {
		freeCompilerArgs.add("-Xexpect-actual-classes")
	}

	android {
		namespace = "org.heidiverse.connect.sdk.sample.shared"
		compileSdk = libs.versions.android.compileSdk.get().toInt()
		minSdk = libs.versions.android.minSdk.get().toInt()

		compilerOptions {
			jvmTarget.set(JvmTarget.JVM_21)
		}
	}

	iosArm64()
	iosSimulatorArm64()

	swiftExport {
		moduleName = "OidcSampleShared"
		flattenPackage = "org.heidiverse.connect.sdk.sample.shared"
	}

	sourceSets {
		all {
			languageSettings.optIn("kotlinx.cinterop.ExperimentalForeignApi")
		}

		commonMain.dependencies {
			api(projects.libKtor)
			// The browserless App2App flow (BrowserlessAuthorizationController, HeidentityConnectBrowserlessKtor)
			api(projects.libExtensionsKtor)
			// Parses the App2App callback URL
			implementation(libs.uri)
			implementation(libs.kotlin.coroutines)
			// For pretty printing decoded JWT payloads
			implementation(libs.kotlin.serialization)
			// AuthorizationController is a KoinComponent, so Koin needs to be on the compile classpath
			implementation(libs.koin.core)
		}
	}
}
