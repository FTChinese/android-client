package com.ft.ftchinese.model.content

import android.net.Uri
import android.os.Parcelable
import com.ft.ftchinese.model.reader.Permission
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import java.lang.NumberFormatException

const val HTML_TYPE_FRAGMENT = 1
const val HTML_TYPE_COMPLETE = 2

/**
 * ChannelSource specifies how to display a channel page and where to fetch its data.
 */
@Parcelize
data class ChannelSource (
    val title: String, // A Tab's title
        // name is used to cache files.
        // If empty, do not cache it, nor should you try to
        // find cache.
    val name: String,  // Cache filename used by this tab
    val contentPath: String, // The resource path of the HTML fragment.
    val htmlType: Int, // Flag used to tell whether the webUrl should be loaded directly
    val permission: Permission? = null // A predefined permission that overrides individual Teaser's permission.

) : Parcelable, AnkoLogger {

    @IgnoredOnParcel
    var shouldReload = false

    val fileName: String?
        get() = if (name.isBlank()) null else "$name.html"

    /**
     * Returns a new instance for a pagination link.
     * Example:
     * If current page for a list of articles are retrieved from:
     * https://api003.ftmailbox.com/channel/china.html?webview=ftcapp&bodyonly=yes
     * This page has pagination link at the bottom, which is a relative page `china.html?page=2`.
     * What we need to do is to extract query parameter
     * `page` and append it to current links, generating a link like:
     * https://api003.ftmailbox.com/channel/china.html?webview=ftcapp&bodyonly=yes&page=2
     *
     * We also need to cureate a new value for `name` field
     * based on `page=<number>`:
     * For `news_china`, the second page should be `new_china_2`.
     * For `news_china_3`, the next page should be `new_china_4`.
     */
    fun withPagination(pageKey: String, pageNumber: String): ChannelSource {
        val currentUri = Uri.parse(contentPath)

        info("Current URI: $currentUri")

        // Current list page is already a started from a pagination link
        if (currentUri.getQueryParameter(pageKey) != null) {
            val newUri = currentUri.buildUpon().clearQuery()

            for (key in currentUri.queryParameterNames) {
                info("URI query key: $key")

                if (key == pageKey) {
                    newUri.appendQueryParameter(key, pageNumber)
                    continue
                }

                val value = currentUri.getQueryParameter(key)
                newUri.appendQueryParameter(key, value)
            }

            return ChannelSource(
                    title = title,
                    name = generatePagedName(pageNumber),
                    contentPath = newUri.build().toString(),
                    htmlType = htmlType
            ).apply {
                shouldReload = true
            }
        } else {
            // Current page is not started from a pagination link, webUrl does not contain `page=xxx`.
            val newUrl = currentUri.buildUpon()
                    .appendQueryParameter(pageKey, pageNumber)
                    .build()
                    .toString()

            return ChannelSource(
                    title = title,
                    name = "${name}_$pageNumber",
                    contentPath = newUrl,
                    htmlType = htmlType
            )
        }
    }

    // Generate a name to be used as cache file name.
    // It changes name with pattern `news_china_2` to `news_china_${pageNumber}`
    private fun generatePagedName(pageNumber: String): String {
        // Give new page a name
        val nameParts = name.split("_").toMutableList()

        return if (nameParts.size > 0) {
            val lastPart = nameParts[nameParts.size - 1]
            // Check if lastPart is a number
            return try {
                lastPart.toInt()

                nameParts[nameParts.size - 1] = pageNumber

                nameParts.joinToString("_")

            } catch (e: NumberFormatException) {
                "name_$pageNumber"
            }
        } else {
            "name_$pageNumber"
        }
    }

}

fun buildFollowChannel(follow: Following): ChannelSource {
    return ChannelSource(
        title = follow.tag,
        name = "${follow.type}_${follow.tag}",
        contentPath = "/${follow.type}/${follow.tag}?bodyonly=yes&webviewftcapp",
        htmlType = HTML_TYPE_FRAGMENT
    )
}

