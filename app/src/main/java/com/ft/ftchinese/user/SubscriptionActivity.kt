package com.ft.ftchinese.user

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.ft.ftchinese.R
import com.ft.ftchinese.models.*
import com.ft.ftchinese.util.RequestCode
import com.ft.ftchinese.util.handleException
import com.ft.ftchinese.util.isNetworkConnected
import kotlinx.android.synthetic.main.activity_subscription.*
import kotlinx.android.synthetic.main.progress_bar.*
import kotlinx.android.synthetic.main.simple_toolbar.*
import kotlinx.coroutines.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.toast
import java.lang.Exception

/**
 * There are three entry point for SuscriptionActivity:
 * 1. User clicked Subscription button on sidebar or is trying to reading restricted contents;
 *
 * 2. Pay by Wechat.
 * This is how we handle Wechat payment while still being able to show user essential data and retrieve latest user account info:
 *
 * After user selected payment method of wechat from PaymentActivity, PaymentActivity notifies SubscriptionActivity to kill itself (why? see below);
 *
 * then user paid successfully;
 *
 * Wechat called WXPayEntryActivity which shows user payment result;
 *
 * then user either click the Done button or back button and WXPayEntryActivity is destroyed;
 *
 * the click action calls SubscriptionActivity -- that's why we killed SubscriptionActivity in previous step; otherwise user will see this activity two times. With Wechat's approach of using activity, it seems this is the only way to send any message from WXPayEntryActivity.
 *
 * then SubscriptionActivity should refresh user account.
 *
 * 3. Alipay
 *
 * Ali does not requires extra activity to be called. We can passed message from PaymentActivity back to SubscripionActivity. `onResult` should distinguish between Wechat pay and Alipay: the former ask the SubscriptionActivity to kill itself,  while the latter does not.
 *
 * When SubscriptionActivity received OK message from PaymentActvitiy, it should start retrieving user data from server.
 */
class SubscriptionActivity : AppCompatActivity(), SwipeRefreshLayout.OnRefreshListener, AnkoLogger {

    private var mSession: SessionManager? = null
    private var mJob: Job? = null

    private var isInProgress: Boolean = false
        set(value) {
            if (value) {
                progress_bar.visibility = View.VISIBLE
            } else {
                progress_bar.visibility = View.GONE
            }
        }

    override fun onRefresh() {
        updateAccount(true)
    }

    // Update user account data.
    // If user is manually refresh data, always use server data as the single source or truth;
    // If it is triggered by payment result, try to retreve user account data and compare it against the current one,
    // discard server data since Wechat or Alipay might not notified server the payment result.
    private fun updateAccount(isManualRefresh: Boolean) {
        if (!isNetworkConnected()) {
            swipe_refresh.isRefreshing = false
            isInProgress = false

            toast(R.string.prompt_no_network)

            return
        }



        val user = mSession?.loadUser()

        if (user == null) {
            swipe_refresh.isRefreshing = false
            isInProgress = false

            return
        }

        // If this action is not triggered by manually swipe refresh, show progress bar.
        if (!isManualRefresh) {
            isInProgress = true
        }

        toast(R.string.progress_refresh_account)

        mJob = GlobalScope.launch(Dispatchers.Main) {
            try {
                val remoteAccount = user.refresh()

                info("Retrieved user account data.")
                swipe_refresh.isRefreshing = false
                isInProgress = false

                toast(R.string.success_updated)

                if (isManualRefresh) {
                    // Save update user info

                    info("Save user account and update ui")
                    mSession?.saveUser(remoteAccount)

                    // Update ui.
                    updateUI(remoteAccount)
                } else {
                    // check remote server's membership against local one.
                    if (remoteAccount.membership.isNewer(user.membership)) {
                        info("Remote user account is fresh.")
                        mSession?.saveUser(remoteAccount)

                        updateUI(remoteAccount)
                    } else {

                        info("Local user account is fresh.")
                        updateUI(user)
                    }
                }
            } catch (resp: ErrorResponse) {
                info("$resp")

                swipe_refresh.isRefreshing = false
                isInProgress = false

                handleApiError(resp)

            } catch (e: Exception) {
                e.printStackTrace()
                swipe_refresh.isRefreshing = false
                isInProgress = false

                handleException(e)
            }
        }
    }

