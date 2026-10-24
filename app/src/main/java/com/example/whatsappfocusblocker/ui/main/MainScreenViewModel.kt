package com.example.whatsappfocusblocker.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whatsappfocusblocker.domain.model.BlockStatus
import com.example.whatsappfocusblocker.domain.model.FocusContact
import com.example.whatsappfocusblocker.domain.repository.ContactRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FocusUiState(
    val isFocusActive: Boolean = false,
    val contacts: List<FocusContact> = emptyList(),
    val isLoading: Boolean = false,
    val userNotice: String? = null
) {
    val selectedCount: Int
        get() = contacts.count { it.isSelected }

    val blockedByAppCount: Int
        get() = contacts.count { it.status == BlockStatus.BLOCKED_BY_APP }

    val totalCount: Int
        get() = contacts.size
}

class MainScreenViewModel(
    private val contactRepository: ContactRepository
) : ViewModel() {

    private val _isFocusActive = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(false)
    private val _userNotice = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FocusUiState> = combine(
        contactRepository.getSavedContacts(),
        _isFocusActive,
        _isLoading,
        _userNotice
    ) { contacts, isFocusActive, isLoading, userNotice ->
        FocusUiState(
            isFocusActive = isFocusActive,
            contacts = contacts,
            isLoading = isLoading,
            userNotice = userNotice
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FocusUiState()
    )

    fun onSelectContactsClicked() {
        _userNotice.value = "Contact selection will be integrated in Step 2 using the device Contact Picker."
    }

    fun onBlockSelectedClicked() {
        val currentContacts = uiState.value.contacts
        val selected = currentContacts.filter { it.isSelected }

        if (selected.isEmpty()) {
            _userNotice.value = "Please select at least one contact to block."
            return
        }

        // Transparency: Do not fake block.
        _userNotice.value = "Real WhatsApp blocking is not yet implemented. Automation investigation takes place in Step 5."
    }

    fun onUnblockAllClicked() {
        val appBlocked = uiState.value.contacts.filter { it.canBeUnblockedByApp() }

        if (appBlocked.isEmpty()) {
            _userNotice.value = "No contacts were blocked by this app to unblock."
            return
        }

        _userNotice.value = "Real WhatsApp unblocking is not yet implemented."
    }

    fun onToggleContact(contactId: String, isSelected: Boolean) {
        viewModelScope.launch {
            contactRepository.toggleContactSelection(contactId, isSelected)
        }
    }

    fun dismissNotice() {
        _userNotice.value = null
    }
}
