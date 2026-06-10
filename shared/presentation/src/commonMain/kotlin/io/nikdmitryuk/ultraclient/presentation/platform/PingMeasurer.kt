package io.nikdmitryuk.ultraclient.presentation.platform

expect suspend fun measurePingMs(): Long?

/**
 * Second probe: hostname reachability (DNS path vs plain-IP [measurePingMs]) — platform implementations differ.
 */
expect suspend fun measureDnsResolveMs(): Long?
