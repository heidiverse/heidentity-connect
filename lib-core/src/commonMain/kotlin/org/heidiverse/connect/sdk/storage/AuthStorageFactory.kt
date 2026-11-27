package org.heidiverse.connect.sdk.storage

internal interface AuthStorageFactory {

	fun createDefault(): AuthStorage

}