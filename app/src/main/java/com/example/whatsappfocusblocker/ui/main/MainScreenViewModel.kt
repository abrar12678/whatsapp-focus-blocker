package com.example.whatsappfocusblocker.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whatsappfocusblocker.data.contacts.DeviceContact
import com.example.whatsappfocusblocker.data.contacts.DeviceContactsDataSource
import com.example.whatsappfocusblocker.domain.model.BlockStatus
import com.example.whatsappfocusblocker.domain.model.FocusContact
import com.example.whatsappfocusblocker.domain.repository.ContactRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FocusUiState(
    val isFocusActive: Boolean = false,
    val contacts: List<FocusContact> = emptyList(),
    val isLoading: Boolean = false,
    val userNotice: String? = null,

    // Contact Picker State
    val isPickerOpen: Boolean = false,
    val isPickerLoading: Boolean = false,
    val deviceContacts: List<DeviceContact> = emptyList(),
    val searchQuery: String = "",
    val pickerSelectedIds: Set<String> = emptySet(),

    // Permission Dialog State
    val showPermissionRationale: Boolean = false,
    val showPermanentlyDeniedDialog: Boolean = false
) {
    val selectedCount: Int
        get() = contacts.count { it.isSelected }

    val blockedByAppCount: Int
        get() = contacts.count { it.status == BlockStatus.BLOCKED_BY_APP }

    val totalCount: Int
        get() = contacts.size

    val filteredDeviceContacts: List<DeviceContact>
        get() = if (searchQuery.isBlank()) {
            deviceContacts
        } else {
            val query = searchQuery.trim().lowercase()
            deviceContacts.filter {
                it.name.lowercase().contains(query) ||
                        it.phoneNumber.replace(Regex("[\\s\\-\\(\\)]"), "").contains(query)
            }
        }
}

class MainScreenViewModel(
    private val contactRepository: ContactRepository,
    private val deviceContactsDataSource: DeviceContactsDataSource? = null
) : ViewModel() {

    private val _isFocusActive = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(false)
    private val _userNotice = MutableStateFlow<String?>(null)

    // Picker state flows
    private val _isPickerOpen = MutableStateFlow(false)
    private val _isPickerLoading = MutableStateFlow(false)
    private val _deviceContacts = MutableStateFlow<List<DeviceContact>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _pickerSelectedIds = MutableStateFlow<Set<String>>(emptySet())

    // Permission dialog flows
    private val _showPermissionRationale = MutableStateFlow(false)
    private val _showPermanentlyDeniedDialog = MutableStateFlow(false)

    val uiState: StateFlow<FocusUiState> = combine(
        contactRepository.getSavedContacts(),
        _isFocusActive,
        _isLoading,
        _userNotice,
        _isPickerOpen,
        _isPickerLoading,
        _deviceContacts,
        _searchQuery,
        _pickerSelectedIds,
        _showPermissionRationale,
        _showPermanentlyDeniedDialog
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        FocusUiState(
            contacts = args[0] as List<FocusContact>,
            isFocusActive = args[1] as Boolean,
            isLoading = args[2] as Boolean,
            userNotice = args[3] as String?,
            isPickerOpen = args[4] as Boolean,
            isPickerLoading = args[5] as Boolean,
            deviceContacts = args[6] as List<DeviceContact>,
            searchQuery = args[7] as String,
            pickerSelectedIds = args[8] as Set<String>,
            showPermissionRationale = args[9] as Boolean,
            showPermanentlyDeniedDialog = args[10] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FocusUiState()
    )

    fun onSelectContactsClicked(hasPermission: Boolean) {
        if (hasPermission) {
            openPickerWithDeviceContacts()
        } else {
            // Permission request must be triggered by UI
            // If already shown rationale, will be handled by UI launcher
        }
    }

    fun openPickerWithDeviceContacts() {
        val currentTrackedIds = uiState.value.contacts.map { it.id }.toSet()
        _pickerSelectedIds.value = currentTrackedIds
        _searchQuery.value = ""
        _isPickerOpen.value = true

        viewModelScope.launch {
            _isPickerLoading.value = true
            try {
                val fetched = deviceContactsDataSource?.fetchDeviceContacts() ?: emptyList()
                _deviceContacts.value = fetched
            } catch (e: Exception) {
                _userNotice.value = "Failed to read contacts: ${e.message}"
                _deviceContacts.value = emptyList()
            } finally {
                _isPickerLoading.value = false
            }
        }
    }

    fun onPermissionResult(isGranted: Boolean, shouldShowRationale: Boolean) {
        if (isGranted) {
            _showPermissionRationale.value = false
            _showPermanentlyDeniedDialog.value = false
            openPickerWithDeviceContacts()
        } else {
            if (shouldShowRationale) {
                _showPermissionRationale.value = true
            } else {
                _showPermanentlyDeniedDialog.value = true
            }
        }
    }

    fun onDismissPermissionRationale() {
        _showPermissionRationale.value = false
    }

    fun onDismissPermanentlyDeniedDialog() {
        _showPermanentlyDeniedDialog.value = false
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onTogglePickerSelection(contactId: String) {
        val current = _pickerSelectedIds.value.toMutableSet()
        if (current.contains(contactId)) {
            current.remove(contactId)
        } else {
            current.add(contactId)
        }
        _pickerSelectedIds.value = current
    }

    fun onSelectAllPickerContacts(selectAll: Boolean) {
        val filtered = uiState.value.filteredDeviceContacts
        val current = _pickerSelectedIds.value.toMutableSet()
        if (selectAll) {
            current.addAll(filtered.map { it.id })
        } else {
            current.removeAll(filtered.map { it.id }.toSet())
        }
        _pickerSelectedIds.value = current
    }

    fun onConfirmContactSelection() {
        val selectedIds = _pickerSelectedIds.value
        val allDeviceMap = _deviceContacts.value.associateBy { it.id }
        val existingTrackedMap = uiState.value.contacts.associateBy { it.id }

        val newFocusContacts = selectedIds.mapNotNull { id ->
            val dev = allDeviceMap[id]
            val existing = existingTrackedMap[id]

            if (dev != null) {
                existing?.copy(
                    name = dev.name,
                    phoneNumber = dev.phoneNumber,
                    isSelected = true
                ) ?: FocusContact(
                    id = dev.id,
                    name = dev.name,
                    phoneNumber = dev.phoneNumber,
                    isSelected = true,
                    status = BlockStatus.UNBLOCKED
                )
            } else {
                existing?.copy(isSelected = true)
            }
        }

        viewModelScope.launch {
            contactRepository.saveContacts(newFocusContacts)
            _isPickerOpen.value = false
            _searchQuery.value = ""
            _userNotice.value = "Saved ${newFocusContacts.size} focus contacts."
        }
    }

    fun onDismissPicker() {
        _isPickerOpen.value = false
        _searchQuery.value = ""
    }

    fun onBlockSelectedClicked() {
        val currentContacts = uiState.value.contacts
        val selected = currentContacts.filter { it.isSelected }

        if (selected.isEmpty()) {
            _userNotice.value = "Please select at least one contact to block."
            return
        }

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
