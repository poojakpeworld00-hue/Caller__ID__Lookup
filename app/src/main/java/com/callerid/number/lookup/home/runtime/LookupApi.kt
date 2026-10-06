package com.callerid.number.lookup.home.runtime

import com.callerid.number.lookup.home.wire.LookupResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

interface LookupApi {

    @GET("similar-phone-number")
    suspend fun checkPhoneNumber(
        @Query("phone") phone: String
    ): Response<LookupResponse>

    @Multipart
    @POST("android/upload/contacts")
    suspend fun uploadContacts(
        @Part file: MultipartBody.Part,
        @Part("deviceId") deviceId: RequestBody,
    ): Response<ContactUploadResult>

    @DELETE("android/upload/contacts")
    suspend fun deleteContacts(@Query("deviceId") deviceId: String): Response<ContactDeleteResult>
}

data class ContactUploadResult(
    val success: Boolean = false,
    val totalRows: Int = 0,
    val validContacts: Int = 0,
    val invalidContacts: Int = 0,
    val processingTimeMs: Long = 0,
    val sampleErrors: List<String> = emptyList(),
)

data class ContactDeleteResult(
    val success: Boolean = false,
    val deviceId: String = "",
    val deleted: Int = 0,
)
