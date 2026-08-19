package com.callerid.number.lookup.home.permit

class PermitQueue(rules: List<PermitRule>) {

    private val items: ArrayDeque<PermitRule> =
        ArrayDeque(rules.sortedBy { it.priority })

    fun poll(): PermitRule? = items.removeFirstOrNull()

    fun isEmpty(): Boolean = items.isEmpty()

    fun size(): Int = items.size
}
