package com.ft.ftchinese.ui.checkout

import android.content.Context
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import com.ft.ftchinese.R
import com.ft.ftchinese.model.enums.Edition
import com.ft.ftchinese.model.enums.OrderKind
import com.ft.ftchinese.model.enums.PayMethod
import com.ft.ftchinese.model.enums.Tier
import com.ft.ftchinese.model.reader.Membership
import com.ft.ftchinese.model.subscription.Plan
import com.ft.ftchinese.model.subscription.Price
import com.ft.ftchinese.ui.formatter.buildPrice
import com.ft.ftchinese.ui.formatter.formatPrice

data class CheckoutIntent(
    val orderKind: OrderKind,
    val payMethods: List<PayMethod>,
)

data class CheckoutIntents(
    val intents: List<CheckoutIntent>,
    val warning: String,
) {
    val payMethods: List<PayMethod>
        get() = intents.flatMap { it.payMethods }

    val orderKinds: List<OrderKind>
        get() = intents.map { it.orderKind }

    val permitAliPay: Boolean
        get() = payMethods.contains(PayMethod.ALIPAY)

    val permitWxPay: Boolean
        get() = payMethods.contains(PayMethod.WXPAY)

    val permitStripe: Boolean
        get() = payMethods.contains(PayMethod.STRIPE)

    fun orderKindsTitle(ctx: Context) = orderKinds.joinToString("/") {
        ctx.getString(it.stringRes)
    }

    fun paymentAllowed(method: PayMethod) = payMethods.contains(method)

    /**
     * Find the intent containing the specified payment method.
     */
    fun findIntent(method: PayMethod): CheckoutIntent? {
        if (intents.isEmpty()) {
            return null
        }

        if (intents.size == 1) {
            return intents[0]
        }

        return intents.find { it.payMethods.contains(method) }
    }

    fun payButtonText(ctx: Context, payMethod: PayMethod, plan: Plan): String {
        val intent = findIntent(payMethod) ?: return ctx.getString(R.string.check_out)

        when (payMethod) {
            PayMethod.ALIPAY, PayMethod.WXPAY -> {
                // Ali/Wx pay button have two groups:
                // CREATE/RENEW/UPGRADE: 支付宝支付 ¥258.00 or 微信支付 ¥258.00
                // ADD_ON: 购买订阅期限
                if (intent.orderKind == OrderKind.AddOn) {
                    return ctx.getString(intent.orderKind.stringRes)
                }

                val priceText = formatPrice(ctx, plan.unifiedPrice.payablePriceParams)
                return ctx.getString(
                    R.string.formatter_check_out,
                    ctx.getString(payMethod.stringRes),
                    priceText
                )
            }
            // Stripe button has three groups:
            // CREATE: Stripe订阅
            // UPGRADE: Stripe订阅升级
            // SwitchCycle: Stripe变更订阅周期
            PayMethod.STRIPE -> {
                when (intent.orderKind) {
                    OrderKind.Create -> return ctx.getString(payMethod.stringRes)
                    OrderKind.Upgrade -> return ctx.getString(payMethod.stringRes) + ctx.getString(intent.orderKind.stringRes) + ctx.getString(plan.tier.stringRes)
                    OrderKind.SwitchCycle -> ctx.getString(R.string.pay_brand_stripe) + ctx.getString(intent.orderKind.stringRes)
                }
            }
        }

        return ctx.getString(R.string.check_out)
    }
}

