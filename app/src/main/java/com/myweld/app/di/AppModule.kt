package com.myweld.app.di

import com.myweld.app.data.ble.MyWeldBleManager
import com.myweld.app.data.repository.DeviceRepository
import com.myweld.app.data.repository.WelderRepository
import com.myweld.app.data.repository.WelderRepositoryImpl
import com.myweld.app.viewmodel.DashboardViewModel
import com.myweld.app.viewmodel.PresetsViewModel
import com.myweld.app.viewmodel.ScanViewModel
import com.myweld.app.viewmodel.SettingsViewModel
import com.myweld.app.viewmodel.FirmwareViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    // BLE
    single { MyWeldBleManager(androidContext(), get()) }

    // Repositories
    single<WelderRepository> { WelderRepositoryImpl(get()) }
    single { DeviceRepository(androidContext()) }

    // ViewModels
    viewModel { ScanViewModel(get(), get()) }
    viewModel { DashboardViewModel(get()) }
    viewModel { PresetsViewModel(get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
    viewModel { FirmwareViewModel(get()) }
}
