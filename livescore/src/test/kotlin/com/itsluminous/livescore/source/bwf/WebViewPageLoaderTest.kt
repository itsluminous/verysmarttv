package com.itsluminous.livescore.source.bwf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The hidden view's reach (ADR-049): BWF's hosts and Cloudflare's challenge host, nothing else. Pure; the WebView itself is not built here. */
class WebViewPageLoaderTest {
    @Test
    fun bwfHostsAndTheChallengeHostAreAllowed() {
        assertTrue(WebViewPageLoader.allowed("match-centre.bwfbadminton.com"))
        assertTrue(WebViewPageLoader.allowed("extranet-lv.bwfbadminton.com"))
        assertTrue(WebViewPageLoader.allowed("bwfbadminton.com"))
        assertTrue(WebViewPageLoader.allowed("BWFBadminton.com."))
        assertTrue(WebViewPageLoader.allowed("challenges.cloudflare.com"))
    }

    @Test
    fun everyOtherHostIsRefused() {
        assertFalse(WebViewPageLoader.allowed(null))
        assertFalse(WebViewPageLoader.allowed(""))
        assertFalse(WebViewPageLoader.allowed("www.google-analytics.com"))
        assertFalse(WebViewPageLoader.allowed("fonts.googleapis.com"))
        assertFalse(WebViewPageLoader.allowed("cloudflare.com"))
        assertFalse(WebViewPageLoader.allowed("notbwfbadminton.com"))
        assertFalse(WebViewPageLoader.allowed("bwfbadminton.com.evil.example"))
    }

    @Test
    fun thePageAndTheApiAreOnAllowedHosts() {
        assertTrue(WebViewPageLoader.allowed(java.net.URI(WebViewPageLoader.PAGE).host))
        assertTrue(WebViewPageLoader.allowed(java.net.URI(WebViewPageLoader.API).host))
    }
}
