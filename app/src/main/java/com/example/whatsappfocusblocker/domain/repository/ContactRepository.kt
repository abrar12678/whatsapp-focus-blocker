package com.example.whatsappfocusblocker.domain.repository

import com.example.whatsappfocusblocker.domain.model.BlockStatus
import com.example.whatsappfocusblocker.domain.model.FocusContact
import kotlinx.coroutines.flow.Flow

interface ContactRepository {
    /**
     * Flow emitting all contacts saved in the focus list.
     */
    fun getSavedContacts(): Flow<List<FocusContact>>

    /**
     * Add or update contacts in the tracked focus list.
     */
    suspend fun saveContacts(contacts: List<FocusContact>)

    /**
     * Toggle selection state for a specific contact.
     */
    suspend fun toggleContactSelection(contactId: String, isSelected: Boolean)

    /**
     * Select or deselect all contacts.
     */
    suspend fun setSelectAll(selectAll: Boolean)

    /**
     * Update the block status and audit details for a specific contact.
     */
    suspend fun updateContactStatus(
        contactId: String,
        newStatus: BlockStatus,
        blockedByAppTimestamp: Long? = null,
        message: String? = null
    )

    /**
     * Remove a contact from the focus list.
     */
    suspend fun deleteContact(contactId: String)
}
