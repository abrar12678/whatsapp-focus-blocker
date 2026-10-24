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
import com.example.whatsappfocusblocker.theme.WhatsAppFocusBlockerTheme
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

    // Step 3: Confirm Block Selected Dialog
    if (uiState.showConfirmBlockDialog) {
        ConfirmBlockDialog(
            selectedContacts = uiState.selectedContacts,
            onConfirm = viewModel::executeBlockSelected,
            onDismiss = viewModel::onDismissConfirmBlockDialog
        )
    }

    // Step 3: Confirm Unblock All Dialog
    if (uiState.showConfirmUnblockDialog) {
        ConfirmUnblockDialog(
            eligibleContacts = uiState.unblockEligibleContacts,
            onConfirm = viewModel::executeUnblockAll,
            onDismiss = viewModel::onDismissConfirmUnblockDialog
        )
    }

    // Step 3: Audit History Dialog
    if (uiState.showAuditHistoryDialog) {
        AuditHistoryDialog(
            auditLogs = uiState.auditLogs,
            onClear = viewModel::onClearAuditHistory,
            onDismiss = viewModel::onDismissAuditHistory
        )
    }

    // Permission Rationale Dialog
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

    // Permanently Denied Dialog (Guide to App Settings)
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
            items(uiState.contacts, key = { it.id }) { contact ->
                ContactListItem(
                    contact = contact,
                    onToggleSelect = { isChecked -> onToggleContact(contact.id, isChecked) }
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
                    text = "Notice: Action Tracking & Safety Mode",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "WhatsApp has no official third-party blocking API. Every action attempt is logged in your local audit history. Contacts are never labeled 'Blocked Confirmed' until an actual block is verified.",
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                contact.lastActionMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            ContactStatusBadge(status = contact.status)
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
        BlockStatus.BLOCKED_CONFIRMED, BlockStatus.BLOCKED_BY_APP -> Triple("Blocked (Confirmed)", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        BlockStatus.UNBLOCKED_CONFIRMED -> Triple("Unblocked (Confirmed)", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
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
                        items(uiState.filteredDeviceContacts, key = { it.id }) { devContact ->
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

// Step 3: Confirmation Dialogs

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
                Text("You are initiating focus block for the following contacts:")
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(selectedContacts) { contact ->
                        Text("• ${contact.name} (${contact.phoneNumber})", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    text = "Technical Reality: Real WhatsApp blocking automation is not configured. Contacts will transition to 'Action Required' and be logged in your local audit history. They will not be falsely labeled as Blocked Confirmed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Proceed")
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
                Text("This action will revert only blocks that this app successfully confirmed:")
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(eligibleContacts) { contact ->
                        Text("• ${contact.name} (${contact.phoneNumber})", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    text = "Safety Rule: Contacts you blocked manually prior to this session will never be unblocked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Proceed")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

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
                    items(auditLogs, key = { it.id }) { log ->
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

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    WhatsAppFocusBlockerTheme {
        MainContent(
            uiState = FocusUiState(),
            onSelectContacts = {},
            onBlockSelected = {},
            onUnblockAll = {},
            onToggleContact = { _, _ -> }
        )
    }
}
