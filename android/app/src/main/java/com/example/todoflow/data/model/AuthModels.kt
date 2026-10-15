package com.example.todoflow.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String? = null,
    val refreshToken: String? = null,
    val emailVerificationRequired: Boolean = false,
    val email: String? = null,
    val message: String? = null
)
