package com.ft.ftchinese

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.support.design.widget.BottomSheetDialog
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import android.webkit.*
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.ft.ftchinese.models.Following
import com.ft.ftchinese.util.gson
import com.tencent.mm.opensdk.modelmsg.SendMessageToWX
import com.tencent.mm.opensdk.modelmsg.WXMediaMessage
import com.tencent.mm.opensdk.modelmsg.WXWebpageObject
import kotlinx.android.synthetic.main.activity_content.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.toast

/**
 * This is used to show the contents of an article in web view.
 * Subclass must implement `init` method to handle data fetching.
 * Subclass must call `onCreate`.
 */
abstract class AbsContentActivity : AppCompatActivity(),
        SwipeRefreshLayout.OnRefreshListener,
        AnkoLogger {

    private var mBottomDialog: BottomSheetDialog? = null

    abstract val articleWebUrl: String
    abstract val articleTitle: String
    abstract val articleStandfirst: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_content)

        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            // Do not show title on the toolbar for any content.
            setDisplayShowTitleEnabled(false)
        }

        swipe_refresh.setOnRefreshListener(this)

        // Configure WebView
        web_view.settings.apply {
            javaScriptEnabled = true
            loadsImagesAutomatically = true
        }

        web_view.apply {

            // Binding JavaScript code to Android Code
            addJavascriptInterface(WebAppInterface(), "Android")

            // Set a WebViewClient to handle various links in the WebView
            webViewClient = BaseWebViewClient(this@AbsContentActivity)

            // Set the chrome handler
            webChromeClient = MyChromeClient()

            // Handle Back button
            setOnKeyListener { _, keyCode, _ ->
                if (keyCode == KeyEvent.KEYCODE_BACK && web_view.canGoBack()) {
                    web_view.goBack()
                    return@setOnKeyListener true
                }

                false
            }
        }

        action_share.setOnClickListener {
            if (mBottomDialog == null) {
                mBottomDialog = BottomSheetDialog(this)
                mBottomDialog?.setContentView(R.layout.fragment_bottom_sheet)

                val shareRecyclerView: RecyclerView? = mBottomDialog?.findViewById(R.id.share_recycler_view)

                shareRecyclerView?.apply {
                    setHasFixedSize(true)
                    layoutManager = LinearLayoutManager(this@AbsContentActivity).apply {
                        orientation = LinearLayoutManager.HORIZONTAL
                    }
                    adapter = ShareAdapter()
                }
            }

            mBottomDialog?.show()
        }

        var isFavourite = false

        action_favourite.setOnClickListener {
            if (!isFavourite) {
                action_favourite.setImageResource(R.drawable.ic_favorite_teal_24dp)
                isFavourite = true
            } else {
                action_favourite.setImageResource(R.drawable.ic_favorite_border_teal_24dp)
                isFavourite = false
            }
        }
    }

    override fun onRefresh() {
        Toast.makeText(this, "Refreshing", Toast.LENGTH_SHORT).show()
    }

    // Create options menu
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.activity_content_list, menu)

        /**
         * Docs on share:
         * https://developer.android.com/training/appbar/action-views
         * https://developer.android.com/training/sharing/shareaction
         * https://developer.android.com/reference/android/support/v7/widget/ShareActionProvider
         * How to set intent: https://developer.android.com/training/sharing/send
         */
//        menu?.findItem(R.id.action_share).also { menuItem ->
//            mShareActionProvider = MenuItemCompat.getActionProvider(menuItem) as ShareActionProvider
//        }

        return true
    }

    /**
     * Called by subclass to set share intent for ShareActionProvider
     */
//    fun setShareIntent(shareIntent: Intent) {
//        mShareActionProvider?.setShareIntent(Intent.createChooser(shareIntent, "分享到"))
//    }

    // Handle menu click events
    override fun onOptionsItemSelected(item: MenuItem?) = when (item?.itemId) {
        R.id.action_listen -> {

            true
        }
        else -> {
            super.onOptionsItemSelected(item)
        }
    }

    abstract fun init()

    fun loadData(data: String?) {

        info("Load HTML string into web view")

        web_view.loadDataWithBaseURL("http://www.ftchinese.com", data, "text/html", null, null)

        showProgress(false)
    }

    fun loadUrl(url: String) {

        info("Load url directly: $url")

        web_view.loadUrl(url)
        showProgress(false)
    }

    fun showProgress(show: Boolean) {
        if (show) {
            progress_bar.visibility = View.VISIBLE
        } else {
            swipe_refresh.isRefreshing = false
            progress_bar.visibility = View.GONE
        }
    }

    /**
     * Get all app installed on device that could be used for share.
     * This is identical to `android.support.v7.widget.ShareActionProvider`.
     * Share to Wechat Moments will not show up. This is not what we want.
     */
