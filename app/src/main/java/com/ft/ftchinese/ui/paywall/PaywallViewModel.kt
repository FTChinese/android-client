package com.ft.ftchinese.ui.paywall

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ft.ftchinese.R
import com.ft.ftchinese.model.reader.Account
import com.ft.ftchinese.model.subscription.*
import com.ft.ftchinese.repository.PaywallClient
import com.ft.ftchinese.repository.StripeClient
import com.ft.ftchinese.store.CacheFileNames
import com.ft.ftchinese.store.FileCache
import com.ft.ftchinese.model.fetch.json
import com.ft.ftchinese.viewmodel.Result
import com.ft.ftchinese.viewmodel.parseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.anko.AnkoLogger
import org.jetbrains.anko.info

class PaywallViewModel(
    private val cache: FileCache
) : ViewModel(), AnkoLogger {
    val isNetworkAvailable = MutableLiveData<Boolean>()

    val paywallResult: MutableLiveData<Result<Paywall>> by lazy {
        MutableLiveData<Result<Paywall>>()
    }

    val stripePrices: MutableLiveData<Result<List<StripePrice>>> by lazy {
        MutableLiveData<Result<List<StripePrice>>>()
    }

    private suspend fun getCachedPaywall(): Paywall? {
        return withContext(Dispatchers.IO) {
            val data = cache.loadText(CacheFileNames.paywall)

            if (!data.isNullOrBlank()) {
                try {
                    json.parse<Paywall>(data)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }

    fun loadPaywall(isRefreshing: Boolean) {
        viewModelScope.launch {

            // If not manually refreshing
            if (!isRefreshing) {
                val pw = getCachedPaywall()
                if (pw != null) {
                    paywallResult.value = Result.Success(pw)
                    // Update the in-memory cache.
                    PlanStore.plans = pw.products.flatMap {
                        it.plans
                    }
                }
            }

            // Always retrieve from api.
            if (isNetworkAvailable.value != true) {
                paywallResult.value = Result.LocalizedError(R.string.prompt_no_network)
                return@launch
            }

            try {
                val paywall = withContext(Dispatchers.IO) {
                    PaywallClient.retrieve()
                }

                if (paywall == null) {
                    paywallResult.value = Result.LocalizedError(R.string.api_server_error)
                    return@launch
                }

                paywallResult.value = Result.Success(paywall.value)
                PlanStore.plans = paywall.value.products.flatMap {
                    it.plans
                }

                withContext(Dispatchers.IO) {
                    cache.saveText(CacheFileNames.paywall, paywall.raw)
                }

            } catch (e: Exception) {
                info(e)
                paywallResult.value = parseException(e)
            }
        }
    }

    // Retrieve ftc pricing plans in background.
    fun refreshFtcPrices() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = PaywallClient.listPrices() ?: return@launch
                PlanStore.plans = result.value
                cache.saveText(CacheFileNames.ftcPrices, result.raw)
            } catch (e: Exception) {
                info(e)
            }
        }
    }

    // Retrieve stripe prices in background and refresh cache.
    // It will be executed whenever user opened MemberActivity or PaywallActivity.
    fun refreshStripePrices(account: Account?) {
        info("Retrieving stripe prices...")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = StripeClient.listPrices(account) ?: return@launch
                StripePriceStore.prices = result.value
                cache.saveText(CacheFileNames.stripePrices, result.raw)
            } catch (e: Exception) {
                info(e)
            }
        }
    }

    private suspend fun stripeCachedPrices(): List<StripePrice>? {
        return withContext(Dispatchers.IO) {
            val data = cache.loadText(CacheFileNames.stripePrices)

            if (data == null) {
                null
            } else {
                try {
                    json.parseArray(data)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    // Load stripe prices from cache, or from server is cached data not found,
    // before show stripe payment page.
    // This works as a backup in case stripe prices is not yet
    // loaded into memory.
    fun loadStripePrices(account: Account?) {
        viewModelScope.launch {
            val prices = stripeCachedPrices()
            if (prices != null) {
                stripePrices.value = Result.Success(prices)
                return@launch
            }

            // Retrieve server data
            try {
                val result = withContext(Dispatchers.IO) {
                    StripeClient.listPrices(account)
                }

                if (result == null) {
                    stripePrices.value = Result.LocalizedError(R.string.api_server_error)
                    return@launch
                }

                stripePrices.value = Result.Success(result.value)

                withContext(Dispatchers.IO) {
                    cache.saveText(CacheFileNames.stripePrices, result.raw)
                }
            }
            catch (e: Exception) {
                info(e)
                stripePrices.value = parseException(e)
            }
        }
    }
}
