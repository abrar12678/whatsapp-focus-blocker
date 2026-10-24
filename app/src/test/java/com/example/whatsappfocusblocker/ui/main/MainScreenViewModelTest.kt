package com.example.whatsappfocusblocker.ui.main

import com.example.whatsappfocusblocker.data.DefaultContactRepository
import com.example.whatsappfocusblocker.data.FakeSharedPreferences
import com.example.whatsappfocusblocker.data.contacts.DeviceContact
import com.example.whatsappfocusblocker.data.contacts.DeviceContactsDataSource
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
        assertTrue(state.contacts.isEmpty())
    }

    @Test
    fun onPermissionResult_granted_opensPickerAndLoadsContacts() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        fakeDataSource.contacts = listOf(
            DeviceContact(id = "1", name = "Zoe", phoneNumber = "+111"),
            DeviceContact(id = "2", name = "Alex", phoneNumber = "+222")
        )

        viewModel.onPermissionResult(isGranted = true, shouldShowRationale = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue("Picker should be open", state.isPickerOpen)
        assertEquals(2, state.deviceContacts.size)
        assertEquals("Zoe", state.deviceContacts[0].name)
    }

    @Test
    fun onPermissionResult_deniedWithRationale_showsRationaleDialog() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.onPermissionResult(isGranted = false, shouldShowRationale = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isPickerOpen)
        assertTrue(state.showPermissionRationale)
        assertFalse(state.showPermanentlyDeniedDialog)
    }

    @Test
    fun onPermissionResult_permanentlyDenied_showsSettingsDialog() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.onPermissionResult(isGranted = false, shouldShowRationale = false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isPickerOpen)
        assertFalse(state.showPermissionRationale)
        assertTrue(state.showPermanentlyDeniedDialog)
    }

    @Test
    fun searchAndFilterContacts_filtersByNameAndNumber() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        fakeDataSource.contacts = listOf(
            DeviceContact(id = "1", name = "Sarah Connor", phoneNumber = "+1234567"),
            DeviceContact(id = "2", name = "John Wick", phoneNumber = "+9876543"),
            DeviceContact(id = "3", name = "John Smith", phoneNumber = "+5551234")
        )

        viewModel.openPickerWithDeviceContacts()
        advanceUntilIdle()

        viewModel.onSearchQueryChanged("john")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.filteredDeviceContacts.size)
        assertTrue(state.filteredDeviceContacts.all { it.name.startsWith("John") })

        viewModel.onSearchQueryChanged("555")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.filteredDeviceContacts.size)
        assertEquals("John Smith", viewModel.uiState.value.filteredDeviceContacts.first().name)
    }

    @Test
    fun multiSelectionAndConfirm_savesContactsCorrectly() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        fakeDataSource.contacts = listOf(
            DeviceContact(id = "c1", name = "Contact One", phoneNumber = "+111"),
            DeviceContact(id = "c2", name = "Contact Two", phoneNumber = "+222")
        )

        viewModel.openPickerWithDeviceContacts()
        advanceUntilIdle()

        // Toggle c1 and c2
        viewModel.onTogglePickerSelection("c1")
        viewModel.onTogglePickerSelection("c2")
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.pickerSelectedIds.size)

        // Confirm
        viewModel.onConfirmContactSelection()
        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertFalse("Picker should be closed after confirm", finalState.isPickerOpen)
        assertEquals(2, finalState.totalCount)
        assertEquals(2, finalState.selectedCount)
        assertEquals(0, finalState.blockedByAppCount) // Must never pretend blocked!
        assertNotNull(finalState.userNotice)
        assertTrue(finalState.userNotice!!.contains("Saved 2"))
    }
}

private class FakeDeviceContactsDataSource : DeviceContactsDataSource {
    var contacts: List<DeviceContact> = emptyList()

    override suspend fun fetchDeviceContacts(): List<DeviceContact> {
        return contacts
    }
}
