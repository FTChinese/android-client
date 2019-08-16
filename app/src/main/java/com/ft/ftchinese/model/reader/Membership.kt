package com.ft.ftchinese.model.reader

import android.os.Parcelable
import com.ft.ftchinese.R
import com.ft.ftchinese.model.MemberStatus
import com.ft.ftchinese.model.NextStep
import com.ft.ftchinese.model.Permission
import com.ft.ftchinese.model.VisibleButtons
import com.ft.ftchinese.model.order.*
import com.ft.ftchinese.util.*
import kotlinx.android.parcel.Parcelize
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatter
import org.threeten.bp.temporal.ChronoUnit

@Parcelize
data class Membership(
        val id: String? = null,
        @KTier
        val tier: Tier? = null,
        @KCycle
        val cycle: Cycle? = null,

        // ISO8601 format. Example: 2019-08-05
        @KDate
        val expireDate: LocalDate? = null,
        @KPayMethod
        val payMethod: PayMethod? = null,

        // If autoRenew is true, ignore expireDate.
        val autoRenew: Boolean? = false,
        @KStripeSubStatus
        val status: StripeSubStatus? = null,
        val vip: Boolean = false
) : Parcelable {

    val tierStringRes: Int
        get() = when {
            vip -> R.string.tier_vip
            tier != null -> tier
                    .stringRes
            else -> R.string.tier_free
        }

    fun withSubscription(s: Subscription): Membership {
        return Membership(
                id = id,
                tier = s.tier,
                cycle = s.cycle,
                expireDate = s.endDate,
                payMethod = s.payMethod,
                autoRenew = autoRenew,
                status = status
        )
    }

    fun getPlan(): Plan? {
        if (tier == null) {
            return null
        }

        if (cycle == null) {
            return null
        }

        return subsPlans.of(tier, cycle)
    }

    fun remainingDays(): Long? {
        if (expireDate == null) {
            return null
        }

        if (autoRenew == true) {
            return null
        }

        return LocalDate.now().until(expireDate, ChronoUnit.DAYS)
    }

    fun localizedExpireDate(): String {
        if (vip) {
            return "无限期"
        }

        if (expireDate == null) {
            return ""
        }
        return expireDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    /**
     * Determine whether to display a warning message
     * on membership info.
     */
    fun isActiveStripe(): Boolean {
        return payMethod == PayMethod.STRIPE && status == StripeSubStatus.Active
    }

    fun stripeInactive(): Boolean {
        return payMethod == PayMethod.STRIPE && status != StripeSubStatus.Active
    }

    fun fromWxOrAli(): Boolean {
        // The first condition is used for backward compatibility.
        return (tier != null && payMethod == null) || payMethod == PayMethod.ALIPAY || payMethod == PayMethod.WXPAY
    }

    /**
     * Checks whether membership expired.
     * For stripe customer, if expire date is past and
     * authRenew is true, take it as not expired.
     */
    fun expired(): Boolean {
        if (vip) {
            return false
        }

        if (expireDate == null) {
            return true
        }
        return expireDate.isBefore(LocalDate.now()) && (autoRenew == false)
    }

    /**
     * Determines whether the Renew button should be visible.
     * This is only applicable to alipay or wechat pay.
     * Status of a membership when its expire date falls into
     * various period.
     *         today              3 years later
     * --------- | -------------- | ---------
     * expired      renew/upgrade   upgrade only for standard
     */
    private fun permitRenewal(): Boolean {
        if (expireDate == null) {
            return false
        }

        if (autoRenew == true) {
            return false
        }

        if (!fromWxOrAli()) {
            return false
        }

        val today = LocalDate.now()
        val threeYearsLater = today.plusYears(3)

        return expireDate.isBefore(threeYearsLater)
    }

    /**
     * getPermission calculates a membership's (including
     * zero value) permission to access content.
     * It uses binary operation to get the combined access
     * rights of a reader.
     * Each position in a binary number represents a unique
     * permission.
     * Returns an int representing the permission, and
     * membership status used to tell user what went wrong.
     */
    fun getPermission(): Pair<Int, MemberStatus?> {
        if (vip) {
            return Pair(
                    Permission.FREE.id or Permission.STANDARD.id or Permission.PREMIUM.id,
                    MemberStatus.Vip
            )
        }

        if (tier == null) {
            return Pair(
                    Permission.FREE.id,
                    MemberStatus.Empty
            )
        }

        if (payMethod == PayMethod.STRIPE && status != StripeSubStatus.Active) {
            return Pair(
                    Permission.FREE.id,
                    MemberStatus.InactiveStripe
            )
        }

        if (expired()) {
            return Pair(
                    Permission.FREE.id,
                    MemberStatus.Expired
            )
        }

        if (tier == Tier.STANDARD) {
            return Pair(
                    Permission.FREE.id or Permission.STANDARD.id,
                    MemberStatus.ActiveStandard
            )
        }

        if (tier == Tier.PREMIUM) {
            return Pair(
                    Permission.FREE.id or Permission.STANDARD.id or Permission.PREMIUM.id,
                    MemberStatus.ActivePremium
            )
        }

        return Pair(Permission.FREE.id, null)
    }

    /**
     * Determines what a membership can do next:
     * re-subscribe, renew or upgrade, or any of the combination
     */
    private fun nextAction(): Int {
        if (vip) {
            return NextStep.None.id
        }

        if (tier == null) {
            return NextStep.Resubscribe.id
        }

        if (status?.shouldResubscribe() == true) {
            return NextStep.Resubscribe.id
        }

        if (expired()) {
            return NextStep.Resubscribe.id
        }

        // Not expired, or auto renew.

        // Do not show renew button for auto renew.
        if (autoRenew == true) {
            return when (tier) {
                Tier.STANDARD -> NextStep.Upgrade.id
                Tier.PREMIUM -> NextStep.None.id
            }
        }

        // Membership is not auto renewal, and not expired.
        if (permitRenewal()) {
            return when (tier) {
                Tier.STANDARD -> NextStep.Renew.id or NextStep.Upgrade.id
                Tier.PREMIUM -> NextStep.Renew.id
            }
        }

        // Renewal is not allowed. The only options left is upgrade.
        if (tier == Tier.STANDARD) {
            return NextStep.Upgrade.id
        }

        return NextStep.None.id
    }

    /**
     * Determines which buttons are visible on MemberActivity
     * to persuade user to take next action: any or combination
     * of resubscribe,
     */
    fun nextVisibleButtons(): VisibleButtons {
        val actions = nextAction()

        return VisibleButtons(
                showSubscribe = (actions and NextStep.Resubscribe.id) > 0,
                showRenew = (actions and NextStep.Renew.id) > 0,
                showUpgrade = (actions and NextStep.Upgrade.id) > 0
        )
    }

    // Determine how user is using CheckOutActivity.
    fun subType(plan: Plan?): OrderUsage? {
        if (plan == null) {
            return null
        }

        if (vip) {
            return null
        }

        if (tier == null) {
            return OrderUsage.CREATE
        }

        if (status?.shouldResubscribe() == true) {
            return OrderUsage.CREATE
        }

        if (expired()) {
            return OrderUsage.CREATE
        }

        if (tier == plan.tier) {
            return OrderUsage.RENEW
        }

        if (tier == Tier.STANDARD && plan.tier == Tier.PREMIUM) {
            return OrderUsage.UPGRADE
        }

        return null
    }

    /**
     * Determines whether the stripe payment method should
     * be visible.
     */
    fun permitStripe(): Boolean {
        if (tier == null) {
            return true
        }

        if (status?.shouldResubscribe() == true) {
            return true
        }

        // expired and not auto renew.
        if (expired()) {
            return true
        }

        return false
    }

    /**
     * Compare local membership against remote.
     */
    fun useRemote(remote: Membership): Boolean {
        if (expireDate == null && remote.expireDate == null) {
            return false
        }

        if (remote.expireDate == null) {
            return false
        }

        if (expireDate == null) {
            return true
        }

        // Renewal
        if (tier == remote.tier) {
            return remote.expireDate.isAfter(expireDate)
        }

        // For upgrading we canno compare the expire date.
        if (tier == Tier.STANDARD && remote.tier
         == Tier.PREMIUM) {
            return true
        }

        return false
    }
}
