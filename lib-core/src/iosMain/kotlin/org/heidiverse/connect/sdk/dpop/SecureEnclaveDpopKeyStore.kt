package org.heidiverse.connect.sdk.dpop

import kotlinx.cinterop.*
import org.heidiverse.connect.sdk.exception.AuthorizationErrorType
import org.heidiverse.connect.sdk.util.BASE64_URL
import org.heidiverse.connect.sdk.util.RandomGenerator
import platform.CoreFoundation.*
import platform.Foundation.*
import platform.Security.*
import platform.darwin.OSStatus
import platform.darwin.noErr
import platform.posix.memcpy
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * [DpopKeyStore] backed by the iOS keychain, with the private key generated inside the Secure Enclave.
 *
 * The access control is `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` with no biometric or user-presence flag:
 * token refreshes can run while the app is backgrounded and the device locked, and requiring user presence would turn
 * those into hard session failures. `ThisDeviceOnly` also keeps the key out of backups and off other devices — implied
 * anyway for a Secure Enclave key, but it is what makes a software-fallback key behave the same way.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class SecureEnclaveDpopKeyStore : DpopKeyStore {

	companion object {
		private const val P256_KEY_SIZE_BITS = 256

		/** Byte size of one P-256 coordinate. */
		private const val P256_FIELD_SIZE = 32

		/** Length of an uncompressed EC point: a 0x04 prefix followed by both coordinates. */
		private const val UNCOMPRESSED_POINT_SIZE = 1 + P256_FIELD_SIZE * 2
		private const val UNCOMPRESSED_POINT_PREFIX: Byte = 0x04

		private const val ALIAS_ENTROPY_BYTES = 12
	}

	override fun isAlgorithmSupported(algorithm: DpopAlgorithm, requireHardwareBacked: Boolean): Boolean {
		// There is no API to probe Secure Enclave availability without creating a key, so hardware backing is decided by
		// generateKey(), which reports HARDWARE_BACKING_UNAVAILABLE when the enclave refuses and no fallback is allowed.
		return algorithm == DpopAlgorithm.ES256
	}

	override fun generateKey(algorithm: DpopAlgorithm, allowSoftwareFallback: Boolean): DpopKeyRef {
		require(algorithm == DpopAlgorithm.ES256) { "Unsupported algorithm $algorithm" }

		val alias = DpopKeyRef.ALIAS_PREFIX + RandomGenerator.generateRandomString(bytes = ALIAS_ENTROPY_BYTES)

		createKey(alias, useSecureEnclave = true)?.let { key ->
			CFRelease(key)
			return DpopKeyRef(alias = alias, algorithm = algorithm, isHardwareBacked = true)
		}

		// No Secure Enclave (simulator, or an enclave that refused this request)
		if (!allowSoftwareFallback) {
			deleteKey(alias)
			throw DpopException(
				errorType = AuthorizationErrorType.AUTHORIZATION,
				reason = DpopException.Reason.HARDWARE_BACKING_UNAVAILABLE,
				message = "The Secure Enclave is unavailable. Enable DpopConfig.allowSoftwareKeyFallback to permit a " +
					"software-backed key.",
			)
		}

		// A partially created entry would make the retry fail with errSecDuplicateItem
		deleteKey(alias)

		val softwareKey = createKey(alias, useSecureEnclave = false)
			?: throw DpopException(
				errorType = AuthorizationErrorType.AUTHORIZATION,
				reason = DpopException.Reason.KEY_GENERATION_FAILED,
				message = "Could not generate a DPoP key pair",
			)
		CFRelease(softwareKey)

		return DpopKeyRef(alias = alias, algorithm = algorithm, isHardwareBacked = false)
	}

	override fun getPublicJwk(keyRef: DpopKeyRef): DpopJwk {
		val privateKey = findPrivateKey(keyRef.alias) ?: throw keyMissing(keyRef.alias)

		try {
			val publicKey = SecKeyCopyPublicKey(privateKey) ?: throw keyMissing(keyRef.alias)
			try {
				val representation = SecKeyCopyExternalRepresentation(publicKey, null)
					?: throw keyMissing(keyRef.alias)
				val point = (CFBridgingRelease(representation) as NSData).toByteArray()

				require(point.size == UNCOMPRESSED_POINT_SIZE && point[0] == UNCOMPRESSED_POINT_PREFIX) {
					"Expected an uncompressed EC point of $UNCOMPRESSED_POINT_SIZE bytes, got ${point.size}"
				}

				return DpopJwk(
					keyType = DpopJwk.KEY_TYPE_EC,
					curve = keyRef.algorithm.curve,
					x = point.copyOfRange(1, 1 + P256_FIELD_SIZE).toBase64Url(),
					y = point.copyOfRange(1 + P256_FIELD_SIZE, UNCOMPRESSED_POINT_SIZE).toBase64Url(),
				)
			} finally {
				CFRelease(publicKey)
			}
		} finally {
			CFRelease(privateKey)
		}
	}

	override fun sign(keyRef: DpopKeyRef, data: ByteArray): ByteArray {
		val privateKey = findPrivateKey(keyRef.alias) ?: throw keyMissing(keyRef.alias)

		val der = try {
			val dataRef: CFDataRef? = CFBridgingRetain(data.toNSData())?.reinterpret()
			try {
				// A fresh signature per call — nothing is cached that could be shared across coroutines
				val signature = SecKeyCreateSignature(
					privateKey,
					kSecKeyAlgorithmECDSASignatureMessageX962SHA256,
					dataRef,
					null,
				) ?: throw DpopException(
					errorType = AuthorizationErrorType.GENERAL,
					reason = DpopException.Reason.SIGNING_FAILED,
					message = "The keychain did not produce a signature for alias ${keyRef.alias}",
				)
				(CFBridgingRelease(signature) as NSData).toByteArray()
			} finally {
				dataRef?.let { CFRelease(it) }
			}
		} finally {
			CFRelease(privateKey)
		}

		return try {
			EcdsaSignature.derToJose(der, P256_FIELD_SIZE)
		} catch (e: IllegalArgumentException) {
			throw DpopException(
				errorType = AuthorizationErrorType.GENERAL,
				reason = DpopException.Reason.SIGNING_FAILED,
				message = "The keychain returned a signature that is not a well-formed ECDSA signature",
				cause = e,
			)
		}
	}

	override fun deleteKey(alias: String) {
		cfScoped {
			val query = dictionary(
				kSecClass to kSecClassKey,
				kSecAttrApplicationTag to bridge(alias.toNSData()),
			)
			// A missing key is not an error, and neither is a keychain that refuses the delete — the caller must not fail
			SecItemDelete(query)
		}
	}

	override fun listAliases(): List<String> {
		return cfScoped {
			val tagKey = kSecAttrApplicationTag.toKotlinString() ?: return@cfScoped emptyList()

			val query = dictionary(
				kSecClass to kSecClassKey,
				kSecAttrKeyClass to kSecAttrKeyClassPrivate,
				kSecMatchLimit to kSecMatchLimitAll,
				kSecReturnAttributes to kCFBooleanTrue,
			)

			memScoped {
				val result = alloc<CFTypeRefVar>()
				val status = SecItemCopyMatching(query, result.ptr)
				if (!status.isSuccess()) return@cfScoped emptyList()

				val items = CFBridgingRelease(result.value) as? List<*> ?: return@cfScoped emptyList()

				items.mapNotNull { item ->
					@Suppress("UNCHECKED_CAST")
					val attributes = item as? Map<Any?, Any?> ?: return@mapNotNull null
					(attributes[tagKey] as? NSData)?.stringValue
				}.filter { it.startsWith(DpopKeyRef.ALIAS_PREFIX) }
			}
		}
	}

	/** @return the created private key, owned by the caller, or null if the keychain refused the request. */
	private fun createKey(alias: String, useSecureEnclave: Boolean): SecKeyRef? = cfScoped {
		val protection: Pair<CFStringRef?, CFTypeRef?> = if (useSecureEnclave) {
			// kSecAccessControlPrivateKeyUsage is only valid for Secure Enclave keys
			val accessControl = SecAccessControlCreateWithFlags(
				null,
				kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
				kSecAccessControlPrivateKeyUsage,
				null,
			)?.also { own(it) } ?: return@cfScoped null
			kSecAttrAccessControl to accessControl
		} else {
			// Without access control flags, the accessibility is set directly with the same value
			kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
		}

		val privateKeyAttributes = dictionary(
			kSecAttrIsPermanent to kCFBooleanTrue,
			kSecAttrApplicationTag to bridge(alias.toNSData()),
			protection,
		)

		val attributes = dictionary(
			buildList {
				add(kSecAttrKeyType to kSecAttrKeyTypeECSECPrimeRandom)
				add(kSecAttrKeySizeInBits to bridge(NSNumber.numberWithInt(P256_KEY_SIZE_BITS)))
				add(kSecPrivateKeyAttrs to privateKeyAttributes)
				if (useSecureEnclave) {
					add(kSecAttrTokenID to kSecAttrTokenIDSecureEnclave)
				}
			}
		)

		SecKeyCreateRandomKey(attributes, null)
	}

	/** @return the private key, owned by the caller, or null if it does not exist. */
	private fun findPrivateKey(alias: String): SecKeyRef? = cfScoped {
		val query = dictionary(
			kSecClass to kSecClassKey,
			kSecAttrKeyClass to kSecAttrKeyClassPrivate,
			kSecAttrApplicationTag to bridge(alias.toNSData()),
			kSecReturnRef to kCFBooleanTrue,
		)

		memScoped {
			val result = alloc<CFTypeRefVar>()
			val status = SecItemCopyMatching(query, result.ptr)
			if (!status.isSuccess()) return@cfScoped null
			val keyRef = result.value ?: return@cfScoped null
			val privateKey: SecKeyRef = keyRef.reinterpret()
			privateKey
		}
	}

	private fun keyMissing(alias: String) = DpopException(
		errorType = AuthorizationErrorType.GENERAL,
		reason = DpopException.Reason.KEY_MISSING,
		message = "No DPoP key found for alias $alias",
	)

	@OptIn(ExperimentalEncodingApi::class)
	private fun ByteArray.toBase64Url(): String = BASE64_URL.encode(this)

	private fun ByteArray.toNSData(): NSData {
		if (isEmpty()) return NSData()
		return usePinned { pinned ->
			NSData.create(bytes = pinned.addressOf(0), length = size.convert())
		}
	}

	private fun NSData.toByteArray(): ByteArray {
		val size = length.toInt()
		if (size == 0) return ByteArray(0)
		return ByteArray(size).apply {
			usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
		}
	}

	private fun String.toNSData(): NSData =
		NSString.create(string = this).dataUsingEncoding(NSUTF8StringEncoding) ?: NSData()

	private val NSData.stringValue: String?
		get() = NSString.create(this, NSUTF8StringEncoding) as String?

	/** Reads a CoreFoundation string constant as a Kotlin string without taking ownership of the constant. */
	private fun CFStringRef?.toKotlinString(): String? =
		this?.let { CFBridgingRelease(CFRetain(it)) as? String }

	private fun OSStatus.isSuccess(): Boolean = toUInt() == noErr

	/**
	 * Runs [block] with a scope that releases every CoreFoundation object created inside it.
	 *
	 * The dictionaries are created with null key/value callbacks, so they neither retain nor release their contents —
	 * everything put into them has to outlive the call that consumes the dictionary and then be released exactly once,
	 * which is what this scope guarantees.
	 */
	private fun <T> cfScoped(block: CfScope.() -> T): T {
		val scope = CfScope()
		try {
			return scope.block()
		} finally {
			scope.releaseAll()
		}
	}

	private class CfScope {
		private val owned = mutableListOf<CFTypeRef>()

		fun <T : CFTypeRef> own(ref: T): T = ref.also { owned += it }

		/** Bridges a Foundation object into a CoreFoundation reference owned by this scope. */
		fun bridge(value: Any?): CFTypeRef? = CFBridgingRetain(value)?.also { owned += it }

		fun dictionary(vararg pairs: Pair<CFStringRef?, CFTypeRef?>): CFDictionaryRef? = dictionary(pairs.toList())

		fun dictionary(pairs: List<Pair<CFStringRef?, CFTypeRef?>>): CFDictionaryRef? {
			val dictionary = CFDictionaryCreateMutable(null, pairs.size.convert(), null, null) ?: return null
			pairs.forEach { (key, value) -> CFDictionaryAddValue(dictionary, key, value) }
			return own(dictionary)
		}

		fun releaseAll() {
			owned.asReversed().forEach { CFRelease(it) }
			owned.clear()
		}
	}
}
