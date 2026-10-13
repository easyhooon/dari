package com.easyhooon.dari.desktop.app.update

/** `major.minor.patch`, the format Dari releases are tagged with. */
data class SemanticVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    override fun toString() = "$major.$minor.$patch"

    companion object {
        private val PATTERN = Regex("""v?(\d+)\.(\d+)\.(\d+)""")

        fun parse(text: String): SemanticVersion? = PATTERN.matchEntire(text.trim())?.destructured
            ?.let { (major, minor, patch) -> SemanticVersion(major.toInt(), minor.toInt(), patch.toInt()) }
    }
}
