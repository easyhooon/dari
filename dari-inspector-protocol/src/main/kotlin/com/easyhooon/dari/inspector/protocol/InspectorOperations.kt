package com.easyhooon.dari.inspector.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object InspectorOperations {
    /** [ListInput] → [ListOutput] */
    const val LIST = "messages.list"

    /** [DetailInput] → [WireMessageDetail] */
    const val DETAIL = "messages.detail"

    /** Stream of [ObserveEvent]. Clients subscribe before calling [LIST] and merge by id. */
    const val OBSERVE = "messages.observe"

    /** No input, empty output. */
    const val CLEAR = "messages.clear"
}

@Serializable
data class ListInput(val limit: Int = DEFAULT_LIST_LIMIT) {
    companion object {
        const val DEFAULT_LIST_LIMIT = 500
    }
}

/** Oldest first, matching the in-app list order. */
@Serializable
data class ListOutput(val messages: List<WireMessageSummary>)

@Serializable
data class DetailInput(val id: Long)

@Serializable
sealed interface ObserveEvent {
    @Serializable
    @SerialName("added")
    data class Added(val message: WireMessageSummary) : ObserveEvent

    @Serializable
    @SerialName("updated")
    data class Updated(val message: WireMessageSummary) : ObserveEvent

    @Serializable
    @SerialName("cleared")
    data object Cleared : ObserveEvent
}
