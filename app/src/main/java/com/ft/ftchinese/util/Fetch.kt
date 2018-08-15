package com.ft.ftchinese.util

import android.util.Log
import com.ft.ftchinese.models.ErrorResponse
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import okhttp3.*
import okhttp3.Request
import java.io.IOException

val gson = Gson()

class Fetch {

    companion object {
        private const val TAG = "Fetch"

        private val client = OkHttpClient()

        private val jsonType = MediaType.parse("application/json")

        fun simpleGet(url: String): String? {

            return try {
                val request = Request.Builder()
                        .url(url)
                        .get()
                        .build()

                val response = client.newCall(request).execute()
                response.body()?.string()

            } catch (e: IllegalStateException) {
                // Request.build() error
                Log.w(TAG, "Empty url: $e")
                null
            } catch (e: IOException) {
                // Call.execute() error
                Log.w(TAG, "OkHttpClient execute/read body error: $e")
                null
            }
        }

        fun get(url: String, uuid: String? = null): Response? {
            val builder = Request.Builder()
                    .url(url)
                    .get()
            if (uuid != null) {
                builder.header("X-User-Id", uuid)
            }

            return execute(builder)
        }

        fun post(url: String, content: String, uuid: String? = null): Response? {
            val body = RequestBody.create(jsonType, content)
            val builder = Request.Builder()
                    .header("X-Client-Type", "android")
                    .header("X-Client-Version", "0.0.1")
                    .cacheControl(CacheControl.Builder().noCache().noStore().noTransform().build())
                    .url(url)
                    .post(body)

            if (uuid != null) {
                builder.header("X-User-Id", uuid)
            }

            return execute(builder)
        }

        /**
         * There are 3 possible results:
         * 1. Response is not null. This must be a normal response from API;
         * 2. Response is null. There must be something wrong with network;
         * 3. Throws ErrorResponse. API responds but the response is a restful error. The error message is wrapped into ErrorResponse.
         */
        private fun execute(builder: Request.Builder): Response? {
            val response = try {
                client.newCall(builder.build()).execute()
            } catch (e: IllegalStateException) {
                // Request.build() error
                Log.w(TAG, "Empty url: $e")
                return null
            } catch (e: IOException) {
                // Call.execute() error
                Log.w(TAG, "OkHttpClient execute error: $e")
                return null
            } catch (e: Exception) {
                Log.w(TAG, e.toString())
                return null
            }

            Log.i(TAG,"Response code: ${response.code()}")
            Log.i(TAG,"Response message: ${response.message()}")

            Log.i(TAG,"Is successful: ${response.isSuccessful}")

            if (response.isSuccessful) {
                return response
            }

            val errResp = try {
                val body = response.body()?.string()

                gson.fromJson<ErrorResponse>(body, ErrorResponse::class.java)
            } catch (e: IOException) {
                Log.w(TAG, "Read response error: $e")
                return null
            } catch (e: JsonSyntaxException) {
                Log.w(TAG, "JSON parse error: $e")
                return null
            }

            throw errResp
        }
    }
}