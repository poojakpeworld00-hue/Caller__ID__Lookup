package com.callerid.admesh.model

data class PingCard(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val description: String,
    val dateTime: Long,
    val color: Int // Add this// optional
)

