package com.iwankurnia.hub.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppCountWidgetTest {

    @Test
    fun appCountWidget_truncatesToWidth() {
        // 100dp fixed part (28 padding, ~61 total, 1 divider, 8 spacer), then 48dp per app.
        assertEquals(1, appsThatFit(180))
        assertEquals(2, appsThatFit(196))
        assertEquals(3, appsThatFit(280))
        assertEquals(3, appsThatFit(250))
        assertEquals(6, appsThatFit(400))
        assertEquals(1, appsThatFit(100))
        assertEquals(8, appsThatFit(2000))
        (0..3000 step 10).forEach { width -> assertTrue(appsThatFit(width) in 1..8) }
    }

    @Test
    fun badge_hiddenWhenZero() {
        assertFalse(showBadge(0))
        assertTrue(showBadge(1))
        assertTrue(showBadge(120))
    }
}
