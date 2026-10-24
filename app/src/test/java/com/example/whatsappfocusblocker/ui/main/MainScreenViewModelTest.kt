package com.example.whatsappfocusblocker.ui.main

import com.example.whatsappfocusblocker.data.DefaultContactRepository
import com.example.whatsappfocusblocker.data.FakeSharedPreferences
import com.example.whatsappfocusblocker.data.contacts.DeviceContact
import com.example.whatsappfocusblocker.data.contacts.DeviceContactsDataSource
import com.example.whatsappfocusblocker.domain.model.ActionType
import com.example.whatsappfocusblocker.domain.model.BlockStatus
import com.example.whatsappfocusblocker.domain.model.FocusContact
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: DefaultContactRepository
    private lateinit var fakeDataSource: FakeDeviceContactsDataSource
    private lateinit var viewModel: MainScreenViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = DefaultContactRepository(FakeSharedPreferences())
        fakeDataSource = FakeDeviceContactsDataSource()
        viewModel = MainScreenViewModel(repository, fakeDataSource)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_initiallyEmptyAndStandby() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isFocusActive)
        assertEquals(0, state.totalCount)
        assertEquals(0, state.selectedCount)
        assertEquals(0, state.blockedByAppCount)
        assertEquals(0, state.actionRequiredCount)
        assertTrue(state.contacts.isEmpty())
        assertTrue(state.auditLogs.isEmpty())
    }

    @Test
    fun onBlockSelectedClicked_showsConfirmDialog_whenContactsSelected() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val contact = FocusContact(id = "1", name = "Alice", phoneNumber = "+123", isSelected = true)
        repository.saveContacts(listOf(contact))
        advanceUntilIdle()

        viewModel.onBlockSelectedClicked()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Confirmation dialog should be displayed", state.showConfirmBlockDialog)
    }

    @Test
    fun executeBlockSelected_transitionsToActionRequired_andLogsAuditRecordsWithoutFaking() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val contact = FocusContact(id = "1", name = "Alice", phoneNumber = "+123", isSelected = true)
        repository.saveContacts(listOf(contact))
        advanceUntilIdle()

        viewModel.onBlockSelectedClicked()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showConfirmBlockDialog)

        viewModel.executeBlockSelected()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showConfirmBlockDialog)
        assertEquals(1, state.actionRequiredCount)
        assertEquals(0, state.blockedByAppCount) // SAFETY: Never falsely claimed as Blocked Confirmed!
        assertEquals(BlockStatus.ACTION_REQUIRED, state.contacts.first().status)

        // Verify audit logs were persisted
        assertEquals(2, state.auditLogs.size)
        val integrationLog = state.auditLogs.find { it.actionType == ActionType.INTEGRATION_UNAVAILABLE }
        assertNotNull(integrationLog)
        assertEquals(BlockStatus.ACTION_REQUIRED, integrationLog!!.newStatus)
        assertFalse(integrationLog.isVerified)
    }

    @Test
    fun onBlockSelectedClicked_duplicatePrevention_rejectsIfPending() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val pendingContact = FocusContact(
            id = "1",
            name = "Bob",
            phoneNumber = "+456",
            isSelected = true,
            status = BlockStatus.PENDING
        )
        repository.saveContacts(listOf(pendingContact))
        advanceUntilIdle()

        viewModel.onBlockSelectedClicked()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Should not open confirm dialog for already pending action", state.showConfirmBlockDialog)
        assertTrue(state.userNotice!!.contains("already in progress"))
    }

    @Test
    fun onUnblockAll_safetyGuardrail_preventsUnblockingActionRequiredOrManualContacts() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val actionRequiredContact = FocusContact(
            id = "1",
            name = "Charlie",
            phoneNumber = "+789",
            isSelected = true,
            status = BlockStatus.ACTION_REQUIRED
        )
        val manualContact = FocusContact(
            id = "2",
            name = "Eve",
            phoneNumber = "+999",
            isSelected = true,
            status = BlockStatus.MANUALLY_BLOCKED_EXTERNAL
        )
        repository.saveContacts(listOf(actionRequiredContact, manualContact))
        advanceUntilIdle()

        viewModel.onUnblockAllClicked()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse("Confirm dialog must NOT open when no confirmed app blocks exist", state.showConfirmUnblockDialog)
        assertNotNull(state.userNotice)
        assertTrue(state.userNotice!!.contains("Safety Guardrail"))

        // Audit log must record safety guardrail block
        val guardrailLog = state.auditLogs.find { it.actionType == ActionType.SAFETY_GUARDRAIL_BLOCKED }
        assertNotNull(guardrailLog)
    }

    @Test
    fun executeUnblockAll_unblocksOnlyConfirmedAppBlocks() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val confirmedBlockedContact = FocusContact(
            id = "1",
            name = "Frank",
            phoneNumber = "+111",
            status = BlockStatus.BLOCKED_CONFIRMED,
            blockedByAppTimestamp = System.currentTimeMillis()
        )
        val externalContact = FocusContact(
            id = "2",
            name = "Grace",
            phoneNumber = "+222",
            status = BlockStatus.MANUALLY_BLOCKED_EXTERNAL
        )
        repository.saveContacts(listOf(confirmedBlockedContact, externalContact))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.blockedByAppCount)

        viewModel.onUnblockAllClicked()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showConfirmUnblockDialog)

        viewModel.executeUnblockAll()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(0, state.blockedByAppCount)

        val unblockedFrank = state.contacts.find { it.id == "1" }
        assertEquals(BlockStatus.UNBLOCKED_CONFIRMED, unblockedFrank!!.status)

        // External contact must REMAIN completely untouched
        val untouchedGrace = state.contacts.find { it.id == "2" }
        assertEquals(BlockStatus.MANUALLY_BLOCKED_EXTERNAL, untouchedGrace!!.status)
    }

    @Test
    fun auditHistory_canBeCleared() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val contact = FocusContact(id = "1", name = "Test", phoneNumber = "+1", isSelected = true)
        repository.saveContacts(listOf(contact))
        advanceUntilIdle()

        viewModel.executeBlockSelected()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.auditLogs.isNotEmpty())

        viewModel.onClearAuditHistory()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.auditLogs.isEmpty())
    }
}

private class FakeDeviceContactsDataSource : DeviceContactsDataSource {
    var contacts: List<DeviceContact> = emptyList()

    override suspend fun fetchDeviceContacts(): List<DeviceContact> {
        return contacts
    }
}
