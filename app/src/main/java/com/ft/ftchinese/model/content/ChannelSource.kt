package com.ft.ftchinese.model.content

import android.net.Uri
import android.os.Parcelable
import com.ft.ftchinese.model.reader.Permission
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import org.jetbrains.anko.AnkoLogger

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
    val contentPath: String, // Deprecated. Repaced by path and query.
    val path: String,
    val query: String,
    val htmlType: Int, // Flag used to tell whether the webUrl should be loaded directly
    val permission: Permission? = null // A predefined permission that overrides individual Teaser's permission.

) : Parcelable, AnkoLogger {

    @IgnoredOnParcel
    var shouldReload = false // Used on pagination. If user clicked the same pagination number, just refersh the content.

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
        val qs = "$pageKey=$pageNumber"

        // Somehow there's a problem on the the web page's pagination:
        // the number on of the current page is never disabled
        // so user could click the page 2 even if it is no page2.
        // TO handle such situation, we just let it to refresh data rather than opening a new ChannelActivity.
        if (query.contains(qs)) {
            return this.apply {
                shouldReload = true
            }
        }

        return ChannelSource(
            title = title,
            name = "${name}_$pageNumber",
            contentPath = contentPath,
            path = path,
            query = qs,
            htmlType = htmlType
        )
    }
}

fun buildFollowChannel(follow: Following): ChannelSource {
    return ChannelSource(
        title = follow.tag,
        name = "${follow.type}_${follow.tag}",
        contentPath = "/${follow.type}/${follow.tag}?bodyonly=yes&webviewftcapp",
        path = "/${follow.type}/${follow.tag}",
        query = "",
        htmlType = HTML_TYPE_FRAGMENT
    )
}

fun buildColumnChannel(item: Teaser): ChannelSource {
    return ChannelSource(
        title = item.title,
        name = "${item.type}_${item.id}",
        contentPath = "/${item.type}/${item.id}?bodyonly=yes&webview=ftcapp",
        path = "/${item.type}/${item.id}",
        query = "",
        htmlType = HTML_TYPE_FRAGMENT
    )
}

fun buildTagOrArchiveChannel(uri: Uri): ChannelSource {
    return ChannelSource(
        title = uri.lastPathSegment ?: "",
        name = uri.pathSegments.joinToString("_"),
        contentPath = "/${uri.path}?${uri.query}",
        path = uri.path ?: "",
        query = uri.query ?: "",
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
    "money.html" to "理财",
    "whoisstealingmydata" to "大数据时代 数据也在偷偷看你",
    "travel2018" to "跟着欧洲玩家小众游",
    "ebooklxkyzygbygr" to "留学可以怎样改变一个人",
    "ebooksfqkljstjypm" to "是非区块链",
    "ebookygtomzdslhssb" to "英国脱欧 II",
    "ebooknjnxb30nzcgcl5" to "30年职场观察录",
    "ebooknjnxb30nzcgcl4" to "",
    "ebooknjnxb30nzcgcl3" to "",
    "ebooknjnxb30nzcgcl2" to "",
    "ebooknjnxb30nzcgcl1" to "",
    "ebookmdtlptzzgjjhzxhf" to "面对特朗普挑战 中国经济走向何方",
    "ebookmgrwsmzctlp" to "美国人为什么支持特朗普",
    "ebookxzaqdym" to "寻找安全的疫苗",
    "ebookylwjywzgmtrdrbgc" to "日本观察: 以邻为鉴",
    "ebookszcnxyydzd" to "职场女性: 优雅地战斗",
    "ebookcfbj" to "拆分北京",
    "ebooklszh" to "楼市之惑",
    "yearbook2018" to "2018: 中美博弈之年",
    "yearbook2017" to "2017: 自信下的焦虑",
    "ebook-english-1" to "读FT学英语",
    "2018lunchwiththeft1" to "与FT共进午餐"

)

val noAccess = mapOf(
    // /channel/english.html?webview=ftcapp
    "english.html" to "每日英语",
    // /channel/mba.html?webview=ftcapp
    "mba.html" to "FT商学院",
    // /channel/weekly.html
    "weekly.html" to "热门文章"
)

/**
 * Handle urls like:
 * /channel/editorchoice-issue.html?issue=EditorChoice-20181105
 * Those links appears on under page with links like:
 * /channel/editorchoice.html
 */
fun buildChannelFromUri(uri: Uri): ChannelSource {
    val isEditorChoice = uri.lastPathSegment == "editorchoice-issue.html"
    val issueName = uri.getQueryParameter("issue")

    return ChannelSource(
        title = pathToTitle[uri.lastPathSegment] ?: "",
        name = issueName
            ?: uri.pathSegments
                .joinToString("_")
                .removeSuffix(".html"),
        contentPath = "/${uri.path}?${uri.query}",
        path = uri.path ?: "",
        query = uri.query ?: "",
        htmlType = HTML_TYPE_FRAGMENT,
        permission = if (isEditorChoice) Permission.PREMIUM else null
    )
}

/**
 * Turn url path like
 * /m/marketing/intelligence.html or
 * /m/corp/preview.html?pageid=huawei2018
 * to ChannelSource.
 */
fun buildMarketingChannel(uri: Uri): ChannelSource {

    val pageId = uri.getQueryParameter("pageid")

    val name = uri.pathSegments
        .joinToString("_")
        .removeSuffix(".html") + if (pageId != null) {
        "_$pageId"
    } else {
        ""
    }

    return ChannelSource(
        title = pathToTitle[
            pageId
            ?: uri.lastPathSegment
            ?: ""
        ]
            ?: "",
        name = name,
        contentPath = "/${uri.path}?${uri.query}",
        path = uri.path ?: "",
        query = uri.query ?: "",
        htmlType = HTML_TYPE_COMPLETE
    )
}
