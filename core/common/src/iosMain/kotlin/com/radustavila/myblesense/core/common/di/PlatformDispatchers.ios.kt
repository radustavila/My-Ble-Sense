package com.radustavila.myblesense.core.common.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

internal actual val platformIoDispatcher: CoroutineDispatcher = Dispatchers.IO
