package com.ft.ftchinese

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.CookieSyncManager
import com.ft.ftchinese.database.ArticleStore
import com.ft.ftchinese.models.ArticleDetail
import com.ft.ftchinese.models.ChannelItem
import com.ft.ftchinese.models.User
import com.ft.ftchinese.util.Store
import com.ft.ftchinese.util.gson
import kotlinx.android.synthetic.main.activity_content.*
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.info
import org.jetbrains.anko.toast
import java.net.CookieManager

/**
 * StoryActivity is used to show a story whose has a JSON api on server.
 * The remote JSON is fetched and concatenated with local HTML template in `res/raw/story.html`.
 * For those articles that do not have a JSON api, do not use this activity. Load the a web page directly into web view.
 */
class StoryActivity : AbsContentActivity() {

    override val articleWebUrl: String
        get() = channelItem?.canonicalUrl ?: ""

    override val articleTitle: String
        get() = channelItem?.headline ?: ""

    override val articleStandfirst: String
        get() = channelItem?.standfirst ?: ""

    private var currentLanguage: Int = ChannelItem.LANGUAGE_CN

    // Hold metadata on where and how to find data for this page.
    private var channelItem: ChannelItem? = null
    private var job: Job? = null

    private var template: String? = null
    private var articleDetail: ArticleDetail? = null

    private var isStarring: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Meta data about current article
        val itemData = intent.getStringExtra(EXTRA_CHANNEL_ITEM)

        channelItem = gson.fromJson(itemData, ChannelItem::class.java)

        action_favourite.setOnClickListener {
            isStarring = !isStarring

            updateFavouriteIcon()

            if (isStarring) {
                ArticleStore.getInstance(this).addStarred(channelItem)
            } else {
                ArticleStore.getInstance(this).deleteStarred(channelItem)
            }
        }

        titlebar_cn.setOnClickListener {
            currentLanguage = ChannelItem.LANGUAGE_CN
            init()
        }

        titlebar_en.setOnClickListener {
            currentLanguage = ChannelItem.LANGUAGE_EN
            init()
        }

        titlebar_bi.setOnClickListener {
            currentLanguage = ChannelItem.LANGUAGE_BI
            init()
        }

        init()

        info("onCreate finished")
    }

    private fun updateFavouriteIcon() {
        action_favourite.setImageResource(if (isStarring) R.drawable.ic_favorite_teal_24dp else R.drawable.ic_favorite_border_teal_24dp )
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
    }

    override fun onRefresh() {
        super.onRefresh()

        // This is based on the assumption that `temaplte` is not null.
        // Since refresh action should definitely happen after init() is called, `template` should never be null by this point.
        job = launch(UI) {
            if (template == null) {
                template = readTemplate()
            }
            useRemoteJson()
        }
    }

    override fun init() {
        info("Initializing content")

        isStarring = ArticleStore.getInstance(this).isStarring(channelItem)
        updateFavouriteIcon()

        job = launch(UI) {

            if (template == null) {
                template = readTemplate()
            }

            if (template == null) {
                return@launch
            }

            articleDetail = channelItem?.jsonFromCache(this@StoryActivity)

            // Use cached data to render template
            if (articleDetail != null) {
                toast("Using cache")

                showLanguageSwitch()

                val html = channelItem?.render(this@StoryActivity, currentLanguage, template, articleDetail)

                loadData(html)

                saveHistory()

                return@launch
            }

            // Cache not found, fetch data from server
            showProgress(true)
            useRemoteJson()
        }
    }

    private fun showLanguageSwitch() {
        language_group.visibility = if (articleDetail?.isBilingual == true) View.VISIBLE else View.GONE
    }

    private suspend fun useRemoteJson() {

        toast("Fetching data from server")
        try {
            articleDetail = channelItem?.jsonFromServer(this)
        } catch (e: Exception) {
            toast(e.toString())
            return
        }

        showLanguageSwitch()

        val html = channelItem?.render(this, currentLanguage, template, articleDetail)

        // If remote json does not exist, or template file is not found, stop and return
        if (html == null) {
            toast("Error! Failed to load data")

            showProgress(false)
            return
        }

        // Load the HTML string into web view.
        loadData(html)

        saveHistory()
    }

    private suspend fun readTemplate(): String? {
        val job = async {
            Store.readRawFile(resources, R.raw.story)
        }

        return job.await()
    }

    private fun saveHistory() {
        val item = channelItem ?: return
        launch(CommonPool) {

            info("Save reading history")
            ArticleStore.getInstance(context = this@StoryActivity).addHistory(item)
        }
    }

    companion object {
        private const val EXTRA_CHANNEL_ITEM = "extra_channel_item"

        /**
         * Start this activity
         */
        fun start(context: Context?, channelItem: ChannelItem) {
            val intent = Intent(context, StoryActivity::class.java)
            intent.putExtra(EXTRA_CHANNEL_ITEM, gson.toJson(channelItem))
            context?.startActivity(intent)
        }
    }
}
