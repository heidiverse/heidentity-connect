plugins {
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.compose.compiler)

	alias(libs.plugins.ubique.alpaka)
	alias(libs.plugins.ubique.preset)
	if (buildEnvironment.versions.ubiqueMaven.get().toBoolean()) alias(libs.plugins.ubique.signing)
}

android {
	namespace = "org.heidiverse.connect.sdk.sample"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		applicationId = "org.heidiverse.connect.sdk.sample"
		minSdk = libs.versions.android.minSdk.get().toInt()
		targetSdk = libs.versions.android.targetSdk.get().toInt()
		versionCode = 1
		versionName = "1.0.0"

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

		alpakaUploadKey = System.getenv("ALPAKA_UPLOAD_KEY") ?: ""
	}

	buildTypes {
		release {
			isMinifyEnabled = false
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
		}
	}

	productFlavors {
		create("prod") {
			// Only needed for the Alpaka plugin
		}
	}

	buildFeatures {
		compose = true
		viewBinding = true
	}
}

dependencies {
	implementation(projects.examples.shared)
	implementation(libs.androidx.coreKtx)
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.lifecycle.runtimeKtx)
	implementation(libs.androidx.activity.compose)

	implementation(libs.ktor.client.logging)

	implementation(libs.kotlin.serialization)
	implementation(libs.kotlin.coroutines)
	implementation(libs.koin.android)

	implementation(platform(libs.compose.bom))
	implementation(libs.compose.ui)
	implementation(libs.compose.ui.graphics)
	implementation(libs.compose.ui.tooling.preview)
	implementation(libs.compose.material3)
	debugImplementation(libs.compose.ui.tooling)
	debugImplementation(libs.compose.ui.test.manifest)
}