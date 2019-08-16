package com.ft.ftchinese.model.reader

import android.os.Parcelable
import com.beust.klaxon.Klaxon
import com.ft.ftchinese.util.*
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import org.jetbrains.anko.AnkoLogger
import org.threeten.bp.DateTimeException
import org.threeten.bp.ZonedDateTime


object WxOAuth {
    const val SCOPE = "snsapi_userinfo"

    fun stateCode(): String {
        return generateNonce(5)
    }

    fun login(code: String): WxSession? {
        val data = json.toJsonString(mapOf(
                "code" to code
        ))

        val (_, body) = Fetch().post(SubscribeApi.WX_LOGIN)
                .setClient()
                .setAppId()
                .noCache()
                .jsonBody(data)
                .responseApi()

        return if (body == null) {
            null
        } else {
            json.parse<WxSession>(body)
        }
    }
}

/**
 * The reason why you want to perform wechat OAuth:
 * for LOGIN, account data will be saved;
 * for LINK, account data will be used for display; never save it!
 */
enum class WxOAuthIntent {
    LOGIN,
    LINK
}

/**
 * A session represents the access token and refresh token
 * retrieved from Wechat OAuth API. Since those tokens cannot be
 * store on client-side, a session id is returned for future query.
 */
data class WxSession(
        val sessionId: String,
        val unionId: String,
        @KDateTime
        val createdAt: ZonedDateTime
) {


    /**
     * Check if createAt is 30 days old.
     * If so, ask user to re-authorize.
     */
    val isExpired: Boolean
        get() = try {
            createdAt.plusDays(30)
                    .isBefore(ZonedDateTime.now())
        } catch (e: DateTimeException) {
            true
        }

    /**
     * Refresh Wechat user info.
     */
    fun refreshInfo(): Boolean {
        val (resp, _) = Fetch().put(SubscribeApi.WX_REFRESH)
                .noCache()
                .setAppId()
                .jsonBody(Klaxon().toJsonString(mapOf(
                        "sessionId" to sessionId
                )))
                .responseApi()

        return resp.code() == 204
    }

    /**
     * Fetch user account data after wechat OAuth succeeded.
     * Account retrieved from here always has loginMethod set to `wechat`.
     * Only used for initial login.
     * DO NOT use this to refresh account data since WxSession only exists
     * if user logged in via wechat OAuth.
     * If user logged in with email + password (and the the email is bound to this wechat),
     * WxSession never actually exists.
     */
    fun fetchAccount(): Account? {
        val (_, body) = Fetch()
                .get(NextApi.WX_ACCOUNT)
                .setUnionId(unionId)
                .noCache()
                .responseApi()

        return if (body == null) {
            return null
        } else {
            json.parse<Account>(body)
        }
    }
}

const val WX_AVATAR_NAME = "wx_avatar.jpg"

/**
 * Example Wechat avatar url:
 * http://thirdwx.qlogo.cn/mmopen/vi_32/Q0j4TwGTfTLB34sBwSiaL3GJmejqDUqJw4CZ8Qs0ztibsRu6wzMpg7jg5icxWKwxF73ssZUmXmee1MvSvaZ6iaqs1A/132
 */
@Parcelize
data class Wechat(
        val nickname: String? = null,
        val avatarUrl: String? = null
): Parcelable, AnkoLogger {

    @IgnoredOnParcel
    val isEmpty: Boolean
        get() = nickname.isNullOrBlank() && avatarUrl.isNullOrBlank()

    fun retrieveAvatar(): ByteArray? {
        if (avatarUrl == null) {
            return null
        }

        return Fetch()
                .get(avatarUrl)
                .download()
    }
}
