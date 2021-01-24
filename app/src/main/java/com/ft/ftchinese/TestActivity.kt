package com.ft.ftchinese

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.commit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import com.ft.ftchinese.databinding.ActivityTestBinding
import com.ft.ftchinese.model.content.ArticleType
import com.ft.ftchinese.model.content.Teaser
import com.ft.ftchinese.model.order.*
import com.ft.ftchinese.model.reader.*
import com.ft.ftchinese.model.subscription.*
import com.ft.ftchinese.service.VerifySubsWorker
import com.ft.ftchinese.store.OrderManager
import com.ft.ftchinese.store.ServiceAcceptance
import com.ft.ftchinese.store.SessionManager
import com.ft.ftchinese.ui.account.LinkPreviewActivity
import com.ft.ftchinese.ui.account.UnlinkActivity
import com.ft.ftchinese.ui.article.*
import com.ft.ftchinese.ui.base.ScopedAppActivity
import com.ft.ftchinese.ui.about.LegalDetailsFragment
import com.ft.ftchinese.ui.login.WxExpireDialogFragment
import com.ft.ftchinese.ui.pay.LatestOrderActivity
import com.ft.ftchinese.ui.share.SocialShareFragment
import com.ft.ftchinese.wxapi.WXEntryActivity
import com.ft.ftchinese.wxapi.WXPayEntryActivity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.tasks.OnCompleteListener
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.messaging.FirebaseMessaging
import org.jetbrains.anko.*
import org.jetbrains.anko.appcompat.v7.Appcompat
import org.threeten.bp.LocalDate
import org.threeten.bp.ZonedDateTime

@kotlinx.coroutines.ExperimentalCoroutinesApi
class TestActivity : ScopedAppActivity(), AnkoLogger {

    private lateinit var binding: ActivityTestBinding
    private lateinit var orderManger: OrderManager
    private var errorDialog: Dialog? = null
    private lateinit var sessionManager: SessionManager
    private lateinit var workManager: WorkManager

    private val freeUser = Account(
        id = "0c726d53-2ec3-41e2-aa8c-5c4b0e23876a",
        unionId = null,
        stripeId = null,
        userName = "Free Edition",
        email = "free@example.org",
        isVerified = false,
        avatarUrl = null,
        loginMethod = LoginMethod.EMAIL,
        wechat = Wechat(
            nickname = null,
            avatarUrl = null
        ),
        membership = Membership()
    )

    private val stdUser = Account(
        id = "0c726d53-2ec3-41e2-aa8c-5c4b0e23876a",
        unionId = null,
        stripeId = null,
        userName = "Standard Edition",
        email = "standard@example.org",
        isVerified = false,
        avatarUrl = null,
        loginMethod = LoginMethod.EMAIL,
        wechat = Wechat(
            nickname = null,
            avatarUrl = null
        ),
        membership = Membership(
            tier = Tier.STANDARD,
            cycle = Cycle.YEAR,
            expireDate = LocalDate.now().plusYears(1),
            payMethod = PayMethod.ALIPAY,
            autoRenew = false,
            status = null,
            vip = false
        )
    )

    private val prmUser = Account(
        id = "0c726d53-2ec3-41e2-aa8c-5c4b0e23876a",
        unionId = null,
        stripeId = null,
        userName = "Premium Edition",
        email = "premium@example.org",
        isVerified = false,
        avatarUrl = null,
        loginMethod = LoginMethod.EMAIL,
        wechat = Wechat(
            nickname = null,
            avatarUrl = null
        ),
        membership = Membership(
            tier = Tier.PREMIUM,
            cycle = Cycle.YEAR,
            expireDate = LocalDate.now().plusYears(1),
            payMethod = PayMethod.ALIPAY,
            autoRenew = false,
            status = null,
            vip = false
        )
    )

    private val vipUser = Account(
        id = "0c726d53-2ec3-41e2-aa8c-5c4b0e23876a",
        unionId = null,
        stripeId = null,
        userName = "Premium Edition",
        email = "premium@example.org",
        isVerified = false,
        avatarUrl = null,
        loginMethod = LoginMethod.EMAIL,
        wechat = Wechat(
            nickname = null,
            avatarUrl = null
        ),
        membership = Membership(
            tier = null,
            cycle = null,
            expireDate = null,
            payMethod = null,
            autoRenew = false,
            status = null,
            vip = true
        )
    )

