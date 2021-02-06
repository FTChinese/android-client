package com.ft.ftchinese.ui.article

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import com.ft.ftchinese.BuildConfig
import com.ft.ftchinese.R
import com.ft.ftchinese.database.StarredArticle
import com.ft.ftchinese.model.content.FollowingManager
import com.ft.ftchinese.model.content.OpenGraphMeta
import com.ft.ftchinese.model.content.Teaser
import com.ft.ftchinese.model.reader.denyPermission
import com.ft.ftchinese.model.enums.Tier
import com.ft.ftchinese.repository.Config
import com.ft.ftchinese.store.SessionManager
import com.ft.ftchinese.ui.ChromeClient
import com.ft.ftchinese.store.FileCache
import com.ft.ftchinese.tracking.PaywallTracker
import com.ft.ftchinese.ui.base.ScopedFragment
import com.ft.ftchinese.ui.base.WVClient
import com.ft.ftchinese.ui.base.WVViewModel
import com.ft.ftchinese.ui.base.isConnected
import com.ft.ftchinese.ui.channel.JS_INTERFACE_NAME
import com.ft.ftchinese.model.fetch.json
import com.ft.ftchinese.viewmodel.ArticleViewModel
import com.ft.ftchinese.viewmodel.ArticleViewModelFactory
import com.ft.ftchinese.viewmodel.ReadArticleViewModel
import com.ft.ftchinese.viewmodel.StarArticleViewModel
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.support.v4.toast

private const val ARG_WEBPAGE_ARTICLE = "arg_web_article"

@kotlinx.coroutines.ExperimentalCoroutinesApi
class WebContentFragment : ScopedFragment(),
        AnkoLogger {

    private lateinit var articleViewModel: ArticleViewModel
    private lateinit var starViewModel: StarArticleViewModel
    private lateinit var readViewModel: ReadArticleViewModel
    private lateinit var wvViewModel: WVViewModel

    private lateinit var sessionManager: SessionManager
    private lateinit var cache: FileCache
    private lateinit var followingManager: FollowingManager

    private var webView: WebView? = null

    private var teaser: Teaser? = null

    private fun onOpenGraphEvaluated(result: String) {

        val og = try {
            json.parse<OpenGraphMeta>(result)
        } catch (e: Exception) {
            null
        }

        val article = mergeOpenGraph(og)

        if (BuildConfig.DEBUG) {
            info("Open graph evaluation result: $og")
            info("Loaded article: $article")
        }


        val denialReason = denyPermission(sessionManager.loadAccount(), article.permission())
        if (denialReason != null) {
            PaywallTracker.fromArticle(article.toChannelItem())

            activity?.finish()
            return
        }

        articleViewModel.webLoaded(article)
    }

    private fun mergeOpenGraph(og: OpenGraphMeta?): StarredArticle {

        return StarredArticle(
                id = if (teaser?.id.isNullOrBlank()) {
                    og?.extractId()
                } else {
                    teaser?.id
                } ?: "",
                type = if (teaser?.type == null) {
                    og?.extractType()
                } else {
                    teaser?.type?.toString()
                } ?: "",
                subType = teaser?.subType ?: "",
                title = if (teaser?.title.isNullOrBlank()) {
                    og?.title
                } else {
                    teaser?.title
                } ?: "",
                standfirst = og?.description ?: "",
                keywords = teaser?.tag ?: og?.keywords ?: "",
                imageUrl = og?.image ?: "",
                audioUrl = teaser?.audioUrl ?: "",
                radioUrl = teaser?.radioUrl ?: "",
                webUrl = teaser?.getCanonicalUrl() ?: og?.url ?: "",
                tier =  when {
                    og?.keywords?.contains("会员专享") == true -> Tier.STANDARD.toString()
                    og?.keywords?.contains("高端专享") == true -> Tier.PREMIUM.toString()
                    else -> ""
                },
                isWebpage = true
        )
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)

        sessionManager = SessionManager.getInstance(context)
        cache = FileCache(context)
        followingManager = FollowingManager.getInstance(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        teaser = arguments?.getParcelable<Teaser>(ARG_WEBPAGE_ARTICLE)

        info("Web content source: $teaser")
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_web_view, container, false)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        webView = activity?.findViewById(R.id.web_view)

        webView?.settings?.apply {
            javaScriptEnabled = true
            loadsImagesAutomatically = true
            domStorageEnabled = true
            databaseEnabled = true
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        articleViewModel = activity?.run {
            ViewModelProvider(
                    this,
                    ArticleViewModelFactory(cache, sessionManager.loadAccount()))
                    .get(ArticleViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        wvViewModel = activity?.run {
            ViewModelProvider(this).get(WVViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        starViewModel = activity?.run {
            ViewModelProvider(this).get(StarArticleViewModel::class.java)
        } ?: throw java.lang.Exception("Invalid Activity")

        readViewModel = activity?.run {
            ViewModelProvider(this).get(ReadArticleViewModel::class.java)
        } ?: throw Exception("Invalid Activity")


        wvViewModel.openGraphEvaluated.observe(viewLifecycleOwner, {
            onOpenGraphEvaluated(it)
        })

//        wvViewModel.pageFinished.observe(viewLifecycleOwner, {
//            articleViewModel.inProgress.value = !it
//        })

        webView?.apply {

            addJavascriptInterface(
                this@WebContentFragment,
                JS_INTERFACE_NAME
            )

            webViewClient = WVClient(requireContext(), wvViewModel)
            webChromeClient = ChromeClient()

            setOnKeyListener { _, keyCode, _ ->
                if (keyCode == KeyEvent.KEYCODE_BACK && webView?.canGoBack() == true) {
                    webView?.goBack()
                    return@setOnKeyListener true
                }
                false
            }
        }

        load()
    }

    private fun load() {
        if (context?.isConnected != true) {
            toast(R.string.prompt_no_network)
            return
        }

        articleViewModel.inProgress.value = true

        val t = teaser ?: return
        val url = Config.buildArticleSourceUrl(sessionManager.loadAccount(), t)
        info("Load content from: $url")


        webView?.loadUrl(url.toString())

        // Get the minimal information of an article.
        val article = teaser?.toStarredArticle() ?: return

        articleViewModel.webLoaded(article)
        // Tell parent to hide progress bar
        articleViewModel.inProgress.value = false
    }

    @JavascriptInterface
    fun onScrollTo(x: Int, y: Int) {
        info("Position: $x, $y")
        webView?.scrollTo(0, 0)
    }

    companion object {
        fun newInstance(channelItem: Teaser) = WebContentFragment().apply {
            arguments = bundleOf(
                    ARG_WEBPAGE_ARTICLE to channelItem
            )
        }
    }
}
