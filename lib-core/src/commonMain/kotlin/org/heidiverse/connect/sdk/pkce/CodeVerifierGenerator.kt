package org.heidiverse.connect.sdk.pkce

import org.heidiverse.connect.sdk.util.BASE64_URL
import org.heidiverse.connect.sdk.util.RandomGenerator
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * PKCE code verifier according to the [RFC 7636 specification](https://datatracker.ietf.org/doc/html/rfc7636)
 */
@OptIn(ExperimentalEncodingApi::class)
internal class CodeVerifierGenerator(
	private val codeChallengeGenerator: CodeChallengeGenerator,
) {

	companion object {

		val koinModule = module {
			singleOf(::CodeVerifierGenerator)
		}

		/**
		 * The minimum amount of characters the code verifier must have.
		 * @see [RFC 7636 Section 4.1](https://datatracker.ietf.org/doc/html/rfc7636#section-4.1)
		 */
		private const val CODE_VERIFIER_LENGTH_MIN = 43

		/**
		 * The maximum amount of characters the code verifier must have.
		 * @see [RFC 7636 Section 4.1](https://datatracker.ietf.org/doc/html/rfc7636#section-4.1)
		 */
		private const val CODE_VERIFIER_LENGTH_MAX = 128

		/**
		 * The minimum entropy (in bytes) the code verifier must have.
		 * This is explicitly stated in the specification to be 256 bits.
		 * @see [RFC 7636 Section 7.1](https://datatracker.ietf.org/doc/html/rfc7636#section-7.1)
		 */
		private const val CODE_VERIFIER_ENTROPY_BYTES_MIN = 32

		/**
		 * The maximum entropy (in bytes) the code verifier must have.
		 * This is implicitly defined by the maximum length and number of allowed characters of the code verifier.
		 */
		private const val CODE_VERIFIER_ENTROPY_BYTES_MAX = 96

		/**
		 * A sensible default entropy (in bytes) for the code verifier
		 */
		private const val CODE_VERIFIER_ENTROPY_BYTES_DEFAULT = 64

		/**
		 * Regex to check if a code verifier meets the character and length requirements
		 */
		private val CODE_VERIFIER_REGEX = "^[0-9a-zA-Z\\-\\.\\_\\~]{43,128}\$".toRegex()

		/**
		 * The base64 instance used for encoding the generated code verifier
		 */
		val PKCE_BASE64 = BASE64_URL
	}

	/**
	 * Checks if the provided [codeVerifier] is a valid code verifier according to the PKCE specification
	 * @return true if the [codeVerifier] satisfies the length and character requirements
	 */
	fun checkCodeVerifier(codeVerifier: String): Boolean {
		val satisfiesLengthRequirement = codeVerifier.length in CODE_VERIFIER_LENGTH_MIN..CODE_VERIFIER_LENGTH_MAX
		val satisfiesCharacterRequirement = CODE_VERIFIER_REGEX.matches(codeVerifier)
		return satisfiesLengthRequirement && satisfiesCharacterRequirement
	}

	/**
	 * Generates a code verifier with default entropy
	 */
	fun generateCodeVerifier(): String {
		return generateCodeVerifier(CODE_VERIFIER_ENTROPY_BYTES_DEFAULT)
	}

	/**
	 * Generates a code verifier with the provided [entropyBytes]
	 */
	fun generateCodeVerifier(entropyBytes: Int): String {
		require(entropyBytes in CODE_VERIFIER_ENTROPY_BYTES_MIN..CODE_VERIFIER_ENTROPY_BYTES_MAX) {
			"entropyBytes does not meet the min or max requirements"
		}

		return RandomGenerator.generateRandomString(bytes = entropyBytes, base64 = PKCE_BASE64)
	}

	/**
	 * Derives the code verifier challenge based on the provided [codeVerifier]
	 */
	fun deriveCodeVerifierChallenge(codeVerifier: String): CodeChallenge {
		return codeChallengeGenerator.generateCodeChallenge(codeVerifier)
	}

}