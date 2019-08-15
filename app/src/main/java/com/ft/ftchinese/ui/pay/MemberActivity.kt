package com.ft.ftchinese.ui.pay

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ft.ftchinese.R
import com.ft.ftchinese.databinding.ActivityMemberBinding
import com.ft.ftchinese.ui.base.ScopedAppActivity
import com.ft.ftchinese.ui.base.handleException
import com.ft.ftchinese.ui.base.isNetworkConnected
import com.ft.ftchinese.model.*
import com.ft.ftchinese.model.order.*
import com.ft.ftchinese.model.reader.Account
import com.ft.ftchinese.model.reader.SessionManager
import com.ft.ftchinese.ui.account.AccountViewModel
import com.ft.ftchinese.ui.account.StripeRetrievalResult
import com.ft.ftchinese.ui.login.AccountResult
import com.ft.ftchinese.util.RequestCode
import kotlinx.android.synthetic.main.activity_member.*
import kotlinx.android.synthetic.main.simple_toolbar.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.toast

@kotlinx.coroutines.ExperimentalCoroutinesApi
class MemberActivity : ScopedAppActivity(),
        SwipeRefreshLayout.OnRefreshListener,
        AnkoLogger {

    private lateinit var sessionManager: SessionManager
    private lateinit var accountViewModel: AccountViewModel
    private lateinit var binding: ActivityMemberBinding


    private fun stopRefresh() {
        swipe_refresh.isRefreshing = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_member)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_member)

        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
        }

        swipe_refresh.setOnRefreshListener(this)

        sessionManager = SessionManager.getInstance(this)
        accountViewModel = ViewModelProvider(this)
                .get(AccountViewModel::class.java)


        accountViewModel.stripeRetrievalResult.observe(this, Observer {
                onStripeSubRetrieved(it)
        })

        accountViewModel.accountRefreshed.observe(this, Observer {
            onAccountRefreshed(it)
        })

        initUI()
    }

    private fun initUI() {

        val account = sessionManager.loadAccount() ?: return

        val member = account.membership

        val visibleButtons = member.nextAction()

        val memberInfo = MemberInfo(
                tier = getString(member.tierStringRes),
                expireDate = member.localizedExpireDate(),
                autoRenewal = member.autoRenew ?: false,
                stripeStatus = if (member.payMethod == PayMethod.STRIPE && member.status != null) {
                    getString(member.status.stringRes)
                } else {
                    null
                },
                stripeInactive = member.stripeInactive(),
                remains = member.remainingDays().let {
                    when {
                        it == null -> null
                        it < 0 -> getString(R.string.member_status_expired)
                        it <= 7 -> getString(R.string.member_will_expire, it)
                        else -> null
                    }
                },
                showSubscribe = (visibleButtons and NextStep.Resubscribe.id) > 0,
                showRenew = (visibleButtons and NextStep.Renew.id) > 0,
                showUpgrade = (visibleButtons and NextStep.Upgrade.id) > 0
        )

        info("Member info for ui: $memberInfo")

        binding.member = memberInfo

        subscribe_btn.setOnClickListener {
            PaywallActivity.start(this)
            it.isEnabled = false
        }

        renew_btn.setOnClickListener {

            val plan = member.getPlan() ?: return@setOnClickListener
            // Tracking
            PaywallTracker.fromRenew()

            CheckOutActivity.startForResult(
                    activity = this,
                    requestCode = RequestCode.PAYMENT,
                    plan = plan
            )

            it.isEnabled = false
        }

        upgrade_btn.setOnClickListener {
            if (member.fromWxOrAli()) {

                UpgradeActivity.startForResult(this, RequestCode.PAYMENT)

            } else if (member.payMethod == PayMethod.STRIPE) {
                StripeSubActivity.startForResult(
                        this,
                        RequestCode.PAYMENT,
                        subsPlans.of(
                                Tier.PREMIUM,
                                Cycle.YEAR
                        )
                )
            }

            it.isEnabled = false
        }
    }

    /**
     * Refresh account.
     * Use different API endpoints depending on the login method.
     */
    override fun onRefresh() {
        if (!isNetworkConnected()) {
            stopRefresh()

            toast(R.string.prompt_no_network)
            return
        }

        val account = sessionManager.loadAccount()

        if (account == null) {
            stopRefresh()
            toast("Your account data not found!")
            return
        }


        if (account.membership.payMethod == PayMethod.STRIPE) {
            toast(R.string.refreshing_stripe_sub)

            accountViewModel.retrieveStripeSub(account)

            return
        }

        refreshAccount(account)
    }

    private fun refreshAccount(account: Account) {
        toast(R.string.refreshing_account)
        accountViewModel.refresh(account)
    }

    private fun onStripeSubRetrieved(result: StripeRetrievalResult?) {
        val account = sessionManager.loadAccount()
        if (account == null) {
            stopRefresh()
            return
        }

        if (result == null) {
            toast(R.string.stripe_refreshing_failed)
            refreshAccount(account)
            return
        }

        if (result.error != null) {
            toast(result.error)
            refreshAccount(account)
            return
        }

        if (result.exception != null) {
            handleException(result.exception)
            refreshAccount(account)
            return
        }

        // Even if user's subscription data is not refresh
        // (for example, API responded 404, in which case
        // subscribedResult.success is null), account still
        // needs to be refreshed.
        // So here we do not perform checks on subscribedResult == null.
        // It is just an indicator that network finished,
        // regardless of result.
        refreshAccount(account)
    }

    private fun onAccountRefreshed(accountResult: AccountResult?) {
        stopRefresh()

        if (accountResult == null) {
            return
        }

        if (accountResult.error != null) {
            toast(accountResult.error)
            return
        }

        if (accountResult.exception != null) {
            handleException(accountResult.exception)
            return
        }

        if (accountResult.success == null) {
            toast("Unknown error")
            return
        }

        toast(R.string.prompt_updated)

        sessionManager.saveAccount(accountResult.success)

        initUI()
    }

    override fun onResume() {
        super.onResume()
        subscribe_btn.isEnabled = true
        renew_btn.isEnabled = true
        upgrade_btn.isEnabled = true

        initUI()
    }


    /**
     * After [CheckOutActivity] finished, it sends activity result here.
     * This activity kills itself since the [CheckOutActivity]
     * will display a new [MemberActivity] to show updated
     * membership.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        info("onActivityResult requestCode: $requestCode, resultCode: $resultCode")

        when (requestCode) {
            RequestCode.PAYMENT -> {
                if (resultCode != Activity.RESULT_OK) {
                    return
                }

                finish()
            }
        }
    }

    // Create menus
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {

        menuInflater.inflate(R.menu.activity_member_menu, menu)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_orders -> {
            MyOrdersActivity.start(this)
            true
        }
        R.id.action_service -> {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:subscriber.service@ftchinese.com")
                putExtra(Intent.EXTRA_SUBJECT, "FT中文网会员订阅")
            }

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                toast(R.string.prompt_no_email_app)
            }

            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    companion object {
        fun start(context: Context?) {
            context?.startActivity(Intent(context, MemberActivity::class.java))
        }
    }
}
