package codex.licence

import codex.LicenseZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * TDD — EPIC CDX-5-1 : Domaine `codex.licence` (14e) + `PdfLicenseDetector`.
 *
 * `PdfLicenseDetector.detect(text)` scans the extracted text of a PDF for
 * license markers and routes the document to the correct zone:
 *
 *  - "Apache License 2.0" / "Apache-2.0"            -> [LicenseZone.OSS]
 *  - "Creative Commons" / "CC-BY" / "CC0"           -> [LicenseZone.OSS]
 *  - "©" / "Copyright" / "All rights reserved"     -> [LicenseZone.CSS]
 *  - No license mention                             -> [LicenseZone.UNKNOWN]
 *
 * The detector is an additive complement to the existing `LicenseZoneDetector`
 * (which detects by project path). When the PDF content yields UNKNOWN, the
 * caller falls back to path-based detection.
 *
 * Baby-step TDD strict RED (types absent) -> GREEN -> REFACTOR.
 */
class PdfLicenseDetectorTest {

    @Test
    fun `apache license 2_0 full phrase yields OSS`() {
        val text = "This work is licensed under the Apache License 2.0 (the \"License\")."
        assertEquals(LicenseZone.OSS, PdfLicenseDetector.detect(text))
    }

    @Test
    fun `spdx identifier Apache-2_0 yields OSS`() {
        val text = "SPDX-License-Identifier: Apache-2.0"
        assertEquals(LicenseZone.OSS, PdfLicenseDetector.detect(text))
    }

    @Test
    fun `creative commons full phrase yields OSS`() {
        val text = "This document is under a Creative Commons Attribution 4.0 License."
        assertEquals(LicenseZone.OSS, PdfLicenseDetector.detect(text))
    }

    @Test
    fun `CC-BY identifier yields OSS`() {
        val text = "Licensed CC-BY 4.0."
        assertEquals(LicenseZone.OSS, PdfLicenseDetector.detect(text))
    }

    @Test
    fun `CC0 identifier yields OSS`() {
        val text = "This work is marked CC0 1.0 Universal (CC0 1.0)."
        assertEquals(LicenseZone.OSS, PdfLicenseDetector.detect(text))
    }

    @Test
    fun `copyright sign yields CSS`() {
        val text = "© 2024 Some Publisher. All rights reserved."
        assertEquals(LicenseZone.CSS, PdfLicenseDetector.detect(text))
    }

    @Test
    fun `copyright word yields CSS`() {
        val text = "Copyright 2024 by Some Author."
        assertEquals(LicenseZone.CSS, PdfLicenseDetector.detect(text))
    }

    @Test
    fun `all rights reserved phrase yields CSS`() {
        val text = "All rights reserved. No part of this publication may be reproduced."
        assertEquals(LicenseZone.CSS, PdfLicenseDetector.detect(text))
    }

    @Test
    fun `no license mention yields UNKNOWN`() {
        val text = "Chapter 1\nIntroduction to Andragogy.\nThis chapter covers adult learning."
        assertEquals(LicenseZone.UNKNOWN, PdfLicenseDetector.detect(text))
    }

    @Test
    fun `blank text yields UNKNOWN`() {
        assertEquals(LicenseZone.UNKNOWN, PdfLicenseDetector.detect(""))
        assertEquals(LicenseZone.UNKNOWN, PdfLicenseDetector.detect("   \n  "))
    }

    @Test
    fun `apache takes precedence when both apache and copyright are present`() {
        val text = "Copyright 2024 Acme. Licensed under the Apache License 2.0."
        assertEquals(LicenseZone.OSS, PdfLicenseDetector.detect(text))
    }

    @Test
    fun `detection is case-insensitive`() {
        val text = "licensed under the APACHE LICENSE 2.0"
        assertEquals(LicenseZone.OSS, PdfLicenseDetector.detect(text))
    }
}