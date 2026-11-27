package org.heidiverse.connect.sdk.dpop

/**
 * Platform access to the secure key storage holding the DPoP keys.
 *
 * Implementations must keep the private key non-extractable: on Android the key lives in the AndroidKeyStore
 * (StrongBox or TEE), on iOS in the Secure Enclave. Only the [DpopKeyRef] alias ever leaves this interface.
 *
 * Implementations must be safe to call from multiple coroutines at once. In particular [sign] must not share a
 * signing handle between calls.
 */
internal interface DpopKeyStore {

	/**
	 * Whether this platform can generate and use a key for [algorithm].
	 *
	 * @param requireHardwareBacked If true, only report support when a secure element is available.
	 */
	fun isAlgorithmSupported(algorithm: DpopAlgorithm, requireHardwareBacked: Boolean): Boolean

	/**
	 * Generates a new non-extractable key pair.
	 *
	 * @throws DpopException with [DpopException.Reason.HARDWARE_BACKING_UNAVAILABLE] if no secure element is available
	 *   and [allowSoftwareFallback] is false, or [DpopException.Reason.KEY_GENERATION_FAILED] on any other failure.
	 */
	fun generateKey(algorithm: DpopAlgorithm, allowSoftwareFallback: Boolean): DpopKeyRef

	/**
	 * @throws DpopException with [DpopException.Reason.KEY_MISSING] if the key no longer exists.
	 */
	fun getPublicJwk(keyRef: DpopKeyRef): DpopJwk

	/**
	 * Signs [data] with the referenced private key.
	 *
	 * @return the signature in the JOSE form required by RFC 7515: the raw concatenation `R || S`, each padded to the
	 *   curve's field size (64 bytes total for ES256) — *not* the ASN.1/DER encoding the platform APIs produce.
	 * @throws DpopException with [DpopException.Reason.KEY_MISSING] or [DpopException.Reason.SIGNING_FAILED].
	 */
	fun sign(keyRef: DpopKeyRef, data: ByteArray): ByteArray

	/** Deletes the key with this alias. A missing key is not an error. */
	fun deleteKey(alias: String)

	/**
	 * All aliases in the secure storage that were created by this SDK, i.e. that start with
	 * [DpopKeyRef.ALIAS_PREFIX]. Used to prune keys orphaned by abandoned login flows.
	 */
	fun listAliases(): List<String>
}