    private fun handleApiError(resp: ErrorResponse) {
        when (resp.statusCode) {
            400 -> {
                toast(R.string.api_bad_request)
            }
            // If request header does not contain X-User-Id
            401 -> {
                toast(R.string.api_unauthorized)
            }
            422 -> {
                toast(resp.message)
            }
            // If this account is not found. It's rare but possible. For example, use logged in at one place, then deleted account at another place.
            404 -> {
                toast(R.string.api_account_not_found)
            }
            else -> {
                toast(R.string.api_server_error)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription)

        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
        }

        swipe_refresh.setOnRefreshListener(this)

        mSession = SessionManager.getInstance(this)

        val user = mSession?.loadUser()

        if (user == null) {
            // Do not show membership box
            membership_container.visibility = View.GONE
            // Do not show renewal button
            renewal_button.visibility = View.GONE

            login_button.setOnClickListener {
                SignInActivity.start(this)
            }

            standard_year_button.setOnClickListener {
                SignInActivity.start(this)
            }

            standard_month_button.setOnClickListener {
                SignInActivity.start(this)
            }


            premium_button.setOnClickListener {
                SignInActivity.start(this)
            }

        } else {
            login_button.visibility = View.GONE

            updateUI(user)

            standard_year_button.setOnClickListener {
                PaymentActivity.startForResult(
                        activity = this,
                        requestCode = RequestCode.PAYMENT,
                        memberTier = Membership.TIER_STANDARD,
                        billingCycle = Membership.CYCLE_YEAR
                )
            }

            standard_month_button.setOnClickListener {
                PaymentActivity.startForResult(
                        activity = this,
                        requestCode = RequestCode.PAYMENT,
                        memberTier = Membership.TIER_STANDARD,
                        billingCycle = Membership.CYCLE_MONTH
                )
            }

            premium_button.setOnClickListener {
                PaymentActivity.startForResult(
                        activity = this,
                        requestCode = RequestCode.PAYMENT,
                        memberTier = Membership.TIER_PREMIUM,
                        billingCycle = Membership.CYCLE_YEAR
                )
            }
        }

        // If starter asked to update user account from server
        val shouldUpdate = intent.getBooleanExtra(EXTRA_SHOULD_UPDATE, false)

        if (shouldUpdate) {
            updateAccount(false)
        }
    }

    private fun updateUI(user: Account) {
        if (user.isVip) {
            tier_tv.text = getString(R.string.member_tier_vip)
            expiration_tv.text = getString(R.string.vip_duration)
            renewal_button.visibility = View.GONE

            return
        }

        val cycleText = when(user.membership.billingCycle) {
            Membership.CYCLE_YEAR -> getString(R.string.billing_cycle_year)
            Membership.CYCLE_MONTH -> getString(R.string.billing_cycle_month)
            else -> ""
        }

        tier_tv.text = when (user.membership.tier) {
            Membership.TIER_STANDARD -> getString(R.string.member_tier_standard) + "/" + cycleText
            Membership.TIER_PREMIUM -> getString(R.string.member_tier_premium) + "/" + cycleText
            else -> getString(R.string.member_tier_free)
        }

        expiration_tv.text = user.membership.expireDate

        if (user.membership.isRenewable) {
            // Only show renewal button for a member whose membership will expire.
            renewal_button.visibility = View.VISIBLE

            renewal_button.setOnClickListener {
                PaymentActivity.startForResult(this, RequestCode.PAYMENT, user.membership.tier, user.membership.billingCycle)
            }
        } else {
            renewal_button.visibility = View.GONE
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        info("onActivityResult requestCode: $requestCode, resultCode: $resultCode")

        // When user selected wechat pay in PaymentActivity, it kills itself and tells MembershipActivity to finish too.
        // Otherwise after user clicked done button in WXPayEntryActivity, MembershipActivity will be started again, and user see this activity two times after clicked back button.
        if (requestCode == RequestCode.PAYMENT) {
            if (resultCode == Activity.RESULT_OK) {

                val paymentMethod = data?.getIntExtra(EXTRA_PAYMENT_METHOD, 0)
                when (paymentMethod) {
                    Subscription.PAYMENT_METHOD_WX -> {
                        // If user selected wechat pay, kill this activity.
                        // After paid, WXPayEntryActivity will call this activity again.
                        finish()
                    }
                    Subscription.PAYMENT_METHOD_ALI -> {
                        // update ui
                        updateAccount(isManualRefresh = false)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        mJob?.cancel()
    }

    companion object {
        private const val EXTRA_SHOULD_UPDATE = "should_update_account"
        fun start(context: Context?, shouldUpdate: Boolean = false) {
            val intent = Intent(context, SubscriptionActivity::class.java).apply {
                putExtra(EXTRA_SHOULD_UPDATE, shouldUpdate)
            }

            context?.startActivity(intent)
        }
    }
}