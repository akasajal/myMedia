package com.ishaan.myMedia.ui.screens

/**
 * Holds the IDs of currently selected media items.
 * An empty set means selection mode is inactive.
 */
data class SelectionState(
    val selectedIds: Set<Long> = emptySet(),
) {
    val isActive: Boolean get() = selectedIds.isNotEmpty()
    val count: Int get() = selectedIds.size

    fun toggle(id: Long): SelectionState =
        if (id in selectedIds) copy(selectedIds = selectedIds - id)
        else copy(selectedIds = selectedIds + id)

    fun clear(): SelectionState = copy(selectedIds = emptySet())
    fun selectAll(ids: Collection<Long>): SelectionState = copy(selectedIds = ids.toSet())
}