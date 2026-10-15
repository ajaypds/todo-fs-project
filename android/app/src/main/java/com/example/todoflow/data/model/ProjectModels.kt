package com.example.todoflow.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ProjectDto(
    val id: String,
    val name: String,
    val color: String? = null
)

@Serializable
data class CreateProjectRequest(
    val name: String,
    val color: String? = null
)