//    private fun getShareApps(): MutableList<AppInfo> {
//        val intent = Intent(Intent.ACTION_SEND, null)
//        intent.addCategory(Intent.CATEGORY_DEFAULT)
//        intent.type = "text/plain"
//        val resolveInfos = packageManager.queryIntentActivities(intent, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
//
//        val apps = mutableListOf<AppInfo>()
//        for (info in resolveInfos) {
//            val pkgName = info.activityInfo.packageName
//            val launcherClassName = info.activityInfo.name
//            val appName = info.loadLabel(packageManager)
//            val icon = info.loadIcon(packageManager)
//            apps.add(AppInfo(pkgName, launcherClassName, appName, icon))
//        }
//
//        return apps
//    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconView: ImageView = itemView.findViewById(R.id.share_icon_view)
        val textView: TextView = itemView.findViewById(R.id.share_text_view)
    }

    inner class ShareAdapter : RecyclerView.Adapter<ViewHolder>() {

        private val apps = arrayOf(
                ShareItem("好友", R.drawable.wechat, ShareItem.WECHAT_FRIEND),
                ShareItem("朋友圈", R.drawable.moments, ShareItem.WECHAT_FRIEND),
                ShareItem("打开链接", R.drawable.chrome, ShareItem.OPEN_IN_BROWSER),
                ShareItem("更多", R.drawable.ic_more_horiz_black_24dp, ShareItem.MORE_OPTIONS)
        )

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(this@AbsContentActivity)
                    .inflate(R.layout.share_item, parent, false)

            return ViewHolder(view)
        }

        override fun getItemCount(): Int {
            return apps.size
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = apps[position]

            holder.iconView.setImageResource(app.icon)
            holder.textView.text = app.appName

            holder.itemView.setOnClickListener {

                when (app.id) {
                    ShareItem.WECHAT_FRIEND -> {

                    }
                    ShareItem.WECHAT_MOMOMENTS -> {
                        val webpage = WXWebpageObject()
                        webpage.webpageUrl = articleWebUrl

                        val msg = WXMediaMessage(webpage)
                        msg.title = articleTitle
                        msg.description = articleStandfirst

                        val req = SendMessageToWX.Req()
                        req.transaction = ""
                        req.message = msg
                        req.scene = SendMessageToWX.Req.WXSceneTimeline

                        
                    }
                    ShareItem.OPEN_IN_BROWSER -> {
                        val webpage = Uri.parse(articleWebUrl)
                        val intent = Intent(Intent.ACTION_VIEW, webpage)
                        if (intent.resolveActivity(packageManager) != null) {
                            startActivity(intent)
                        }
                    }
                    ShareItem.MORE_OPTIONS -> {
                        val shareString = getString(R.string.share_template, articleTitle, articleWebUrl)

                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareString)
                            type = "text/plain"
                        }
                        startActivity(Intent.createChooser(sendIntent, "分享到"))
                    }
                    else -> {
                        toast("${app.appName}")
                    }
                }

                mBottomDialog?.dismiss()
            }
        }

    }

    // Methods injected to JavaScript in WebView
    inner class WebAppInterface : AnkoLogger {

        /**
         * Usage in JS: Android.follow(message)
         */
        @JavascriptInterface
        fun follow(message: String) {
            info("Clicked a follow button")
            info("Received follow message: $message")

            val following = gson.fromJson<Following>(message, Following::class.java)
            following.save(this@AbsContentActivity)
        }
    }
}

//data class AppInfo(
//        val pkgName: String,
//        val launcherClassname: String,
//        val appName: CharSequence,
//        val icon: Drawable
//)

data class ShareItem(
        val appName: CharSequence,
        val icon: Int,
        val id: Int
) {
    companion object {
        const val WECHAT_FRIEND = 1
        const val WECHAT_MOMOMENTS = 2
        const val OPEN_IN_BROWSER = 3
        const val MORE_OPTIONS = 4
    }
}