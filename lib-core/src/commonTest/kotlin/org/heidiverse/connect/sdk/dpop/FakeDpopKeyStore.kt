package org.heidiverse.connect.sdk.dpop

import org.heidiverse.connect.sdk.exception.AuthorizationErrorType

/**
 * A [DpopKeyStore] with a fixed key and a deterministic signature, so that proofs can be asserted byte for byte.
 */
internal class FakeDpopKeyStore(
	private val jwk: DpopJwk = DpopJwk(
		keyType = DpopJwk.KEY_TYPE_EC,
		curve = "P-256",
		x = "f83OJ3D2xF1Bg8vub9tLe1gHMzV76e8Tus9uPHvRVEU",
		y = "x_FEzRu9m36HLN_tue659LNpXW6pCyStikYjKIWI5a0",
	),
	private val signature: ByteArray = ByteArray(64) { it.toByte() },
) : DpopKeyStore {

	val aliases = mutableListOf<String>()

	/** Aliases for which the key store behaves as if the key had been deleted from under the SDK */
	val missingAliases = mutableSetOf<String>()
	var signedInputs = mutableListOf<ByteArray>()
		private set

	override fun isAlgorithmSupported(algorithm: DpopAlgorithm, requireHardwareBacked: Boolean) =
		algorithm == DpopAlgorithm.ES256

	override fun generateKey(algorithm: DpopAlgorithm, allowSoftwareFallback: Boolean): DpopKeyRef {
		val alias = "${DpopKeyRef.ALIAS_PREFIX}${aliases.size}"
		aliases += alias
		return DpopKeyRef(alias = alias, algorithm = algorithm, isHardwareBacked = true)
	}

	override fun getPublicJwk(keyRef: DpopKeyRef): DpopJwk {
		if (keyRef.alias in missingAliases) {
			throw DpopException(
				errorType = AuthorizationErrorType.GENERAL,
				reason = DpopException.Reason.KEY_MISSING,
			)
		}
		return jwk
	}

	override fun sign(keyRef: DpopKeyRef, data: ByteArray): ByteArray {
		signedInputs += data
		return signature
	}

	override fun deleteKey(alias: String) {
		aliases -= alias
	}

	override fun listAliases(): List<String> = aliases.toList()
}
