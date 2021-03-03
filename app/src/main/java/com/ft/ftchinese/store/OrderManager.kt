package com.ft.ftchinese.store

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.ft.ftchinese.model.enums.Cycle
import com.ft.ftchinese.model.enums.OrderKind
import com.ft.ftchinese.model.enums.PayMethod
import com.ft.ftchinese.model.enums.Tier
import com.ft.ftchinese.model.ftcsubs.*
import com.ft.ftchinese.model.fetch.formatISODateTime
import com.ft.ftchinese.model.fetch.formatLocalDate
import com.ft.ftchinese.model.fetch.parseISODateTime
import com.ft.ftchinese.model.fetch.parseLocalDate

private const val PREF_FILE_NAME = "subscription"
private const val PREF_ORDER_ID = "order_id"
private const val PREF_FTC_ID = "ftc_id"
private const val PREF_UNION_ID = "union_id"
private const val PREF_PLAN_ID = "plan_id"
private const val PREF_DISCOUNT_ID = "discount_id"
private const val PREF_PRICE = "price"
private const val PREF_TIER = "tier"
private const val PREF_CYCLE = "cycle"
private const val PREF_AMOUNT = "amount"
private const val PREF_USAGE = "usage_type"
private const val PREF_PAYMENT_METHOD = "pay_method"
private const val PREF_CREATED_AT = "create_at"
private const val PREF_CONFIRMED_AT = "confirmed_at"
private const val PREF_START_DATE = "start_date"
private const val PREF_END_DATE = "end_date"

// https://stackoverflow.com/questions/16319237/cant-put-double-sharedpreferences
fun SharedPreferences.Editor.putDouble(key: String, double: Double): SharedPreferences.Editor =
        putLong(key, java.lang.Double.doubleToRawLongBits(double))

fun SharedPreferences.getDouble(key: String, default: Double) =
        java.lang.Double.longBitsToDouble(getLong(key, java.lang.Double.doubleToRawLongBits(default)))

/**
 * Save and load subscription detail to share preferences.
 */
class OrderManager private constructor(context: Context) {
    private val sharedPreferences = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)

    fun save(order: Order) {
        sharedPreferences.edit {
            clear()
        }

        sharedPreferences.edit {
            putString(PREF_ORDER_ID, order.id)
            putString(PREF_FTC_ID, order.ftcId)
            putString(PREF_UNION_ID, order.unionId)
            putString(PREF_PLAN_ID, order.priceId)
            putString(PREF_DISCOUNT_ID, order.discountId)
            if( order.price != null) {
                putDouble(PREF_PRICE, order.price)
            }
            putString(PREF_TIER, order.tier.toString())
            putString(PREF_CYCLE, order.cycle.toString())
            putDouble(PREF_AMOUNT, order.amount)
            putString(PREF_USAGE, order.kind.toString())
            putString(PREF_PAYMENT_METHOD, order.payMethod.toString())
            putString(PREF_CREATED_AT, formatISODateTime(order.createdAt))
            putString(PREF_CONFIRMED_AT, formatISODateTime(order.confirmedAt))
            putString(PREF_START_DATE, formatLocalDate(order.startDate))
            putString(PREF_END_DATE, formatLocalDate(order.endDate))
        }
    }

    fun load(): Order? {
        val orderId = sharedPreferences.getString(PREF_ORDER_ID, null) ?: return null
        val ftcId = sharedPreferences.getString(PREF_FTC_ID, null)
        val unionId = sharedPreferences.getString(PREF_UNION_ID, null)
        val planId = sharedPreferences.getString(PREF_PLAN_ID, null)
        val discountId = sharedPreferences.getString(PREF_DISCOUNT_ID, null)
        val price = sharedPreferences.getDouble(PREF_PRICE, 0.0)
        val tier = Tier.fromString(sharedPreferences.getString(PREF_TIER, null)) ?: return null
        val amount = sharedPreferences.getDouble(PREF_AMOUNT, 0.0)
        val cycle = Cycle.fromString(sharedPreferences.getString(PREF_CYCLE, null)) ?: return null
        val usageType = OrderKind.fromString(sharedPreferences.getString(PREF_USAGE, null))
                ?: return null
        val payMethod = PayMethod.fromString(sharedPreferences.getString(PREF_PAYMENT_METHOD, null))
            ?: return null
        val createdAt = parseISODateTime(sharedPreferences.getString(PREF_CREATED_AT, null)) ?: return null
        val confirmed = parseISODateTime(sharedPreferences.getString(PREF_CONFIRMED_AT, null))
        val start = parseLocalDate(sharedPreferences.getString(PREF_START_DATE, null))
        val end = parseLocalDate(sharedPreferences.getString(PREF_END_DATE, null))


        return Order(
            id = orderId,
            ftcId= ftcId,
            unionId = unionId,
            priceId = planId,
            discountId = discountId,
            price = price,
            tier = tier,
            cycle = cycle,
            amount = amount,
            payMethod = payMethod,
            createdAt = createdAt,
            kind = usageType,
            confirmedAt = confirmed,
            startDate = start,
            endDate = end
        )
    }

    companion object {
        private var instance: OrderManager? = null

        @Synchronized fun getInstance(ctx: Context): OrderManager {
            if (instance == null) {
                instance = OrderManager(ctx)
            }

            return instance!!
        }
    }
}

