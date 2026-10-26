package com.opson.libraries.navigation.impl

import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DeepLinkBridgeImplTest {

    @Test
    fun linksThatArriveBeforeTheAppListensAreAllDelivered() = runTest {
        val bridge = DeepLinkBridgeImpl()

        bridge.emit("opson://login-callback#a")
        bridge.emit("opson://auth/confirmed")

        assertEquals(listOf("opson://login-callback#a", "opson://auth/confirmed"), bridge.urls.take(2).toList())
    }

    @Test
    fun aLinkIsHandledOnceEvenWhenTheAppListensAgain() = runTest {
        val bridge = DeepLinkBridgeImpl()
        bridge.emit("opson://widget/log")
        assertEquals(listOf("opson://widget/log"), bridge.urls.take(1).toList())

        // A second composition (an iPad window, a recreated host) starts listening.
        val seen = mutableListOf<String>()
        val second = backgroundScope.launch { bridge.urls.collect { seen += it } }
        testScheduler.runCurrent()
        bridge.emit("opson://auth/confirmed")
        testScheduler.runCurrent()
        second.cancel()

        assertEquals(listOf("opson://auth/confirmed"), seen)
    }
}
