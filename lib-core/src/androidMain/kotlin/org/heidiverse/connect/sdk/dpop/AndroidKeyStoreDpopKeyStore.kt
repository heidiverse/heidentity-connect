package org.heidiverse.connect.sdk.dpop

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import org.heidiverse.connect.sdk.exception.AuthorizationErrorType
import org.heidiverse.connect.sdk.util.BASE64_URL
import org.heidiverse.connect.sdk.util.RandomGenerator
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * [DpopKeyStore] backed by the AndroidKeyStore, preferring StrongBox and falling back to the TEE.
 *
 * The keys deliberately carry no user-authentication and no unlocked-device requirement: token refreshes happen
 * opportunistically, including while the app is in the background and the device is locked, and a key that needs user
 * presence would turn those into hard session failures.
 */
internal class AndroidKeyStoreDpopKeyStore : DpopKeyStore {

	companion object {
		private const val ANDROID_KEY_STORE = "AndroidKeyStore"
		private const val CURVE_P256 = "secp256r1"
		private const val SIGNATURE_ALGORITHM_ES256 = "SHA256withECDSA"

		/** Byte size of one P-256 coordinate. */
		private const val P256_FIELD_SIZE = 32

		private const val ALIAS_ENTROPY_BYTES = 12
	}

	private val keyStore: KeyStore
		get() = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

	override fun isAlgorithmSupported(algorithm: DpopAlgorithm, requireHardwareBacked: Boolean): Boolean {
		// Every supported API level (26+) has an AndroidKeyStore that can sign with EC P-256. Whether a key actually ends
		// up hardware-backed can only be determined after generating it, which generateKey() verifies.
		return algorithm == DpopAlgorithm.ES256
	}

	override fun generateKey(algorithm: DpopAlgorithm, allowSoftwareFallback: Boolean): DpopKeyRef {
		require(algorithm == DpopAlgorithm.ES256) { "Unsupported algorithm $algorithm" }

		val alias = DpopKeyRef.ALIAS_PREFIX + RandomGenerator.generateRandomString(bytes = ALIAS_ENTROPY_BYTES)

		val privateKey = try {
			generateKeyPairPreferringStrongBox(alias)
		} catch (e: Exception) {
			deleteKey(alias)
			throw DpopException(
				errorType = AuthorizationErrorType.AUTHORIZATION,
				reason = DpopException.Reason.KEY_GENERATION_FAILED,
				message = "Could not generate a DPoP key pair",
				cause = e,
			)
		}

		val isHardwareBacked = isHardwareBacked(privateKey)
		if (!isHardwareBacked && !allowSoftwareFallback) {
			deleteKey(alias)
			throw DpopException(
				errorType = AuthorizationErrorType.AUTHORIZATION,
				reason = DpopException.Reason.HARDWARE_BACKING_UNAVAILABLE,
				message = "The generated DPoP key is not backed by a secure element. " +
					"Enable DpopConfig.allowSoftwareKeyFallback to permit this.",
			)
		}

		return DpopKeyRef(alias = alias, algorithm = algorithm, isHardwareBacked = isHardwareBacked)
	}

	override fun getPublicJwk(keyRef: DpopKeyRef): DpopJwk {
		val publicKey = runCatching { keyStore.getCertificate(keyRef.alias)?.publicKey }.getOrNull() as? ECPublicKey
			?: throw keyMissing(keyRef.alias)

		return publicKey.w.toJwk(keyRef.algorithm)
	}

	override fun sign(keyRef: DpopKeyRef, data: ByteArray): ByteArray {
		val privateKey = runCatching { keyStore.getKey(keyRef.alias, null) as? PrivateKey }.getOrNull()
			?: throw keyMissing(keyRef.alias)

		val der = try {
			Signature.getInstance(SIGNATURE_ALGORITHM_ES256).run {
				initSign(privateKey)
				update(data)
				sign()
			}
		} catch (e: Exception) {
			throw DpopException(
				errorType = AuthorizationErrorType.GENERAL,
				reason = DpopException.Reason.SIGNING_FAILED,
				message = "Signing the DPoP proof failed",
				cause = e,
			)
		}

		return try {
			EcdsaSignature.derToJose(der, P256_FIELD_SIZE)
		} catch (e: IllegalArgumentException) {
			throw DpopException(
				errorType = AuthorizationErrorType.GENERAL,
				reason = DpopException.Reason.SIGNING_FAILED,
				message = "The keystore returned a signature that is not a well-formed ECDSA signature",
				cause = e,
			)
		}
	}

