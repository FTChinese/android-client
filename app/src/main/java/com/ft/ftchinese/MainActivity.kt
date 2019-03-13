package com.ft.ftchinese

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsIntent
import androidx.fragment.app.FragmentPagerAdapter
import androidx.core.view.GravityCompat
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import android.view.*
import android.webkit.WebView
import android.widget.ImageView
import android.widget.TextView
import com.beust.klaxon.Klaxon
import com.ft.ftchinese.models.*
import com.ft.ftchinese.splash.Schedule
import com.ft.ftchinese.splash.SplashScreenManager
import com.ft.ftchinese.splash.splashScheduleFile
import com.ft.ftchinese.user.*
import com.ft.ftchinese.util.*
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.google.firebase.analytics.FirebaseAnalytics
import com.tencent.mm.opensdk.openapi.IWXAPI
import com.tencent.mm.opensdk.openapi.WXAPIFactory
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.toast
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.io.ByteArrayInputStream
import kotlin.Exception

/**
 * MainActivity implements ChannelFragment.OnFragmentInteractionListener to interact with TabLayout.
 */
class MainActivity : AppCompatActivity(),
        TabLayout.OnTabSelectedListener,
        WxExpireDialogFragment.WxExpireDialogListener,
        AnkoLogger {

    private var bottomDialog: BottomSheetDialog? = null
    private var mBackKeyPressed = false

    private var mExitJob: Job? = null
    private var mShowAdJob: Job? = null
    private var mDownloadAdJob: Job? = null

    private var mAdScheduleJob: Job? = null

    private var mNewsAdapter: TabPagerAdapter? = null
    private var mEnglishAdapter: TabPagerAdapter? = null
    private var mFtaAdapter: TabPagerAdapter? = null
    private var mVideoAdapter: TabPagerAdapter? = null
    private var mMyftPagerAdapter: MyftPagerAdapter? = null

    private var mChannelPages: Array<ChannelSource>? = null

    private lateinit var cache: FileCache

    private lateinit var firebaseAnalytics: FirebaseAnalytics
    private lateinit var sessionManager: SessionManager
    private lateinit var splashManager: SplashScreenManager
    private lateinit var wxApi: IWXAPI

    // Cache UI
    private var drawerHeaderTitle: TextView? = null
    private var drawerHeaderImage: ImageView? = null
    private var menuItemAccount: MenuItem? = null
    private var menuItemSubs: MenuItem? = null
    private var menuItemMySubs: MenuItem? = null

    /**
     * Update UI depending on user's login/logout state
     */
    private fun updateSessionUI() {
        val account = sessionManager.loadAccount()

        if (account == null) {
            drawerHeaderTitle?.text = getString(R.string.nav_not_logged_in)
            drawerHeaderImage?.setImageResource(R.drawable.ic_account_circle_black_24dp)

            // show signin/signup
            drawer_nav.menu
                    .setGroupVisible(R.id.drawer_group_sign_in_up, true)

            // Do not show account
            menuItemAccount?.isVisible = false
            // Show subscription
            menuItemSubs?.isVisible = true
            // Do not show my subscription
            menuItemMySubs?.isVisible = false
            return
        }

        drawerHeaderTitle?.text = account.displayName
        showAvatar(drawerHeaderImage, account.wechat)


        // Hide signin/signup
        drawer_nav.menu
                .setGroupVisible(R.id.drawer_group_sign_in_up, false)
        // Show account
        menuItemAccount?.isVisible = true

        // If user is not logged in, isMember always false.
        val isMember = account.isMember

        // Show subscription if user is a member; otherwise
        // hide it
        menuItemSubs?.isVisible = !isMember
        // Show my subscription if user is a member; otherwise hide it.
        menuItemMySubs?.isVisible = isMember
    }

    private fun showAvatar(imageView: ImageView?, wechat: Wechat) {
        if (imageView == null) {
            return
        }

        val drawable = cache.readDrawable(wechat.avatarName)
        if (drawable != null) {
            imageView.setImageDrawable(drawable)
        }

        if (wechat.avatarUrl == null) {
            return
        }

        GlobalScope.launch(Dispatchers.Main) {
            val bytes = withContext(Dispatchers.IO) {
                wechat.downloadAvatar(filesDir)
            } ?: return@launch

            imageView.setImageDrawable(
                    Drawable.createFromStream(
                            ByteArrayInputStream(bytes),
                            wechat.avatarName
                    )
            )
        }
    }

    override fun onWxAuthExpired() {}

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setTheme(R.style.Origami)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        displayLogo()

        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        cache = FileCache(this)
        splashManager = SplashScreenManager(this)

        // Show advertisement
        // Keep a reference the coroutine in case user exit at this moment
        mShowAdJob = GlobalScope.launch(Dispatchers.Main) {
            showAd()
        }

        firebaseAnalytics = FirebaseAnalytics.getInstance(this)

        // Register Wechat id
        wxApi = WXAPIFactory.createWXAPI(this, BuildConfig.WX_SUBS_APPID, false)
        wxApi.registerApp(BuildConfig.WX_SUBS_APPID)

        sessionManager = SessionManager.getInstance(this)

        val menu = drawer_nav.menu
        val headerView = drawer_nav.getHeaderView(0)
        drawerHeaderTitle = headerView.findViewById(R.id.nav_header_title)
        drawerHeaderImage = headerView.findViewById(R.id.nav_header_image)

        menuItemAccount = menu.findItem(R.id.action_account)
        menuItemSubs = menu.findItem(R.id.action_subscription)
        menuItemMySubs = menu.findItem(R.id.action_my_subs)

        // Set ViewPager adapter
        setupHome()

        // Link ViewPager and TabLayout
        tab_layout.setupWithViewPager(view_pager)
        tab_layout.addOnTabSelectedListener(this)

        setupBottomNav()

        setupDrawer()

        updateSessionUI()

        prepareSplash()
    }

    private fun setupBottomNav() {
        bottom_nav.setOnNavigationItemSelectedListener {
            info("Selected bottom nav item ${it.title}")

            when (it.itemId) {
                R.id.nav_news -> {
                    setupHome()

                    displayLogo()
                }

                R.id.nav_english -> {
                    if (mEnglishAdapter == null) {
                        mEnglishAdapter = TabPagerAdapter(Navigation.englishPages, supportFragmentManager)
                    }
                    view_pager.adapter = mEnglishAdapter
                    mChannelPages = Navigation.englishPages

                    displayTitle(R.string.nav_english)
                }

                R.id.nav_ftacademy -> {
                    if (mFtaAdapter == null) {
                        mFtaAdapter = TabPagerAdapter(Navigation.ftaPages, supportFragmentManager)
                    }
                    view_pager.adapter = mFtaAdapter
                    mChannelPages = Navigation.ftaPages

                    displayTitle(R.string.nav_ftacademy)
                }

                R.id.nav_video -> {
                    if (mVideoAdapter == null) {
                        mVideoAdapter = TabPagerAdapter(Navigation.videoPages, supportFragmentManager)
                    }
                    view_pager.adapter = mVideoAdapter
                    mChannelPages = Navigation.videoPages

                    displayTitle(R.string.nav_video)
                }

                R.id.nav_myft -> {
                    if (mMyftPagerAdapter == null) {
                        mMyftPagerAdapter = MyftPagerAdapter(supportFragmentManager)
                    }
                    view_pager.adapter = mMyftPagerAdapter
                    mChannelPages = null

                    displayTitle(R.string.nav_myft)
                }
            }
            true
        }
    }

    private fun setupDrawer() {
        val toggle = ActionBarDrawerToggle(
                this,
                drawer_layout,
                toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        // Set a listener that will be notified when a menu item is selected.
        drawer_nav.setNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.action_login ->  CredentialsActivity.startForResult(this)
                R.id.action_account -> AccountActivity.start(this)
                R.id.action_subscription -> SubscriptionActivity.start(this)
                R.id.action_my_subs -> MySubsActivity.start(this)
                R.id.action_about -> AboutUsActivity.start(this)
                R.id.action_feedback -> feedbackEmail()
                R.id.action_settings -> SettingsActivity.start(this)
            }

            drawer_layout.closeDrawer(GravityCompat.START)

            true
        }

        // Set listener on the title text inside drawer's header view
        drawer_nav.getHeaderView(0)
                ?.findViewById<TextView>(R.id.nav_header_title)
                ?.setOnClickListener {
                    if (!sessionManager.isLoggedIn()) {
                        CredentialsActivity.startForResult(this)
                        return@setOnClickListener
                    }

                    // Setup bottom dialog
                    if (bottomDialog == null) {
                        bottomDialog = BottomSheetDialog(this)
                        bottomDialog?.setContentView(R.layout.fragment_logout)
                    }

                    bottomDialog?.findViewById<TextView>(R.id.action_logout)?.setOnClickListener{
                        logout()

                        bottomDialog?.dismiss()
                        toast("账号已登出")
                    }

                    bottomDialog?.show()
                }
    }

    private fun logout() {
        sessionManager.logout()
        updateSessionUI()
    }

    private fun setupHome() {
        if (mNewsAdapter == null) {
            mNewsAdapter = TabPagerAdapter(Navigation.newsPages, supportFragmentManager)
        }
        view_pager.adapter = mNewsAdapter
        mChannelPages = Navigation.newsPages
    }

    private fun displayLogo() {
        supportActionBar?.setDisplayUseLogoEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setLogo(R.drawable.ic_menu_masthead)
    }

    private fun displayTitle(title: Int) {
        supportActionBar?.setDisplayUseLogoEnabled(false)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.setTitle(title)
    }

    private fun showSystemUI() {
        supportActionBar?.show()
        root_container.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN


        checkWxSession()
    }

    /**
     * Check whether wechat session has expired.
     * Wechat refresh token expires after 30 days.
     */
    private fun checkWxSession() {
        val account = sessionManager.loadAccount() ?: return
        if (account.loginMethod != LoginMethod.WECHAT) {
            return
        }

        val wxSession = sessionManager.loadWxSession() ?: return
        if (wxSession.isExpired) {
            logout()
            WxExpireDialogFragment().show(supportFragmentManager, "WxExpireDialog")
        }
    }

    private fun prepareSplash() {
        val tier = sessionManager
                .loadAccount()
                ?.membership
                ?.tier

        info("Prepare splash screen for next round")

        mDownloadAdJob = GlobalScope.launch(Dispatchers.IO) {

            if (!cache.exists(splashScheduleFile)) {
                info("Splash schedule is not found. Fetch from remote.")
                // Cache is not found.
                val schedule = fetchAdSchedule()
                        ?: return@launch

                splashManager.prepareNextRound(schedule, tier)
                return@launch
            }

            val body = cache.loadText(splashScheduleFile)

            info("Splash schedule cache found")

            // If cache is not loaded, fetch remote data.
            if (body == null) {
                info("Splash cache is found but empty")

                val schedule = fetchAdSchedule() ?: return@launch

                splashManager.prepareNextRound(schedule, tier)
                return@launch
            }

            // Read local cache
            val schedule = try {
                Klaxon().parse<Schedule>(body)
            } catch (e: Exception) {
                null
            }

            info("Splash schedule: $schedule")

            splashManager.prepareNextRound(schedule, tier)

            // After cache is used, update cache.
            info("Updating splash cache")
            fetchAdSchedule()

        }
    }

    private fun fetchAdSchedule(): Schedule? {
        if (!isActiveNetworkWifi()) {
            return null
        }

        return try {
            val body = Fetch()
                    .get(LAUNCH_SCHEDULE_URL)
                    .responseString()
                    ?: return null

            cache.saveText(splashScheduleFile, body)
            Klaxon().parse<Schedule>(body)
        } catch (e: Exception) {
            info(e)
            null
        }
    }

    private suspend fun showAd() {

        // Pick an ad based on its probability distribution factor.
        val screenAd = splashManager.load()

        if (screenAd == null || !screenAd.isToday()) {
            showSystemUI()
            return
        }

        info("Splash screen ad: $screenAd")

        val imageFileName = screenAd.imageName

        // Check if the required ad image exists.
        if (imageFileName.isBlank() || !cache.exists(imageFileName)) {
            info("Ad image ${screenAd.imageName} not found")
            showSystemUI()
            return
        }

        // Read this article on how inflate works:
        // https://www.bignerdranch.com/blog/understanding-androids-layoutinflater-inflate/
        val adView = View.inflate(this, R.layout.ad_view, null)

        info("Starting to show ad. Hide system ui.")
        supportActionBar?.hide()
        adView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LOW_PROFILE or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

        info("Added ad view")
        root_container.addView(adView)

        val adImage = adView.findViewById<ImageView>(R.id.ad_image)
        val adTimer = adView.findViewById<TextView>(R.id.ad_timer)

        val bundle = Bundle().apply {
            putString(FirebaseAnalytics.Param.CREATIVE_NAME, screenAd.title)
            putString(FirebaseAnalytics.Param.CREATIVE_SLOT, AdSlot.APP_OPEN)
        }

        adTimer.setOnClickListener {
            root_container.removeView(adView)
            showSystemUI()
            mShowAdJob?.cancel()
            info("Skipped ads")

            // Log user skipping advertisement action.
            firebaseAnalytics.logEvent(FtcEvent.AD_SKIP, bundle)
        }

        adImage.setOnClickListener {
            val customTabsInt = CustomTabsIntent.Builder().build()
            customTabsInt.launchUrl(this, Uri.parse(screenAd.linkUrl))
            root_container.removeView(adView)
            showSystemUI()
            mShowAdJob?.cancel()

            firebaseAnalytics.logEvent(FtcEvent.AD_CLICK, bundle)
            info("Clicked ads")
        }

        // Read image and show it.
        val drawable = withContext(Dispatchers.IO) {
            cache.readDrawable(imageFileName)
        }

        adImage.setImageDrawable(drawable)

        adTimer.visibility = View.VISIBLE
        info("Show timer")

        // send impressions in background.
        GlobalScope.launch(Dispatchers.IO) {
            try {
                screenAd.sendImpression()
            } catch (e: Exception) {
                info("Send launch screen impression failed: ${e.message}")
            }
        }

        firebaseAnalytics.logEvent(FtcEvent.AD_VIEWED, bundle)

        for (i in 5 downTo 1) {
            adTimer.text = getString(R.string.prompt_ad_timer, i)
            delay(1000)
        }

        root_container.removeView(adView)

        showSystemUI()
    }

    override fun onRestart() {
        super.onRestart()
        info("onRestart finished")
    }

    override fun onStart() {
        super.onStart()
        info("onStart finished")
        updateSessionUI()


    }

    /**
     * Deal with the cases that an activity launched by this activity exits.
     * For example, the LoginActvity will automatically finish when it successfully logged in,
     * and then it should inform the MainActivity to update UI for a logged in mUser.
     * `requestCode` is used to identify who this result cam from. We are using it to identify if the result came from LoginActivity or SignupActivity.
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        info("onActivityResult: requestCode $requestCode, resultCode $resultCode")

        when (requestCode) {
            // If the result come from SignIn or SignUp, update UI to show mUser login state.
            RequestCode.SIGN_IN, RequestCode.SIGN_UP -> {

                if (resultCode != Activity.RESULT_OK) {
                    return
                }

                toast(R.string.prompt_logged_in)
                updateSessionUI()
            }
        }
    }

    override fun onResume() {
        super.onResume()

        val bundle = Bundle().apply {

            val now = ZonedDateTime.now().format(DateTimeFormatter.ISO_INSTANT)
            info("APP_OPEN event: $now")

            putString(FirebaseAnalytics.Param.SUCCESS, now)
        }

        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.APP_OPEN, bundle)

        info("onResume finished")
    }

    override fun onPause() {
        super.onPause()
        info("onPause finished")

        mShowAdJob?.cancel()
        mDownloadAdJob?.cancel()
        mAdScheduleJob?.cancel()
    }

    override fun onStop() {
        super.onStop()
        info("onStop finished")

        mShowAdJob?.cancel()
        mDownloadAdJob?.cancel()
        mAdScheduleJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()

        mShowAdJob?.cancel()
        mDownloadAdJob?.cancel()
        mAdScheduleJob?.cancel()

        mShowAdJob = null
        mDownloadAdJob = null
        mAdScheduleJob = null
    }

    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            doubleClickToExit()
        }
    }

    private fun doubleClickToExit() {
        // If this is the first time user clicked back button, the first part the will executed.
        if (!mBackKeyPressed) {
            toast("再按一次退出程序")
            mBackKeyPressed = true

            // Delay for 2 seconds.
            // If user did not touch the back button within 2 seconds, mBackKeyPressed will be changed back gto false.
            // If user touch the back button within 2 seconds, `if` condition will be false, this part will not be executed.
            mExitJob = GlobalScope.launch {
                delay(2000)
                mBackKeyPressed = false
            }
        } else {
            // If user clicked back button two times within 2 seconds, this part will be executed.
            mExitJob?.cancel()
            finish()
        }
    }

    /**
     * Create menus on toolbar
     */
