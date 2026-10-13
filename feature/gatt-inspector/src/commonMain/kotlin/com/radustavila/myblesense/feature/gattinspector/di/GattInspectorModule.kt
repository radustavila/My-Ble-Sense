package com.radustavila.myblesense.feature.gattinspector.di

import com.radustavila.myblesense.feature.gattinspector.GattInspectorViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val gattInspectorModule: Module = module {
    viewModelOf(::GattInspectorViewModel)
}