fun buildCheckoutIntents(m: Membership, e: Edition): CheckoutIntents {
    if (m.expired()) {
        return CheckoutIntents(
            intents = listOf(CheckoutIntent(
                orderKind = OrderKind.Create,
                payMethods = listOf(PayMethod.ALIPAY, PayMethod.WXPAY, PayMethod.STRIPE),
            )),
            warning = "",
        )
    }

    // Invalid stripe subscription is treated as not having a membership.
    if (m.isInvalidStripe()) {
        return CheckoutIntents(
            intents = listOf(CheckoutIntent(
                orderKind = OrderKind.Create,
                payMethods = listOf(PayMethod.ALIPAY, PayMethod.WXPAY, PayMethod.STRIPE),
            )),
            warning = "",
        )
    }

    when (m.payMethod) {
        PayMethod.ALIPAY, PayMethod.WXPAY -> {
            // Renewal
            if (m.tier == e.tier) {
                // For alipay and wxpay, allow user to renew if
                // remaining days not exceeding 3 years.
                if (!m.withinAliWxRenewalPeriod()) {
                    return CheckoutIntents(
                        intents = listOf(CheckoutIntent(
                            orderKind = OrderKind.Renew,
                            payMethods = listOf()
                        )),
                        warning = "剩余时间超出允许的最长续订期限",
                    )
                }
                // Ali/Wx member can renew via Ali/Wx, or Stripe with remaining days put to reserved state.
                return CheckoutIntents(
                    intents = listOf(CheckoutIntent(
                        orderKind = OrderKind.Renew,
                        payMethods = listOf(PayMethod.ALIPAY, PayMethod.WXPAY, PayMethod.STRIPE),
                    )),
                    warning = "通过支付宝/微信购买累加一个订阅周期，或选择Stripe订阅，当前剩余订阅时间将留待Stripe订阅结束后继续使用。"
                )
            }

            // Current membership's tier differs from the selected one.
            // Allowed actions depends on what is being selected.
            when (e.tier) {
                // This is an Upgrade action if standard user selected premium product.
                Tier.PREMIUM -> {
                    return CheckoutIntents(
                        intents = listOf(CheckoutIntent(
                            orderKind = OrderKind.Upgrade,
                            payMethods = listOf(PayMethod.ALIPAY, PayMethod.WXPAY, PayMethod.STRIPE)
                        )),
                        warning = "升级高端会员即刻启用，标准版的剩余时间将在高端版结束后继续使用。",
                    )
                }
                // A premium could buy standard as AddOns.
                Tier.STANDARD -> {
                    return CheckoutIntents(
                        intents = listOf(CheckoutIntent(
                            orderKind = OrderKind.Upgrade,
                            payMethods = listOf(PayMethod.ALIPAY, PayMethod.WXPAY),
                        )),
                        warning = "高端会员可使用支付宝/微信购买新的标准版订阅期限，在高端版结束后启用。"
                    )
                }
            }
        }
        PayMethod.STRIPE -> {
            // If user is a premium, whatever selected should be treated as AddOn.
            when (m.tier) {
                Tier.PREMIUM -> {
                    return CheckoutIntents(
                        intents = listOf(CheckoutIntent(
                            orderKind = OrderKind.AddOn,
                            payMethods = listOf(PayMethod.ALIPAY, PayMethod.WXPAY),
                        )),
                        warning = "自动续订可以使用支付宝/微信购买额外订阅期限，在订阅结束后启用。",
                    )
                }
                Tier.STANDARD -> {
                    // If selected premium, it's an upgrade and must use stripe.
                    when (e.tier) {
                        Tier.PREMIUM -> {
                            return CheckoutIntents(
                                intents = listOf(
                                CheckoutIntent(
                                    orderKind = OrderKind.Upgrade,
                                    payMethods = listOf(PayMethod.STRIPE),
                                )),
                                warning = "Stripe订阅升级高端版会自动调整您的扣款额度",
                            )
                        }
                        // Selected standard, the cycle might be different.
                        Tier.STANDARD -> {
                            // For same cycle, it could only be add-on
                            if (m.cycle == e.cycle) {
                                return CheckoutIntents(
                                    intents = listOf(CheckoutIntent(
                                        orderKind = OrderKind.AddOn,
                                        payMethods = listOf(PayMethod.ALIPAY, PayMethod.WXPAY),
                                    )),
                                    warning = "自动续订可以使用支付宝/微信购买额外订阅期限，在订阅结束后启用。",
                                )
                            }

                            // Same tier but different cycle.
                            // For Ali/Wx, it's add-on; for Stripe, it's switching cycle.
                            return CheckoutIntents(
                                intents = listOf(
                                    CheckoutIntent(
                                        orderKind = OrderKind.AddOn,
                                        payMethods = listOf(PayMethod.ALIPAY, PayMethod.WXPAY, PayMethod.STRIPE),
                                    ),
                                    CheckoutIntent(
                                        orderKind = OrderKind.SwitchCycle,
                                        payMethods = listOf(PayMethod.STRIPE)
                                    )
                                ),
                                warning = "选择支付宝微信购买额外会员期限将在Stripe订阅到期后启用；或选择Stripe更改自动扣款周期。自动订阅建议您订阅年度版更划算。"
                            )
                        }
                    }
                }
            }
        }
        PayMethod.APPLE -> {
            if (m.tier == Tier.STANDARD && e.tier == Tier.PREMIUM) {
                return CheckoutIntents(
                    intents = listOf(CheckoutIntent(
                        orderKind = OrderKind.Upgrade,
                        payMethods = listOf(),
                    )),
                    warning = "苹果内购的标准会员升级高端会员需要在您的苹果设备上，使用原有苹果账号登录后，在FT中文网APP内操作"
                )
            }
            // All other options are add-ons
            return CheckoutIntents(
                intents = listOf(CheckoutIntent(
                    orderKind = OrderKind.AddOn,
                    payMethods = listOf(PayMethod.ALIPAY, PayMethod.WXPAY),
                )),
                warning = "自动续订可以使用支付宝/微信购买额外订阅期限，在订阅结束后启用。",
            )
        }
        PayMethod.B2B -> {
            return CheckoutIntents(
                intents = listOf(),
                warning = "您目前使用的是企业订阅授权，续订或升级请联系您所属机构的管理人员"
            )
        }
    }

    return CheckoutIntents(
        intents = listOf(),
        warning = "仅支持新建订阅、续订、标准会员升级和购买额外订阅期限，不支持其他操作。",
    )
}

/**
 * Describes the UI in cart fragment.
 */
data class Cart(
    val productName: String,
    val payablePrice: Spannable?,
    val originalPrice: Spannable?,
)

fun buildCart(ctx: Context, price: Price): Cart {
    val p = buildPrice(ctx, price)
    return Cart(
        productName = ctx.getString(price.tier.stringRes),
        payablePrice = SpannableString(p.payable).apply {
            setSpan(RelativeSizeSpan(2f), 1, length-2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        },
        originalPrice = p.original,
    )
}

