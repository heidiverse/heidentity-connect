package org.heidiverse.connect.sdk.extensions.browserless.networking

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

@Serializable(with = BrowserlessResponseSerializer::class)
sealed class BrowserlessAuthorizationResponse {
    @Serializable
    data class Action(
        @SerialName("action")
        val type: ActionType,

        val url: String,
    ) : BrowserlessAuthorizationResponse() {
        @Serializable
        enum class ActionType {
            @SerialName("deep_link")
            DeepLink,

            @SerialName("call")
            Call
        }
    }
}

object BrowserlessResponseSerializer : JsonContentPolymorphicSerializer<BrowserlessAuthorizationResponse>(BrowserlessAuthorizationResponse::class) {
    override fun selectDeserializer(element: JsonElement) = when {
        "action" in element.jsonObject -> BrowserlessAuthorizationResponse.Action.serializer()

        else -> throw SerializationException("Unknown BrowserlessAuthorizationResponse format")
    }
}