fun buildColumnChannel(item: Teaser): ChannelSource {
    return ChannelSource(
        title = item.title,
        name = "${item.type}_${item.id}",
        contentPath = "/${item.type}/${item.id}?bodyonly=yes&webview=ftcapp",
        htmlType = HTML_TYPE_FRAGMENT
    )
}

fun buildTagOrArchiveChannel(uri: Uri): ChannelSource {
    return ChannelSource(
        title = uri.lastPathSegment ?: "",
        name = uri.pathSegments.joinToString("_"),
        contentPath = "/${uri.path}?${uri.query}",
        htmlType = HTML_TYPE_FRAGMENT
    )
}

/**
 * Those links need to start a ChannelActivity.
 * Other links include:
 * /tag/汽车未来
 */
val pathToTitle = mapOf(
    // /m/marketing/intelligence.html?webview=ftcapp
    "intelligence.html" to "FT研究院",
    // /m/marketing/businesscase.html
    "businesscase.html" to "中国商业案例精选",
    // /channel/editorchoice-issue.html?issue=EditorChoice-20181029
    "editorchoice-issue.html" to "编辑精选",
    // /channel/chinabusinesswatch.html
    "chinabusinesswatch.html" to "宝珀·中国商业观察",
    // /m/corp/preview.html?pageid=huawei2018
    "huawei2018" to "+智能 见未来 重塑商业力量",
    // /channel/tradewar.html
    "tradewar.html" to "中美贸易战",
    "viewtop.html" to "高端视点",
    "Emotech2017.html" to "2018·预见人工智能",
    "antfinancial.html" to "“新四大发明”背后的中国浪潮",
    "teawithft.html" to "与FT共进下午茶",
    "creditease.html" to "未来生活 未来金融",
    "markets.html" to "金融市场",
    "hxxf2016.html" to "透视中国PPP模式",
    "money.html" to "理财"
)

val noAccess = mapOf(
    // /channel/english.html?webview=ftcapp
    "english.html" to "每日英语",
    // /channel/mba.html?webview=ftcapp
    "mba.html" to "FT商学院",
    // /channel/weekly.html
    "weekly.html" to "热门文章"
)

fun buildChannelFromUri(uri: Uri): ChannelSource {
    // Handle links on this page: https://api003.ftmailbox.com/channel/editorchoice.html?webview=ftcapp&bodyonly=yes&ad=no&showEnglishAudio=yes&018
    // The link itself looks like:
    // http://www.ftchinese.com/channel/editorchoice-issue.html?issue=EditorChoice-20181105
    val isEditorChoice = uri.lastPathSegment == "editorchoice-issue.html"
    val issueName = uri.getQueryParameter("issue")

    return ChannelSource(
        title = pathToTitle[uri.lastPathSegment] ?: "",
        name = issueName
            ?: uri.pathSegments
                .joinToString("_")
                .removeSuffix(".html"),
        contentPath = "/${uri.path}?${uri.query}",
        htmlType = HTML_TYPE_FRAGMENT,
        permission = if (isEditorChoice) Permission.PREMIUM else null
    )
}

fun buildMarketingChannel(uri: Uri): ChannelSource {

    val pageName = uri.getQueryParameter("pageid")

    val name = if (pageName != null) {
        // Links like /m/corp/preview.html?pageid=huawei2018
        uri.pathSegments
            .joinToString("_")
            .removeSuffix(".html") + "_$pageName"
    } else {
        // Links like /m/marketing/intelligence.html?webview=ftcapp
        uri.pathSegments
            .joinToString("_")
            .removeSuffix(".html")
    }

    return ChannelSource(
        title = pathToTitle[
            pageName
            ?: uri.lastPathSegment
            ?: ""
        ]
            ?: "",
        name = name,
        contentPath = "/${uri.path}?${uri.query}",
        htmlType = HTML_TYPE_COMPLETE
    )
}
