/*
 * Copyright 2026 Janada Sroor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jnd.ngdroid.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Online/offline monitor backed by [ConnectivityManager].
 * Fail-open: if the check itself throws, we assume online so users are
 * never blocked by a broken query — only a confirmed loss shows offline.
 */
class NetworkMonitor(context: Context) {

    private val appContext = context.applicationContext
    private val connectivity: ConnectivityManager? =
        appContext.getSystemService(ConnectivityManager::class.java)

    private val _isOnline = MutableStateFlow(query())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = true
        }

        override fun onLost(network: Network) {
            _isOnline.value = query()
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            _isOnline.value = query()
        }
    }

    private var started = false

    fun start() {
        if (started) return
        started = true
        runCatching { connectivity?.registerDefaultNetworkCallback(callback) }
    }

    fun stop() {
        if (!started) return
        started = false
        runCatching { connectivity?.unregisterNetworkCallback(callback) }
    }

    private fun query(): Boolean = runCatching {
        val cm = connectivity ?: return@runCatching true
        val network = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(network) ?: return@runCatching false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }.getOrDefault(true)
}