//    override fun onCreateOptionsMenu(menu: Menu): Boolean {
//        // Inflate the menu; this adds mFollows to the action bar if it is present.
//
//        menuInflater.inflate(R.menu.activity_main_search, menu)
//
//        val expandListener = object : MenuItem.OnActionExpandListener {
//            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
//                info("Menu item action collapse")
//                return true
//            }
//
//            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
//                info("Menu item action expand")
//                return true
//            }
//        }
//
//        // Configure action view.
//        // See https://developer.android.com/training/appbar/action-views
//        val searchItem = menu.findItem(R.id.action_search)
//        searchItem.setOnActionExpandListener(expandListener)
//
//        val searchView = searchItem.actionView as SearchView
//
//        // Handle activity_main_search. See
//        // guide https://developer.android.com/guide/topics/search/
//        // API https://developer.android.com/reference/android/support/v7/widget/SearchView
//
//        return super.onCreateOptionsMenu(menu)
//    }

    /**
     * Respond to menu item on the toolbar being selected
     */
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
            R.id.action_search -> {
                info("Clicked activity_main_search")
                super.onOptionsItemSelected(item)
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }

    /**
     * Implementation of TabLayout.OnTabSelectedListener
     * Tab index starts from 0
     */
    override fun onTabSelected(tab: TabLayout.Tab?) {
        info("Tab selected: ${tab?.position}")
        val position = tab?.position ?: return
        val pages = mChannelPages ?: return

        info("View item list event: ${pages[position]}")

        firebaseAnalytics.logEvent(FirebaseAnalytics.Event.VIEW_ITEM_LIST, Bundle().apply {
            putString(FirebaseAnalytics.Param.ITEM_CATEGORY, pages[position].title)
        })
    }

    override fun onTabReselected(tab: TabLayout.Tab?) {
        info("Tab reselected: ${tab?.position}")
    }

    override fun onTabUnselected(tab: TabLayout.Tab?) {
        info("Tab unselected: ${tab?.position}")
    }

    private fun feedbackEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, "ftchinese.feedback@gmail.com")
            putExtra(Intent.EXTRA_SUBJECT, "Feedback on FTC Android App")
        }

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            toast(R.string.prompt_no_email_app)
        }
    }

    /**
     * A [FragmentPagerAdapter] that returns a fragment corresponding to
     * one of the sections/tabs/mPages.
     */
    inner class TabPagerAdapter(private var mPages: Array<ChannelSource>, fm: androidx.fragment.app.FragmentManager) : androidx.fragment.app.FragmentStatePagerAdapter(fm) {

        override fun getItem(position: Int): androidx.fragment.app.Fragment {
            info("TabPagerAdapter getItem $position. Data passed to ChannelFragment: ${mPages[position]}")
            return ChannelFragment.newInstance(mPages[position])
        }

        override fun getCount(): Int {
            return mPages.size
        }

        override fun getPageTitle(position: Int): CharSequence? {
            return mPages[position].title
        }
    }

//    inner class MyftPagerAdapter(private val pages: Array<MyftTab>, fm: FragmentManager) : FragmentStatePagerAdapter(fm) {
//
//        override fun getItem(position: Int): androidx.fragment.app.Fragment {
//            val page = pages[position]
//            return if (page.id == MyftTab.FOLLOWING) {
//                FollowingFragment.newInstance()
//            } else {
//                MyftFragment.newInstance(page.id)
//            }
//        }
//
//        override fun getCount(): Int {
//            return pages.size
//        }
//
//        override fun getPageTitle(position: Int): CharSequence? {
//            return pages[position].title
//        }
//    }
}


