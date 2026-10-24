package com.example.whatsappfocusblocker.ui.main

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.whatsappfocusblocker.data.DefaultContactRepository
import com.example.whatsappfocusblocker.data.contacts.AndroidDeviceContactsDataSource
import com.example.whatsappfocusblocker.data.contacts.DeviceContact
import com.example.whatsappfocusblocker.domain.model.ActionAuditRecord
import com.example.whatsappfocusblocker.domain.model.BlockStatus
import com.example.whatsappfocusblocker.domain.model.FocusContact
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.whatsappfocusblocker.accessibility.AccessibilityServiceHelper
import com.example.whatsappfocusblocker.accessibility.BlockAutomationState
import com.example.whatsappfocusblocker.theme.WhatsAppFocusBlockerTheme
import com.example.whatsappfocusblocker.util.WhatsAppLaunchResult
import com.example.whatsappfocusblocker.util.WhatsAppLauncher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = createDefaultViewModel(LocalContext.current)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = context as? Activity

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.updateAccessibilityEnabled(AccessibilityServiceHelper.isAccessibilityServiceEnabled(context))
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val shouldShowRationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.READ_CONTACTS)
        } ?: false
        viewModel.onPermissionResult(isGranted, shouldShowRationale)
    }

    LaunchedEffect(uiState.userNotice) {
        uiState.userNotice?.let { notice ->
            snackbarHostState.showSnackbar(notice)
            viewModel.dismissNotice()
        }
    }

    val onSelectContactsClick = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.openPickerWithDeviceContacts()
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            AppHeader(
                onViewAuditHistory = viewModel::onOpenAuditHistory,
                auditRecordCount = uiState.auditLogs.size
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        MainContent(
            uiState = uiState,
            onSelectContacts = onSelectContactsClick,
            onBlockSelected = viewModel::onBlockSelectedClicked,
            onUnblockAll = viewModel::onUnblockAllClicked,
            onToggleContact = viewModel::onToggleContact,
            onStartGuidedBlock = viewModel::startGuidedBlockForContact,
            onStartGuidedUnblock = viewModel::startGuidedUnblockForContact,
            onDeleteContact = viewModel::onDeleteContact,
            onOpenAccessibilityOnboarding = viewModel::onOpenAccessibilityOnboarding,
            onStartAutomation = viewModel::onStartSingleContactAutomation,
            modifier = Modifier.padding(innerPadding)
        )
    }

    // Contact Picker Modal Bottom Sheet
    if (uiState.isPickerOpen) {
        ContactPickerBottomSheet(
            uiState = uiState,
            onSearchChange = viewModel::onSearchQueryChanged,
            onToggleContact = viewModel::onTogglePickerSelection,
            onSelectAll = viewModel::onSelectAllPickerContacts,
            onConfirm = viewModel::onConfirmContactSelection,
            onDismiss = viewModel::onDismissPicker
        )
    }

    // Confirmation Dialogs
    if (uiState.showConfirmBlockDialog) {
        ConfirmBlockDialog(
            selectedContacts = uiState.selectedContacts,
            onConfirm = viewModel::executeBlockSelected,
            onDismiss = viewModel::onDismissConfirmBlockDialog
        )
    }

    if (uiState.showConfirmUnblockDialog) {
        ConfirmUnblockDialog(
            eligibleContacts = uiState.unblockEligibleContacts,
            onConfirm = viewModel::executeUnblockAll,
            onDismiss = viewModel::onDismissConfirmUnblockDialog
        )
    }

    // Step-by-Step Guided Assistant Dialogs
    uiState.activeGuidedBlockContact?.let { contact ->
        AssistedBlockGuideDialog(
            contact = contact,
            onOpenWhatsApp = {
                val result = WhatsAppLauncher.openChat(context, contact.phoneNumber)
                handleLaunchResult(context, contact, result, viewModel)
            },
            onConfirmBlocked = { viewModel.onConfirmManualBlockCompleted(contact.id) },
            onSkip = { viewModel.onSkipGuidedBlock(contact.id) },
            onDismiss = viewModel::onDismissGuidedBlock
        )
    }

    uiState.activeGuidedUnblockContact?.let { contact ->
        AssistedUnblockGuideDialog(
            contact = contact,
            onOpenWhatsApp = {
                val result = WhatsAppLauncher.openChat(context, contact.phoneNumber)
                handleLaunchResult(context, contact, result, viewModel)
            },
            onConfirmUnblocked = { viewModel.onConfirmManualUnblockCompleted(contact.id) },
            onSkip = { viewModel.onSkipGuidedUnblock(contact.id) },
            onDismiss = viewModel::onDismissGuidedUnblock
        )
    }

    // Supervised Single-Contact Automation PoC Dialogs (Phase 2)
    uiState.candidateAutomationContact?.let { contact ->
        SupervisedAutomationConsentDialog(
            contact = contact,
            onStart = {
                val result = WhatsAppLauncher.openChat(context, contact.phoneNumber)
                handleLaunchResult(context, contact, result, viewModel)
                viewModel.onConfirmStartAutomation()
            },
            onDismiss = viewModel::onDismissAutomationConsentDialog
        )
    }

    uiState.activeAutomationContact?.let { contact ->
        SupervisedAutomationActiveDialog(
            contact = contact,
            state = uiState.automationState,
            statusMessage = uiState.automationStatusMessage,
            onStop = viewModel::onStopAutomation,
            onSwitchToManual = viewModel::onSwitchToManualFromAutomation
        )
    }

    // Audit History Dialog
    if (uiState.showAuditHistoryDialog) {
        AuditHistoryDialog(
            auditLogs = uiState.auditLogs,
            onClear = viewModel::onClearAuditHistory,
            onDismiss = viewModel::onDismissAuditHistory
        )
    }

    // Permission Dialogs
    if (uiState.showPermissionRationale) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissPermissionRationale,
            title = { Text("Contact Permission Required") },
            text = {
                Text("WhatsApp Focus Blocker needs permission to read your contacts locally so you can choose which contacts to restict during focus sessions. Your contacts remain strictly on this device and are never uploaded.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.onDismissPermissionRationale()
                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                ) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissPermissionRationale) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.showPermanentlyDeniedDialog) {
        AlertDialog(
            onDismissRequest = viewModel::onDismissPermanentlyDeniedDialog,
            title = { Text("Permission Denied") },
            text = {
                Text("Contact permission is disabled for this app. To select contacts from your device address book, please open Settings and enable Contact access.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.onDismissPermanentlyDeniedDialog()
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissPermanentlyDeniedDialog) {
                    Text("Not Now")
                }
            }
        )
    }

    // Accessibility Onboarding & Setup Dialog (Phase 1 PoC)
    if (uiState.showAccessibilityOnboarding) {
        AccessibilityOnboardingDialog(
            isCurrentlyEnabled = uiState.isAccessibilityEnabled,
            onOpenAccessibilitySettings = {
                try {
                    context.startActivity(AccessibilityServiceHelper.createOpenAccessibilitySettingsIntent())
                } catch (e: Exception) {
                    // Fallback to app settings
                    context.startActivity(AccessibilityServiceHelper.createOpenAppDetailsSettingsIntent(context))
                }
            },
            onOpenAppDetailsSettings = {
                context.startActivity(AccessibilityServiceHelper.createOpenAppDetailsSettingsIntent(context))
            },
            onDismiss = viewModel::onDismissAccessibilityOnboarding
        )
    }
}

