package com.ekam.baton.di

import com.ekam.baton.MainViewModel
import com.ekam.baton.ui.auth.AuthViewModel
import com.ekam.baton.feature.agents.AgentsViewModel
import com.ekam.baton.feature.agents.a2a.A2AViewModel
import com.ekam.baton.feature.agents.tunnel.TunnelSetupViewModel
import com.ekam.baton.feature.chat.ChatViewModel
import com.ekam.baton.feature.memory.MemoryViewModel
import com.ekam.baton.feature.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    viewModelOf(::MainViewModel)
    viewModelOf(::AuthViewModel)
    viewModelOf(::AgentsViewModel)
    viewModelOf(::A2AViewModel)
    viewModelOf(::TunnelSetupViewModel)
    viewModelOf(::ChatViewModel)
    viewModelOf(::MemoryViewModel)
    viewModelOf(::SettingsViewModel)
}
