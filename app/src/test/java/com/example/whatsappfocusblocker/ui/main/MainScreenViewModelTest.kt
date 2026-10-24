package com.example.whatsappfocusblocker.ui.main

import com.example.whatsappfocusblocker.data.DefaultContactRepository
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
    private lateinit var viewModel: MainScreenViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = DefaultContactRepository()
        viewModel = MainScreenViewModel(repository)
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
    fun onSelectContactsClicked_emitsInformationalNotice() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.onSelectContactsClicked()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.userNotice)
        assertTrue(state.userNotice!!.contains("Step 2"))
    }

    @Test
    fun onBlockSelectedClicked_withEmptySelection_informsUser() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.onBlockSelectedClicked()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Please select at least one contact to block.", state.userNotice)
    }

    @Test
    fun onUnblockAllClicked_withNoAppBlockedContacts_informsUser() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        viewModel.onUnblockAllClicked()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("No contacts were blocked by this app to unblock.", state.userNotice)
    }

    @Test
    fun toggleContactSelection_updatesStateCorrectly() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        val contact = FocusContact(
            id = "+123456789",
            name = "John Doe",
            phoneNumber = "+123456789",
            isSelected = false,
            status = BlockStatus.UNBLOCKED
        )
        repository.saveContacts(listOf(contact))
        advanceUntilIdle()

        viewModel.onToggleContact("+123456789", true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.totalCount)
        assertEquals(1, state.selectedCount)
        assertTrue(state.contacts.first().isSelected)
    }
}
