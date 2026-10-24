package com.example.whatsappfocusblocker.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ActionType {
    BLOCK_INITIATED,
    BLOCK_CONFIRMED,
    BLOCK_FAILED,
    UNBLOCK_INITIATED,
    UNBLOCK_CONFIRMED,
    UNBLOCK_FAILED,
    INTEGRATION_UNAVAILABLE,
    SAFETY_GUARDRAIL_BLOCKED
}

/**
 * Immutable audit log record tracking every block/unblock action attempt and outcome.
 * Persisted locally for full accountability and safety audits.
 */
@Serializable
data class ActionAuditRecord(
    val id: String,
    val timestamp: Long,
    val actionType: ActionType,
    val contactId: String,
    val contactName: String,
    val phoneNumber: String,
    val previousStatus: BlockStatus,
    val newStatus: BlockStatus,
    val details: String,
    val isVerified: Boolean = false
)
