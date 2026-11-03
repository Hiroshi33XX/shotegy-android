// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Hiroshi33XX and contributors.
package com.shotegy.android

import org.junit.Assert.*
import org.junit.Test

class CaptureTelemetryTest {
    private val north = floatArrayOf(1f,0f,0f, 0f,0f,-1f, 0f,1f,0f)
    private val fix = CaptureFix(31.2, 121.5, 12.0, 5f, 50_000)
    private val attitude = CaptureAttitude(north, 8.0, 50_000)
    private fun capture(f: CaptureFix? = fix, a: CaptureAttitude? = attitude, now: Long = 50_000) = validatedCapture(f, a, now, 1_700_000_000_000, 0.0, "Asia/Shanghai")
    private fun rejects(block: () -> Unit) { try { block(); fail("Must reject invalid capture data") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {} }
    @Test fun rearAxisUsesCardinalDirections() {
        assertEquals(0.0, rearCameraAzimuth(north, 0.0)!!, .001)
        assertEquals(90.0, rearCameraAzimuth(floatArrayOf(0f,0f,-1f, -1f,0f,0f, 0f,1f,0f), 0.0)!!, .001)
        assertEquals(180.0, rearCameraAzimuth(floatArrayOf(-1f,0f,0f, 0f,0f,1f, 0f,1f,0f), 0.0)!!, .001)
        assertEquals(270.0, rearCameraAzimuth(floatArrayOf(0f,0f,1f, 1f,0f,0f, 0f,1f,0f), 0.0)!!, .001)
    }
    @Test fun screenRotationDoesNotChangeOpticalAxisAndDeclinationWraps() {
        val landscape = floatArrayOf(0f,-1f,0f, 0f,0f,-1f, 1f,0f,0f)
        assertEquals(rearCameraAzimuth(north, 0.0), rearCameraAzimuth(landscape, 0.0))
        assertEquals(350.0, rearCameraAzimuth(north, -10.0)!!, .001)
        assertEquals(10.0, rearCameraAzimuth(north, 370.0)!!, .001)
    }
    @Test fun nearVerticalCameraIsRejectedAtFifteenDegrees() {
        assertNull(rearCameraAzimuth(floatArrayOf(1f,0f,0f, 0f,1f,0f, 0f,0f,1f), 0.0))
        fun tilted(degrees: Double) = north.copyOf().apply { this[2] = -kotlin.math.sin(Math.toRadians(degrees)).toFloat(); this[5] = 0f }
        assertNull(rearCameraAzimuth(tilted(14.9), 0.0)); assertNotNull(rearCameraAzimuth(tilted(15.1), 0.0))
    }
    @Test fun freshnessUsesMonotonicThirtyAndFiveSecondLimits() {
        capture(fix.copy(elapsedMillis = 20_000), attitude.copy(elapsedMillis = 45_000))
        rejects { capture(fix.copy(elapsedMillis = 19_999)) }; rejects { capture(a = attitude.copy(elapsedMillis = 44_999)) }
        rejects { capture(f = null) }; rejects { capture(a = null) }
    }
    @Test fun accuracyRejectsUnknownAndInaccurateSamples() {
        capture(fix.copy(accuracy = 100f), attitude.copy(accuracyDegrees = 20.0))
        rejects { capture(fix.copy(accuracy = 100.1f)) }; rejects { capture(fix.copy(accuracy = -1f)) }
        rejects { capture(a = attitude.copy(accuracyDegrees = null)) }; rejects { capture(a = attitude.copy(accuracyDegrees = 20.1)) }
        rejects { capture(a = attitude.copy(accuracyDegrees = Double.NaN)) }; rejects { capture(fix.copy(latitude = 0.0, longitude = 0.0)) }
    }
    @Test fun shutterSnapshotKeepsPhotoTimeCoordinatesAndZoneTogether() {
        val snapshot = capture()
        assertEquals(31.2, snapshot.latitude, 0.0); assertEquals(121.5, snapshot.longitude, 0.0)
        assertEquals(1_700_000_000_000, snapshot.capturedAt); assertEquals("Asia/Shanghai", snapshot.timeZone)
        assertEquals(8.0, snapshot.headingAccuracy!!, 0.0)
    }
    @Test fun albumAndIncompleteRecordsCannotBeUsedForSolarPlanning() {
        assertFalse(scoutCanPlan(Scout(imageId = "album")))
        val s = capture(); val camera = Scout(latitude = s.latitude, longitude = s.longitude, heading = s.heading, headingAccuracy = s.headingAccuracy, timeZoneIdentifier = s.timeZone)
        assertTrue(scoutCanPlan(camera)); assertFalse(scoutCanPlan(camera.copy(headingAccuracy = null))); assertFalse(scoutCanPlan(camera.copy(timeZoneIdentifier = null)))
    }
    @Test fun calibratedSensorMayCaptureUnknownAccuracyWithoutInventingDegrees() {
        val calibrated = attitude.copy(accuracyDegrees = null, calibrationStatus = android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_HIGH)
        val result = validatedCapture(fix, calibrated, 50_000, 1_700_000_000_000, 0.0, "Asia/Shanghai", allowCalibratedUnknownAccuracy = true)
        assertNull(result.headingAccuracy)
        assertEquals(0f, result.heading, .001f)
        rejects { validatedCapture(fix, calibrated.copy(calibrationStatus = android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW), 50_000, 1_700_000_000_000, 0.0, "Asia/Shanghai", allowCalibratedUnknownAccuracy = true) }
        val scout = Scout(latitude = result.latitude, longitude = result.longitude, heading = result.heading, timeZoneIdentifier = result.timeZone)
        assertTrue(scoutCanCalculate(scout)); assertFalse(scoutCanPlan(scout))
        assertFalse(scoutCanCalculate(scout.copy(headingAccuracy = 50.0)))
        assertFalse(scoutCanCalculate(Scout(imageId = "album")))
    }

    @Test fun calibratedMagnetometerFallbackMeasuresRearAxisWithoutGyroscope() {
        val matrix = magneticCameraMatrix(floatArrayOf(0f, 9.81f, 0f), floatArrayOf(0f, -20f, -45f))!!
        assertEquals(0.0, rearCameraAzimuth(matrix, 0.0)!!, .001)
        val east = magneticCameraMatrix(floatArrayOf(0f, 9.81f, 0f), floatArrayOf(-45f, -20f, 0f))!!
        assertEquals(90.0, rearCameraAzimuth(east, 0.0)!!, .001)
        assertNull(magneticCameraMatrix(floatArrayOf(0f, 0f, 0f), floatArrayOf(0f, 0f, 45f)))
        assertNull(magneticCameraMatrix(floatArrayOf(0f, 9.81f, 0f), floatArrayOf(900f, 0f, 0f)))
    }

}
