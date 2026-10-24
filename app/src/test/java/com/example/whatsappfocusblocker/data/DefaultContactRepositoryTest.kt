package com.example.whatsappfocusblocker.data

import android.content.SharedPreferences
import com.example.whatsappfocusblocker.domain.model.BlockStatus
import com.example.whatsappfocusblocker.domain.model.FocusContact
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultContactRepositoryTest {

    @Test
    fun saveContacts_persistsToPreferences_andRestoresOnNewInstance() = runTest {
        val fakePrefs = FakeSharedPreferences()
        val repo1 = DefaultContactRepository(fakePrefs)

        val contact1 = FocusContact(
            id = "+1234567890",
            name = "Alice",
            phoneNumber = "+1234567890",
            isSelected = true,
            status = BlockStatus.UNBLOCKED
        )
        repo1.saveContacts(listOf(contact1))

        assertEquals(1, repo1.getSavedContacts().first().size)

        // Instantiate new repository instance with same SharedPreferences (simulating app restart)
        val repo2 = DefaultContactRepository(fakePrefs)
        val restored = repo2.getSavedContacts().first()

        assertEquals(1, restored.size)
        assertEquals("Alice", restored.first().name)
        assertEquals("+1234567890", restored.first().phoneNumber)
        assertTrue(restored.first().isSelected)
    }

    @Test
    fun toggleContactSelection_updatesAndPersists() = runTest {
        val fakePrefs = FakeSharedPreferences()
        val repo = DefaultContactRepository(fakePrefs)

        val contact = FocusContact(
            id = "c1",
            name = "Bob",
            phoneNumber = "+987654321",
            isSelected = false,
            status = BlockStatus.UNBLOCKED
        )
        repo.saveContacts(listOf(contact))
        repo.toggleContactSelection("c1", true)

        val updated = repo.getSavedContacts().first()
        assertTrue(updated.first().isSelected)

        // Verify reloaded instance also sees selection
        val reloaded = DefaultContactRepository(fakePrefs).getSavedContacts().first()
        assertTrue(reloaded.first().isSelected)
    }

    @Test
    fun deleteContact_removesFromPersistence() = runTest {
        val fakePrefs = FakeSharedPreferences()
        val repo = DefaultContactRepository(fakePrefs)

        val contact = FocusContact(id = "c1", name = "Charlie", phoneNumber = "+111", isSelected = false)
        repo.saveContacts(listOf(contact))
        assertEquals(1, repo.getSavedContacts().first().size)

        repo.deleteContact("c1")
        assertEquals(0, repo.getSavedContacts().first().size)

        // Verify across restart
        val reloaded = DefaultContactRepository(fakePrefs).getSavedContacts().first()
        assertTrue(reloaded.isEmpty())
    }
}

/**
 * Lightweight in-memory SharedPreferences for unit tests without Android mock dependencies.
 */
class FakeSharedPreferences : SharedPreferences {
    val map = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = map
    override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = null
    override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
    override fun contains(key: String?): Boolean = map.containsKey(key)
    override fun edit(): SharedPreferences.Editor = FakeEditor(this)
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    class FakeEditor(private val prefs: FakeSharedPreferences) : SharedPreferences.Editor {
        private val temp = mutableMapOf<String, Any?>()

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) temp[key] = value
            return this
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = this
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = this
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = this
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = this
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = this
        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) temp.remove(key)
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            temp.clear()
            return this
        }
        override fun commit(): Boolean {
            prefs.map.putAll(temp)
            return true
        }
        override fun apply() {
            commit()
        }
    }
}
