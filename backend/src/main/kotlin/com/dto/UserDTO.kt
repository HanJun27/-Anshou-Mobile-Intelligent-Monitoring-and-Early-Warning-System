package com.livewell.dto

data class UserDTO(
    val id: Long,
    val username: String,
    val name: String,
    val phone: String?,
    val email: String?,
    val avatarColor: String,
    val status: String,
    val age: Int? = null,
    val address: String? = null,
    val emergencyContact: EmergencyContactDTO? = null,
    val todayCheckin: Boolean = false,
    val todaySteps: Int = 0,
    val sleepHours: Double = 0.0
)

data class CreateUserRequest(
    val username: String,
    val password: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val avatarColor: String = "#1890ff",
    val age: Int? = null,
    val address: String? = null
)

data class UpdateUserRequest(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val avatarColor: String? = null,
    val age: Int? = null,
    val address: String? = null
)

data class UserListResponse(
    val list: List<UserDTO>,
    val total: Long
)