    private val stripeUser = Account(
        id = "0c726d53-2ec3-41e2-aa8c-5c4b0e23876a",
        unionId = null,
        stripeId = "cus_abc",
        userName = "Standard Edition",
        email = "standard@example.org",
        isVerified = false,
        avatarUrl = null,
        loginMethod = LoginMethod.EMAIL,
        wechat = Wechat(
            nickname = null,
            avatarUrl = null
        ),
        membership = Membership(
            tier = Tier.STANDARD,
            cycle = Cycle.YEAR,
            expireDate = LocalDate.now().plusYears(1),
            payMethod = PayMethod.STRIPE,
            autoRenew = true,
            status = StripeSubStatus.Canceled,
            vip = false
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_test)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
        }

        sessionManager = SessionManager.getInstance(this)
        orderManger = OrderManager.getInstance(this)
        workManager = WorkManager.getInstance(this)

        binding.oneTimeWorkManager.setOnClickListener {
            val request = OneTimeWorkRequestBuilder<VerifySubsWorker>().build()
            workManager.enqueue(request)
        }

        val uploadWorkRequest: WorkRequest = OneTimeWorkRequestBuilder<VerifySubsWorker>()
            .build()

        WorkManager.getInstance(this).enqueue(uploadWorkRequest)

        binding.createChannel.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channelId = getString(R.string.news_notification_channel_id)
                val channelName = getString(R.string.news_notification_channel_name)
                val channelDesc = getString(R.string.news_notification_channel_description)

                val channel = NotificationChannel(
                        channelId,
                        channelName,
                        NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = channelDesc
                }

