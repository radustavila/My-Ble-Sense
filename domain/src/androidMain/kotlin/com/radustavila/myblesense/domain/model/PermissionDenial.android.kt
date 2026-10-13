package com.radustavila.myblesense.domain.model

actual fun Throwable.isPermissionDenial(): Boolean = this is SecurityException
