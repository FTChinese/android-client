package com.ft.ftchinese.viewmodel

import com.ft.ftchinese.R
import com.ft.ftchinese.util.ClientError
import com.ft.ftchinese.util.NetworkException
import java.io.IOException

val statusCodes = mapOf(
        401 to R.string.api_unauthorized,
        429 to R.string.api_too_many_request,
        500 to R.string.api_server_error
)

fun parseApiError(e: ClientError): Result<Nothing> {
    return when (e.statusCode) {
        401 -> Result.LocalizedError(R.string.api_unauthorized)
        429 -> Result.LocalizedError(R.string.api_too_many_request)
        500 -> Result.LocalizedError(R.string.api_server_error)
        else -> Result.Error(e)
    }
}

fun parseException(e: Exception): Result<Nothing> {
    return when (e) {
        is IllegalStateException -> {
            Result.LocalizedError(R.string.api_empty_url)
        }
        is NetworkException -> {
            Result.LocalizedError(R.string.api_network_failure)
        }
        is IOException -> {
            Result.LocalizedError(R.string.api_io_error)
        }
        else -> {
            Result.Error(e)
        }
    }
}
