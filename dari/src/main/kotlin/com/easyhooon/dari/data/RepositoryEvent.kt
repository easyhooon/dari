package com.easyhooon.dari.data

import com.easyhooon.dari.MessageEntry

/** [entry] paired with an id that stays the same after the Room id replaces the temp id. */
internal data class InspectorEntry(
    val wireId: Long,
    val entry: MessageEntry,
)

internal sealed interface RepositoryEvent {
    data class Added(val item: InspectorEntry) : RepositoryEvent
    data class Updated(val item: InspectorEntry) : RepositoryEvent
    data object Cleared : RepositoryEvent
}
