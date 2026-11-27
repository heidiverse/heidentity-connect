package org.heidiverse.connect.sdk.dpop

import org.heidiverse.connect.sdk.exception.AuthorizationErrorType
import org.heidiverse.connect.sdk.logging.OidcEvent
import org.heidiverse.connect.sdk.logging.OidcEventLogger
import org.heidiverse.connect.sdk.networking.configuration.ProviderMetadata
import kotlin.concurrent.Volatile

/**
 * Owns the lifecycle of DPoP keys: algorithm negotiation, generation, deletion, and cleaning up keys left behind by
 * abandoned login flows.
 *
 * Keeping this separate from [DpopKeyStore] keeps the platform implementations free of policy — they only do crypto.
 */
internal class DpopKeyManager(
	private val keyStore: DpopKeyStore,
	private val logger: OidcEventLogger,
) {

	/**
	 * Aliases of keys this process is still using, so that [pruneOrphanedKeys] cannot delete a key belonging to a flow
	 * that is in flight somewhere else — nothing stops an app from holding more than one [org.heidiverse.connect.sdk.AuthorizationController].
	 * Keys left over by *previous* processes are absent here, which is exactly what makes them prunable.
	 *
	 * Copy-on-write behind a volatile reference rather than a lock: these are called from arbitrary threads, and the
	 * worst case of a lost update is a key becoming prunable one sweep early, i.e. the behaviour we would have had
	 * without tracking at all.
	 */
	@Volatile
	private var liveAliases: Set<String> = emptySet()

	/** Registers the key of a restored flow, which was created by an earlier process. */
	fun trackExistingKey(keyRef: DpopKeyRef?) {
		keyRef?.let { liveAliases = liveAliases + it.alias }
	}

	/**
	 * Negotiates an algorithm and generates a new key.
	 *
	 * The negotiation intersects, in order of preference, the configured algorithms with what the provider advertises in
	 * `dpop_signing_alg_values_supported` (skipped when the provider omits the field, which is common) and with what the
	 * platform can actually do.
	 *
	 * @throws DpopException if no algorithm survives the negotiation or the key cannot be generated.
	 */
	fun createKey(config: DpopConfig, providerMetadata: ProviderMetadata): DpopKeyRef {
		val requireHardwareBacked = !config.allowSoftwareKeyFallback
		val providerAlgorithms = providerMetadata.dpopSigningAlgValuesSupported

		val algorithm = config.supportedAlgorithms
			.filter { providerAlgorithms == null || it.jwsAlgorithm in providerAlgorithms }
			.firstOrNull { keyStore.isAlgorithmSupported(it, requireHardwareBacked) }
			?: throw DpopException(
				errorType = AuthorizationErrorType.AUTHORIZATION,
				reason = DpopException.Reason.NO_SUPPORTED_ALGORITHM,
				message = "None of ${config.supportedAlgorithms.map { it.jwsAlgorithm }} is supported by both the " +
					"provider (${providerAlgorithms ?: "unspecified"}) and this device " +
					"(requireHardwareBacked=$requireHardwareBacked)",
			)

		val keyRef = keyStore.generateKey(algorithm, config.allowSoftwareKeyFallback)
		liveAliases = liveAliases + keyRef.alias

		logger.logEvent(
			OidcEvent.DpopKeyGenerated(
				alias = keyRef.alias,
				algorithm = keyRef.algorithm.jwsAlgorithm,
				hardwareBacked = keyRef.isHardwareBacked,
			)
		)
		if (!keyRef.isHardwareBacked) {
			logger.logEvent(OidcEvent.DpopKeyNotHardwareBacked(keyRef.alias))
		}

		return keyRef
	}

	/**
	 * Creates a key according to [DpopConfig.mode]: in [DpopMode.OPPORTUNISTIC] mode, a device that cannot create a
	 * suitable key continues without DPoP.
	 *
	 * @return the key, or null if DPoP is not used for this flow
	 * @throws DpopException in [DpopMode.ENFORCED] mode, if no key can be created
	 */
	fun createKeyForMode(config: DpopConfig, providerMetadata: ProviderMetadata): DpopKeyRef? {
		return try {
			createKey(config, providerMetadata)
		} catch (e: DpopException) {
			if (config.mode == DpopMode.ENFORCED) throw e
			logger.logEvent(OidcEvent.DpopNotUsed("No DPoP key could be created: ${e.reason}"))
			null
		}
	}

	/** Deletes a key. Does not throw. */
	fun deleteKey(keyRef: DpopKeyRef?) {
		val alias = keyRef?.alias ?: return
		liveAliases = liveAliases - alias
		runCatching { keyStore.deleteKey(alias) }
			.onFailure { logger.logEvent(OidcEvent.DpopKeyDeletionFailed(alias, it)) }
	}

	/**
	 * Deletes every SDK-owned key that is not in [retain]. Does not throw
	 */
	fun pruneOrphanedKeys(retain: Collection<DpopKeyRef?>) {
		val retainedAliases = retain.mapNotNull { it?.alias }.toSet() + liveAliases

		runCatching {
			val orphaned = keyStore.listAliases().filterNot { it in retainedAliases }
			orphaned.forEach { keyStore.deleteKey(it) }
			orphaned.size
		}.onSuccess { prunedCount ->
			if (prunedCount > 0) {
				logger.logEvent(OidcEvent.DpopOrphanedKeysPruned(prunedCount))
			}
		}.onFailure {
			logger.logEvent(OidcEvent.DpopKeyPruningFailed(it))
		}
	}
}
