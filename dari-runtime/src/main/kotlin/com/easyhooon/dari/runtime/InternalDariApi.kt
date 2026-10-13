package com.easyhooon.dari.runtime

/** Shared between Dari artifacts only; may change without notice. Apps should use `Dari`. */
@RequiresOptIn(
    message = "Internal Dari API shared between Dari artifacts. Use the Dari object instead.",
    level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY)
annotation class InternalDariApi
