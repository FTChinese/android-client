package com.ft.ftchinese.model.enums

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
enum class OrderKind(val code: String) : Parcelable {
    CREATE("create"),
    RENEW("renew"),
    UPGRADE("upgrade"),
    ADD_ON("add_on");

    override fun toString(): String {
        return code
    }

    companion object{

        private val STRING_TO_ENUM: Map<String, OrderKind> = values().associateBy {
            it.code
        }

        @JvmStatic
        fun fromString(symbol: String?): OrderKind? {
            if (symbol == null) {
                return null
            }
            return STRING_TO_ENUM[symbol]
        }
    }
}