	override fun deleteKey(alias: String) {
		// A missing entry is not an error, and a keystore that cannot be opened must not fail the caller
		runCatching { keyStore.deleteEntry(alias) }
	}

	override fun listAliases(): List<String> {
		return runCatching {
			keyStore.aliases().toList().filter { it.startsWith(DpopKeyRef.ALIAS_PREFIX) }
		}.getOrDefault(emptyList())
	}

	/**
	 * Generates the key pair in StrongBox where available. A device can advertise StrongBox and still refuse an
	 * individual request, so any failure of the StrongBox attempt falls back to the TEE. If that also fails, the caller
	 * reports the second failure.
	 *
	 * [android.security.keystore.StrongBoxUnavailableException] is deliberately not named here — the class does not
	 * exist below API 28, and referencing it in a catch clause risks class verification failures on those devices.
	 */
	private fun generateKeyPairPreferringStrongBox(alias: String): PrivateKey {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
			return generateKeyPair(alias, useStrongBox = false)
		}

		return try {
			generateKeyPair(alias, useStrongBox = true)
		} catch (e: Exception) {
			deleteKey(alias)
			generateKeyPair(alias, useStrongBox = false)
		}
	}

	private fun generateKeyPair(alias: String, useStrongBox: Boolean): PrivateKey {
		val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
			.setAlgorithmParameterSpec(ECGenParameterSpec(CURVE_P256))
			.setDigests(KeyProperties.DIGEST_SHA256)
			.apply {
				if (useStrongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
					setIsStrongBoxBacked(true)
				}
			}
			.build()

		return KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE)
			.apply { initialize(spec) }
			.generateKeyPair()
			.private
	}

	private fun isHardwareBacked(privateKey: PrivateKey): Boolean {
		val keyInfo = runCatching {
			KeyFactory.getInstance(privateKey.algorithm, ANDROID_KEY_STORE)
				.getKeySpec(privateKey, KeyInfo::class.java)
		}.getOrNull() ?: return false

		return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			keyInfo.securityLevel != KeyProperties.SECURITY_LEVEL_SOFTWARE
		} else {
			@Suppress("DEPRECATION")
			keyInfo.isInsideSecureHardware
		}
	}

	@OptIn(ExperimentalEncodingApi::class)
	private fun ECPoint.toJwk(algorithm: DpopAlgorithm): DpopJwk {
		return DpopJwk(
			keyType = DpopJwk.KEY_TYPE_EC,
			curve = algorithm.curve,
			x = BASE64_URL.encode(affineX.toFixedWidthBytes(P256_FIELD_SIZE)),
			y = BASE64_URL.encode(affineY.toFixedWidthBytes(P256_FIELD_SIZE)),
		)
	}

	/**
	 * RFC 7518 §6.2.1.2 requires the coordinates to be encoded at the full field size. [BigInteger.toByteArray] is a
	 * signed two's-complement encoding, so it can be one byte too long (a leading 0x00 sign byte) or too short (leading
	 * zeros omitted).
	 */
	private fun BigInteger.toFixedWidthBytes(width: Int): ByteArray {
		val bytes = toByteArray()
		val stripped = if (bytes.size > width && bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes
		require(stripped.size <= width) { "Coordinate is ${stripped.size} bytes, expected at most $width" }
		return ByteArray(width).also { stripped.copyInto(it, width - stripped.size) }
	}

	private fun keyMissing(alias: String) = DpopException(
		errorType = AuthorizationErrorType.GENERAL,
		reason = DpopException.Reason.KEY_MISSING,
		message = "No DPoP key found for alias $alias",
	)
}