private const val PREF_FILE_PAYMENT_RESULT = "com.ft.ftchinese.last_paid_order"
private const val PREF_PAYMENT_STATE = "payment_state"
private const val PREF_PAYMENT_STATE_DESC = "payment_state_desc"
private const val PREF_TOTAL_FEE = "total_fee"
private const val PREF_TX_ID = "tx_id"
private const val PREF_PAID_AT = "paid_at"
private const val PREF_PAY_METHOD = "pay_method"

/**
 * PaymentManager is used to save the last successful payment.
 * This differs from OrderManager in that OrderManager always saves the last order user created,
 * which might not be paid.
 */
class PaymentManager private constructor(ctx: Context) {
    private val sharedPreferences = ctx.getSharedPreferences(PREF_FILE_PAYMENT_RESULT, Context.MODE_PRIVATE)

    // Upon successful payment, we save the order id so that later we know which order updated
    // current membership and use it to perform verification.
    fun saveOrderId(id: String) {
        sharedPreferences.edit {
            clear()
        }

        sharedPreferences.edit {
            putString(PREF_ORDER_ID, id)
        }
    }

    fun save(pr: PaymentResult) {
        sharedPreferences.edit {
            clear()
        }

        sharedPreferences.edit {
            putString(PREF_PAYMENT_STATE, pr.paymentState)
            putString(PREF_PAYMENT_STATE_DESC, pr.paymentStateDesc)
            putInt(PREF_TOTAL_FEE, pr.totalFee)
            putString(PREF_TX_ID, pr.transactionId)
            putString(PREF_ORDER_ID, pr.ftcOrderId)
            putString(PREF_PAID_AT, pr.paidAt)
            putString(PREF_PAY_METHOD, pr.payMethod.toString())
        }
    }

    fun load(): PaymentResult {
        val paymentState = sharedPreferences.getString(PREF_PAYMENT_STATE, "")
        val paymentStateDesc = sharedPreferences.getString(PREF_PAYMENT_STATE_DESC, "")
        val totalFee = sharedPreferences.getInt(PREF_TOTAL_FEE, 0)
        val txId = sharedPreferences.getString(PREF_TX_ID, "")
        val orderId = sharedPreferences.getString(PREF_ORDER_ID, "")
        val paidAt = sharedPreferences.getString(PREF_PAID_AT, "")
        val payMethod = sharedPreferences.getString(PREF_PAYMENT_METHOD, "")

        return PaymentResult(
            paymentState = paymentState ?: "",
            paymentStateDesc = paymentStateDesc ?: "",
            totalFee = totalFee,
            transactionId = txId ?: "",
            ftcOrderId = orderId ?: "",
            paidAt = paidAt ?: "",
            payMethod = PayMethod.fromString(payMethod)
        )
    }

    companion object {
        private var instance: PaymentManager? = null

        @Synchronized fun getInstance(ctx: Context): PaymentManager {
            if (instance == null) {
                instance = PaymentManager(ctx)
            }

            return instance!!
        }
    }
}
