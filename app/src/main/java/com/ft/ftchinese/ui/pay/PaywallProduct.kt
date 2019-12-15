package com.ft.ftchinese.ui.pay

import android.os.Parcelable
import com.ft.ftchinese.model.order.Tier
import kotlinx.android.parcel.Parcelize

@Parcelize
data class PaywallProduct(
        val tier: Tier? = null,
        val heading: String = "",
        val description: String = "",
        val smallPrint: String? = null,
        val yearPrice: String = "", // ¥258.00/年
        val monthPrice: String? = null
) : Parcelable
