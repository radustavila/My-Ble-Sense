package com.radustavila.myblesense.core.bluetooth.di

import com.radustavila.myblesense.domain.model.BleScanMode
import com.radustavila.myblesense.domain.model.BleScanSettings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlin.time.Duration.Companion.seconds

/**
 * Single place where the scan policy is configured, so the duration can be tuned (or overridden
 * in tests / other build variants) without touching the presentation layer.
 */
@Module
@InstallIn(SingletonComponent::class)
object ScanSettingsModule {

    @Provides
    fun provideScanSettings(): BleScanSettings = BleScanSettings(
        duration = SCAN_DURATION_SECONDS.seconds,
        mode = BleScanMode.BALANCED,
    )

    private const val SCAN_DURATION_SECONDS = 15
}
