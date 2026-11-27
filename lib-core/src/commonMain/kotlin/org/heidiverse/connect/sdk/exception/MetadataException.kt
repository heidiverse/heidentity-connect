package org.heidiverse.connect.sdk.exception

class MetadataException(
	override val errorType: HeidentityConnectErrorType,
	val reason: Reason,
	val fieldName: String? = null,
	message: String? = null,
	cause: Throwable? = null,
) : HeidentityConnectException(
	errorType = errorType,
	message = message,
	cause = cause,
	errorCode = HeidentityConnectErrorCode.typed(errorType.errorCode, reason.classOffset),
) {

	enum class Reason {
		NOT_AVAILABLE,
		DESERIALIZATION,
		MISSING_FIELD;

		val classOffset: Int
			get() = when (this) {
				NOT_AVAILABLE -> HeidentityConnectErrorCode.OFFSET_METADATA_NOT_AVAILABLE
				DESERIALIZATION -> HeidentityConnectErrorCode.OFFSET_METADATA_DESERIALIZATION
				MISSING_FIELD -> HeidentityConnectErrorCode.OFFSET_METADATA_MISSING_FIELD
			}
	}
}
