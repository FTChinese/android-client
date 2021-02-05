package com.ft.ftchinese.ui.product

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.ft.ftchinese.model.reader.Account
import com.ft.ftchinese.model.subscription.Plan
import com.ft.ftchinese.model.subscription.Product
import com.ft.ftchinese.model.subscription.Promo
import org.jetbrains.anko.AnkoLogger

/**
 * Used by ProductFragment to pass information to host
 * activity which product is selected.
 */
class ProductViewModel : ViewModel(), AnkoLogger {

    val planSelected: MutableLiveData<Plan> by lazy {
        MutableLiveData<Plan>()
    }

    val inputEnabled: MutableLiveData<Boolean> by lazy {
        MutableLiveData<Boolean>()
    }

    val accountChanged: MutableLiveData<Account?> by lazy {
        MutableLiveData<Account?>()
    }

    // When host activity retrieved paywall data, pass the raw products to ProductFragment.
    val productsReceived: MutableLiveData<List<Product>> by lazy {
        MutableLiveData<List<Product>>()
    }

    // When the host activity retrieved paywall data, convert the promo field to PromoUI
    val promoCreated: MutableLiveData<Promo> by lazy {
        MutableLiveData<Promo>()
    }
}
