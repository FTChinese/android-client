package com.ft.ftchinese.model.subscription

import android.os.Parcelable
import com.ft.ftchinese.model.enums.Cycle
import com.ft.ftchinese.model.enums.Edition
import com.ft.ftchinese.model.enums.PriceSource
import com.ft.ftchinese.model.enums.Tier
import com.ft.ftchinese.model.fetch.KCycle
import com.ft.ftchinese.model.fetch.KTier
import com.ft.ftchinese.tracking.GAAction
import kotlinx.parcelize.Parcelize

/**
 * A plan for a product.
 */
@Parcelize
data class Plan(
    val id: String,
    val productId: String,
    val price: Double,
    @KTier
    val tier: Tier,
    @KCycle
    val cycle: Cycle,
    val description: String? = null,
    val currency: String = "cny", // Not from API
    val promotionOffer: Discount = Discount()
) : Parcelable {

    val edition: Edition
        get() = Edition(tier, cycle)

    val unifiedPrice: Price
        get() = Price(
            id = id,
            tier = tier,
            cycle = cycle,
            active = true,
            currency = currency,
            liveMode = true,
            nickname = null,
            productId = productId,
            source = PriceSource.Ftc,
            unitAmount = price,
            promotionOffer = promotionOffer,
        )

    // Deprecated.
    fun payableAmount(): Double {
        if (!promotionOffer.isValid()) {
            return price
        }

        return price - (promotionOffer.priceOff ?: 0.0)
    }

    fun getNamedKey(): String {
        return "${tier}_$cycle"
    }

    fun gaGAAction(): String {
        return when (tier) {
            Tier.STANDARD -> when (cycle) {
                Cycle.YEAR -> GAAction.BUY_STANDARD_YEAR
                Cycle.MONTH -> GAAction.BUY_STANDARD_MONTH
            }
            Tier.PREMIUM -> GAAction.BUY_PREMIUM
        }
    }
}

/**
 * PlanStore works as a in-memory cache of all plans.
 * This is kept for backward compatibility and many activities
 * use this to find out which plan a member is subscribed to.
 */
object PlanStore {
    // Will be updated once paywall data is fetched from server or cache.
    var plans = defaultPaywall.products.flatMap { it.plans }

    // Update plans from a list of products.
    // Used when paywall data is retrieved
    fun updateFromProduct(products: List<Product>) {
        plans = products.flatMap { it.plans }
    }

    fun set(plans: List<Plan>) {
        this.plans = plans
    }

    fun get(): List<Plan> {
        return plans
    }

    fun findById(id: String): Plan? {
        return plans.find { it.id == id }
    }

    fun findByEdition(e: Edition): Plan? {
        return plans.find {
            it.tier == e.tier && it.cycle == e.cycle
        }
    }

    /**
     * Use to find out what plan an existing member is subscribed to,
     * or what plan an order is created for.
     */
    fun find(tier: Tier, cycle: Cycle): Plan? {
        return plans.find {
            it.tier == tier && it.cycle == cycle
        }
    }
}
