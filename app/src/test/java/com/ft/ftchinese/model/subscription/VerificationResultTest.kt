package com.ft.ftchinese.model.subscription

import com.ft.ftchinese.model.fetch.json
import org.junit.Assert.*
import org.junit.Test

class VerificationResultTest {
    private val data = """
{
    "order": {
        "id": "FT1CCD950137595F32",
        "ftcId": "e1a1f5c0-0e23-11e8-aa75-977ba2bcc6ae",
        "unionId": "tvSxA7L6cgl8nwkrScm_yRzZoVTy",
        "planId": "plan_ICMPPM0UXcpZ",
        "discountId": null,
        "price": 258,
        "tier": "standard",
        "cycle": "year",
        "amount": 258,
        "currency": "",
        "cycleCount": 1,
        "extraDays": 1,
        "usageType": "create",
        "payMethod": "wechat",
        "totalBalance": null,
        "createdAt": "2020-11-19T02:52:18Z",
        "confirmedAt": null,
        "startDate": null,
        "endDate": null,
        "live": true
    },
    "membership": {
        "ftcId": "e1a1f5c0-0e23-11e8-aa75-977ba2bcc6ae",
        "unionId": null,
        "tier": "standard",
        "cycle": "year",
        "expireDate": "2020-10-22",
        "payMethod": "alipay",
        "stripeSubsId": null,
        "autoRenew": false,
        "status": null,
        "appleSubsId": null,
        "b2bLicenceId": null
    },
    "payment": {
        "paymentState": "NOTPAY",
        "paymentStateDesc": "订单未支付",
        "totalFee": 25800,
        "transactionId": "",
        "ftcOrderId": "FT1CCD950137595F32",
        "paidAt": null,
        "payMethod": "wechat"
    }
}
    """.trimIndent()

    @Test
    fun parseJson() {
        val vrfResult = json.parse<VerificationResult>(data)

        assertNotNull(vrfResult)

        println(vrfResult)
    }
}