private fun handleLaunchResult(
    context: Context,
    contact: FocusContact,
    result: WhatsAppLaunchResult,
    viewModel: MainScreenViewModel
) {
    when (result) {
        WhatsAppLaunchResult.Success -> {
            viewModel.recordLauncherAudit(contact, true, "Opened WhatsApp chat via deep link")
        }
        WhatsAppLaunchResult.WhatsAppNotInstalled -> {
            viewModel.recordLauncherAudit(contact, false, "WhatsApp app is not installed on device")
            WhatsAppLauncher.openPlayStoreForWhatsApp(context)
        }
        is WhatsAppLaunchResult.InvalidPhoneNumber -> {
            viewModel.recordLauncherAudit(contact, false, result.message)
        }
        is WhatsAppLaunchResult.Error -> {
            viewModel.recordLauncherAudit(contact, false, result.throwable.message ?: "Failed to launch intent")
        }
    }
}

@Composable
private fun createDefaultViewModel(context: Context): MainScreenViewModel {
    return viewModel {
        val prefs = context.getSharedPreferences(
            DefaultContactRepository.PREFS_NAME,
            Context.MODE_PRIVATE
        )
        val repository = DefaultContactRepository(prefs)
        val dataSource = AndroidDeviceContactsDataSource(context.contentResolver)
        MainScreenViewModel(repository, dataSource)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppHeader(
    onViewAuditHistory: () -> Unit,
    auditRecordCount: Int
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "WhatsApp Focus Blocker",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Manage focus sessions & protect your time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        actions = {
            TextButton(onClick = onViewAuditHistory) {
                Text("Audit Log ($auditRecordCount)")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

@Composable
fun MainContent(
    uiState: FocusUiState,
    onSelectContacts: () -> Unit,
    onBlockSelected: () -> Unit,
    onUnblockAll: () -> Unit,
    onToggleContact: (String, Boolean) -> Unit,
    onStartGuidedBlock: (FocusContact) -> Unit,
    onStartGuidedUnblock: (FocusContact) -> Unit,
    onDeleteContact: (String) -> Unit,
    onOpenAccessibilityOnboarding: () -> Unit,
    onStartAutomation: (FocusContact) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            FocusStatusCard(
                isFocusActive = uiState.isFocusActive,
                totalContacts = uiState.totalCount,
                selectedContacts = uiState.selectedCount,
                blockedByAppCount = uiState.blockedByAppCount,
                actionRequiredCount = uiState.actionRequiredCount
            )
        }

        item {
            AccessibilityStatusCard(
                isEnabled = uiState.isAccessibilityEnabled,
                onConfigure = onOpenAccessibilityOnboarding
            )
        }

        item {
            TechnicalNoticeCard()
        }

        item {
            ActionControlsCard(
                selectedCount = uiState.selectedCount,
                blockedByAppCount = uiState.blockedByAppCount,
                onSelectContacts = onSelectContacts,
                onBlockSelected = onBlockSelected,
                onUnblockAll = onUnblockAll
            )
        }

        item {
            Text(
                text = "Tracked Focus Contacts (${uiState.totalCount})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (uiState.contacts.isEmpty()) {
            item {
                EmptyContactsCard(onSelectContacts = onSelectContacts)
            }
        } else {
            itemsIndexed(uiState.contacts, key = { index, contact -> "${contact.id}_${contact.phoneNumber}_$index" }) { _, contact ->
                ContactListItem(
                    contact = contact,
                    onToggleSelect = { isChecked -> onToggleContact(contact.id, isChecked) },
                    onStartGuidedBlock = { onStartGuidedBlock(contact) },
                    onStartGuidedUnblock = { onStartGuidedUnblock(contact) },
                    onDelete = { onDeleteContact(contact.id) },
                    onStartAutomation = { onStartAutomation(contact) }
                )
            }
        }
    }
}

@Composable
fun FocusStatusCard(
    isFocusActive: Boolean,
    totalContacts: Int,
    selectedContacts: Int,
    blockedByAppCount: Int,
    actionRequiredCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Status",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusChip(
                    text = if (isFocusActive) "Focus Active" else "Ready / Standby",
                    isActive = isFocusActive
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatusMetricItem(label = "Saved", value = totalContacts.toString())
                StatusMetricItem(label = "Selected", value = selectedContacts.toString())
                StatusMetricItem(label = "App Blocked", value = blockedByAppCount.toString())
                StatusMetricItem(label = "Action Req.", value = actionRequiredCount.toString())
            }
        }
    }
}

@Composable
fun StatusMetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun StatusChip(text: String, isActive: Boolean) {
    val bgColor = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val contentColor = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Surface(
        shape = CircleShape,
        color = bgColor,
        modifier = Modifier.border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            shape = CircleShape
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun TechnicalNoticeCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Notice",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "Safe User-Assisted WhatsApp Guide",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "WhatsApp provides no official third-party blocking API. This app uses safe deep links to open conversations and guides you through manual blocking. Contacts are marked 'User Confirmed' only after your explicit confirmation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ActionControlsCard(
    selectedCount: Int,
    blockedByAppCount: Int,
    onSelectContacts: () -> Unit,
    onBlockSelected: () -> Unit,
    onUnblockAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onSelectContacts,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Select Contacts")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onBlockSelected,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (selectedCount > 0) "Block Selected ($selectedCount)" else "Block Selected",
                        maxLines = 1
                    )
                }

                OutlinedButton(
                    onClick = onUnblockAll,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (blockedByAppCount > 0) "Unblock All ($blockedByAppCount)" else "Unblock All",
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyContactsCard(
    onSelectContacts: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No Contacts Added Yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Tap 'Select Contacts' to choose WhatsApp contacts from your device. Contacts remain 100% private and stored locally.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSelectContacts,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Select Contacts")
            }
        }
    }
}

