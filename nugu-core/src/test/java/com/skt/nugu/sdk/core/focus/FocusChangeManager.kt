package com.skt.nugu.sdk.core.focus

import com.skt.nugu.sdk.core.interfaces.focus.FocusState
import org.junit.Assert

open class FocusChangeManager {
    fun assertFocusChange(client: TestClient, expectedFocusState: FocusState) {
        val result = client.waitFocusChangedAndReset()
        Assert.assertTrue(result.focusChanged)
        Assert.assertEquals(expectedFocusState, result.focusState)
    }

    fun assertNoFocusChange(client: TestClient) {
        val result = client.waitFocusChangedAndReset()
        Assert.assertFalse(result.focusChanged)
    }
}