package com.ft.ftchinese.model.subscription

import android.os.Parcelable
import com.ft.ftchinese.util.KCycle
import com.ft.ftchinese.util.KTier
import kotlinx.parcelize.Parcelize

@Parcelize
data class StripePrice(
    val id: String,
    @KTier
    val tier: Tier,
    @KCycle
    val cycle: Cycle,
    val active: Boolean = true,
    val currency: String,
    val liveMode: Boolean,
    val nickname: String? = null,
    val productId: String,
    val unitAmount: Int,
) : Parcelable {

    fun humanAmount(): Double {
        return (unitAmount / 100).toDouble()
    }
}

// In-memory cache of stripe prices. The data is also persisted to cache file.
// See FileCache.kt for cached file name
object StripePriceStore {
    var prices = listOf<StripePrice>()

    fun find(tier: Tier, cycle: Cycle): StripePrice? {
        return prices.find {
            it.tier == tier && it.cycle == cycle
        }
    }
}

