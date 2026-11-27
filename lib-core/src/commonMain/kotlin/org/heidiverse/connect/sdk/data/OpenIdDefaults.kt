package org.heidiverse.connect.sdk.data

object OpenIdDefaults {

	object ResponseType {
		const val CODE = "code"

		const val TOKEN = "token"

		const val ID_TOKEN = "id_token"
	}

	object Scope {
		/**
		 * https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest
		 */
		const val OPENID = "openid"

		/**
		 * https://openid.net/specs/openid-connect-core-1_0.html#OfflineAccess
		 */
		const val OFFLINE_ACCESS = "offline_access"

		/**
		 * https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims
		 */
		const val PROFILE = "profile"

		/**
		 * https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims
		 */
		const val EMAIL = "email"

		/**
		 * https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims
		 */
		const val PHONE = "phone"

		/**
		 * https://openid.net/specs/openid-connect-core-1_0.html#ScopeClaims
		 */
		const val ADDRESS = "address"
	}

	object Display {
		/**
		 * https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest
		 */
		const val PAGE = "page"

		/**
		 * https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest
		 */
		const val POPUP = "popup"

		/**
		 * https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest
		 */
		const val TOUCH = "touch"

		/**
		 * https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest
		 */
		const val WAP = "wap"
	}

	object Prompt {
		/**
		 * https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest
		 */
		const val NONE = "none"

		/**
		 * https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest
		 */
		const val LOGIN = "login"

		/**
		 * https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest
		 */
		const val CONSENT = "consent"

		/**
		 * https://openid.net/specs/openid-connect-core-1_0.html#AuthRequest
		 */
		const val SELECT_ACCOUNT = "select_account"
	}

}