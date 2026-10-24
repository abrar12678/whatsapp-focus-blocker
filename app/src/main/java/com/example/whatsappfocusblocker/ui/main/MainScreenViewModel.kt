package com.example.whatsappfocusblocker.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.whatsappfocusblocker.data.contacts.DeviceContact
import com.example.whatsappfocusblocker.data.contacts.DeviceContactsDataSource
import com.example.whatsappfocusblocker.domain.model.ActionAuditRecord
import com.example.whatsappfocusblocker.domain.model.ActionType
import com.example.whatsappfocusblocker.domain.model.BlockStatus
import com.example.whatsappfocusblocker.domain.model.FocusContact
import com.example.whatsappfocusblocker.domain.repository.ContactRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.whatsappfocusblocker.accessibility.BlockAutomationState
import com.example.whatsappfocusblocker.accessibility.WhatsAppAutomationSupervisor
import java.util.UUID

data class FocusUiState(
    val isFocusActive: Boolean = false,
    val contacts: List<FocusContact> = emptyList(),
    val auditLogs: List<ActionAuditRecord> = emptyList(),
    val isLoading: Boolean = false,
    val userNotice: String? = null,

    // Contact Picker State
    val isPickerOpen: Boolean = false,
    val isPickerLoading: Boolean = false,
    val deviceContacts: List<DeviceContact> = emptyList(),
    val searchQuery: String = "",
    val pickerSelectedIds: Set<String> = emptySet(),

    // Confirmation & Action Dialog States
    val showConfirmBlockDialog: Boolean = false,
    val showConfirmUnblockDialog: Boolean = false,
    val showAuditHistoryDialog: Boolean = false,

    // Assisted WhatsApp Guide Dialogs
    val activeGuidedBlockContact: FocusContact? = null,
    val activeGuidedUnblockContact: FocusContact? = null,

    // Permission Dialog State
    val showPermissionRationale: Boolean = false,
    val showPermanentlyDeniedDialog: Boolean = false,

    // Accessibility Service State (Phase 1 & Phase 2 PoC)
    val isAccessibilityEnabled: Boolean = false,
    val showAccessibilityOnboarding: Boolean = false,
    val candidateAutomationContact: FocusContact? = null,
    val activeAutomationContact: FocusContact? = null,
    val automationState: BlockAutomationState = BlockAutomationState.IDLE,
    val automationStatusMessage: String? = null
) {
    val selectedCount: Int
        get() = contacts.count { it.isSelected }

    val blockedByAppCount: Int
        get() = contacts.count { it.canBeUnblockedByApp() }

    val actionRequiredCount: Int
        get() = contacts.count { it.status == BlockStatus.ACTION_REQUIRED }

    val totalCount: Int
        get() = contacts.size

    val selectedContacts: List<FocusContact>
        get() = contacts.filter { it.isSelected }

    val unblockEligibleContacts: List<FocusContact>
        get() = contacts.filter { it.canBeUnblockedByApp() }

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

    // Confirmation dialog flows
    private val _showConfirmBlockDialog = MutableStateFlow(false)
    private val _showConfirmUnblockDialog = MutableStateFlow(false)
    private val _showAuditHistoryDialog = MutableStateFlow(false)

    // Guided assistant flows
    private val _activeGuidedBlockContact = MutableStateFlow<FocusContact?>(null)
    private val _activeGuidedUnblockContact = MutableStateFlow<FocusContact?>(null)
    private var guidedBlockQueue = mutableListOf<FocusContact>()
    private var guidedUnblockQueue = mutableListOf<FocusContact>()

    // Permission dialog flows
    private val _showPermissionRationale = MutableStateFlow(false)
    private val _showPermanentlyDeniedDialog = MutableStateFlow(false)

    // Accessibility Service flows (Phase 1 & Phase 2 PoC)
    private val _isAccessibilityEnabled = MutableStateFlow(false)
    private val _showAccessibilityOnboarding = MutableStateFlow(false)
    private val _candidateAutomationContact = MutableStateFlow<FocusContact?>(null)
    private val _activeAutomationContact = MutableStateFlow<FocusContact?>(null)
    private val _automationState = MutableStateFlow(BlockAutomationState.IDLE)
    private val _automationStatusMessage = MutableStateFlow<String?>("Ready")

    init {
        viewModelScope.launch {
            WhatsAppAutomationSupervisor.currentState.collect { state ->
                _automationState.value = state
            }
        }
        viewModelScope.launch {
            WhatsAppAutomationSupervisor.statusMessage.collect { msg ->
                _automationStatusMessage.value = msg
            }
        }
        WhatsAppAutomationSupervisor.onAutomationCompleted = { contact, isVerified, details ->
            handleAutomationCompleted(contact, isVerified, details)
        }
    }

    val uiState: StateFlow<FocusUiState> = combine(
        contactRepository.getSavedContacts(),
        contactRepository.getActionAuditHistory(),
        _isFocusActive,
        _isLoading,
        _userNotice,
        _isPickerOpen,
        _isPickerLoading,
        _deviceContacts,
        _searchQuery,
        _pickerSelectedIds,
        _showConfirmBlockDialog,
        _showConfirmUnblockDialog,
        _showAuditHistoryDialog,
        _activeGuidedBlockContact,
        _activeGuidedUnblockContact,
        _showPermissionRationale,
        _showPermanentlyDeniedDialog,
        _isAccessibilityEnabled,
        _showAccessibilityOnboarding,
        _candidateAutomationContact,
        _activeAutomationContact,
        _automationState,
        _automationStatusMessage
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        FocusUiState(
            contacts = args[0] as List<FocusContact>,
            auditLogs = args[1] as List<ActionAuditRecord>,
            isFocusActive = args[2] as Boolean,
            isLoading = args[3] as Boolean,
            userNotice = args[4] as String?,
            isPickerOpen = args[5] as Boolean,
            isPickerLoading = args[6] as Boolean,
            deviceContacts = args[7] as List<DeviceContact>,
            searchQuery = args[8] as String,
            pickerSelectedIds = args[9] as Set<String>,
            showConfirmBlockDialog = args[10] as Boolean,
            showConfirmUnblockDialog = args[11] as Boolean,
            showAuditHistoryDialog = args[12] as Boolean,
            activeGuidedBlockContact = args[13] as FocusContact?,
            activeGuidedUnblockContact = args[14] as FocusContact?,
            showPermissionRationale = args[15] as Boolean,
            showPermanentlyDeniedDialog = args[16] as Boolean,
            isAccessibilityEnabled = args[17] as Boolean,
            showAccessibilityOnboarding = args[18] as Boolean,
            candidateAutomationContact = args[19] as FocusContact?,
            activeAutomationContact = args[20] as FocusContact?,
            automationState = args[21] as BlockAutomationState,
            automationStatusMessage = args[22] as String?
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FocusUiState()
    )

    fun updateAccessibilityEnabled(enabled: Boolean) {
        _isAccessibilityEnabled.value = enabled
    }

    fun onOpenAccessibilityOnboarding() {
        _showAccessibilityOnboarding.value = true
    }

    fun onDismissAccessibilityOnboarding() {
        _showAccessibilityOnboarding.value = false
    }

    fun onStartSingleContactAutomation(contact: FocusContact) {
        if (!_isAccessibilityEnabled.value) {
            _showAccessibilityOnboarding.value = true
            return
        }
        _candidateAutomationContact.value = contact
    }

    fun onDismissAutomationConsentDialog() {
        _candidateAutomationContact.value = null
    }

    fun onConfirmStartAutomation() {
        val contact = _candidateAutomationContact.value ?: return
        _candidateAutomationContact.value = null
        _activeAutomationContact.value = contact

        viewModelScope.launch {
            val targetDigits = contact.phoneNumber.replace(Regex("[^0-9]"), "")
            val targetSuffix = if (targetDigits.length >= 7) targetDigits.takeLast(7) else targetDigits
            val alternateNames = mutableSetOf<String>()

            try {
                val devContacts = if (_deviceContacts.value.isNotEmpty()) {
                    _deviceContacts.value
                } else {
                    deviceContactsDataSource?.fetchDeviceContacts() ?: emptyList()
                }
                devContacts.forEach { dc ->
                    val d = dc.phoneNumber.replace(Regex("[^0-9]"), "")
                    if (targetSuffix.isNotBlank() && d.endsWith(targetSuffix)) {
                        alternateNames.add(dc.name)
                    }
                }
            } catch (_: Exception) {}

            val now = System.currentTimeMillis()
            contactRepository.recordAuditLog(
                ActionAuditRecord(
                    id = UUID.randomUUID().toString(),
                    timestamp = now,
                    actionType = ActionType.BLOCK_INITIATED,
                    contactId = contact.id,
                    contactName = contact.name,
                    phoneNumber = contact.phoneNumber,
                    previousStatus = contact.status,
                    newStatus = BlockStatus.PENDING,
                    details = "Supervised accessibility automation initiated for single contact",
                    isVerified = false
                )
            )

            WhatsAppAutomationSupervisor.startSingleContactBlock(contact, alternateNames.toList())
        }
    }

    fun onStopAutomation() {
        WhatsAppAutomationSupervisor.stopAutomation("Cancelled by user via emergency STOP")
        _activeAutomationContact.value = null
    }

    fun onSwitchToManualFromAutomation() {
        val contact = _activeAutomationContact.value
        WhatsAppAutomationSupervisor.stopAutomation("Switched to manual assisted mode")
        _activeAutomationContact.value = null
        if (contact != null) {
            startGuidedBlockForContact(contact)
        }
    }

    private fun handleAutomationCompleted(contact: FocusContact, isVerified: Boolean, details: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (isVerified) {
                contactRepository.recordAuditLog(
                    ActionAuditRecord(
                        id = UUID.randomUUID().toString(),
                        timestamp = now,
                        actionType = ActionType.BLOCK_CONFIRMED,
                        contactId = contact.id,
                        contactName = contact.name,
                        phoneNumber = contact.phoneNumber,
                        previousStatus = contact.status,
                        newStatus = BlockStatus.AUTO_BLOCKED_BY_APP,
                        details = details,
                        isVerified = true
                    )
                )
                contactRepository.updateContactStatus(
                    contactId = contact.id,
                    newStatus = BlockStatus.AUTO_BLOCKED_BY_APP,
                    blockedByAppTimestamp = now,
                    message = "Auto-blocked & verified via AccessibilityService"
                )
                _userNotice.value = "Supervised auto-block verified for ${contact.name}"
            } else if (details.contains("Cancelled", ignoreCase = true) || details.contains("Stopped", ignoreCase = true)) {
                contactRepository.recordAuditLog(
                    ActionAuditRecord(
                        id = UUID.randomUUID().toString(),
                        timestamp = now,
                        actionType = ActionType.BLOCK_FAILED,
                        contactId = contact.id,
                        contactName = contact.name,
                        phoneNumber = contact.phoneNumber,
                        previousStatus = contact.status,
                        newStatus = contact.status,
                        details = details,
                        isVerified = false
                    )
                )
                _userNotice.value = "Automation cancelled"
            } else {
                contactRepository.recordAuditLog(
                    ActionAuditRecord(
                        id = UUID.randomUUID().toString(),
                        timestamp = now,
                        actionType = ActionType.BLOCK_FAILED,
                        contactId = contact.id,
                        contactName = contact.name,
                        phoneNumber = contact.phoneNumber,
                        previousStatus = contact.status,
                        newStatus = BlockStatus.NEEDS_MANUAL_VERIFICATION,
                        details = details,
                        isVerified = false
                    )
                )
                contactRepository.updateContactStatus(
                    contactId = contact.id,
                    newStatus = BlockStatus.NEEDS_MANUAL_VERIFICATION,
                    blockedByAppTimestamp = null,
                    message = "Automation ended without banner confirmation: $details"
                )
                _userNotice.value = "Action attempted, but blocked banner not verified. Marked for manual review."
            }
            _activeAutomationContact.value = null
        }
    }

    fun onSelectContactsClicked(hasPermission: Boolean) {
        if (hasPermission) {
            openPickerWithDeviceContacts()
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

    // Block Selected Flow with Confirmation & Step-by-Step Guide
    fun onBlockSelectedClicked() {
        val selected = uiState.value.selectedContacts

        if (selected.isEmpty()) {
            _userNotice.value = "Please select at least one contact to block."
            return
        }

        if (selected.any { it.status == BlockStatus.PENDING }) {
            _userNotice.value = "An action is already in progress for selected contacts."
            return
        }

        _showConfirmBlockDialog.value = true
    }

    fun onDismissConfirmBlockDialog() {
        _showConfirmBlockDialog.value = false
    }

    fun executeBlockSelected() {
        _showConfirmBlockDialog.value = false
        val selected = uiState.value.selectedContacts
        if (selected.isEmpty()) return

        viewModelScope.launch {
            val now = System.currentTimeMillis()

            selected.forEach { contact ->
                val prevStatus = contact.status

                val initiatedAudit = ActionAuditRecord(
                    id = UUID.randomUUID().toString(),
                    timestamp = now,
                    actionType = ActionType.BLOCK_INITIATED,
                    contactId = contact.id,
                    contactName = contact.name,
                    phoneNumber = contact.phoneNumber,
                    previousStatus = prevStatus,
                    newStatus = BlockStatus.PENDING,
                    details = "Block action initiated by user",
                    isVerified = false
                )
                contactRepository.recordAuditLog(initiatedAudit)

                // HONESTY: Never label as Blocked Confirmed until verified. Set to ACTION_REQUIRED.
                val integrationAudit = ActionAuditRecord(
                    id = UUID.randomUUID().toString(),
                    timestamp = now + 1,
                    actionType = ActionType.INTEGRATION_UNAVAILABLE,
                    contactId = contact.id,
                    contactName = contact.name,
                    phoneNumber = contact.phoneNumber,
                    previousStatus = BlockStatus.PENDING,
                    newStatus = BlockStatus.ACTION_REQUIRED,
                    details = "WhatsApp automation not configured. User-assisted manual block required.",
                    isVerified = false
                )
                contactRepository.recordAuditLog(integrationAudit)

                contactRepository.updateContactStatus(
                    contactId = contact.id,
                    newStatus = BlockStatus.ACTION_REQUIRED,
                    blockedByAppTimestamp = null,
                    message = "Action Required: Tap to block in WhatsApp"
                )
            }

            _isFocusActive.value = true

            // Enqueue selected contacts for step-by-step guidance
            guidedBlockQueue = selected.toMutableList()
            advanceGuidedBlock()
        }
    }

    fun startGuidedBlockForContact(contact: FocusContact) {
        guidedBlockQueue = mutableListOf(contact)
        advanceGuidedBlock()
    }

    private fun advanceGuidedBlock() {
        if (guidedBlockQueue.isNotEmpty()) {
            _activeGuidedBlockContact.value = guidedBlockQueue.removeAt(0)
        } else {
            _activeGuidedBlockContact.value = null
        }
    }

    fun onConfirmManualBlockCompleted(contactId: String) {
        val currentContact = uiState.value.contacts.find { it.id == contactId }
            ?: _activeGuidedBlockContact.value
            ?: return

        viewModelScope.launch {
            val now = System.currentTimeMillis()

            val confirmedAudit = ActionAuditRecord(
                id = UUID.randomUUID().toString(),
                timestamp = now,
                actionType = ActionType.BLOCK_CONFIRMED,
                contactId = currentContact.id,
                contactName = currentContact.name,
                phoneNumber = currentContact.phoneNumber,
                previousStatus = currentContact.status,
                newStatus = BlockStatus.USER_CONFIRMED_BLOCKED,
                details = "User explicitly confirmed manual block completed in WhatsApp",
                isVerified = true
            )
            contactRepository.recordAuditLog(confirmedAudit)

            contactRepository.updateContactStatus(
                contactId = currentContact.id,
                newStatus = BlockStatus.USER_CONFIRMED_BLOCKED,
                blockedByAppTimestamp = now,
                message = "User Confirmed in WhatsApp"
            )

            _userNotice.value = "Recorded '${currentContact.name}' as User Confirmed Blocked."
            advanceGuidedBlock()
        }
    }

    fun onSkipGuidedBlock(contactId: String) {
        val currentContact = uiState.value.contacts.find { it.id == contactId }
            ?: _activeGuidedBlockContact.value

        if (currentContact != null) {
            viewModelScope.launch {
                val skipAudit = ActionAuditRecord(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    actionType = ActionType.BLOCK_FAILED,
                    contactId = currentContact.id,
                    contactName = currentContact.name,
                    phoneNumber = currentContact.phoneNumber,
                    previousStatus = currentContact.status,
                    newStatus = BlockStatus.ACTION_REQUIRED,
                    details = "User skipped or cancelled block confirmation",
                    isVerified = false
                )
                contactRepository.recordAuditLog(skipAudit)
            }
        }
        advanceGuidedBlock()
    }

    fun onDismissGuidedBlock() {
        guidedBlockQueue.clear()
        _activeGuidedBlockContact.value = null
    }

    // Unblock All Flow with Confirmation & Guided Assistant
    fun onUnblockAllClicked() {
        val eligible = uiState.value.unblockEligibleContacts

        if (eligible.isEmpty()) {
            val hasManual = uiState.value.contacts.any { it.status == BlockStatus.MANUALLY_BLOCKED_EXTERNAL }
            val hasActionRequired = uiState.value.contacts.any { it.status == BlockStatus.ACTION_REQUIRED }

            val message = when {
                hasManual -> "Safety Guardrail: Contacts manually blocked prior to this session are protected and will never be unblocked."
                hasActionRequired -> "Safety Guardrail: Contacts in 'Action Required' were never verified as blocked by this app."
                else -> "No contacts were confirmed blocked by this app to unblock."
            }

            viewModelScope.launch {
                val guardrailAudit = ActionAuditRecord(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    actionType = ActionType.SAFETY_GUARDRAIL_BLOCKED,
                    contactId = "ALL",
                    contactName = "All Contacts",
                    phoneNumber = "",
                    previousStatus = BlockStatus.UNBLOCKED,
                    newStatus = BlockStatus.UNBLOCKED,
                    details = message,
                    isVerified = true
                )
                contactRepository.recordAuditLog(guardrailAudit)
            }

            _userNotice.value = message
            return
        }

        _showConfirmUnblockDialog.value = true
    }

    fun onDismissConfirmUnblockDialog() {
        _showConfirmUnblockDialog.value = false
    }

    fun executeUnblockAll() {
        _showConfirmUnblockDialog.value = false
        val eligible = uiState.value.unblockEligibleContacts
        if (eligible.isEmpty()) return

        // Launch step-by-step unblock guidance for confirmed contacts
        guidedUnblockQueue = eligible.toMutableList()
        advanceGuidedUnblock()
    }

    fun startGuidedUnblockForContact(contact: FocusContact) {
        guidedUnblockQueue = mutableListOf(contact)
        advanceGuidedUnblock()
    }

    private fun advanceGuidedUnblock() {
        if (guidedUnblockQueue.isNotEmpty()) {
            _activeGuidedUnblockContact.value = guidedUnblockQueue.removeAt(0)
        } else {
            _activeGuidedUnblockContact.value = null
            if (uiState.value.unblockEligibleContacts.isEmpty()) {
                _isFocusActive.value = false
            }
        }
    }

    fun onConfirmManualUnblockCompleted(contactId: String) {
        val currentContact = uiState.value.contacts.find { it.id == contactId }
            ?: _activeGuidedUnblockContact.value
            ?: return

        viewModelScope.launch {
            val now = System.currentTimeMillis()

            val unblockAudit = ActionAuditRecord(
                id = UUID.randomUUID().toString(),
                timestamp = now,
                actionType = ActionType.UNBLOCK_CONFIRMED,
                contactId = currentContact.id,
                contactName = currentContact.name,
                phoneNumber = currentContact.phoneNumber,
                previousStatus = currentContact.status,
                newStatus = BlockStatus.USER_CONFIRMED_UNBLOCKED,
                details = "User explicitly confirmed manual unblock in WhatsApp",
                isVerified = true
            )
            contactRepository.recordAuditLog(unblockAudit)

            contactRepository.updateContactStatus(
                contactId = currentContact.id,
                newStatus = BlockStatus.USER_CONFIRMED_UNBLOCKED,
                blockedByAppTimestamp = null,
                message = "User Confirmed Unblocked"
            )

            _userNotice.value = "Recorded '${currentContact.name}' as User Confirmed Unblocked."
            advanceGuidedUnblock()
        }
    }

    fun onSkipGuidedUnblock(contactId: String) {
        advanceGuidedUnblock()
    }

    fun onDismissGuidedUnblock() {
        guidedUnblockQueue.clear()
        _activeGuidedUnblockContact.value = null
    }

    // Delete contact from focus list
    fun onDeleteContact(contactId: String) {
        viewModelScope.launch {
            contactRepository.deleteContact(contactId)
            _userNotice.value = "Contact removed from focus list."
        }
    }

    // Audit Log Dialog Controls
    fun onOpenAuditHistory() {
        _showAuditHistoryDialog.value = true
    }

    fun onDismissAuditHistory() {
        _showAuditHistoryDialog.value = false
    }

    fun onClearAuditHistory() {
        viewModelScope.launch {
            contactRepository.clearAuditHistory()
            _userNotice.value = "Audit history cleared."
        }
    }

    fun onToggleContact(contactId: String, isSelected: Boolean) {
        viewModelScope.launch {
            contactRepository.toggleContactSelection(contactId, isSelected)
        }
    }

    fun recordLauncherAudit(contact: FocusContact, success: Boolean, message: String) {
        viewModelScope.launch {
            val audit = ActionAuditRecord(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                actionType = if (success) ActionType.BLOCK_INITIATED else ActionType.BLOCK_FAILED,
                contactId = contact.id,
                contactName = contact.name,
                phoneNumber = contact.phoneNumber,
                previousStatus = contact.status,
                newStatus = contact.status,
                details = message,
                isVerified = success
            )
            contactRepository.recordAuditLog(audit)
        }
    }

    fun dismissNotice() {
        _userNotice.value = null
    }
}
