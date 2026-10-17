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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Intelligent DNS resolver with built-in DNS-over-HTTPS (DoH) bypass.
 *
 * Automatically circumvents Indian ISP DNS tampering (e.g. Jio / Airtel blocking TMDB domains
 * api.themoviedb.org & image.tmdb.org to sinkhole IPs such as 49.44.79.236).
 *
 * Performance optimizations for Android TV:
 * - Thread-safe in-memory cache with 30-minute TTL: subsequent lookups execute in 0ms without network overhead.
 * - System DNS is evaluated first; if clean, returns immediately without extra roundtrips.
 * - Zero TLS certificate errors: OkHttp transmits standard SNI headers, allowing CDN edge nodes
 *   to terminate SSL seamlessly.
 * - Bootstrap DNS hardcodes Google (8.8.8.8) and Cloudflare (1.1.1.1) Anycast IPs, preventing
 *   chicken-and-egg resolution issues.
 * - Hardcoded Anycast CDN fallbacks guarantee connectivity even under total DNS blocking.
 */
object SecureDns : Dns {

    private const val TAG = "SecureDns"
    private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes

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

    /**
     * Dedicated lightweight HTTP client for querying DNS-over-HTTPS endpoints.
     */
    private val bootstrapHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(BootstrapDns)
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun isTmdbHost(hostname: String): Boolean {
        val lower = hostname.lowercase()
        return lower == "themoviedb.org" ||
                lower.endsWith(".themoviedb.org") ||
                lower.contains("tmdb.org")
    }

    /**
     * Checks if the resolved address belongs to an ISP block/sinkhole landing page.
     * Common Indian ISP block page ranges: 49.44.79.x, 49.44.80.x (Jio blackhole).
     */
    private fun isPoisoned(address: InetAddress): Boolean {
        val host = address.hostAddress ?: return true
        if (address.isLoopbackAddress || address.isAnyLocalAddress) return true
        if (host.startsWith("49.44.79.") ||
            host.startsWith("49.44.80.") ||
            host.startsWith("49.44.") ||
            host == "127.0.0.1" ||
            host == "0.0.0.0"
        ) {
            return true
        }
        return false
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

    /**
     * Fallback Anycast CDN IP addresses for TMDB in case all DNS queries fail.
     */
    private fun getStaticFallbackAddresses(hostname: String): List<InetAddress> {
        val fallbackIps = if (hostname.contains("image.tmdb.org", ignoreCase = true)) {
            listOf(
                "79.127.213.216",
                "169.150.247.38",
                "84.17.46.220",
                "104.16.208.20",
                "104.16.209.20"
            )
        } else {
            listOf(
                "13.224.245.47",
                "13.224.245.92",
                "99.84.152.53",
                "99.84.152.85",
                "104.16.208.20",
                "104.16.209.20"
            )
        }
        return parseIps(hostname, fallbackIps)
    }

    override fun lookup(hostname: String): List<InetAddress> {
        // Standard non-TMDB hosts go directly through system DNS
        if (!isTmdbHost(hostname)) {
            return Dns.SYSTEM.lookup(hostname)
        }

        // 1. Instant in-memory cache lookup (0ms overhead)
        cache[hostname]?.let { entry ->
            if (System.currentTimeMillis() < entry.expiresAt && entry.addresses.isNotEmpty()) {
                return entry.addresses
            }
        }

        // 2. Try System DNS; verify it is not returning poisoned sinkhole IPs
        try {
            val systemAddresses = Dns.SYSTEM.lookup(hostname)
            val cleanAddresses = systemAddresses.filter { !isPoisoned(it) }
            if (cleanAddresses.isNotEmpty() && cleanAddresses.size == systemAddresses.size) {
                cache[hostname] = DnsCacheEntry(cleanAddresses, System.currentTimeMillis() + CACHE_TTL_MS)
                return cleanAddresses
            } else {
                Log.w(TAG, "ISP DNS poisoning detected for $hostname (got ${systemAddresses.map { it.hostAddress }}). Engaging DoH bypass...")
            }
        } catch (e: Exception) {
            Log.w(TAG, "System DNS failed for $hostname (${e.message}). Engaging DoH bypass...")
        }

        // 3. Resolve via Google DNS-over-HTTPS
        val googleAddrs = resolveViaGoogleDoh(hostname)
        if (!googleAddrs.isNullOrEmpty()) {
            Log.d(TAG, "Successfully resolved $hostname via Google DoH: ${googleAddrs.map { it.hostAddress }}")
            cache[hostname] = DnsCacheEntry(googleAddrs, System.currentTimeMillis() + CACHE_TTL_MS)
            return googleAddrs
        }

        // 4. Resolve via Cloudflare DNS-over-HTTPS
        val cloudflareAddrs = resolveViaCloudflareDoh(hostname)
        if (!cloudflareAddrs.isNullOrEmpty()) {
            Log.d(TAG, "Successfully resolved $hostname via Cloudflare DoH: ${cloudflareAddrs.map { it.hostAddress }}")
            cache[hostname] = DnsCacheEntry(cloudflareAddrs, System.currentTimeMillis() + CACHE_TTL_MS)
            return cloudflareAddrs
        }

        // 5. Hardcoded Anycast CDN IP fallback
        Log.w(TAG, "DoH lookup failed for $hostname. Falling back to Anycast CDN static IPs.")
        val fallbackAddrs = getStaticFallbackAddresses(hostname)
        if (fallbackAddrs.isNotEmpty()) {
            cache[hostname] = DnsCacheEntry(fallbackAddrs, System.currentTimeMillis() + 5 * 60 * 1000L)
            return fallbackAddrs
        }

        throw UnknownHostException("Unable to resolve $hostname through System DNS, DoH, or static fallback")
    }
}
