package com.ft.ftchinese.ui.account

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ft.ftchinese.R
import com.ft.ftchinese.model.Account
import com.ft.ftchinese.model.FtcUser
import com.ft.ftchinese.model.LoginMethod
import com.ft.ftchinese.model.WxSession
import com.ft.ftchinese.ui.login.AccountResult
import com.ft.ftchinese.util.ClientError
import com.ft.ftchinese.util.Fetch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AccountViewModel : ViewModel() {

    val inProgress = MutableLiveData<Boolean>()
    val uiType = MutableLiveData<LoginMethod>()
    val refreshTokenExpired = MutableLiveData<Boolean>()

    val sendEmailResult = MutableLiveData<BinaryResult>()

    private val _accountResult = MutableLiveData<AccountResult>()
    val accountResult: LiveData<AccountResult> = _accountResult

    private val _avatarResult = MutableLiveData<ImageResult>()
    val avatarResult: LiveData<ImageResult> = _avatarResult

    private val _wxRefreshResult = MutableLiveData<WxRefreshResult>()
    val wxRefreshResult: LiveData<WxRefreshResult> = _wxRefreshResult

    fun refreshWxInfo(wxSession: WxSession) {
        viewModelScope.launch {
            try {
                val done = withContext(Dispatchers.IO) {
                    wxSession.refreshInfo()
                }

                _wxRefreshResult.value = WxRefreshResult(
                        success = done
                )
            } catch (e: ClientError) {
                if (e.statusCode == 422) {
                    _wxRefreshResult.value = WxRefreshResult(
                            isExpired = true
                    )
                    return@launch
                }

                _wxRefreshResult.value = WxRefreshResult(
                        error = when (e.statusCode) {
                            404 -> R.string.api_account_not_found
                            else -> null
                        },
                        exception = e
                )

            } catch (e: Exception) {
                _wxRefreshResult.value = WxRefreshResult(
                        exception = e
                )
            }
        }
    }

    fun refresh(account: Account) {
        viewModelScope.launch {
            try {

                val updatedAccount = withContext(Dispatchers.IO) {
                    account.refresh()
                }

                _accountResult.value = AccountResult(
                        success = updatedAccount ?: account
                )
            } catch (e: ClientError) {
                val msgId = if (e.statusCode == 404) {
                    R.string.api_account_not_found
                } else {
                    e.statusMessage()
                }

                _accountResult.value = AccountResult(
                        error = msgId,
                        exception = e
                )
            } catch (e: Exception) {

                _accountResult.value = AccountResult(
                        exception = e
                )
            }
        }
    }

    fun downloadAvatar(url: String) {
        viewModelScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    Fetch()
                            .get(url)
                            .download()
                }

                _avatarResult.value = ImageResult(
                        success = bytes
                )
            } catch (e: Exception) {
                _avatarResult.value = ImageResult(
                        exception = e
                )
            }
        }
    }

    fun requestVerification(userId: String) {
        viewModelScope.launch {
            try {
                val done = withContext(Dispatchers.IO) {
                    FtcUser(userId).requestVerification()
                }

                sendEmailResult.value = BinaryResult(
                        success = done
                )

            } catch (e: ClientError) {
                val msgId = when (e.statusCode) {
                    404 -> R.string.api_account_not_found
                    422 -> when (e.error?.key) {
                        "email_server_missing" -> R.string.api_email_server_down
                        else -> null
                    }
                    else -> e.statusMessage()
                }

                sendEmailResult.value = BinaryResult(
                        error = msgId,
                        exception = e
                )

            } catch (e: Exception) {
                sendEmailResult.value = BinaryResult(
                        exception = e
                )
            }
        }
    }

    fun showProgress(show: Boolean) {
        inProgress.value = show
    }

    fun switchUI(m: LoginMethod) {
        uiType.value = m
    }

    fun setRefreshTokenExpired() {
        refreshTokenExpired.value = true
    }
}