@Composable
fun ContactListItem(
    contact: FocusContact,
    onToggleSelect: (Boolean) -> Unit,
    onStartGuidedBlock: () -> Unit,
    onStartGuidedUnblock: () -> Unit,
    onDelete: () -> Unit,
    onStartAutomation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = contact.isSelected,
                    onCheckedChange = onToggleSelect
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = contact.phoneNumber,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                ContactStatusBadge(status = contact.status)
            }

            // Quick Actions row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                contact.lastActionMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.weight(1f)
                    )
                } ?: Spacer(modifier = Modifier.weight(1f))

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (contact.status == BlockStatus.ACTION_REQUIRED ||
                        contact.status == BlockStatus.SELECTED ||
                        contact.status == BlockStatus.NEEDS_MANUAL_VERIFICATION
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.clickable { onStartAutomation() }
                        ) {
                            Text(
                                text = "Auto (PoC)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.clickable { onStartGuidedBlock() }
                        ) {
                            Text(
                                text = "Block in WA",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else if (contact.canBeUnblockedByApp()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.clickable { onStartGuidedUnblock() }
                        ) {
                            Text(
                                text = "Unblock in WA",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    TextButton(onClick = onDelete) {
                        Text("Remove", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
fun ContactStatusBadge(status: BlockStatus) {
    val (label, bgColor, textColor) = when (status) {
        BlockStatus.UNBLOCKED -> Triple("Unblocked", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        BlockStatus.SELECTED -> Triple("Selected", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        BlockStatus.PENDING -> Triple("Pending", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        BlockStatus.ACTION_REQUIRED -> Triple("Action Required", MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f), MaterialTheme.colorScheme.onErrorContainer)
        BlockStatus.USER_CONFIRMED_BLOCKED -> Triple("User Confirmed Blocked", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        BlockStatus.BLOCKED_CONFIRMED, BlockStatus.BLOCKED_BY_APP -> Triple("Blocked (Confirmed)", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        BlockStatus.USER_CONFIRMED_UNBLOCKED -> Triple("User Confirmed Unblocked", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        BlockStatus.UNBLOCKED_CONFIRMED -> Triple("Unblocked (Confirmed)", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        BlockStatus.AUTO_BLOCKED_BY_APP -> Triple("Auto Blocked (App)", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        BlockStatus.NEEDS_MANUAL_VERIFICATION -> Triple("Needs Verification", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        BlockStatus.MANUALLY_BLOCKED_EXTERNAL -> Triple("Manual (External)", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        BlockStatus.FAILED -> Triple("Failed", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerBottomSheet(
    uiState: FocusUiState,
    onSearchChange: (String) -> Unit,
    onToggleContact: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Device Contacts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Search by name or number...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val filtered = uiState.filteredDeviceContacts
                val allSelected = filtered.isNotEmpty() && filtered.all { uiState.pickerSelectedIds.contains(it.id) }

                Text(
                    text = "Found ${filtered.size} contacts",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                TextButton(onClick = { onSelectAll(!allSelected) }) {
                    Text(if (allSelected) "Deselect All" else "Select All")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (uiState.isPickerLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (uiState.filteredDeviceContacts.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (uiState.searchQuery.isBlank()) "No contacts found on this device" else "No matching contacts found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(
                            items = uiState.filteredDeviceContacts,
                            key = { index, devContact -> "${devContact.id}_${devContact.phoneNumber}_$index" }
                        ) { _, devContact ->
                            val isSelected = uiState.pickerSelectedIds.contains(devContact.id)
                            PickerContactItem(
                                contact = devContact,
                                isSelected = isSelected,
                                onToggle = { onToggleContact(devContact.id) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Confirm (${uiState.pickerSelectedIds.size})")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun PickerContactItem(
    contact: DeviceContact,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// Confirmation Dialogs

@Composable
fun ConfirmBlockDialog(
    selectedContacts: List<FocusContact>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Block Selected (${selectedContacts.size})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("You are preparing to block the following contacts for this focus session:")
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(selectedContacts) { contact ->
                        Text("• ${contact.name} (${contact.phoneNumber})", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    text = "Honest Disclosure: WhatsApp does not allow third-party apps to block contacts directly. This app will launch each contact's chat and guide you through blocking. Contacts will only be marked as 'User Confirmed' after you confirm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Start Guided Blocking")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ConfirmUnblockDialog(
    eligibleContacts: List<FocusContact>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Confirm Unblock All (${eligibleContacts.size})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This action will guide you through unblocking only the contacts you confirmed blocking through this app:")
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(eligibleContacts) { contact ->
                        Text("• ${contact.name} (${contact.phoneNumber})", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    text = "Safety Guarantee: Contacts in 'Action Required' or blocked prior to this session are never included.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Start Guided Unblocking")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Step-by-Step Guided Assistant Dialogs

@Composable
fun AssistedBlockGuideDialog(
    contact: FocusContact,
    onOpenWhatsApp: () -> Unit,
    onConfirmBlocked: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Guide: Block ${contact.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Contact: ${contact.name} (${contact.phoneNumber})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Step 1: Tap below to open this conversation in WhatsApp.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = onOpenWhatsApp,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("1. Open in WhatsApp")
                }

                Text(
                    text = "Step 2: In WhatsApp, tap the 3 dots (⋮) in the top-right ➔ More ➔ Block.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "Step 3: After completing the block in WhatsApp, return here and tap confirm below.",
                    style = MaterialTheme.typography.bodySmall
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Notice: Opening WhatsApp chat does not automatically block. You must manually tap Block in WhatsApp.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirmBlocked) {
                Text("I Have Blocked This Contact")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text("Skip / Cancel")
            }
        }
    )
}

@Composable
fun AssistedUnblockGuideDialog(
    contact: FocusContact,
    onOpenWhatsApp: () -> Unit,
    onConfirmUnblocked: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Guide: Unblock ${contact.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Contact: ${contact.name} (${contact.phoneNumber})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Step 1: Tap below to open this conversation in WhatsApp.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = onOpenWhatsApp,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("1. Open in WhatsApp")
                }

                Text(
                    text = "Step 2: In WhatsApp, tap the unblock banner or 3 dots (⋮) ➔ More ➔ Unblock.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "Step 3: Return here and tap confirm.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirmUnblocked) {
                Text("I Have Unblocked This Contact")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text("Skip")
            }
        }
    )
}

// Audit History Dialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditHistoryDialog(
    auditLogs: List<ActionAuditRecord>,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Action Audit History")
                if (auditLogs.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        text = {
            if (auditLogs.isEmpty()) {
                Text("No actions recorded yet. Every block and unblock attempt will be logged here.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(auditLogs, key = { index, log -> "${log.id}_$index" }) { _, log ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = log.actionType.name,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = dateFormat.format(Date(log.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${log.contactName} (${log.phoneNumber})",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${log.previousStatus} ➔ ${log.newStatus}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = log.details,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun AccessibilityStatusCard(
    isEnabled: Boolean,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Accessibility PoC",
                        tint = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Automated Assistant (PoC)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = if (isEnabled) "Active" else "Disabled",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isEnabled) {
                    "Accessibility Service is active for WhatsApp. Ready for guarded single-contact automation testing."
                } else {
                    "Optional helper to automate repetitive taps inside WhatsApp. Requires user-authorized Accessibility permission."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onConfigure,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isEnabled) "Manage Service & Guide" else "Setup Accessibility Assistant")
            }
        }
    }
}

@Composable
fun AccessibilityOnboardingDialog(
    isCurrentlyEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppDetailsSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Accessibility Assistant Setup",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "WhatsApp Focus Blocker includes an experimental accessibility assistant to automate repetitive taps (Menu -> More -> Block) for selected focus contacts.",
                    style = MaterialTheme.typography.bodyMedium
                )

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Strict Privacy Guarantees:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Scoped strictly to WhatsApp (com.whatsapp).\n• Never reads message text, chats, or personal info.\n• Operates only during explicit blocking runs.\n• Never collects or transmits data externally.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Android 13+ Setup Instructions:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "1. Sideloaded apps have accessibility restricted by Android by default.\n2. Tap 'Allow Restricted Settings' below -> Tap top-right 3-dots -> Select 'Allow restricted settings'.\n3. Tap 'Open Accessibility' below -> Turn on WhatsApp Focus Blocker.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isCurrentlyEnabled) {
                    Text(
                        text = "Status: Service is currently ACTIVE and ready.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onOpenAccessibilitySettings) {
                Text("Open Accessibility")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenAppDetailsSettings) {
                    Text("Allow Restricted")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

@Composable
fun SupervisedAutomationConsentDialog(
    contact: FocusContact,
    onStart: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Supervised PoC Automation",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Target: ${contact.name} (${contact.phoneNumber})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "This experimental single-contact PoC automates the repetitive menu clicks in WhatsApp to block this contact.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Guarantees & Guardrails:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "• Scoped exclusively to WhatsApp.\n• Chat header verified before clicking.\n• 'Report contact' spam checkbox automatically unchecked.\n• No personal messages read, collected, or stored.\n• 3.5s timeout watchdog & Emergency STOP available.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onStart) {
                Text("Start Supervised Run")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SupervisedAutomationActiveDialog(
    contact: FocusContact,
    state: BlockAutomationState,
    statusMessage: String?,
    onStop: () -> Unit,
    onSwitchToManual: () -> Unit
) {
    val isPaused = state == BlockAutomationState.PAUSED_FOR_MANUAL_INTERVENTION

    AlertDialog(
        onDismissRequest = { /* Modal: force user to tap STOP or Switch to Manual */ },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isPaused) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp)
                }
                Text(
                    text = if (isPaused) "Automation Paused" else "Automating Block (PoC)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPaused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Contact: ${contact.name} (${contact.phoneNumber})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "State: $state",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = statusMessage ?: "Processing...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isPaused) {
                    Text(
                        text = "An unexpected screen or timeout was encountered. You can complete the action manually in WhatsApp or abort.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (isPaused) {
                Button(onClick = onSwitchToManual) {
                    Text("Complete Manually")
                }
            } else {
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("EMERGENCY STOP")
                }
            }
        },
        dismissButton = {
            if (isPaused) {
                TextButton(onClick = onStop) {
                    Text("Abort")
                }
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    WhatsAppFocusBlockerTheme {
        MainContent(
            uiState = FocusUiState(),
            onSelectContacts = {},
            onBlockSelected = {},
            onUnblockAll = {},
            onToggleContact = { _, _ -> },
            onStartGuidedBlock = {},
            onStartGuidedUnblock = {},
            onDeleteContact = {},
            onOpenAccessibilityOnboarding = {}
        )
    }
}

