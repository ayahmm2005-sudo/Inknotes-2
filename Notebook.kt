package com.example.inknotes.model

data class Notebook(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val noteCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
)
