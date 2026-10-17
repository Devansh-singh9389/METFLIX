/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.metflix.data.network

import android.util.Log
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * High-performance DNS resolver with instant zero-delay TMDB resolution.
 *
 * Bypasses Indian ISP DNS tampering (e.g. Jio/Airtel blocking api.themoviedb.org & image.tmdb.org
 * to 49.44.79.236 or 2405:200:1607:2820:41::36 sinkhole IPs).
 *
 * Architecture & Speed Guarantees:
 * 1. Pre-warmed Anycast CDN cache: Lookups for TMDB API and image CDN return in 0ms immediately on app boot.
 * 2. Never queries local system DNS for TMDB: Eliminates the 20+ second connection timeout caused by ISP sinkhole IPs.
 * 3. Asynchronous background DoH: Refreshes DNS-over-HTTPS via Google & Cloudflare in the background without blocking the UI or network calls.
 * 4. Comprehensive sinkhole filtering: Blocks both IPv4 (49.44.x.x) and IPv6 (2405:x:x:x) ISP landing pages.
 */
object SecureDns : Dns {

    private const val TAG = "SecureDns"
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours

    private data class DnsCacheEntry(
        val addresses: List<InetAddress>,
        val expiresAt: Long
    )

    private val cache = ConcurrentHashMap<String, DnsCacheEntry>()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Serializable
    private data class DohResponse(
        val Status: Int? = null,
        val Answer: List<DohAnswer>? = null
    )

    @Serializable
    private data class DohAnswer(
        val name: String? = null,
        val type: Int? = null,
        val TTL: Long? = null,
        val data: String? = null
    )

