package com.example.whatsappfocusblocker.data

import android.content.SharedPreferences
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
 * Repository implementation supporting local persistence across app restarts.
 *
 * Stored locally in app private preferences. Never uploaded to any external server.
 */
class DefaultContactRepository(
    private val preferences: SharedPreferences? = null
) : ContactRepository {

    companion object {
        const val PREFS_NAME = "whatsapp_focus_blocker_prefs"
        private const val KEY_SAVED_CONTACTS = "key_saved_focus_contacts"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _contactsFlow = MutableStateFlow<List<FocusContact>>(loadFromPreferences())

    private fun loadFromPreferences(): List<FocusContact> {
        val raw = preferences?.getString(KEY_SAVED_CONTACTS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<FocusContact>>(raw)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persist(list: List<FocusContact>) {
        preferences?.edit()?.let { editor ->
            try {
                val serialized = json.encodeToString(list)
                editor.putString(KEY_SAVED_CONTACTS, serialized).apply()
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
            // Merge preserving existing block statuses and timestamps
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
            persist(merged)
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
            persist(updated)
            updated
        }
    }

    override suspend fun setSelectAll(selectAll: Boolean) {
        _contactsFlow.update { list ->
            val updated = list.map { it.copy(isSelected = selectAll) }
            persist(updated)
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
            persist(updated)
            updated
        }
    }

    override suspend fun deleteContact(contactId: String) {
        _contactsFlow.update { list ->
            val updated = list.filterNot { it.id == contactId }
            persist(updated)
            updated
        }
    }
}
