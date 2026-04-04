package com.livewell.dto

data class UserDTO(
    val id: Long,
    val username: String,
    val name: String,
    val phone: String?,
    val email: String?,
    val avatarColor: String,
    val status: String
)

data class CreateUserRequest(
    val username: String,
    val password: String,
    val name: String,
    val phone: String? = null,
    val email: String? = null,
    val avatarColor: String = "#1890ff"
)

data class UpdateUserRequest(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val avatarColor: String? = null
)