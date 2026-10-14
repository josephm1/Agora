package com.newoether.agora.data.repository

import kotlinx.coroutines.launch

/** Integration preference writes use the existing Settings owner and DataStore authority. */
fun SettingsRepository.setNotesIntegrationEnabled(enabled:Boolean) = scope.launch {
    settingsManager.saveNotesIntegrationEnabled(enabled)
}
fun SettingsRepository.setNotesPageImagesEnabled(enabled:Boolean) = scope.launch {
    settingsManager.saveNotesPageImagesEnabled(enabled)
}
