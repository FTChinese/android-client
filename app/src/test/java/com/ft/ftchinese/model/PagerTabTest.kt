package com.ft.ftchinese.model

import org.junit.Before
import org.junit.Test

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PagerTabTest {

    private var mPagerTab = Navigation.newsPages[1]

    @Before
    fun setUp() {

    }

    @Test
    fun getFileName() {
        println(mPagerTab.fileName)
    }

    @Test
    fun withPagination() {
    }

    @Test
    fun render() {
    }
}
