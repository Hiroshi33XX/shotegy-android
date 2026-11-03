// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class SunCalculatorTest {
    @Test fun sameInstantHasSameSolarPositionInDifferentTimeZones() {
        val atUtc = ZonedDateTime.parse("2026-03-20T12:00:00Z")
        val first = sunPosition(atUtc, 0.0, 0.0)
        val second = sunPosition(atUtc.withZoneSameInstant(ZoneId.of("Asia/Shanghai")), 0.0, 0.0)
        assertEquals(first.azimuth, second.azimuth, 1e-8)
        assertEquals(first.elevation, second.elevation, 1e-8)
        assertTrue(first.elevation > 85.0)
    }

    @Test fun recommendedWindowsRespectScoutZoneAndDay() {
        val zone = ZoneId.of("Asia/Shanghai")
        val date = LocalDate.of(2026, 12, 1)
        val windows = lightWindows(date, 31.23, 121.47, 180.0, "逆光", 30.0, zone)
        assertTrue(windows.isNotEmpty())
        windows.forEach { window ->
            assertEquals(zone, window.start.zone)
            assertEquals(date, window.start.toLocalDate())
            assertTrue(window.end.toInstant().isAfter(window.start.toInstant()))
            assertTrue(window.position.elevation >= 2.0)
        }
    }
}