    /**
     * Bootstrap DNS that resolves DoH servers without relying on the local ISP's DNS.
     */
    private object BootstrapDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return when (hostname.lowercase()) {
                "dns.google" -> listOf(
                    InetAddress.getByAddress("dns.google", byteArrayOf(8, 8, 8, 8)),
                    InetAddress.getByAddress("dns.google", byteArrayOf(8, 8, 4, 4))
                )
                "cloudflare-dns.com" -> listOf(
                    InetAddress.getByAddress("cloudflare-dns.com", byteArrayOf(1, 1, 1, 1)),
                    InetAddress.getByAddress("cloudflare-dns.com", byteArrayOf(1, 0, 0, 1))
                )
                "1.1.1.1" -> listOf(
                    InetAddress.getByAddress("1.1.1.1", byteArrayOf(1, 1, 1, 1))
                )
                else -> Dns.SYSTEM.lookup(hostname)
            }
        }
    }

    private val bootstrapHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(BootstrapDns)
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // Blacklist of CloudFront & BunnyCDN IPs that stall or suffer severe packet loss on Indian ISPs
    private val blacklistedIps = setOf(
        "13.224.245.47",
        "99.84.152.85",
        "99.84.152.8",
        "84.17.46.220"
    )

    init {
        // Pre-warm verified, unblocked Anycast CDN IP addresses for 0ms instant startup
        prewarmCache()
    }

    private fun prewarmCache() {
        val now = System.currentTimeMillis()
        val expires = now + CACHE_TTL_MS

        // TMDB API is globally accelerated via Amazon CloudFront
        // Filtered to verified sub-second latency IPs in India on Jio/Airtel networks
        val apiIps = listOf(
            "13.224.245.63",
            "13.224.245.44",
            "13.224.245.92",
            "99.84.152.53",
            "99.84.152.32"
        )
        val apiAddrs = parseIps("api.themoviedb.org", apiIps)
        if (apiAddrs.isNotEmpty()) {
            cache["api.themoviedb.org"] = DnsCacheEntry(apiAddrs, expires)
            cache["themoviedb.org"] = DnsCacheEntry(apiAddrs, expires)
        }

        // TMDB Image CDN is globally accelerated via BunnyCDN
        // Filtered to verified responsive nodes in India
        val imageIps = listOf(
            "169.150.207.212",
            "79.127.213.216",
            "169.150.207.217",
            "169.150.247.38",
            "84.17.38.227"
        )
        val imageAddrs = parseIps("image.tmdb.org", imageIps)
        if (imageAddrs.isNotEmpty()) {
            cache["image.tmdb.org"] = DnsCacheEntry(imageAddrs, expires)
        }

        Log.i(TAG, "Pre-warmed SecureDns with verified sub-second Anycast IPs.")
    }

    private fun isTmdbHost(hostname: String): Boolean {
        val lower = hostname.lowercase()
        return lower == "themoviedb.org" ||
                lower.endsWith(".themoviedb.org") ||
                lower.contains("tmdb.org")
    }

    /**
     * Filters out ISP sinkhole IPs and known stalled/dropped IPs.
     */
    private fun isPoisoned(address: InetAddress): Boolean {
        val host = address.hostAddress ?: return true
        if (address.isLoopbackAddress || address.isAnyLocalAddress) return true
        if (host in blacklistedIps) return true
        if (host.startsWith("49.44.") ||
            host.startsWith("2405:200:") ||
            host.startsWith("2405:") ||
            host == "127.0.0.1" ||
            host == "0.0.0.0" ||
            host == "::1"
        ) {
            return true
        }
        return false
    }

    /**
     * Promotes an IP that succeeded quickly to index 0 for future zero-delay connections.
     */
    fun recordSuccess(hostname: String, address: InetAddress) {
        val entry = cache[hostname] ?: return
        val list = entry.addresses
        val idx = list.indexOfFirst { it.hostAddress == address.hostAddress }
        if (idx > 0) {
            val reordered = ArrayList<InetAddress>(list.size).apply {
                add(list[idx])
                for (i in list.indices) {
                    if (i != idx) add(list[i])
                }
            }
            cache[hostname] = DnsCacheEntry(reordered, entry.expiresAt)
        }
    }

    private fun parseIps(hostname: String, ipStrings: List<String>): List<InetAddress> {
        return ipStrings.mapNotNull { ipStr ->
            try {
                val cleanIp = ipStr.trim().trimEnd('.')
                val inet = InetAddress.getByName(cleanIp)
                if (!isPoisoned(inet)) {
                    InetAddress.getByAddress(hostname, inet.address)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun resolveViaGoogleDoh(hostname: String): List<InetAddress>? {
        return runCatching {
            val request = Request.Builder()
                .url("https://dns.google/resolve?name=$hostname&type=A")
                .header("Accept", "application/json")
                .header("User-Agent", "MetFlix-AndroidTV/1.0")
                .build()

            bootstrapHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string().orEmpty()
                val parsed = json.decodeFromString<DohResponse>(body)
                val ips = parsed.Answer
                    ?.filter { it.type == 1 && !it.data.isNullOrBlank() }
                    ?.map { it.data!! }
                    .orEmpty()
                parseIps(hostname, ips).takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    private fun resolveViaCloudflareDoh(hostname: String): List<InetAddress>? {
        return runCatching {
            val request = Request.Builder()
                .url("https://cloudflare-dns.com/dns-query?name=$hostname&type=A")
                .header("Accept", "application/dns-json")
                .header("User-Agent", "MetFlix-AndroidTV/1.0")
                .build()

            bootstrapHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string().orEmpty()
                val parsed = json.decodeFromString<DohResponse>(body)
                val ips = parsed.Answer
                    ?.filter { it.type == 1 && !it.data.isNullOrBlank() }
                    ?.map { it.data!! }
                    .orEmpty()
                parseIps(hostname, ips).takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
    }

    private fun refreshDohInBackground(hostname: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val freshAddrs = resolveViaGoogleDoh(hostname) ?: resolveViaCloudflareDoh(hostname)
            if (!freshAddrs.isNullOrEmpty()) {
                cache[hostname] = DnsCacheEntry(freshAddrs, System.currentTimeMillis() + CACHE_TTL_MS)
                Log.d(TAG, "Background DoH refreshed $hostname: ${freshAddrs.map { it.hostAddress }}")
            }
        }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        // Standard non-TMDB hosts use system DNS
        if (!isTmdbHost(hostname)) {
            return Dns.SYSTEM.lookup(hostname)
        }

        // 1. Instant cache lookup (0ms overhead)
        val entry = cache[hostname]
        if (entry != null && entry.addresses.isNotEmpty()) {
            // If near expiry, refresh in background without stalling the network call
            if (System.currentTimeMillis() > entry.expiresAt - 60_000L) {
                refreshDohInBackground(hostname)
            }
            return entry.addresses
        }

        // 2. If for any reason cache was empty, resolve via DoH synchronously
        val dohAddrs = resolveViaGoogleDoh(hostname) ?: resolveViaCloudflareDoh(hostname)
        if (!dohAddrs.isNullOrEmpty()) {
            cache[hostname] = DnsCacheEntry(dohAddrs, System.currentTimeMillis() + CACHE_TTL_MS)
            return dohAddrs
        }

        // 3. Fallback: Re-populate prewarm cache
        prewarmCache()
        cache[hostname]?.addresses?.let { return it }

        throw UnknownHostException("Unable to resolve $hostname through SecureDns")
    }
}
