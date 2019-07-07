package com.ft.ftchinese.ui.account

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.ft.ftchinese.R
import com.ft.ftchinese.base.ScopedAppActivity
import com.ft.ftchinese.base.handleException
import com.ft.ftchinese.base.isNetworkConnected
import com.ft.ftchinese.model.*
import com.ft.ftchinese.model.order.PlanPayable
import com.ft.ftchinese.model.order.Tier
import com.ft.ftchinese.model.order.subsPlans
import com.ft.ftchinese.ui.RowAdapter
import com.ft.ftchinese.ui.TableRow
import com.ft.ftchinese.ui.pay.CheckOutActivity
import com.ft.ftchinese.ui.pay.MyOrdersActivity
import com.ft.ftchinese.ui.pay.PaywallActivity
import com.ft.ftchinese.ui.pay.UpgradeActivity
import com.ft.ftchinese.util.RequestCode
import com.ft.ftchinese.util.formatLocalDate
import kotlinx.android.synthetic.main.activity_member.*
import kotlinx.android.synthetic.main.simple_toolbar.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.toast

@kotlinx.coroutines.ExperimentalCoroutinesApi
class MemberActivity : ScopedAppActivity(),
        SwipeRefreshLayout.OnRefreshListener,
        AnkoLogger {

    private lateinit var viewAdapter: RowAdapter
    private lateinit var sessionManager: SessionManager
    private lateinit var accountViewModel: AccountViewModel

    private fun stopRefresh() {
        swipe_refresh.isRefreshing = false
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

        toast(R.string.prompt_refreshing)

        accountViewModel.refresh(account)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_member)

        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
        }

        swipe_refresh.setOnRefreshListener(this)

        sessionManager = SessionManager.getInstance(this)
        accountViewModel = ViewModelProviders.of(this)
                .get(AccountViewModel::class.java)

        accountViewModel.accountRefreshed.observe(this, Observer {
            stopRefresh()

            val accountResult = it ?: return@Observer

            if (accountResult.error != null) {
                toast(accountResult.error)
                return@Observer
            }

            if (accountResult.exception != null) {
                handleException(accountResult.exception)
                return@Observer
            }

            if (accountResult.success == null) {
                toast("Unknown error")
                return@Observer
            }

            toast(R.string.prompt_updated)

            sessionManager.saveAccount(accountResult.success)

            updateUI(accountResult.success)
        })

        initUI()
    }

    override fun onResume() {
        super.onResume()
        resubscribe_btn.isEnabled = true
        renew_btn.isEnabled = true
        upgrade_btn.isEnabled = true
    }

    private fun initUI() {
        val account = sessionManager.loadAccount() ?: return

        viewAdapter = RowAdapter(buildRows(account))

        member_rv.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(this@MemberActivity)
            adapter = viewAdapter
        }

        showButton(account.membership)

        // For expired user
        resubscribe_btn.setOnClickListener {
            PaywallActivity.start(this)
            it.isEnabled = false
        }


        upgrade_btn.setOnClickListener {
            UpgradeActivity.start(this)
            it.isEnabled = false
        }

        val tier = account.membership.tier ?: return
        val cycle = account.membership.cycle ?: return

        // For renewal
        renew_btn.setOnClickListener {

            // Tracking
            PaywallTracker.fromRenew()

            val p = PlanPayable.fromPlan(subsPlans.of(tier, cycle))
            p.isRenew = true

            CheckOutActivity.startForResult(
                    activity = this,
                    requestCode = RequestCode.PAYMENT,
                    p = p
            )

            it.isEnabled = false
        }
    }

    // Hide all buttons on create.
    private fun hideButton() {
        resubscribe_btn.visibility = View.GONE
        renew_btn.visibility = View.GONE
        upgrade_btn.visibility = View.GONE
    }

    private fun showButton(m: Membership) {
        hideButton()

        when (m.getStatus()) {
            MemberStatus.INVALID -> {
                resubscribe_btn.visibility = View.VISIBLE
            }
            MemberStatus.EXPIRED -> {
                resubscribe_btn.visibility = View.VISIBLE
            }
            MemberStatus.RENEWABLE -> {
                renew_btn.visibility = View.VISIBLE
                upgrade_btn.visibility = if (m.allowUpgrade())
                    View.VISIBLE
                else View.GONE

            }
            MemberStatus.BEYOND_RENEW -> {
                upgrade_btn.visibility = if (m.allowUpgrade())
                    View.VISIBLE
                else View.GONE
            }
        }
    }

    private fun buildRows(account: Account): Array<TableRow> {

        val tierText = when (account.membership.tier) {
            Tier.STANDARD -> getString(R.string.tier_standard)
            Tier.PREMIUM -> getString(R.string.tier_premium)
            else -> if (account.isVip) getString(R.string.tier_vip) else getString(R.string.tier_free)

        }

        val endDateText = if (account.isVip) {
            getString(R.string.cycle_vip)
        } else {
            formatLocalDate(account.membership.expireDate)
        }

        val row1 = TableRow(
                header = getString(R.string.label_member_tier),
                data = tierText,
                isBold = true
        )

        val row2 = TableRow(
                header = getString(R.string.label_member_duration),

                data = endDateText ?: "",
                color = ContextCompat.getColor(this, R.color.colorClaret)
        )

        return arrayOf(row1, row2)
    }

    private fun updateUI(account: Account) {
        val rows = buildRows(account)

        viewAdapter.refreshData(rows)
        viewAdapter.notifyDataSetChanged()

        showButton(account.membership)
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

    override fun onOptionsItemSelected(item: MenuItem?) = when (item?.itemId) {
        R.id.action_orders -> {
            MyOrdersActivity.start(this)
            true
        }
        R.id.action_service -> {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, "subscriber.service@ftchinese.com")
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
