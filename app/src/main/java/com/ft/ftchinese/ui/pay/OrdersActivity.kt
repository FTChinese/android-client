package com.ft.ftchinese.ui.pay

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.ft.ftchinese.R
import com.ft.ftchinese.ui.base.*
import com.ft.ftchinese.model.order.PayMethod
import com.ft.ftchinese.model.reader.SessionManager
import com.ft.ftchinese.model.order.Order
import com.ft.ftchinese.util.*
import kotlinx.android.synthetic.main.activity_my_orders.*
import kotlinx.android.synthetic.main.progress_bar.*
import kotlinx.android.synthetic.main.simple_toolbar.*
import kotlinx.coroutines.*
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info
import org.jetbrains.anko.toast

@kotlinx.coroutines.ExperimentalCoroutinesApi
class MyOrdersActivity : ScopedAppActivity(), AnkoLogger {

    private lateinit var viewAdapter: OrderAdapter
    private lateinit var sessionManager: SessionManager

    private fun showProgress(show: Boolean) {
        if (show) {
            progress_bar.visibility = View.VISIBLE
        } else {
            progress_bar.visibility = View.GONE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_orders)
        setSupportActionBar(toolbar)

        sessionManager = SessionManager.getInstance(this)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
        }

        val viewManager = LinearLayoutManager(this)
        viewAdapter = OrderAdapter(listOf())

        orders_rv.apply {
            layoutManager = viewManager
            adapter = viewAdapter
        }

        fetchData()
    }

    private fun fetchData() {
        if (!isNetworkConnected()) {

            toast(R.string.prompt_no_network)
            return
        }

        showProgress(true)

        launch {
            try {
                val orders = withContext(Dispatchers.IO) {
                    sessionManager.loadAccount()?.getOrders()
                } ?: return@launch

                showProgress(false)

                info("Orders: $orders")

                viewAdapter.setData(buildRows(orders))
                viewAdapter.notifyDataSetChanged()

            } catch (e: ClientError) {
                showProgress(false)
                handleApiError(e)
            } catch (e: Exception) {
                showProgress(false)
                toast(parseException(e))
            }
        }
    }

    private fun buildRows(orders: List<Order>): List<OrderRow> {

        return orders.map {
            val tierCycle = getTierCycleText(it.tier, it.cycle)

            val payMethod = when (it.payMethod) {
                PayMethod.ALIPAY -> getString(R.string.pay_method_ali)
                PayMethod.WXPAY -> getString(R.string.pay_method_wechat)
                PayMethod.STRIPE -> getString(R.string.pay_method_stripe)
            }

            val price = getString(R.string.formatter_price, "￥", it.netPrice)

            OrderRow(
                    orderId = getString(R.string.order_id, it.id),
                    plan = getString(R.string.order_subscribed_plan, tierCycle),
                    period = getString(R.string.order_period, it.startDate, it.endDate),
                    price = getString(R.string.order_price, price),
                    payMethod = getString(R.string.order_pay_method, payMethod),
                    creationTime = getString(R.string.order_creation_time, formatISODateTime(it.createdAt))
            )
        }
    }

    companion object {
        fun start(context: Context?) {
            context?.startActivity(
                    Intent(context, MyOrdersActivity::class.java)
            )
        }
    }
}

