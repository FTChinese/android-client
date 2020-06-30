package com.ft.ftchinese.ui.pay

import android.app.Activity
import com.ft.ftchinese.R
import com.ft.ftchinese.model.reader.MemberStatus
import com.ft.ftchinese.model.reader.Account
import com.ft.ftchinese.model.reader.Permission
import com.ft.ftchinese.ui.login.LoginActivity
import org.jetbrains.anko.alert
import org.jetbrains.anko.appcompat.v7.Appcompat
import org.jetbrains.anko.toast

/**
 * Access control of member to content.
 * It first checks whether account membership is valid.
 * If valid, then checks access against content's required
 * access.
 */
@kotlinx.coroutines.ExperimentalCoroutinesApi
fun Activity.handlePermissionDenial(reason: MemberStatus, contentPerm: Permission) {
    when (reason) {
        MemberStatus.NotLoggedIn -> {
            toast(R.string.prompt_login_to_read)
            LoginActivity.startForResult(this)
        }
        MemberStatus.Empty -> {
            toast(R.string.prompt_member_only)
            PaywallActivity.start(this, contentPerm == Permission.PREMIUM)
        }
        MemberStatus.InactiveStripe -> {
            toast(R.string.stripe_not_active)
            MemberActivity.start(this)
        }
        MemberStatus.Expired -> {
            toast(R.string.prompt_membership_expired)
            PaywallActivity.start(this, contentPerm == Permission.PREMIUM)
        }
        MemberStatus.ActiveStandard -> {
            toast(R.string.prompt_upgrade_premium)
            MemberActivity.start(this)
        }
        // These two cases cannot happen.
        MemberStatus.ActivePremium,
        MemberStatus.Vip -> {

        }
    }
}
