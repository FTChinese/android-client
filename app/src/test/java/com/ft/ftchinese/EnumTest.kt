package com.ft.ftchinese

import com.ft.ftchinese.tracking.AdParser
import com.ft.ftchinese.tracking.AdPosition
import org.junit.Test

class EnumTest {
    @Test fun enumValues() {
        println(AdPosition.TOP_BANNER.name)

        enumValues<AdPosition>().forEach {
            val adCode = AdParser.getAdCode(it)

            print(adCode)
            print(it.position)
        }
    }
}
