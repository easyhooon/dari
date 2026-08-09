package com.easyhooon.dari

import platform.Foundation.NSDate

internal actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1_000.0).toLong()
