package com.ft.ftchinese.model

import com.ft.ftchinese.repository.TabPages
import com.ft.ftchinese.repository.Fetch
import org.junit.Test

class TabPagesTest {
    @Test
    fun homePage() {
        val content = Fetch().get(TabPages.newsPages[0].contentUrl)
                .responseString()

        println(content)
    }
}
