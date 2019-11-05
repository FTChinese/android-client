package com.ft.ftchinese.ui.article

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.ft.ftchinese.BuildConfig
import com.ft.ftchinese.R
import com.ft.ftchinese.database.StarredArticle
import com.ft.ftchinese.model.*
import com.ft.ftchinese.ui.base.isNetworkConnected
import com.ft.ftchinese.ui.pay.grantPermission
import com.ft.ftchinese.model.order.Tier
import com.ft.ftchinese.model.reader.SessionManager
import com.ft.ftchinese.ui.ChromeClient
import com.ft.ftchinese.util.FileCache
import com.ft.ftchinese.util.json
import kotlinx.android.synthetic.main.fragment_web_view.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.support.v4.toast

private const val ARG_WEBPAGE_ARTICLE = "arg_web_article"

@kotlinx.coroutines.ExperimentalCoroutinesApi
class WebContentFragment : Fragment(),
        WVClient.OnWebViewInteractionListener,
        AnkoLogger {

    private lateinit var articleViewModel: ArticleViewModel
    private lateinit var starViewModel: StarArticleViewModel
    private lateinit var readViewModel: ReadArticleViewModel

    private lateinit var sessionManager: SessionManager
    private lateinit var cache: FileCache
    private lateinit var followingManager: FollowingManager

    private var teaser: Teaser? = null

    override fun onOpenGraphEvaluated(result: String) {

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

        val account = sessionManager.loadAccount()

        val granted = activity?.grantPermission(account, article.permission())
        if (granted == null || granted == false) {
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
                type = if (teaser?.type.isNullOrBlank()) {
                    og?.extractType()
                } else {
                    teaser?.type
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        web_view.settings.apply {
            javaScriptEnabled = true
            loadsImagesAutomatically = true
            domStorageEnabled = true
            databaseEnabled = true
        }

        val wvClient = WVClient(activity)
        wvClient.setWVInteractionListener(this)

        web_view.apply {

            webViewClient = wvClient
            webChromeClient = ChromeClient()

            setOnKeyListener { _, keyCode, _ ->
                if (keyCode == KeyEvent.KEYCODE_BACK && web_view.canGoBack()) {
                    web_view.goBack()
                    return@setOnKeyListener true
                }
                false
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        articleViewModel = activity?.run {
            ViewModelProvider(
                    this,
                    ArticleViewModelFactory(cache, followingManager))
                    .get(ArticleViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        starViewModel = activity?.run {
            ViewModelProvider(this).get(StarArticleViewModel::class.java)
        } ?: throw java.lang.Exception("Invalid Activity")

        readViewModel = activity?.run {
            ViewModelProvider(this).get(ReadArticleViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

        load()
    }

    private fun load() {
        if (activity?.isNetworkConnected() != true) {
            toast(R.string.prompt_no_network)
            return
        }

        val url = buildUrl()
        info("Load content from: $url")


        web_view.loadUrl(url)

        // Get the minimal information of an article.
        val article = teaser?.toStarredArticle() ?: return

        articleViewModel.webLoaded(article)
    }

    private fun buildUrl(): String {
        // Use api webUrl first.
        val apiUrl = teaser?.contentUrl()
        if (!apiUrl.isNullOrBlank()) {
            return apiUrl
        }

        // Fallback to canonical webUrl
        val webUrl = teaser?.webUrl ?: return ""


        val url = Uri.parse(webUrl)

        val builder = try {
            url.buildUpon()
        } catch (e: Exception) {
            return ""
        }

        if (url.getQueryParameter("webview") == null) {
            builder.appendQueryParameter("webview", "ftcapp")
        }

        return builder.build().toString()
    }

    companion object {
        fun newInstance(channelItem: Teaser) = WebContentFragment().apply {
            arguments = bundleOf(
                    ARG_WEBPAGE_ARTICLE to channelItem
            )
        }
    }
}
