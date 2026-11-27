package org.heidiverse.connect.sdk.dpop

import org.heidiverse.connect.sdk.logging.OidcEvent
import org.heidiverse.connect.sdk.networking.configuration.ProviderMetadata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DpopKeyManagerTest {

	private val keyStore = FakeDpopKeyStore()
	private val logger = RecordingEventLogger()
	private val manager = DpopKeyManager(keyStore, logger)

	@Test
	fun createsAKeyWhenTheProviderDoesNotAdvertiseAnyAlgorithm() {
		// Providers commonly omit dpop_signing_alg_values_supported, which must not be read as "no support"
		val keyRef = manager.createKey(DpopConfig(), metadata(dpopAlgorithms = null))

		assertEquals(DpopAlgorithm.ES256, keyRef.algorithm)
		assertTrue(keyRef.alias.startsWith(DpopKeyRef.ALIAS_PREFIX))
		assertEquals(1, logger.count<OidcEvent.DpopKeyGenerated>())
	}

	@Test
	fun createsAKeyWhenTheProviderAdvertisesTheAlgorithm() {
		val keyRef = manager.createKey(DpopConfig(), metadata(dpopAlgorithms = listOf("ES256", "RS256")))

		assertEquals(DpopAlgorithm.ES256, keyRef.algorithm)
	}

	@Test
	fun failsWhenTheProviderSupportsNoAlgorithmWeCanUse() {
		val exception = assertFailsWith<DpopException> {
			manager.createKey(DpopConfig(), metadata(dpopAlgorithms = listOf("RS256", "PS256")))
		}

		assertEquals(DpopException.Reason.NO_SUPPORTED_ALGORITHM, exception.reason)
		assertEquals(0, logger.count<OidcEvent.DpopKeyGenerated>())
	}

	@Test
	fun deletingAKeyRemovesItFromTheStore() {
		val keyRef = manager.createKey(DpopConfig(), metadata())

		manager.deleteKey(keyRef)

		assertEquals(emptyList(), keyStore.listAliases())
	}

	@Test
	fun deletingNullIsANoop() {
		manager.createKey(DpopConfig(), metadata())

		manager.deleteKey(null)

		assertEquals(1, keyStore.listAliases().size)
	}

	@Test
	fun pruningKeepsTheRetainedKeysAndDeletesTheRest() {
		// Created through another manager instance, standing in for keys left behind by an earlier process
		val earlierProcess = DpopKeyManager(keyStore, RecordingEventLogger())
		val kept = earlierProcess.createKey(DpopConfig(), metadata())
		earlierProcess.createKey(DpopConfig(), metadata())
		earlierProcess.createKey(DpopConfig(), metadata())

		manager.pruneOrphanedKeys(listOf(kept, null))

		assertEquals(listOf(kept.alias), keyStore.listAliases())
		assertEquals(1, logger.count<OidcEvent.DpopOrphanedKeysPruned>())
	}

	/**
	 * Nothing stops an app from holding more than one controller, so a sweep must not delete the key of a flow that is
	 * still in flight elsewhere in this process even when the caller does not know about it.
	 */
	@Test
	fun pruningKeepsKeysOfFlowsStillLiveInThisProcess() {
		val liveKey = manager.createKey(DpopConfig(), metadata())

		manager.pruneOrphanedKeys(emptyList())

		assertEquals(listOf(liveKey.alias), keyStore.listAliases())
	}

	/** A key restored from a saved flow was created by an earlier process, so it has to be registered explicitly. */
	@Test
	fun pruningKeepsExplicitlyTrackedRestoredKeys() {
		val restored = manager.createKey(DpopConfig(), metadata())
		val freshManager = DpopKeyManager(keyStore, logger)

		freshManager.trackExistingKey(restored)
		freshManager.pruneOrphanedKeys(emptyList())

		assertEquals(listOf(restored.alias), keyStore.listAliases())
	}

	/** A key that has been deleted stops being live, otherwise it could never be swept. */
	@Test
	fun deletedKeysStopBeingTrackedAsLive() {
		val key = manager.createKey(DpopConfig(), metadata())
		manager.deleteKey(key)

		// Recreate the same alias out of band, as a previous process would have left it behind
		keyStore.aliases += key.alias
		manager.pruneOrphanedKeys(emptyList())

		assertEquals(emptyList(), keyStore.listAliases())
	}

	@Test
	fun pruningWithNothingToDoLogsNothing() {
		val kept = DpopKeyManager(keyStore, RecordingEventLogger()).createKey(DpopConfig(), metadata())

		manager.pruneOrphanedKeys(listOf(kept))

		assertEquals(listOf(kept.alias), keyStore.listAliases())
		assertEquals(0, logger.count<OidcEvent.DpopOrphanedKeysPruned>())
	}

	/** Cleanup runs on the login path, so a failing key store must not be able to fail a login. */
	@Test
	fun pruningNeverThrows() {
		val failingManager = DpopKeyManager(
			object : DpopKeyStore by keyStore {
				override fun listAliases(): List<String> = throw IllegalStateException("keystore unavailable")
			},
			logger,
		)

		failingManager.pruneOrphanedKeys(emptyList())

		assertEquals(1, logger.count<OidcEvent.DpopKeyPruningFailed>())
	}

	@Test
	fun deletionNeverThrows() {
		val failingManager = DpopKeyManager(
			object : DpopKeyStore by keyStore {
				override fun deleteKey(alias: String) = throw IllegalStateException("keystore unavailable")
			},
			logger,
		)

		failingManager.deleteKey(DpopKeyRef("alias", DpopAlgorithm.ES256, isHardwareBacked = true))

		assertEquals(1, logger.count<OidcEvent.DpopKeyDeletionFailed>())
	}

	@Test
	fun softwareBackedKeyIsReportedSeparately() {
		val softwareKeyStore = object : DpopKeyStore by keyStore {
			override fun generateKey(algorithm: DpopAlgorithm, allowSoftwareFallback: Boolean) =
				DpopKeyRef("${DpopKeyRef.ALIAS_PREFIX}soft", algorithm, isHardwareBacked = false)
		}

		DpopKeyManager(softwareKeyStore, logger)
			.createKey(DpopConfig(allowSoftwareKeyFallback = true), metadata())

		assertEquals(1, logger.count<OidcEvent.DpopKeyNotHardwareBacked>())
	}

	@Test
	fun enforcedModeFailsWhenNoKeyCanBeCreated() {
		assertFailsWith<DpopException> {
			manager.createKeyForMode(
				DpopConfig(mode = DpopMode.ENFORCED),
				metadata(dpopAlgorithms = listOf("RS256")),
			)
		}
		assertEquals(0, logger.count<OidcEvent.DpopNotUsed>())
	}

	@Test
	fun opportunisticModeContinuesWithoutDpopWhenNoKeyCanBeCreated() {
		val keyRef = manager.createKeyForMode(
			DpopConfig(mode = DpopMode.OPPORTUNISTIC),
			metadata(dpopAlgorithms = listOf("RS256")),
		)

		assertNull(keyRef)
		assertEquals(1, logger.count<OidcEvent.DpopNotUsed>())
	}

	@Test
	fun opportunisticModeCreatesAKeyWhenPossible() {
		val keyRef = manager.createKeyForMode(DpopConfig(mode = DpopMode.OPPORTUNISTIC), metadata())

		assertNotNull(keyRef)
		assertEquals(0, logger.count<OidcEvent.DpopNotUsed>())
	}

	private fun metadata(dpopAlgorithms: List<String>? = null) = ProviderMetadata(
		issuer = "https://idp.example.com",
		authorizationEndpoint = "https://idp.example.com/auth",
		tokenEndpoint = "https://idp.example.com/token",
		jwksUri = "https://idp.example.com/jwks",
		responseTypesSupported = listOf("code"),
		subjectTypesSupported = listOf("public"),
		idTokenSigningAlgValuesSupported = listOf("RS256"),
		dpopSigningAlgValuesSupported = dpopAlgorithms,
	)
}
