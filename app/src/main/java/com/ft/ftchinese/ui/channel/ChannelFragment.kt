package com.ft.ftchinese.ui.channel

import android.content.Context
import android.os.Bundle
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import androidx.core.os.bundleOf
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.ft.ftchinese.*
import com.ft.ftchinese.ui.base.ScopedFragment
import com.ft.ftchinese.ui.pay.grantPermission
import com.ft.ftchinese.ui.base.showException
import com.ft.ftchinese.ui.base.isNetworkConnected
import com.ft.ftchinese.model.*
import com.ft.ftchinese.model.reader.SessionManager
import com.ft.ftchinese.ui.ChromeClient
import com.ft.ftchinese.ui.article.ArticleActivity
import com.ft.ftchinese.ui.article.WVClient
import com.ft.ftchinese.util.*
import kotlinx.android.synthetic.main.fragment_channel.*
import kotlinx.android.synthetic.main.progress_bar.*
import kotlinx.coroutines.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.support.v4.toast

const val JS_INTERFACE_NAME = "Android"

/**
 * Hosted inside [TabPagerAdapter] or [ChannelActivity]
 * when used to handle pagination.
 */
@kotlinx.coroutines.ExperimentalCoroutinesApi
class ChannelFragment : ScopedFragment(),
        WVClient.OnWebViewInteractionListener,
        SwipeRefreshLayout.OnRefreshListener,
        AnkoLogger {

    /**
     * Meta data about current page: the tab's title, where to load data, etc.
     * Passed in when the fragment is created.
     */
    private var channelSource: ChannelSource? = null

    private lateinit var sessionManager: SessionManager
    private lateinit var cache: FileCache
    private lateinit var statsTracker: StatsTracker

    private lateinit var channelViewModel: ChannelViewModel

    private var articleList: List<Teaser>? = null
    private var channelMeta: ChannelMeta? = null

    private fun showProgress(value: Boolean) {
        if (swipe_refresh.isRefreshing) {
            toast(R.string.prompt_updated)
        }

        if (value) {
            progress_bar?.visibility = View.VISIBLE
        } else {
            progress_bar?.visibility = View.GONE
            swipe_refresh?.isRefreshing = false
        }
    }

    /**
     * Bind listeners here.
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)

        sessionManager = SessionManager.getInstance(context)
        cache = FileCache(context)
        statsTracker = StatsTracker.getInstance(context)

        info("onAttach finished")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Get metadata about current tab
        channelSource = arguments?.getParcelable(ARG_CHANNEL_SOURCE) ?: return

        info("Channel source: $channelSource")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        super.onCreateView(inflater, container, savedInstanceState)

        info("onCreateView finished")
        return inflater.inflate(R.layout.fragment_channel, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup swipe refresh listener
        swipe_refresh.setOnRefreshListener(this)

        // Configure web view.
        web_view.settings.apply {
            javaScriptEnabled = true
            loadsImagesAutomatically = true
            domStorageEnabled = true
            databaseEnabled = true
        }

        val wvClient = WVClient(activity)
        wvClient.setWVInteractionListener(this)

        web_view.apply {

            // Interact with JS.
            // See Page/Layouts/Page/SuperDataViewController.swift#viewDidLoad() how iOS inject js to web view.
            addJavascriptInterface(
                    this@ChannelFragment,
                    JS_INTERFACE_NAME
            )

            // Set WebViewClient to handle various links
            webViewClient = wvClient

            webChromeClient = ChromeClient()
        }

        // Setup back key behavior.
        web_view.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK && web_view.canGoBack()) {
                web_view.goBack()
                return@setOnKeyListener true
            }

            false
        }

        info("Initiating current page with data: $channelSource")
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        channelViewModel = ViewModelProvider(this, ChannelViewModelFactory(cache))
                .get(ChannelViewModel::class.java)

        channelViewModel.cacheFound.observe(this, Observer {

            info("Is cache found: $it")

            // If cache is found
            if (it) {
                info("Cache found. Update silently.")

                if (activity?.isNetworkConnected() != true) {
                    return@Observer
                }

                val chSrc = channelSource ?: return@Observer

                // Load data only. No rendering.
                channelViewModel.loadFromServer(
                        channelSource = chSrc,
                        shouldRender = false
                )
                return@Observer
            }

            // Cache not found.
            info("Cache not found")
            if (activity?.isNetworkConnected() != true) {
                showProgress(false)
                toast(R.string.prompt_no_network)
                return@Observer
            }

            // Fetch data from remote and cache only.
            val chSrc = channelSource ?: return@Observer

            // Load data and render.
            channelViewModel.loadFromServer(
                    channelSource = chSrc
            )
        })

        channelViewModel.renderResult.observe(this, Observer {

            val htmlResult = it ?: return@Observer

            if (htmlResult.error != null) {
                showProgress(false)
                toast(htmlResult.error)
                return@Observer
            }

            if (htmlResult.exception != null) {
                showProgress(false)
                activity?.showException(htmlResult.exception)
                return@Observer
            }

            val htmlStr = htmlResult.success ?: return@Observer

            load(htmlStr)
        })

        initLoading()
    }

    override fun onRefresh() {

        toast(R.string.refreshing_data)

        initLoading()
    }

    private fun initLoading() {
        val chSrc = channelSource
        if (chSrc == null) {
            showProgress(false)
            return
        }

        // Show progress bar upon loading content, regardless of
        if (!swipe_refresh.isRefreshing) {
            showProgress(true)
        }

        // For partial HTML, we need to crawl its content and render it with a template file to get the template HTML page.
        when (channelSource?.htmlType) {
            HTML_TYPE_FRAGMENT -> {
                info("initLoading: html fragment")

                if (swipe_refresh.isRefreshing) {
                    channelViewModel.loadFromServer(
                            channelSource = chSrc
                    )
                } else {
                    channelViewModel.loadFromCache(chSrc)
                }

            }
            // For complete HTML, load it directly into Web view.
            HTML_TYPE_COMPLETE -> {
                info("initLoading: web page")
                web_view.loadUrl(chSrc.normalizedUrl())
                if (swipe_refresh.isRefreshing) {
                    toast(R.string.prompt_updated)
                }
                showProgress(false)
            }
        }
    }

    private fun load(html: String) {
        if (BuildConfig.DEBUG) {
            info("Loading web page to web view")
        }
        web_view.loadDataWithBaseURL(WV_BASE_URL, html, "text/html", null, null)
        showProgress(false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        info("onSaveInstanceState finished")
    }

    override fun onPause() {
        super.onPause()
        cancel()
    }

    override fun onStop() {
        super.onStop()
        cancel()
    }

    /**
     * WVClient click pagination.
     */
    override fun onPagination(pageKey: String, pageNumber: String) {
        val pageMeta = channelSource ?: return

        val listPage = pageMeta.withPagination(pageKey, pageNumber)

        info("Open a pagination: $listPage")

        if (listPage.shouldReload) {
            info("Reloading a pagination $listPage")

            channelSource = listPage

            channelViewModel.loadFromCache(listPage)

        } else {
            info("Start a new activity for $listPage")
            ChannelActivity.start(activity, listPage)
        }
    }

    /**
     * Collection data when a web page is loaded into a web view.
     */
    @JavascriptInterface
    fun onPageLoaded(message: String) {

        info("Channel loaded: $message")

        val channelContent = json.parse<ChannelContent>(message) ?: return

        articleList = channelContent.sections[0].lists[0].items
        channelMeta = channelContent.meta

        cacheChannelData(message)
    }

    @JavascriptInterface
    fun onSelectItem(index: String) {
        info("select item: $index")

        val i = try {
            index.toInt()
        } catch (e: Exception) {
            -1
        }

        selectItem(i)
    }

    @JavascriptInterface
    fun onLoadedSponsors(message: String) {

//         See what the sponsor data is.
        if (BuildConfig.DEBUG) {
            val name = channelSource?.name

            if (name != null) {
                info("Saving js posted data for sponsors of $channelSource")
                launch {
                    cache.saveText("${name}_sponsors.json", message)
                }
            }
        }

        info("Loaded sponsors: $message")

        try {
            SponsorManager.sponsors = json.parseArray(message) ?: return
        } catch (e: Exception) {
            info(e)
        }
    }

    private fun cacheChannelData(data: String) {
        if (!BuildConfig.DEBUG) {
            return
        }

        val fileName = channelSource?.name ?: return

        launch(Dispatchers.IO) {
            cache.saveText("$fileName.json", data)
        }
    }

    // User click on an item of article list.
    private fun selectItem(index: Int) {
        if (index < 0) {
            return
        }

        val channelItem = articleList
                ?.getOrNull(index)
                ?: return
        
        channelItem.withMeta(channelMeta)

        info("Select item: $channelItem")

        /**
         * {
         * "id": "007000049",
         * "type": "column",
         * "headline": "徐瑾经济人" }
         * Canonical URL: http://www.ftchinese.com/channel/column.html
         * Content URL: https://api003.ftmailbox.com/column/007000049?webview=ftcapp&bodyonly=yes
         */
        if (channelItem.type == Teaser.TYPE_COLUMN) {
            openColumn(channelItem)
            return
        }

        val account = sessionManager.loadAccount()

        info("Is channel require membership: $channelSource")

        PaywallTracker.fromArticle(channelItem)

        // Check whether this article requires permission.
        val contentPerm = channelSource?.permission ?: channelItem.permission()

        info("Content permission: $contentPerm")

        val granted = activity?.grantPermission(account, contentPerm)

        if (granted == true) {
            openArticle(channelItem)
        }
    }

    private fun openColumn(item: Teaser) {
        val chSrc = ChannelSource(
                title = item.title,
                name = "${item.type}_${item.id}",
                contentUrl = item.contentUrl(),
                htmlType = HTML_TYPE_FRAGMENT
        )
        info("Open a column: $chSrc")

        ChannelActivity.start(context, chSrc)
    }

    private fun openArticle(teaser: Teaser) {
        info("Open article for an channel teaser: $teaser")

        when (teaser.type) {
            Teaser.TYPE_STORY,
            Teaser.TYPE_PREMIUM -> {
                ArticleActivity.start(activity, teaser)
            }
            Teaser.TYPE_INTERACTIVE -> {
                ArticleActivity.start(activity, teaser)
            }
            else -> {
                ArticleActivity.start(context, teaser)
            }
        }

        statsTracker.selectListItem(teaser)
    }

    companion object {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private const val ARG_CHANNEL_SOURCE = "arg_channel_source"

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        fun newInstance(channel: ChannelSource) = ChannelFragment().apply {
            arguments = bundleOf(ARG_CHANNEL_SOURCE to channel)
        }

    }
}


