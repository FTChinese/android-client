package com.ft.ftchinese.models

import android.content.Context
import android.content.SharedPreferences

private val followTypes = arrayOf("tag", "topic", "area", "industry", "author", "column")
private val FOLLOW_TYPE_TAG = followTypes[0]
private val FOLLOW_TYPE_TOPIC = followTypes[1]
private val FOLLOW_TYPE_AREA = followTypes[2]
private val FOLLOW_TYPE_INDUSTRY = followTypes[3]
private val FOLLOW_TYPE_AUTHOR = followTypes[4]
private val FOLLOW_TYPE_COLUMN = followTypes[5]

/**
 * Used to parse messages passed from JS when user clicked `FOLLOW` button.
 * Those data are stored locally.
 * Example saved XML:
 * ```xml
 * <map>
 *  <set name="tag">
 *      <string>美国</string>
 *      <string>中国经济</string>
 *      <string>特朗普</string>
 *   </set>
 * </map>
 * ```
 */
data class Following(
        var type: String, // JS uses this value. Possible values: `tag`, `topic`, `industry`, `area`, `augthor`, `column`. `augthor` is a typo in JS code, but you have to keep that typo on.
        var tag: String, // This is the string show along with the FOLLOW button
        var action: String // `follow` or `unfollow`. Used to determine if user if follow or unfollow something.
) {
    val bodyUrl: String
        get() = "https://api003.ftmailbox.com/$type/$tag?bodyonly=yes&webviewftcapp"
}

/**
 * This is used to manage saving and loading of data
 * into/from shared preference for 关注 button in WebView.
 * It is used by the inner class ContentWebViewInterface in
 * AbsContentActivity, and FollowingActivity.
 */
class FollowingManager(context: Context) {
    private val sharedPreferences = context.getSharedPreferences(PREF_FILE_FOLLOWING, Context.MODE_PRIVATE)
    private val editor = sharedPreferences.edit()

    fun save(following: Following) {
        val hs = sharedPreferences.getStringSet(following.type, HashSet<String>())

        val newHs = HashSet(hs)

        when (following.action) {
            ACTION_FOLLOW -> {
                newHs.add(following.tag)
            }

            ACTION_UNFOLLOW -> {
                newHs.remove(following.tag)
            }
        }

        val editor = sharedPreferences.edit()
        editor.putStringSet(following.type, newHs)
        editor.apply()
    }

    fun loadForJS(): JSFollows {

        val result = mutableMapOf<String, String>()

        for (key in followTypes) {
            try {
                val ss = sharedPreferences.getStringSet(key, null) ?: setOf()

                result[key] = ss.joinToString { "'$it'" }

            } catch (e: Exception) {
                e.printStackTrace()
                continue
            }
        }

        return JSFollows(result)
    }

    fun load(): MutableList<Following> {
        val result = mutableListOf<Following>()

        for (key in followTypes) {
            try {
                val ss = sharedPreferences.getStringSet(key, setOf()) ?: setOf()

                ss.forEach {
                    result.add(Following(type = key, tag = it, action = ACTION_UNFOLLOW))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                continue
            }
        }

        return result
    }

    companion object {
        const val ACTION_FOLLOW = "follow"
        const val ACTION_UNFOLLOW = "unfollow"
        const val PREF_FILE_FOLLOWING = "following"

        private var instance: FollowingManager ?= null

        @Synchronized fun getInstance(ctx: Context): FollowingManager {
            if (instance == null) {
                instance = FollowingManager(ctx.applicationContext)
            }
            return instance!!
        }
    }
}

data class JSFollows(
        private val follows: Map<String, String>
) {
    val tag: String
        get() = follows[FOLLOW_TYPE_TAG] ?: ""

    val topic: String
        get() = follows[FOLLOW_TYPE_TOPIC] ?: ""

    val area: String
        get() = follows[FOLLOW_TYPE_AREA] ?: ""

    val industry: String
        get() = follows[FOLLOW_TYPE_INDUSTRY] ?: ""

    val author: String
        get() = follows[FOLLOW_TYPE_AUTHOR] ?: ""

    val column: String
        get() = follows[FOLLOW_TYPE_COLUMN] ?: ""
}