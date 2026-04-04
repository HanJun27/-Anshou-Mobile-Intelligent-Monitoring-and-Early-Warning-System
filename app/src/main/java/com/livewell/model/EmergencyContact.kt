package com.livewell.model

data class EmergencyContact(
    val id: Int = 0,
    val name: String = "",
    val phoneNumber: String = "",
    val relation: String = "",
    val isActive: Boolean = true
)