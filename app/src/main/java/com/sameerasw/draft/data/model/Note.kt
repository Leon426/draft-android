package com.sameerasw.draft.data.model

data class Note(
    val id: String,
    val title: String,
    val body: String,
    val updatedAt: Long,
    val filePath: String
)
