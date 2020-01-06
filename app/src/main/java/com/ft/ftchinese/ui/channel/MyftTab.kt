package com.ft.ftchinese.ui.channel

data class MyftTab(
        val id: MyftTabId,
        val title: String
)

enum class MyftTabId {
    READ,
    STARRED,
    FOLLOWING
}
