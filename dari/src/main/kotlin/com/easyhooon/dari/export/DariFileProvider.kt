package com.easyhooon.dari.export

import androidx.core.content.FileProvider

/**
 * Custom FileProvider subclass to avoid authority conflicts
 * with the host app's own FileProvider.
 */
class DariFileProvider : FileProvider()