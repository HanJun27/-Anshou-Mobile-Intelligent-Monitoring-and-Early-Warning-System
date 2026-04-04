package com.livewell.dto

data class EmergencyContactDTO(
    val id: Long,
    val name: String,
    val phoneNumber: String,
    val relation: String,
    val priority: Int,
    val isActive: Boolean
)

data class CreateEmergencyContactRequest(
    val name: String,
    val phoneNumber: String,
    val relation: String,
    val priority: Int = 1,
    val email: String = ""
)