package com.example.whatsappfocusblocker.data

import android.content.SharedPreferences
import com.example.whatsappfocusblocker.domain.model.ActionAuditRecord
import com.example.whatsappfocusblocker.domain.model.BlockStatus
import com.example.whatsappfocusblocker.domain.model.FocusContact
import com.example.whatsappfocusblocker.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository implementation supporting local persistence of contacts and action audit records across app restarts.
 *
 * Stored locally in app private preferences. Never uploaded to any external server.
 */
class DefaultContactRepository(
    private val preferences: SharedPreferences? = null
) : ContactRepository {

    companion object {
        const val PREFS_NAME = "whatsapp_focus_blocker_prefs"
        private const val KEY_SAVED_CONTACTS = "key_saved_focus_contacts"
        private const val KEY_AUDIT_LOGS = "key_action_audit_history"
        private const val MAX_AUDIT_ENTRIES = 100
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _contactsFlow = MutableStateFlow<List<FocusContact>>(loadContactsFromPreferences())
    private val _auditLogsFlow = MutableStateFlow<List<ActionAuditRecord>>(loadAuditLogsFromPreferences())

    private fun loadContactsFromPreferences(): List<FocusContact> {
        val raw = preferences?.getString(KEY_SAVED_CONTACTS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<FocusContact>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun loadAuditLogsFromPreferences(): List<ActionAuditRecord> {
        val raw = preferences?.getString(KEY_AUDIT_LOGS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<ActionAuditRecord>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persistContacts(list: List<FocusContact>) {
        preferences?.edit()?.let { editor ->
            try {
                val serialized = json.encodeToString(list)
                editor.putString(KEY_SAVED_CONTACTS, serialized).apply()
            } catch (e: Exception) {
                // Graceful fallback
            }
        }
    }

    private fun persistAuditLogs(list: List<ActionAuditRecord>) {
        preferences?.edit()?.let { editor ->
            try {
                val serialized = json.encodeToString(list)
                editor.putString(KEY_AUDIT_LOGS, serialized).apply()
            } catch (e: Exception) {
                // Graceful fallback
            }
        }
    }

    override fun getSavedContacts(): Flow<List<FocusContact>> {
        return _contactsFlow.asStateFlow()
    }

    override suspend fun saveContacts(contacts: List<FocusContact>) {
        _contactsFlow.update { current ->
            val existingMap = current.associateBy { it.id }
            val merged = contacts.map { incoming ->
                val existing = existingMap[incoming.id]
                if (existing != null) {
                    incoming.copy(
                        status = existing.status,
                        blockedByAppTimestamp = existing.blockedByAppTimestamp,
                        lastActionMessage = existing.lastActionMessage
                    )
                } else {
                    incoming
                }
            }
            persistContacts(merged)
            merged
        }
    }

    override suspend fun toggleContactSelection(contactId: String, isSelected: Boolean) {
        _contactsFlow.update { list ->
            val updated = list.map { contact ->
                if (contact.id == contactId) {
                    contact.copy(isSelected = isSelected)
                } else {
                    contact
                }
            }
            persistContacts(updated)
            updated
        }
    }

    override suspend fun setSelectAll(selectAll: Boolean) {
        _contactsFlow.update { list ->
            val updated = list.map { it.copy(isSelected = selectAll) }
            persistContacts(updated)
            updated
        }
    }

    override suspend fun updateContactStatus(
        contactId: String,
        newStatus: BlockStatus,
        blockedByAppTimestamp: Long?,
        message: String?
    ) {
        _contactsFlow.update { list ->
            val updated = list.map { contact ->
                if (contact.id == contactId) {
                    contact.copy(
                        status = newStatus,
                        blockedByAppTimestamp = blockedByAppTimestamp ?: contact.blockedByAppTimestamp,
                        lastActionMessage = message ?: contact.lastActionMessage
                    )
                } else {
                    contact
                }
            }
            persistContacts(updated)
            updated
        }
    }

    override suspend fun deleteContact(contactId: String) {
        _contactsFlow.update { list ->
            val updated = list.filterNot { it.id == contactId }
            persistContacts(updated)
            updated
        }
    }

    override fun getActionAuditHistory(): Flow<List<ActionAuditRecord>> {
        return _auditLogsFlow.asStateFlow()
    }

    override suspend fun recordAuditLog(record: ActionAuditRecord) {
        _auditLogsFlow.update { current ->
            val updated = (listOf(record) + current).take(MAX_AUDIT_ENTRIES)
            persistAuditLogs(updated)
            updated
        }
    }

    override suspend fun clearAuditHistory() {
        _auditLogsFlow.update {
            persistAuditLogs(emptyList())
            emptyList()
        }
    }
}
