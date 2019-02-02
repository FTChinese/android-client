package com.ft.ftchinese.models

import org.threeten.bp.LocalDate

data class Membership(
        @KTier
        val tier: Tier? = null,
        @KCycle
        val cycle: Cycle? = null,
        // ISO8601 format. Example: 2019-08-05
        @KDate
        val expireDate: LocalDate? = null
) {
    /**
     * Compare expireAt against now.
     * Return true if expireDate is before now,
     * or if expireDate is null.
     */
    val isExpired: Boolean
        get() = expireDate
                    ?.isBefore(LocalDate.now())
                    ?: true


    // Determine is renewal button is visible.
    // Only check subscribed user.
    val isRenewable: Boolean
        get() {

            if (expireDate == null || cycle == null) return false

            return expireDate
                    .isBefore(cycle.endDate(LocalDate.now()))
        }

    val isPaidMember: Boolean
        get() {
            return tier == Tier.STANDARD || tier == Tier.PREMIUM
        }


    // Use the combination of tier and billing cycle to uniquely identify this membership.
    // It is used as key to retrieve a price;
    // It is also used as the ITEM_ID for firebase's ADD_TO_CART event.
    val key: String
        get() = "${tier?.string()}_${cycle?.string()}"

//    val price: Double?
//        get() = prices[id]

    // Compare expireDate against another instance.
    // Pick whichever is later.
    fun isNewer(m: Membership): Boolean {
        if (expireDate == null && m.expireDate == null) {
            return false
        }

        if (m.expireDate == null) {
            return true
        }

        if (expireDate == null) {
            return false
        }

        return expireDate.isAfter(m.expireDate)
    }
}

