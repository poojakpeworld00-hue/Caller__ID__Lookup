package com.callerid.number.lookup.home.permit

/**
 * An ordered, drainable queue of the permissions to request on one Activity.
 *
 * Rules are ordered by [PermitRule.priority] ascending (lower value first);
 * ties keep their incoming order (stable sort). The engine polls one rule at a
 * time and only advances after the previous request completes — giving the
 * required sequential flow.
 */
class PermitQueue(rules: List<PermitRule>) {

    private val items: ArrayDeque<PermitRule> =
        ArrayDeque(rules.sortedBy { it.priority })

    /** Removes and returns the next rule, or null when the queue is drained. */
    fun poll(): PermitRule? = items.removeFirstOrNull()

    fun isEmpty(): Boolean = items.isEmpty()

    fun size(): Int = items.size
}
