package com.ft.ftchinese.model.subscription

/**
 * After WXPayEntryActivity received result 0, check against our server for the payment result.
 * Server will in turn check payment result from Wechat server.
 */
data class PaymentResult(
        val paymentState: String,
        val paymentStateDesc: String,
        val totalFee: Int,
        val transactionId: String,
        val ftcOrderId: String,
        val paidAt: String // ISO8601
)
