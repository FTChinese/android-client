package com.ft.ftchinese.models

import com.ft.ftchinese.util.Fetch
import com.ft.ftchinese.util.NextApi
import com.ft.ftchinese.util.json

data class FtcUser(
        val id: String
) {
   fun fetchAccount(): Account? {
       val(_, body) = Fetch().get(NextApi.ACCOUNT)
               .noCache()
               .setUserId(id)
               .responseApi()

       return if (body == null) {
           null
       } else {
           json.parse<Account>(body)
       }
   }


    fun updateEmail(email: String): Boolean {
        val (resp, _) = Fetch().patch(NextApi.UPDATE_EMAIL)
                .noCache()
                .setUserId(id)
                .jsonBody(json.toJsonString(mapOf("email" to email)))
                .responseApi()

        return resp.code() == 204
    }

    fun updateUserName(name: String): Boolean {
        val (resp, _) = Fetch().patch(NextApi.UPDATE_USER_NAME)
                .noCache()
                .setUserId(id)
                .jsonBody(json.toJsonString(mapOf("userName" to name)))
                .responseApi()

        return resp.code() == 204
    }

    fun updatePassword(pw: Passwords): Boolean {
        val(resp, _) = Fetch().patch(NextApi.UPDATE_PASSWORD)
                .noCache()
                .setUserId(id)
                .jsonBody(json.toJsonString(pw))
                .responseApi()

        return resp.code() == 204
    }

    fun requestVerification(): Boolean {
        val (resp, _) = Fetch().post(NextApi.REQUEST_VERIFICATION)
                .noCache()
                .setClient()
                .setUserId(id)
                .body()
                .responseApi()

        return resp.code() == 204
    }
}