rootProject.name = "heidentity-connect"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    fun setting(vararg environmentVariables: String, property: String): String? =
        (environmentVariables.map { System.getenv(it) } + providers.gradleProperty(property).orNull)
            .firstOrNull { !it.isNullOrBlank() }

    // Ubique's internal Maven repository, used only when it is configured. Ubique's builds need it
    // for the sample app's signing plugin (see examples/android/build.gradle.kts); everything else
    // comes from the public repositories, so a build without it, e.g. of the public mirror, works.
    val ubiqueMavenUrl = setting("UB_ARTIFACTORY_URL_ANDROID", "ARTIFACTORY_URL_ANDROID", property = "ubiqueMavenUrl")

    fun RepositoryHandler.ubique() {
        if (ubiqueMavenUrl == null) return
        maven {
            name = "ubique"
            url = uri(ubiqueMavenUrl)
            credentials {
                username = setting("UB_ARTIFACTORY_USER", "ARTIFACTORY_USER_NAME", property = "ubiqueMavenUser") ?: ""
                password = setting("UB_ARTIFACTORY_PASSWORD", "ARTIFACTORY_API_KEY", property = "ubiqueMavenPass") ?: ""
            }
            authentication {
                create<BasicAuthentication>("basic")
                create<DigestAuthentication>("digest")
            }
            content {
                includeGroupAndSubgroups("ch.ubique")
            }
        }
    }

    repositories {
        ubique()
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }

    dependencyResolutionManagement {
        repositories {
            ubique()
            google()
            mavenCentral()
        }

        versionCatalogs {
            // Not a catalog of dependencies: tells build scripts whether Ubique's Maven repository is
            // configured, as buildEnvironment.versions.ubiqueMaven. A plugins {} block can read a
            // version catalog, but not Gradle properties.
            create("buildEnvironment") {
                version("ubiqueMaven", (ubiqueMavenUrl != null).toString())
            }
        }
    }
}

include(":examples:android")
include(":examples:shared")

include(":lib-core")
include(":lib-extensions")
include(":lib-extensions-ktor")
include(":lib-extensions-okhttp")
include(":lib-ktor")
include(":lib-okhttp")
