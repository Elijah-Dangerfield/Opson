package com.opson

import kotlin.test.Test
import kotlin.test.assertEquals

class DeepLinkLogTest {

    @Test
    fun aLoggedLinkKeepsWhereItPointsAndDropsWhatItCarries() {
        assertEquals(
            "opson://auth/confirmed",
            "opson://auth/confirmed#access_token=t&refresh_token=r".withoutQueryOrFragment(),
        )
        assertEquals("opson://auth/confirmed", "opson://auth/confirmed?code=abc".withoutQueryOrFragment())
        assertEquals("opson://auth/confirmed", "opson://auth/confirmed".withoutQueryOrFragment())
    }
}