                val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)

                toast("$channelName created")
            }
        }

        binding.createNotification.setOnClickListener {
            createNotification()
        }

        intent.extras?.let {
            for (key in it.keySet()) {
                val value = intent.extras?.get(key)
                info("Key: $key Value: $value")
            }
        }

        // Subscribe a topic.
        binding.btnSubscribeTopic.setOnClickListener {
            info("Subscribing to news topic")

            FirebaseMessaging
                    .getInstance()
                    .subscribeToTopic("news")
                    .addOnCompleteListener {
                        if (!it.isSuccessful) {
                            alert(Appcompat, "Subscription failed").show()
                        } else {
                            alert(Appcompat, "Subscribed").show()
                        }
                    }
        }

        // Retrieve registration token.
        binding.btnLogToken.setOnClickListener {
            FirebaseInstanceId
                    .getInstance()
                    .instanceId
                    .addOnCompleteListener(OnCompleteListener {
                        if (!it.isSuccessful) {
                            info("getInstanceId failed", it.exception)
                            alert(Appcompat, "Failed to get token due to: ${it.exception?.message}", "Failed").show()
                            return@OnCompleteListener
                        }

                        val token = it.result?.token

                        info("Token $token")
                        alert(Appcompat, "Token retrieved: $token", "Success").show()
                    })
        }

        binding.checkGoogleApi.setOnClickListener {
            if (checkPlayServices()) {
                alert(Appcompat, "Play service available").show()
            } else {
                alert(Appcompat, "Play service not available").show()
            }
        }

        binding.downloadGooglePlay.setOnClickListener {
            val googleApiAvailability = GoogleApiAvailability.getInstance()

            googleApiAvailability.makeGooglePlayServicesAvailable(this)
                    .addOnSuccessListener {
                        toast("Success")
                    }
                    .addOnFailureListener {
                        toast("Failed")
                    }
        }

        // Setup bottom bar menu
        binding.bottomBar.replaceMenu(R.menu.activity_test_menu)
        binding.bottomBar.setOnMenuItemClickListener {
            onBottomMenuItemClicked(it)

            true
        }

        binding.btnFreeUser.setOnClickListener {
            sessionManager.saveAccount(freeUser)
        }

        binding.btnStandardUser.setOnClickListener {
            sessionManager.saveAccount(stdUser)
        }

        binding.btnPremiumUser.setOnClickListener {
            sessionManager.saveAccount(prmUser)
        }

        binding.btnVipUser.setOnClickListener {
            sessionManager.saveAccount(vipUser)
        }

        binding.btnStripeUser.setOnClickListener {
            sessionManager.saveAccount(stripeUser)
        }

        binding.bottomDialog.setOnClickListener {
            SocialShareFragment().show(supportFragmentManager, "TestBottomDialog")
        }

        binding.btnService.setOnClickListener {
            supportFragmentManager.commit {
                add(android.R.id.content, LegalDetailsFragment.newInstance())
            }
        }

        binding.btnLatestOrder.setOnClickListener {
            LatestOrderActivity.start(this)
        }

        binding.clearServiceAccepted.setOnClickListener {
            ServiceAcceptance.getInstance(this).clear()
        }

        val layout = LinearLayoutManager(this)
        binding.rvHtml.apply {
            layoutManager = layout
            adapter = LyricsAdapter(listOf(
                "<p>Rich countries are set to take on at least $17tn of extra public debt as they battle the economic consequences of the pandemic, according to the OECD, as sharp drops in tax revenues are expected to dwarf the stimulus measures put in place to battle the disease. </p>",
                "<p>Across the OECD club of rich countries, average government financial liabilities are expected to rise from 109 per cent of gross domestic product to more than 137 per cent this year, leaving many with public debt burdens similar to the current level in Italy.</p>",
                "<p>Additional debt of that scale would amount to a minimum of $13,000 per person across the 1.3bn people that live in OECD member countries. Debt levels could rise even further if the economic recovery from the pandemic is slower than many economists hope. </p>",
                "<p>Randall Kroszner, of the Chicago Booth School of Business and a former Federal Reserve governor, said the situation raised questions about the long-term sustainability of high levels of public and private debt.</p>",
                "<p>“We have to face the hard reality we’re not going to have a V-shaped recovery,” he said.</p>",
                "<p>The OECD said that public debt among its members rose by 28 percentage points of GDP in the financial crisis of 2008-09, totalling $17tn. “For 2020, the economic impact of the Covid-19 pandemic is expected to be worse than the great financial crisis,” it said.</p>",
                "<p>Although many governments have introduced additional fiscal measures this year ranging from 1 per cent of GDP in France and Spain to 6 per cent in the US, they are likely to be outpaced by the rise in public debt because tax revenues tend to fall even faster than economic activity in a deep recession, according to the OECD.</p>",
                "<p>A decade ago, fashionable economic thinking suggested that beyond 90 per cent of GDP, government debt levels became unsustainable. Although most economists do not now believe there is such a clear limit, many still believe that allowing public debt to build up ever higher would threaten to undermine private sector spending, creating a drag on growth. </p>",
                "<p>Rising debt levels will become a problem in future, Angel Gurría, OECD secretary-general, has warned, although he said that countries should not worry about their fiscal positions now in the middle of the crisis.</p>",
                "<p>“We are going to be heavy on the wing because we are trying to fly and we were already carrying a lot of debt and now we are adding more,” he said. </p>",
                "<p>As a result, many more countries are set to face a similar economic environment to that experienced by Japan since its financial bubble burst in the early 1990s. Concern about government debt and deficits has been a defining feature of Japan’s political economy ever since, with debt eventually stabilising at about 240 per cent of GDP under current prime minister Shinzo Abe.</p>",
                "<p>Many politicians and business leaders are alarmed by the fresh spending packages to tackle the pandemic in Japan.</p>",
                "<p>“Our economic strategy is using a considerable amount of money, and honestly speaking it’s going to be a big fiscal problem in the future,” said Hiroaki Nakanishi, executive chairman of Hitachi and head of the Keidanren business lobby, in a recent interview with the Financial Times. “I have no good plan. Until the economy is properly back on its feet, I don’t think there is any sensible answer.”</p>"
            ))
        }
    }

    private fun createNotification() {
        val intent = Intent(this, ArticleActivity::class.java).apply {
            putExtra(EXTRA_ARTICLE_TEASER, Teaser(
                    id = "001083331",
                    type = ArticleType.Story,
                    title = "波司登遭做空机构质疑 股价暴跌"
            ))
        }

        val pendingIntent: PendingIntent? = TaskStackBuilder.create(this).run {
            addNextIntentWithParentStack(intent)
            getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        val builder = NotificationCompat.Builder(
                this,
                getString(R.string.news_notification_channel_id))
                .setSmallIcon(R.drawable.logo_round)
                .setContentTitle("波司登遭做空机构质疑 股价暴跌")
//                .setContentText("")
                .setStyle(NotificationCompat.BigTextStyle()
                        .bigText("周一，波司登的股价下跌了24.8%，随后宣布停牌。此前，做空机构Bonitas Research对波司登的收入和利润提出了质疑。"))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            notify(1, builder.build())
        }
    }

    private fun checkPlayServices(): Boolean {
        val googleApiAvailability = GoogleApiAvailability.getInstance()
        val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(this)

        if (resultCode != ConnectionResult.SUCCESS) {
            if (googleApiAvailability.isUserResolvableError(resultCode)) {
                if (errorDialog == null) {
                    errorDialog = googleApiAvailability.getErrorDialog(this, resultCode, 2404)
                    errorDialog?.setCancelable(false)
                }

                if (errorDialog?.isShowing == false) {
                    errorDialog?.show()
                }
            }
        }

        return resultCode == ConnectionResult.SUCCESS
    }


    private fun onBottomMenuItemClicked(item: MenuItem) {
        when (item.itemId) {
            R.id.menu_test_wechat_expired -> {
                WxExpireDialogFragment().show(supportFragmentManager, "WxExpiredDialog")
            }
            R.id.menu_test_anko_alert -> {
                alert(Appcompat,"Anko alert", "Test") {
                    positiveButton("OK") {

                    }
                    negativeButton("Cancel") {

                    }
                }.show()

            }
            R.id.menu_show_unlink_activity -> {
                UnlinkActivity.startForResult(this)
            }
            R.id.menu_wxpay_activity -> {
                // Create a mock order.
                // This order actually exists, since you
                // Wechat does not provide a fake test
                // mechanism.
                orderManger.save(Order(
                        id = "FTEFD5E11FDFA709E0",
                        tier = Tier.PREMIUM,
                        cycle = Cycle.YEAR,
                        cycleCount = 1,
                        extraDays = 1,
                        amount = 1998.00,
                        usageType = OrderKind.CREATE,
                        payMethod = PayMethod.WXPAY,
                        createdAt = ZonedDateTime.now()
                ))
                WXPayEntryActivity.start(this)
            }
            R.id.menu_wx_oauth -> {
                WXEntryActivity.start(this)
            }
            R.id.menu_link_preview -> {
                LinkPreviewActivity.startForResult(this, Account(
                        id = "",
                        unionId = "AgqiTngwsasF6r8m83jOdhZRolJ9",
                        stripeId = null,
                        userName = null,
                        email = "",
                        isVerified = false,
                        avatarUrl = null,
                        loginMethod = LoginMethod.WECHAT,
                        wechat = Wechat(
                                nickname = "aliquam_quas_minima",
                                avatarUrl = "https://randomuser.me/api/portraits/thumb/men/17.jpg"
                        ),
                        membership = Membership(
                                tier = Tier.STANDARD,
                                cycle = Cycle.YEAR,
                                expireDate = LocalDate.now().plusDays(30),
                                payMethod = PayMethod.WXPAY,
                                autoRenew = false,
                                status = null,
                                vip =  false
                        )
                ))
            }
            R.id.menu_clear_idempotency -> {
                Idempotency.getInstance(this).clear()
                toast("Cleared")
            }
            R.id.menu_stripe_subscription -> {

//                StripeSubActivity.startTest(this, findPlan(Tier.STANDARD, Cycle.YEAR))
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.article_top_bar, menu)

        return true
    }

    // Show system share.
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_share -> {
                startActivity(Intent.createChooser(Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, "FT中文网 - test")
                    type = "text/plain"
                }, "分享"))
            }
        }


        return super.onOptionsItemSelected(item)
    }

    companion object {
        @JvmStatic
        fun start(context: Context) {
            context.startActivity(Intent(context, TestActivity::class.java))
        }

        @JvmStatic
        fun newIntent(context: Context): Intent {
            return Intent(context, TestActivity::class.java)
        }
    }
}
