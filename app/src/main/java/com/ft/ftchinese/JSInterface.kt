package com.ft.ftchinese

import android.app.Activity
import android.webkit.JavascriptInterface
import com.ft.ftchinese.models.*
import com.ft.ftchinese.user.SignInActivity
import com.ft.ftchinese.user.SubscriptionActivity
import com.ft.ftchinese.util.gson
import com.google.gson.JsonSyntaxException
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.toast


class JSInterface(private val activity: Activity?) : AnkoLogger {

    /**
     * Passed from JS
     * iOS equivalent is defined Page/Layouts/Pages/Content/DetailModelController.swift#pageData
     * This is a list of articles on each mPageMeta.
     * Its value is set when WebView finished loading a web page
     */
    private var mChannelItems: Array<ChannelItem>? = null
    // Passed from JS
    private var mChannelMeta: ChannelMeta? = null
    var mSession: SessionManager? = null

    private var mListener: OnJSInteractionListener? = null

    interface OnJSInteractionListener {
        fun onPageLoaded(message: String)
    }


    fun setOnJSInteractionListener(listener: OnJSInteractionListener?) {
        mListener = listener
    }
    /**
     * Method injected to WebView to receive a list of articles in a mPageMeta page upon finished loading.
     * Structure of the JSON received:
     * {
     *  "meta": {
     *      "title": "FT中文网",
     *      "description": "",
     *      "theme": "default",
     *      "adid": "1000", // from window.adchID, default '1000'
     *      "adZone": "home" // Extracted from a <script> block.
     *  },
     *  "sections": [
     *      "lists": [
     *          {
     *             "name": "New List",
     *             "items": [
     *                  {
     *                      "id": "001078965", // from attribute data-id.
     *                      "type": "story",  //
     *                      "headline": "中国千禧一代将面临养老金短缺", // The content of .item-headline-link
     *                       "eaudio": "https://s3-us-west-2.amazonaws.com/ftlabs-audio-rss-bucket.prod/7a6d6d6a-9f75-11e8-85da-eeb7a9ce36e4.mp3",
     *                      "timeStamp": "1534308162"
     *                  }
     *             ]
     *          }
     *      ]
     *  ]
     * }
     *
     * In development mode, the data is written to json files.
     * You can view them in Device File Explorer.
     * Or see this repo for example data:
     * https://gitlab.com/neefrankie/android-helper
     */
    @JavascriptInterface fun onPageLoaded(message: String) {

        try {
            val channelData = gson.fromJson<ChannelContent>(message, ChannelContent::class.java)

            mChannelItems = channelData.sections[0].lists[0].items
            mChannelMeta = channelData.meta

        } catch (e: JsonSyntaxException) {
            info("Cannot parse JSON after a channel loaded")
        } catch (e: Exception) {
            info("$e")
        }
    }

    /**
     * Handle click event on an item of article list.
     * See Page/Layouts/Page/SuperDataViewController.swift#SuperDataViewController what kind of data structure is passed back from web view.
     * iOS equivalent: Page/Layouts/Pages/Content/DetailModelController.swift
     * @param index is the index of article a user clicked in current page.
     */
    @JavascriptInterface fun onSelectItem(index: String) {
        try {
            val i = index.toInt()

            val channelMeta = mChannelMeta ?: return
            val channelItem = mChannelItems?.getOrNull(i) ?: return

            info("Clicked item: $channelItem")

            channelItem.channelTitle = channelMeta.title
            channelItem.theme = channelMeta.theme
            channelItem.adId = channelMeta.adid
            channelItem.adZone = channelMeta.adZone

            when (channelItem.type) {
                /**
                 * {
                 * "id": "007000049",
                 * "type": "column",
                 * "headline": "徐瑾经济人" }
                 * Canonical URL: http://www.ftchinese.com/channel/column.html
                 * Content URL: https://api003.ftmailbox.com/column/007000049?webview=ftcapp&bodyonly=yes
                 */
                ChannelItem.TYPE_COLUMN -> {
                    val listPage = PagerTab(
                            title = channelItem.headline,
                            name = "${channelItem.type}_${channelItem.id}",
                            contentUrl = channelItem.buildUrlForListFrag().toString(),
                            htmlType = PagerTab.HTML_TYPE_FRAGMENT
                    )

                    ChannelActivity.start(activity, listPage)

                    return
                }
            }

            if (!channelItem.isMembershipRequired) {
                startReading(channelItem)

                return
            }

            val account = mSession?.loadUser()

            if (account == null) {
                activity?.toast(R.string.prompt_restricted_paid_user)
                SignInActivity.start(activity)

                return
            }

            if (!account.canAccessPaidContent) {
                activity?.toast(R.string.prompt_restricted_paid_user)
                SubscriptionActivity.start(activity)

                return
            }

            startReading(channelItem)

        } catch (e: NumberFormatException) {
            info("$e")
        }
    }

    private fun startReading(channelItem: ChannelItem) {
        when (channelItem.type) {
            ChannelItem.TYPE_STORY, ChannelItem.TYPE_PREMIUM -> {
                StoryActivity.start(activity, channelItem)
            }
            ChannelItem.TYPE_INTERACTIVE -> {
                when (channelItem.subType) {
                    ChannelItem.SUB_TYPE_RADIO -> {
                        RadioActivity.start(activity, channelItem)
                    }
                    else -> WebContentActivity.start(activity, channelItem)
                }
            }
            else -> WebContentActivity.start(activity, channelItem)
        }
    }

    /**
     * Data retrieved from HTML element .specialanchor.
     * JSON structure:
     * [
     *  {
     *      "tag": "",  // from attribute 'tag'
     *      "title": "", // from attribute 'title'
     *      "adid": "", // from attribute 'adid'
     *      "zone": "",  // from attribute 'zone'
     *      "channel": "", // from attribute 'channel'
     *      "hideAd": ""  // from optinal attribute 'hideAd'
     *  }
     * ]
     */
    @JavascriptInterface fun onLoadedSponsors(message: String) {

        mListener?.onPageLoaded(message)

        try {
            SponsorManager.sponsors = gson.fromJson(message, Array<Sponsor>::class.java)

        } catch (e: Exception) {
            info("$e")
        }
    }

    /**
     * {
     *  forceNewAdTags: [],
     *  forceOldAdTags: [],
     *  grayReleaseTarget: '0'
     * }
     */
    @JavascriptInterface fun onNewAdSwitchData(message: String) {
        try {
            val adSwitch = gson.fromJson<AdSwitch>(message, AdSwitch::class.java)


        } catch (e: Exception) {
            info("$e")
        }
    }

    @JavascriptInterface fun onSharePageFromApp(message: String) {

    }

    @JavascriptInterface fun onSendPageInfoToApp(message: String) {

    }
}