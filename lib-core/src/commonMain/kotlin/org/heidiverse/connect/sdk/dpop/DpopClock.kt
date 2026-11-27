package org.heidiverse.connect.sdk.dpop

import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.parse
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The time source for the DPoP `iat` claim.
 *
 * Servers only accept proofs whose `iat` lies within a few seconds of their own time, so a wrong device clock makes every
 * proof fail with `invalid_dpop_proof`. When that happens, the server's `Date` header is taken as the correct time and
 * the device clock is offset to match it from then on.
 */
@OptIn(ExperimentalTime::class)
internal class DpopClock(
	private val systemClock: Clock = Clock.System,
) {

	/** Seconds to add to the device clock to get the server's time. */
	@Volatile
	var offsetSeconds: Long = 0
		private set

	/** The current time in epoch seconds, corrected to the server's time. */
	fun nowEpochSeconds(): Long = systemClock.now().epochSeconds + offsetSeconds

	/**
	 * Adopts the time of a server's `Date` response header (RFC 9110 §5.6.7).
	 *
	 * @return false if the header is missing or malformed
	 */
	fun syncTo(dateHeader: String?): Boolean {
		val serverEpochSeconds = dateHeader?.let(::parseHttpDate) ?: return false
		offsetSeconds = serverEpochSeconds - systemClock.now().epochSeconds
		return true
	}

	private fun parseHttpDate(value: String): Long? = runCatching {
		DateTimeComponents.parse(value.trim(), DateTimeComponents.Formats.RFC_1123).toInstantUsingOffset().epochSeconds
	}.getOrNull()
}
