package com.ft.ftchinese.model.invoice

import com.beust.klaxon.Json
import com.ft.ftchinese.model.enums.*
import com.ft.ftchinese.model.fetch.*
import com.ft.ftchinese.model.price.Edition
import org.threeten.bp.ZonedDateTime

data class Invoice(
    val id: String,
    val compoundId: String,
    @KTier
    val tier: Tier,
    @KCycle
    val cycle: Cycle,
    val years: Int = 0,
    val months: Int = 0,
    val days: Int = 0,
    @KAddOnSource
    val addOnSource: AddOnSource? = null,
    val appleTxId: String? = null,
    @Json(ignored = true)
    val currency: String = "cny",
    var orderId: String? = null,
    @KOrderKind
    val orderKind: OrderKind? = null,
    val paidAmount: Double,
    @KPayMethod
    val payMethod: PayMethod? = null,
    val priceId: String? = null,
    val stripeSubsId: String? = null,
    @KDateTime
    val createdUtc: ZonedDateTime? = null,
    @KDateTime
    val consumedUtc: ZonedDateTime? = null,
    @KDateTime
    val startUtc: ZonedDateTime? = null,
    @KDateTime
    val endUtc: ZonedDateTime? = null,
    @KDateTime
    val carriedOverUtc: ZonedDateTime? = null,
) {

    val edition: Edition
        get() = Edition(tier, cycle)

    fun withOrderId(id: String): Invoice {
        orderId = id
        return this
    }

    val totalDays: Int
        get() = years * 366 + months * 31 + days

    fun toAddOn(): AddOn {
        return when (tier) {
            Tier.STANDARD -> AddOn(
                standardAddOn = totalDays,
                premiumAddOn = 0,
            )
            Tier.PREMIUM -> AddOn(
                standardAddOn = 0,
                premiumAddOn = totalDays,
            )
        }
    }
}
