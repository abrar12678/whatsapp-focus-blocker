package com.example.whatsappfocusblocker.data.contacts

data class DeviceContact(
    val id: String,
    val name: String,
    val phoneNumber: String
)

interface DeviceContactsDataSource {
    suspend fun fetchDeviceContacts(): List<DeviceContact>
}
