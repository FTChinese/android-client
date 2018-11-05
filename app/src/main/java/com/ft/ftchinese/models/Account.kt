package com.ft.ftchinese.models

import com.ft.ftchinese.util.NextApi
import com.ft.ftchinese.util.Fetch
import com.ft.ftchinese.util.SubscribeApi
import com.ft.ftchinese.util.gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async

const val PREFERENCE_USER_ACCOUNT = "user_account"

/**
 * A user's essential data.
 * All fields should be declared as `var` except `id` which should never be changed.
 * When user changes data like email, user userName, verified email, purchased subscription, the corresponding fields should be updated and saved to shared preferences.
 * Avoid modifying an instance when user's data changed so that everything is immutable.
 */
data class Account(
        val id: String,
        var userName: String,
        var email: String,
        val avatarUrl: String,
        val isVip: Boolean,
        val isVerified: Boolean,
        val membership: Membership
) {
    val canAccessPaidContent: Boolean
        get() {
            return (membership.isPaidMember && !membership.isExpired) || isVip
        }
    /**
     * @return Account. Always returns a new one rather than modifying the existing one to make it immutable.
     * @throws JsonSyntaxException If the content returned by API could not be parsed into valid JSON
     * See Fetch#exectue for other exceptions
     */
    suspend fun refresh(): Account {
        val response = GlobalScope.async {
            Fetch().get(NextApi.ACCOUNT)
                    .noCache()
                    .setUserId(this@Account.id)
                    .end()
        }.await()

        val body = response.body()?.string()

        return gson.fromJson<Account>(body, Account::class.java)
    }

    suspend fun requestVerification(): Int {
        val response = GlobalScope.async {
            Fetch().post(NextApi.REQUEST_VERIFICATION)
                    .noCache()
                    .setUserId(this@Account.id)
                    .body(null)
                    .end()
        }.await()

        return response.code()
    }

    suspend fun wxPlaceOrder(membership: Membership?): WxPrepayOrder? {
        if (membership == null) {
            return null
        }
        val response = GlobalScope.async {
            Fetch().post("${SubscribeApi.WX_UNIFIED_ORDER}/${membership.tier}/${membership.billingCycle}")
                    .noCache()
                    .setUserId(this@Account.id)
                    .setClient()
                    .body(null)
                    .end()
        }.await()


        val body = response.body()?.string()
        return gson.fromJson<WxPrepayOrder>(body, WxPrepayOrder::class.java)
    }

    suspend fun wxQueryOrder(orderId: String): WxQueryOrder {
        val resp = GlobalScope.async {
            Fetch().get("${SubscribeApi.WX_ORDER_QUERY}/$orderId")
                    .noCache()
                    .setUserId(this@Account.id)
                    .setClient()
                    .end()
        }.await()

        val body = resp.body()?.string()

        return gson.fromJson<WxQueryOrder>(body, WxQueryOrder::class.java)
    }

    suspend fun aliPlaceOrder(membership: Membership?): AlipayOrder? {
        if (membership == null) {
            return null
        }
        val response = GlobalScope.async {
            Fetch().post("${SubscribeApi.ALI_ORDER}/${membership.tier}/${membership.billingCycle}")
                    .setUserId(this@Account.id)
                    .setClient()
                    .body(null)
                    .end()
        }.await()


        val body = response.body()?.string()
        return gson.fromJson<AlipayOrder>(body, AlipayOrder::class.java)
    }

    suspend fun aliVerifyOrderAsync(content: String): AliVerifiedOrder {

        val resp = GlobalScope.async {
            Fetch().post(SubscribeApi.ALI_VERIFY_APP_PAY)
                    .noCache()
                    .setUserId(this@Account.id)
                    .setClient()
                    .body(content)
                    .end()
        }.await()


        val body = resp.body()?.string()

        return gson.fromJson<AliVerifiedOrder>(body, AliVerifiedOrder::class.java)
    }

    fun starArticle(articleId: String): Boolean {

        val response = Fetch().put("${NextApi.STARRED}/$articleId")
                .noCache()
                .body(null)
                .setUserId(this@Account.id)
                .end()

        return response.code() == 204
    }

    fun unstarArticle(articleId: String): Boolean {

        val response = Fetch().delete("${NextApi.STARRED}/$articleId")
                .noCache()
                .setUserId(this@Account.id)
                .body(null)
                .end()

        return response.code() == 204
    }

    fun isStarring(articleId: String): Boolean {

        val response = Fetch().get("${NextApi.STARRED}/$articleId")
                .noCache()
                .setUserId(this@Account.id)
                .end()

        return response.code() == 204
    }
}

