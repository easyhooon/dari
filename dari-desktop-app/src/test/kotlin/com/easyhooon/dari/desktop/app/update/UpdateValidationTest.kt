package com.easyhooon.dari.desktop.app.update

import com.easyhooon.dari.desktop.app.update.UpdateValidation.BundleInfo
import kotlin.test.Test
import kotlin.test.assertFailsWith

class UpdateValidationTest {
    private val id = "io.github.easyhooon.dari.desktop"
    private val installed = BundleInfo(id, "2.0.0")
    private val expected = SemanticVersion(2, 1, 0)

    @Test
    fun `accepts the expected newer Dari`() {
        UpdateValidation.validateMetadata(installed, BundleInfo(id, "2.1.0"), expected)
    }

    @Test
    fun `accepts metadata for the 2_1_0 to 2_1_1 update`() {
        UpdateValidation.validateMetadata(
            BundleInfo(id, "2.1.0"),
            BundleInfo(id, "2.1.1"),
            SemanticVersion(2, 1, 1),
        )
    }

    @Test
    fun `rejects another app, another version, or a downgrade`() {
        listOf(
            BundleInfo("com.example.other", "2.1.0"),
            BundleInfo(null, "2.1.0"),
            BundleInfo(id, "2.2.0"),
            BundleInfo(id, null),
        ).forEach { candidate ->
            assertFailsWith<UpdateValidation.Failure>(candidate.toString()) {
                UpdateValidation.validateMetadata(installed, candidate, expected)
            }
        }
        assertFailsWith<UpdateValidation.Failure> {
            UpdateValidation.validateMetadata(BundleInfo(id, "2.1.0"), BundleInfo(id, "2.1.0"), expected)
        }
        assertFailsWith<UpdateValidation.Failure> {
            UpdateValidation.validateMetadata(BundleInfo(null, "2.0.0"), BundleInfo(null, "2.1.0"), expected)
        }
    }
}
