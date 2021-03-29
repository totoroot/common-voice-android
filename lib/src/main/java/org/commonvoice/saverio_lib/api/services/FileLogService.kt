package org.commonvoice.saverio_lib.api.services

import org.commonvoice.saverio_lib.api.requestBodies.RetrofitFileLogUpdate
import org.commonvoice.saverio_lib.api.responseBodies.ResponseGetFileLog
import org.commonvoice.saverio_lib.api.responseBodies.ResponsePostFileLog
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST

interface FileLogService {

    @Headers("Content-Type: application/json")
    @POST("v2/logs/")
    suspend fun postFileLog(@Body fileLog: RetrofitFileLogUpdate): Response<ResponsePostFileLog>

    @GET("v2/logs/get/")
    suspend fun getFileLog(): Response<ResponseGetFileLog>
}