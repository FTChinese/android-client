package com.ft.ftchinese.repository

import com.ft.ftchinese.model.fetch.Fetch
import com.ft.ftchinese.model.fetch.JSONResult
import com.ft.ftchinese.model.fetch.json
import com.ft.ftchinese.model.paywall.Paywall
import com.ft.ftchinese.model.price.Price

object PaywallClient {
    // Paywall data does not distinguish test or live account.
    private val baseUrl = "${Endpoint.subsBase()}/paywall"

    fun retrieve(): JSONResult<Paywall>? {
        val (_, body) = Fetch()
            .get(baseUrl)
            .endJsonText()

        if (body.isNullOrBlank()) {
            return null
        }

        val pw = json.parse<Paywall>(body)
        return if (pw == null) {
            null
        } else {
            JSONResult(pw, body)
        }
    }

    fun listPrices(): JSONResult<List<Price>>? {
        val (_, body) = Fetch()
            .get("$baseUrl/plans")
            .endJsonText()

        if (body.isNullOrBlank()) {
            return null
        }

        val plans = json.parseArray<Price>(body)
        return if (plans == null) {
            null
        } else {
            JSONResult(plans, body)
        }
    }
}
