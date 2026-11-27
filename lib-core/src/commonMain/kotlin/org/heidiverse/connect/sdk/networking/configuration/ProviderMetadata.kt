package org.heidiverse.connect.sdk.networking.configuration

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response body according to the OIDC [specification](https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata)
 */
@Serializable
data class ProviderMetadata(
	@SerialName("issuer")
	val issuer: String,

	@SerialName("authorization_endpoint")
	val authorizationEndpoint: String,

	@SerialName("token_endpoint")
	val tokenEndpoint: String? = null,

	@SerialName("userinfo_endpoint")
	val userinfoEndpoint: String? = null,

	@SerialName("jwks_uri")
	val jwksUri: String,

	@SerialName("registration_endpoint")
	val registrationEndpoint: String? = null,

	@SerialName("scopes_supported")
	val scopesSupported: List<String>? = null,

	@SerialName("response_types_supported")
	val responseTypesSupported: List<String>,

	@SerialName("response_modes_supported")
	val responseModesSupported: List<String>? = null,

	@SerialName("grant_types_supported")
	val grantTypesSupported: List<String>? = null,

	@SerialName("acr_values_supported")
	val acrValuesSupported: List<String>? = null,

	@SerialName("subject_types_supported")
	val subjectTypesSupported: List<String>,

	@SerialName("id_token_signing_alg_values_supported")
	val idTokenSigningAlgValuesSupported: List<String>,

	@SerialName("id_token_encryption_alg_values_supported")
	val idTokenEncryptionAlgValuesSupported: List<String>? = null,

	@SerialName("id_token_encryption_enc_values_supported")
	val idTokenEncryptionEncValuesSupported: List<String>? = null,

	@SerialName("userinfo_signing_alg_values_supported")
	val userinfoSigningAlgValuesSupported: List<String>? = null,

	@SerialName("userinfo_encryption_alg_values_supported")
	val userinfoEncryptionAlgValuesSupported: List<String>? = null,

	@SerialName("userinfo_encryption_enc_values_supported")
	val userinfoEncryptionEncValuesSupported: List<String>? = null,

	@SerialName("request_object_signing_alg_values_supported")
	val requestObjectSigningAlgValuesSupported: List<String>? = null,

	@SerialName("request_object_encryption_alg_values_supported")
	val requestObjectEncryptionAlgValuesSupported: List<String>? = null,

	@SerialName("request_object_encryption_enc_values_supported")
	val requestObjectEncryptionEncValuesSupported: List<String>? = null,

	@SerialName("token_endpoint_auth_methods_supported")
	val tokenEndpointAuthMethodsSupported: List<String>? = null,

	@SerialName("token_endpoint_auth_signing_alg_values_supported")
	val tokenEndpointAuthSigningAlgValuesSupported: List<String>? = null,

	@SerialName("display_values_supported")
	val displayValuesSupported: List<String>? = null,

	@SerialName("claim_types_supported")
	val claimTypesSupported: List<String>? = null,

	@SerialName("claims_supported")
	val claimsSupported: List<String>? = null,

	@SerialName("service_documentation")
	val serviceDocumentation: String? = null,

	@SerialName("claims_locales_supported")
	val claimsLocalesSupported: List<String>? = null,

	@SerialName("ui_locales_supported")
	val uiLocalesSupported: List<String>? = null,

	@SerialName("claims_parameter_supported")
	val claimsParameterSupported: Boolean? = null,

	@SerialName("request_parameter_supported")
	val requestParameterSupported: Boolean? = null,

	@SerialName("request_uri_parameter_supported")
	val requestUriParameterSupported: Boolean? = null,

	@SerialName("require_request_uri_registration")
	val requireRequestUriRegistration: Boolean? = null,

	@SerialName("op_policy_uri")
	val opPolicyUri: String? = null,

	@SerialName("op_tos_uri")
	val opTosUri: String? = null,

	/**
	 * JWS algorithms supported for DPoP proofs, per
	 * [RFC 9449 §5.1](https://datatracker.ietf.org/doc/html/rfc9449#section-5.1). A null value means the provider does
	 * not advertise DPoP support — which is not the same as not supporting it, so the SDK only uses this to narrow the
	 * algorithm choice when it is present.
	 */
	@SerialName("dpop_signing_alg_values_supported")
	val dpopSigningAlgValuesSupported: List<String>? = null,

)
