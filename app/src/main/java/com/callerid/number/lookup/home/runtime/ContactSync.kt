package com.callerid.number.lookup.home.runtime

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.callerid.number.lookup.home.BuildConfig
import com.callerid.number.lookup.home.store.ContactItem
import com.callerid.number.lookup.home.store.ContactSource
import com.callerid.number.lookup.home.store.StorageRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.UUID

object ContactSync {

    private const val TAG = "ContactSync"
    private const val FILE_NAME = "contacts_upload.csv"
    private const val PREFS = "contact_sync"
    private const val KEY_DEVICE_ID = "device_id"
    private const val CSV_HEADER = "phoneNumber,displayName,city,state,pincode"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var inProgress = false

    fun hasUploaded(context: Context): Boolean = StorageRegistry(context.applicationContext).isContactsUploaded

    /**
     * The id the server files this install's rows under, so they can be deleted again. ANDROID_ID
     * is per app and signing key since Android 8, and survives a reinstall; a device without a
     * usable one gets a saved UUID.
     */
    fun deviceId(context: Context): String {
        @Suppress("HardwareIds")
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull()?.filter { it.isLetterOrDigit() || it == '_' || it == '-' }.orEmpty()
        if (androidId.length in 8..64) return androidId
        val prefs = prefs(context)
        prefs.getString(KEY_DEVICE_ID, null)?.let { return it }
        return UUID.randomUUID().toString().also { id -> prefs.edit { putString(KEY_DEVICE_ID, id) } }
    }

    fun uploadOnceIfNeeded(context: Context) {

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Skipping upload in debug build")
            return
        }
        val app = context.applicationContext
        val prefs = StorageRegistry(app)
        if (prefs.isContactsUploaded || inProgress) return
        if (ContextCompat.checkSelfPermission(app, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        if (!ApiCredentials.isConfigured) {
            Log.w(TAG, "Contact upload skipped: no API key configured")
            return
        }

        inProgress = true
        scope.launch {
            var file: File? = null
            try {
                val contacts = ContactSource(app).getContacts()
                if (contacts.isEmpty()) {
                    Log.w(TAG, "No contacts to upload")
                    return@launch
                }

                file = File(app.cacheDir, FILE_NAME).apply { writeText(toCsv(contacts)) }
                val id = deviceId(app)
                Log.d(TAG, "Uploading ${contacts.size} contacts (${file.length()} bytes), deviceId=$id")

                val part = MultipartBody.Part.createFormData(
                    "file", file.name, file.asRequestBody(CSV_MEDIA_TYPE)
                )
                val response = HttpClientFactory.uploadApi.uploadContacts(part, id.toRequestBody(TEXT_MEDIA_TYPE))

                if (response.isSuccessful) {
                    prefs.isContactsUploaded = true
                    val body = response.body()
                    Log.i(TAG, "Upload SUCCESS (${response.code()}): total=${body?.totalRows} valid=${body?.validContacts} invalid=${body?.invalidContacts}")
                } else {
                    val err = runCatching { response.errorBody()?.string() }.getOrNull()
                    Log.e(TAG, "Upload FAILED (${response.code()}): $err")
                }
            } catch (e: Exception) {

                Log.e(TAG, "Upload ERROR: ${e.message}", e)
            } finally {
                runCatching { file?.delete() }
                inProgress = false
            }
        }
    }

    /** Removes everything this install uploaded. Returns the rows deleted, or null on failure. */
    suspend fun deleteUploaded(context: Context): Int? = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        runCatching {
            val response = HttpClientFactory.uploadApi.deleteContacts(deviceId(app))
            if (response.isSuccessful) {
                // Cleared so the next launch may upload again.
                StorageRegistry(app).isContactsUploaded = false
                prefs(app).edit { remove(KEY_DEVICE_ID) }
                val deleted = response.body()?.deleted ?: 0
                Log.i(TAG, "Delete OK: removed $deleted rows")
                deleted
            } else {
                Log.w(TAG, "Delete FAILED (${response.code()}): ${runCatching { response.errorBody()?.string() }.getOrNull()}")
                null
            }
        }.getOrElse { Log.w(TAG, "Delete ERROR: ${it.message}", it); null }
    }

    internal fun toCsv(contacts: List<ContactItem>): String = buildString {
        append(CSV_HEADER).append('\n')
        contacts.forEach { contact ->
            append(csvField(contact.detail)).append(',')
            append(csvField(contact.name)).append(',')
            append("\"\",\"\",\"\"\n")
        }
    }

    // Every field quoted, inner quotes doubled, as the API's CSV rule asks.
    private fun csvField(value: String?): String = "\"" + value.orEmpty().replace("\"", "\"\"") + "\""

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val CSV_MEDIA_TYPE = "text/csv".toMediaTypeOrNull()
    private val TEXT_MEDIA_TYPE = "text/plain".toMediaTypeOrNull()
}
