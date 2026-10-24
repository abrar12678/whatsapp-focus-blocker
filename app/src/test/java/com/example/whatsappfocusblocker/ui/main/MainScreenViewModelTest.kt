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
import junit.framework.TestCase.assertNull
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
    fun executeBlockSelected_startsGuidedAssistant_andAllowsUserConfirmation() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val contact = FocusContact(id = "c1", name = "Alice", phoneNumber = "+1234567890", isSelected = true)
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
        assertEquals(0, state.blockedByAppCount)

        // Guided assistant should now be active for Alice
        assertNotNull(state.activeGuidedBlockContact)
        assertEquals("Alice", state.activeGuidedBlockContact!!.name)

        // User confirms they completed the block in WhatsApp
        viewModel.onConfirmManualBlockCompleted("c1")
        advanceUntilIdle()

        val confirmedState = viewModel.uiState.value
        assertNull("Guided block should be complete", confirmedState.activeGuidedBlockContact)
        assertEquals(1, confirmedState.blockedByAppCount) // Now counted as confirmed by user
        assertEquals(0, confirmedState.actionRequiredCount)

        val alice = confirmedState.contacts.first()
        assertEquals(BlockStatus.USER_CONFIRMED_BLOCKED, alice.status)
        assertEquals("User Confirmed in WhatsApp", alice.lastActionMessage)

        // Verify audit log has BLOCK_CONFIRMED with isVerified = true
        val confirmedLog = confirmedState.auditLogs.find { it.actionType == ActionType.BLOCK_CONFIRMED }
        assertNotNull(confirmedLog)
        assertTrue(confirmedLog!!.isVerified)
    }

    @Test
    fun unblockAll_guidedFlow_unblocksOnlyUserConfirmedContacts() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val confirmedContact = FocusContact(
            id = "c1",
            name = "Bob",
            phoneNumber = "+111222333",
            status = BlockStatus.USER_CONFIRMED_BLOCKED,
            blockedByAppTimestamp = System.currentTimeMillis()
        )
        val externalContact = FocusContact(
            id = "c2",
            name = "Charlie",
            phoneNumber = "+444555666",
            status = BlockStatus.MANUALLY_BLOCKED_EXTERNAL
        )
        repository.saveContacts(listOf(confirmedContact, externalContact))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.blockedByAppCount)

        viewModel.onUnblockAllClicked()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Confirm unblock dialog should open", state.showConfirmUnblockDialog)
        assertEquals(1, state.unblockEligibleContacts.size)
        assertEquals("Bob", state.unblockEligibleContacts.first().name)

        viewModel.executeUnblockAll()
        advanceUntilIdle()

        // Guided unblock should open for Bob
        assertNotNull(viewModel.uiState.value.activeGuidedUnblockContact)
        assertEquals("Bob", viewModel.uiState.value.activeGuidedUnblockContact!!.name)

        viewModel.onConfirmManualUnblockCompleted("c1")
        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertNull(finalState.activeGuidedUnblockContact)
        assertEquals(0, finalState.blockedByAppCount)

        val bob = finalState.contacts.find { it.id == "c1" }
        assertEquals(BlockStatus.USER_CONFIRMED_UNBLOCKED, bob!!.status)

        // Charlie MUST remain completely untouched as MANUALLY_BLOCKED_EXTERNAL
        val charlie = finalState.contacts.find { it.id == "c2" }
        assertEquals(BlockStatus.MANUALLY_BLOCKED_EXTERNAL, charlie!!.status)
    }

    @Test
    fun unblockAll_safetyGuardrail_preventsActionRequiredOrManualFromUnblocking() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val actionRequired = FocusContact(
            id = "c1",
            name = "Dan",
            phoneNumber = "+123",
            status = BlockStatus.ACTION_REQUIRED
        )
        val manual = FocusContact(
            id = "c2",
            name = "Eve",
            phoneNumber = "+456",
            status = BlockStatus.MANUALLY_BLOCKED_EXTERNAL
        )
        repository.saveContacts(listOf(actionRequired, manual))
        advanceUntilIdle()

        viewModel.onUnblockAllClicked()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showConfirmUnblockDialog)
        assertNotNull(state.userNotice)
        assertTrue(state.userNotice!!.contains("Safety Guardrail"))
    }

    @Test
    fun deleteContact_removesFromListAndPersists() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val contact = FocusContact(id = "c1", name = "Frank", phoneNumber = "+777")
        repository.saveContacts(listOf(contact))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.totalCount)

        viewModel.onDeleteContact("c1")
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.totalCount)
    }

    @Test
    fun accessibilityState_updatesAndReflectsInUiState() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isAccessibilityEnabled)

        viewModel.updateAccessibilityEnabled(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isAccessibilityEnabled)
    }

    @Test
    fun accessibilityOnboarding_opensAndDismissesCorrectly() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showAccessibilityOnboarding)

        viewModel.onOpenAccessibilityOnboarding()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showAccessibilityOnboarding)

        viewModel.onDismissAccessibilityOnboarding()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showAccessibilityOnboarding)
    }
}

private class FakeDeviceContactsDataSource : DeviceContactsDataSource {
    var contacts: List<DeviceContact> = emptyList()

    override suspend fun fetchDeviceContacts(): List<DeviceContact> {
        return contacts
    }
}
