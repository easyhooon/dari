package com.easyhooon.dari.data

import com.easyhooon.dari.MessageEntry
import com.easyhooon.dari.runtime.InternalDariApi

/** [entry] paired with an id that stays the same after the Room id replaces the temp id. */
@InternalDariApi
data class InspectorEntry(
    val wireId: Long,
    val entry: MessageEntry,
)

@InternalDariApi
sealed interface RepositoryEvent {
    data class Added(val item: InspectorEntry) : RepositoryEvent
    data class Updated(val item: InspectorEntry) : RepositoryEvent
    data object Cleared : RepositoryEvent
}
