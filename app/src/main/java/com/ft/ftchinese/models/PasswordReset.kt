package com.ft.ftchinese.models

import com.ft.ftchinese.util.NextApi
import com.ft.ftchinese.util.Fetch
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import java.io.IOException

data class PasswordReset(
        val email: String
) {
    /**
     * @return HTTP status code
     * @throws ErrorResponse If HTTP response status is above 400.
     * @throws IllegalStateException If request url is empty.
     * @throws IOException If network request failed, or response body can not be read, regardless of if response is successful or not.
     * @throws JsonSyntaxException If the content returned by API could not be parsed into valid JSON, regardless of if response is successful or not
     */
    suspend fun send(): Int {

        val response= GlobalScope.async {
            Fetch().post(NextApi.PASSWORD_RESET)
                    .noCache()
                    .body(this@PasswordReset)
                    .end()
        }.await()

        return response.code()
    }
}