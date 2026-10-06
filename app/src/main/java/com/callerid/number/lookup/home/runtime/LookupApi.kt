package com.callerid.number.lookup.home.runtime

import com.google.gson.JsonObject
import com.callerid.number.lookup.home.wire.LookupResponse
import okhttp3.MultipartBody
import retrofit2.Call
import retrofit2.Response
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
    @POST("upload/contacts")
    fun uploadContacts(
        @Part file: MultipartBody.Part,
    ): Call<JsonObject>
}
