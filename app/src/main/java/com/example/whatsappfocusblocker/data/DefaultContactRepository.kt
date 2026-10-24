package com.example.whatsappfocusblocker.data

import com.example.whatsappfocusblocker.domain.model.BlockStatus
import com.example.whatsappfocusblocker.domain.model.FocusContact
import com.example.whatsappfocusblocker.domain.repository.ContactRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory repository foundation for Step 1.
 *
 * Guaranteed not to provide fake contacts. Starts with an empty contact list.
 * Will be backed by persistent storage (DataStore/Room) in Step 2.
 */
class DefaultContactRepository : ContactRepository {

    private val _contactsFlow = MutableStateFlow<List<FocusContact>>(emptyList())

    override fun getSavedContacts(): Flow<List<FocusContact>> {
        return _contactsFlow.asStateFlow()
    }

    override suspend fun saveContacts(contacts: List<FocusContact>) {
        _contactsFlow.update { current ->
            val existingIds = current.map { it.id }.toSet()
            val newUnique = contacts.filterNot { it.id in existingIds }
            current + newUnique
        }
    }

    override suspend fun toggleContactSelection(contactId: String, isSelected: Boolean) {
        _contactsFlow.update { list ->
            list.map { contact ->
                if (contact.id == contactId) {
                    contact.copy(isSelected = isSelected)
                } else {
                    contact
                }
            }
        }
    }

    override suspend fun setSelectAll(selectAll: Boolean) {
        _contactsFlow.update { list ->
            list.map { it.copy(isSelected = selectAll) }
        }
    }

    override suspend fun updateContactStatus(
        contactId: String,
        newStatus: BlockStatus,
        blockedByAppTimestamp: Long?,
        message: String?
    ) {
        _contactsFlow.update { list ->
            list.map { contact ->
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
        }
    }

    override suspend fun deleteContact(contactId: String) {
        _contactsFlow.update { list ->
            list.filterNot { it.id == contactId }
        }
    }
}
