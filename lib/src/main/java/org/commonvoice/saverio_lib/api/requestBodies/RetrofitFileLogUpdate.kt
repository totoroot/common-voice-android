package org.commonvoice.saverio_lib.api.requestBodies

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RetrofitFileLogUpdate(

    @Json(name = "logged")
    val isLogged: Int,

    @Json(name = "language")
    val language: String,

    @Json(name = "version")
    val appVersioncode: String,

    @Json(name = "source")
    val appSource: String,

    @Json(name = "errorLevel")
    val errorLevel: String,

    @Json(name = "tag")
    val tag: String,

    @Json(name = "stackTrace")
    val stackTrace: String,

    @Json(name = "logDate")
    val logDate: String,

    @Json(name = "additionalLogs")
    val additionalLogs: String
) {
    constructor(
        isLogged: Int,
        language: String,
        appVersioncode: String,
        appSource: String,
        stackTrace: String,
        logDate: String
    ) : this(
        isLogged = isLogged,
        language = language,
        appVersioncode = appVersioncode,
        appSource = appSource,
        errorLevel = "error",
        stackTrace = stackTrace,
        logDate = logDate,
        tag = "",
        additionalLogs = ""
    )
}