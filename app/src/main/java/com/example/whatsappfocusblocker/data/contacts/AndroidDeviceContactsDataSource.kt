package com.example.whatsappfocusblocker.data.contacts

import android.content.ContentResolver
import android.provider.ContactsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads real contacts from the device's ContactsContract provider.
 * Strictly operates locally without any network access.
 */
class AndroidDeviceContactsDataSource(
    private val contentResolver: ContentResolver
) : DeviceContactsDataSource {

    override suspend fun fetchDeviceContacts(): List<DeviceContact> = withContext(Dispatchers.IO) {
        val contactsList = mutableListOf<DeviceContact>()
        val seenNumbers = mutableSetOf<String>()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"

        try {
            contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val lookupIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY)
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val rawId = if (idIndex != -1) cursor.getString(idIndex) else ""
                    val lookupKey = if (lookupIndex != -1) cursor.getString(lookupIndex) else rawId
                    val name = if (nameIndex != -1) cursor.getString(nameIndex) ?: "Unknown" else "Unknown"
                    val rawNumber = if (numberIndex != -1) cursor.getString(numberIndex) ?: "" else ""

                    val normalizedNumber = rawNumber.replace(Regex("[\\s\\-\\(\\)]"), "")
                    if (normalizedNumber.isNotBlank() && !seenNumbers.contains(normalizedNumber)) {
                        seenNumbers.add(normalizedNumber)
                        val uniqueId = if (lookupKey.isNotBlank()) lookupKey else normalizedNumber
                        contactsList.add(
                            DeviceContact(
                                id = uniqueId,
                                name = name.trim(),
                                phoneNumber = rawNumber.trim()
                            )
                        )
                    }
                }
            }
        } catch (e: SecurityException) {
            // Permission revoked or not granted
            return@withContext emptyList()
        } catch (e: Exception) {
            return@withContext emptyList()
        }

        contactsList
    }
}
