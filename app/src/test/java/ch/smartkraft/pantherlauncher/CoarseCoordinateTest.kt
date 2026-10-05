package ch.smartkraft.pantherlauncher

import ch.smartkraft.pantherlauncher.helper.receivers.coarseCoordinate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CoarseCoordinateTest {

    @Test
    fun coordinatesAreSentWithTwoDecimals() {
        assertEquals("47.38", coarseCoordinate(47.376887))
        assertEquals("8.54", coarseCoordinate(8.541694))
        assertEquals("-33.87", coarseCoordinate(-33.868820))
    }

    @Test
    fun theDecimalSeparatorDoesNotFollowTheDeviceLanguage() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))   // would print 47,38
            assertEquals("47.38", coarseCoordinate(47.376887))
        } finally {
            Locale.setDefault(before)
        }
    }
}
