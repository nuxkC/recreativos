package com.recre.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/** Duraciones de las animaciones de firma (spec §5). */
class MotionTest {
    @Test
    fun duraciones_de_firma_del_spec() {
        assertEquals(600, RecreMotionDurations.COUNT_UP_MS)
        assertEquals(900, RecreMotionDurations.SUCCESS_FLASH_MS)
        assertEquals(400, RecreMotionDurations.DANGER_SHAKE_MS)
        assertEquals(1600, RecreMotionDurations.OFFLINE_PULSE_MS)
        assertEquals(900, RecreMotionDurations.SYNC_SPIN_MS)
    }
}
