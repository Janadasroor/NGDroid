package com.jnd.ngdroid.data

import java.util.UUID

const val MAX_SAVED_CHATS = 50
const val MAX_MESSAGES_PER_CHAT = 200

enum class StoredMsgRole { USER, ASSISTANT, SYSTEM }

data class StoredMsg(
    val id: String = UUID.randomUUID().toString(),
    val role: StoredMsgRole = StoredMsgRole.USER,
    val text: String = "",
    val timestampMillis: Long = System.currentTimeMillis()
)

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "New chat",
    val providerName: String = "",
    val model: String = "",
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
    val messages: List<StoredMsg> = emptyList()
)
