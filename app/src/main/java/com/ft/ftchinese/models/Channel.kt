package com.ft.ftchinese.models

import android.content.Context
import android.content.res.Resources
import android.util.Log
import com.ft.ftchinese.R
import com.ft.ftchinese.util.Fetch
import com.ft.ftchinese.util.Store
import com.ft.ftchinese.util.gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.experimental.async

class Endpoints{
    companion object {
        const val HOST_FTC = "www.ftchinese.com"
        const val HOST_MAILBOX = "api003.ftmailbox.com"
        val hosts = arrayOf(HOST_FTC, HOST_MAILBOX)
    }
}

/**
 * The following data keys is used to parse JSON data passed from WebView by WebAppInterface.postItems methods.
 * Most of them are useless, serving as placeholders so that we can extract the deep nested JSON values.
 * `ChannelMeta` represent the `meta` filed in JSON data.
 */
data class ChannelMeta(
        val title: String,
        val description: String,
        val theme: String,
        val adid: String
)

/**
 * ChannelItem represents an item in a page of ViewPager.
 * This is the data type passed to AbsContentActivity so that it know what kind of data to load.
 * iOS equivalent might be Page/Layouts/Content/ContentItem.swift#ContentItem
 * The fields are collected from all HTML elements `div.item-container-app`.
 * See https://github.com/FTChinese/android-client/app/scripts/list.js.
 * In short it used those attributes:
 * `data-id` for `id`
 * `data-type` for type. Possible values: `story`, `interactive`,
 * The content of `a.item-headline-link` inside `div.item-container-app` for `headline`
 * `data-audio` for `shortlead`
 * `data-caudio` for `caudio`
 * `data-eaudio` for `eaudio`
 * `data-sub-type` for `subType`. Possible values: `radio`, `speedreading`
 * `data-date` for `timeStamp`
 *
 * The fields in ChannelItem are also persisted to SQLite when user clicked on it.
 * It seems the Room library does not work well with Kotlin data class. Use a plain class works.
 *
 * The data type is also used to record reading history. `standfirst` is used only for this purpose. Do not use `subType` and `shortlead` should not be used for this purpose. ReadingHistory could only recored `type==story`.
 */
