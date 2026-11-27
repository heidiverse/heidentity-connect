import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	alias(libs.plugins.kotlin.serialization)
	alias(libs.plugins.android.library)
	alias(libs.plugins.vanniktech.publish)
}

kotlin {
	compilerOptions {
		jvmTarget = JvmTarget.JVM_21
	}
}

android {
	namespace = "org.heidiverse.connect.sdk.okhttp"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()
	}

	buildTypes {
		release {
			isMinifyEnabled = false
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
		}
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_21
		targetCompatibility = JavaVersion.VERSION_21
	}
}

mavenPublishing {
	// Maven Central requires a javadoc jar. An empty one, because Dokka has issues with JVM 11+:
	// https://github.com/Kotlin/dokka/issues/2956
	configure(AndroidSingleVariantLibrary(javadocJar = JavadocJar.Empty(), sourcesJar = SourcesJar.Sources(), variant = "release"))
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

dependencies {
	api(projects.libCore)
	implementation(libs.koin.core)
	implementation(libs.androidx.coreKtx)
	implementation(libs.androidx.appcompat)
	implementation(libs.okhttp)
	implementation(libs.kotlin.serialization)
}
