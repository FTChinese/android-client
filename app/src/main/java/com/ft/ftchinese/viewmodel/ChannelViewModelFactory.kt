package com.ft.ftchinese.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ft.ftchinese.model.reader.Account
import com.ft.ftchinese.store.FileCache

/**
* ViewModel provider factory to instantiate LoginViewModel.
* Required given LoginViewModel has a non-empty constructor
*/
class ChannelViewModelFactory(val cache: FileCache, val account: Account?) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChannelViewModel::class.java)) {
            return ChannelViewModel(
                cache,
                account
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