data class ChannelItem(
        val id: String,
        val type: String,
        val subType: String? = null,
        val headline: String,
        val shortlead: String? = null
) {

    var standfirst: String = ""
    var favouredAt: Long = 0

    val canonicalUrl: String
        get() = "http://www.ftchinese.com/$type/$id"

    private val filename: String
        get() = "${type}_$id.json"

    private val prefKey: String
        get() = "${type}_$id"

    var adId: String = ""

    private val commentsId: String
        get() {
            return when(subType) {
                "interactive" -> "r_interactive_$id"
                "video" -> "r_video_$id"
                "story" -> id
                "photo", "photonews" -> "r_photo_$id"
                else -> "r_${type}_$id"
            }
        }

    private val commentsOrder: String
        get() {
            return "story"
        }

    // See Page/FTChinese/Main/APIs.swift
    // https://api003.ftmailbox.com/interactive/12339?bodyonly=no&webview=ftcapp&001&exclusive&hideheader=yes&ad=no&inNavigation=yes&for=audio&enableScript=yes&v=24
    val apiUrl: String?
        get() {
            return when(type) {
                "story", "premium" -> "https://api.ftmailbox.com/index.php/jsapi/get_story_more_info/$id"
                "interactive" -> "https://api003.ftmailbox.com/interactive/$id?bodyonly=no&webview=ftcapp&001&exclusive&hideheader=yes&ad=no&inNavigation=yes&for=audio&enableScript=yes&v=24"
                "gym", "special" -> "https://api003.ftmailbox.com/$type/$id?bodyonly=yes&webview=ftcapp"
                "video" -> "https://api003.ftmailbox.com/$type/$id?bodyonly=yes&webview=ftcapp&004"
                "radio" -> "https://api003.ftmailbox.com/$type/$id?bodyonly=yes&webview=ftcapp&exclusive"
                else -> null
            }
        }

//    suspend fun renderFromCache(context: Context): String? {
//        val template = readTemplate(context.resources) ?: return null
//
//        val articleDetail = jsonFromCache(context) ?: return null
//
//        standfirst = articleDetail.clongleadbody
//
//        return render(context, template, articleDetail)
//    }
//
//    suspend fun renderFromServer(context: Context): String? {
//        val template = readTemplate(context.resources) ?: return null
//
//        val articleDetail = jsonFromServer(context) ?: return null
//        standfirst = articleDetail.clongleadbody
//
//        return render(context, template, articleDetail)
//    }

    suspend fun jsonFromCache(context: Context?): ArticleDetail? {
        val job = async {
            Store.load(context, filename)
        }

        val jsonData = job.await() ?: return null

        return parseJson(jsonData)
    }

    suspend fun jsonFromServer(context: Context): ArticleDetail? {
        if (apiUrl == null) {
            return null
        }

        val url = apiUrl ?: return null

        val job = async {
            Fetch().get(url).string()
        }

        val jsonData = job.await() ?: return null

        async {
            Store.save(context, filename, jsonData)
        }

        return parseJson(jsonData)
    }

    private fun parseJson(jsonData: String?): ArticleDetail? {
        return try {
            val article = gson.fromJson<ArticleDetail>(jsonData, ArticleDetail::class.java)
            standfirst = article.clongleadbody

            article
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "Cannot parse json: $e")
            null
        }
    }

    fun render(context: Context, language: Int, template: String?, article: ArticleDetail?): String? {

        if (template == null || article == null) {
            return null
        }

        val follows = Following.loadAsMap(context)

        val followTags = follows[Following.keys[0]]
        val followTopics = follows[Following.keys[1]]
        val followAreas = follows[Following.keys[2]]
        val followIndustries = follows[Following.keys[3]]
        val followAuthors = follows[Following.keys[4]]
        val followColumns = follows[Following.keys[5]]

        var body = ""
        var title = ""

        when (language) {
            LANGUAGE_CN -> {
                body = article.bodyXML.cn
                title = article.title.cn
            }
            LANGUAGE_EN -> {
                body = article.bodyXML.en ?: ""
                title = article.title.en ?: ""
            }
            LANGUAGE_BI -> {
                body = article.bodyAlignedXML
                title = "${article.title.cn}<br>${article.title.en}"
            }
        }

        return template.replace("{story-body}", body)
                .replace("{story-headline}", title)
                .replace("{story-byline}", article.byline)
                .replace("{story-time}", article.createdAt)
                .replace("{story-lead}", article.standfirst)
                .replace("{story-theme}", article.htmlForTheme())
                .replace("{story-tag}", article.tag)
                .replace("{story-id}", article.id)
                .replace("{story-image}", article.htmlForCoverImage())
                .replace("{related-stories}", article.htmlForRelatedStories())
                .replace("{related-topics}", article.htmlForRelatedTopics())
                .replace("{comments-order}", commentsOrder)
                .replace("{story-container-style}", "")
                .replace("'{follow-tags}'", followTags ?: "")
                .replace("'{follow-topics}'", followTopics ?: "")
                .replace("'{follow-industries}'", followIndustries ?: "")
                .replace("'{follow-areas}'", followAreas ?: "")
                .replace("'{follow-authors}'", followAuthors ?: "")
                .replace("'{follow-columns}'", followColumns ?: "")
                .replace("{adchID}", adId)
                //                        .replace("{ad-banner}", "")
                //                        .replace("{ad-mpu}", "")
                //                        .replace("{font-class}", "")
                .replace("{comments-id}", commentsId)
    }

    fun saveHistory() {

    }

    fun favour(context: Context) {
        val sharedPreferences = context.getSharedPreferences(PREF_NAME_FAVOURITE, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        if (sharedPreferences.contains(prefKey)) {
            editor.remove(prefKey)
        } else {
            favouredAt = System.currentTimeMillis()
            editor.putString(prefKey, gson.toJson(this))
        }

        editor.apply()
    }

    fun isFavouring(context: Context): Boolean {
        return context.getSharedPreferences(PREF_NAME_FAVOURITE, Context.MODE_PRIVATE).contains(prefKey)
    }

    companion object {
        private const val TAG = "ChannelItem"
        private const val PREF_NAME_FAVOURITE = "favourite"
        const val LANGUAGE_CN = 0
        const val LANGUAGE_EN = 1
        const val LANGUAGE_BI = 2

        suspend fun readTemplate(resources: Resources): String? {
            val job = async {
                Store.readRawFile(resources, R.raw.story)
            }

            return job.await()
        }

        fun loadFavourites(context: Context?): MutableList<ChannelItem>? {
            if (context == null) return null

            val sharedPreferences = context.getSharedPreferences(PREF_NAME_FAVOURITE, Context.MODE_PRIVATE)

            val values = sharedPreferences.all.values.toList()

            val items = mutableListOf<ChannelItem>()

            for (v in values) {
                if (v !is String) {
                    continue
                }

                try {
                    val item = gson.fromJson<ChannelItem>(v, ChannelItem::class.java)

                    items.add(item)
                } catch (e: JsonSyntaxException) {
                    continue
                }
            }

            items.sortByDescending {
                it.favouredAt
            }

            return items
        }
    }
}

data class ChannelList(
        val items: Array<ChannelItem>
)
data class ChannelSection(
        val lists: Array<ChannelList>
)
data class ChannelContent(
        val meta: ChannelMeta,
        val sections: Array<ChannelSection>
)

val pathToTitle = mapOf(
        "businesscase.html" to "中国商业案例精选",
        "editorchoice-issue.html" to "编辑精选",
        "chinabusinesswatch.html" to "宝珀·中国商业观察",
        "viewtop.html" to "高端视点",
        "Emotech2017.html" to "2018·预见人工智能",
        "antfinancial.html" to "“新四大发明”背后的中国浪潮",
        "teawithft.html" to "与FT共进下午茶",
        "creditease.html" to "未来生活 未来金融",
        "markets.html" to "金融市场",
        "hxxf2016.html" to "透视中国PPP模式",
        "money.html" to "理财"
)