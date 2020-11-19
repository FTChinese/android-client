package com.ft.ftchinese.repository

import com.ft.ftchinese.model.AppRelease
import com.ft.ftchinese.util.json
import org.jetbrains.anko.AnkoLogger

object ReleaseRepo : AnkoLogger {

    fun getRelease(versionName: String): Pair<AppRelease?, String>? {
        val (_, body) = Fetch()
                .setAppId()
                .get("${NextApi.releaseOf}/${normalizeVersionName(versionName)}")
                .endJsonText()

        return if (body == null) {
            null
        } else {
            Pair(json.parse(body), body)
        }
    }

    fun getLatest(): Pair<AppRelease?, String>? {
        val (_, body) = Fetch()
            .setAppId()
            .get(NextApi.latestRelease)
            .endJsonText()

        return if (body == null) {
            null
        } else {
            Pair(json.parse(body), body)
        }
    }

    private fun normalizeVersionName(versionName: String): String {
        val parts = versionName.split("-")
        if (parts.isEmpty()) {
            return versionName
        }

        val name = parts[0]
        if (name.startsWith("v")) {
            return name
        }

        return "v$name"
    }
}
