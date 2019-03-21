package com.ft.ftchinese.models

data class PaywallSource(
        val id: String,
        val category: String,
        val name: String,
        val variant: Language? = null
) {
    fun getGALable(): String {
        return "$category/$id"
    }
}