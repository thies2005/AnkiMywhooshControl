package com.ankiride.bridge.mywhoosh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanAddressValidatorTest {

    @Test
    fun testNormalizeIpv4MappedHost() {
        assertEquals("127.0.0.1", LanAddressValidator.normalizeIpv4MappedHost("::ffff:127.0.0.1"))
        assertEquals("192.168.1.1", LanAddressValidator.normalizeIpv4MappedHost("::FFFF:192.168.1.1"))
        assertEquals("10.0.0.1", LanAddressValidator.normalizeIpv4MappedHost("0:0:0:0:0:ffff:10.0.0.1"))
        assertEquals("172.16.0.1", LanAddressValidator.normalizeIpv4MappedHost("0:0:0:0:0:FFFF:172.16.0.1"))
        assertEquals("192.168.1.50", LanAddressValidator.normalizeIpv4MappedHost("192.168.1.50"))
        assertEquals("127.0.0.1", LanAddressValidator.normalizeIpv4MappedHost("  ::ffff:127.0.0.1  "))
    }

    @Test
    fun testDualStackAddressesAllowed() {
        assertTrue(LanAddressValidator.isLocalOrLanAddress("::ffff:127.0.0.1"))
        assertTrue(LanAddressValidator.isLocalOrLanAddress("::ffff:192.168.1.1"))
        assertTrue(LanAddressValidator.isLocalOrLanAddress("::ffff:10.0.0.5"))
        assertTrue(LanAddressValidator.isLocalOrLanAddress("::ffff:172.16.1.1"))
        assertTrue(LanAddressValidator.isLocalOrLanAddress("0:0:0:0:0:ffff:127.0.0.1"))
        assertTrue(LanAddressValidator.isLocalOrLanAddress("0:0:0:0:0:ffff:192.168.0.100"))
        assertTrue(LanAddressValidator.isAllowedTargetHost("::ffff:127.0.0.1"))
        assertTrue(LanAddressValidator.isAllowedTargetHost("::ffff:192.168.1.1"))
    }

    @Test
    fun testDualStackPublicAddressesBlocked() {
        assertFalse(LanAddressValidator.isLocalOrLanAddress("::ffff:8.8.8.8"))
        assertFalse(LanAddressValidator.isLocalOrLanAddress("0:0:0:0:0:ffff:1.1.1.1"))
        assertFalse(LanAddressValidator.isAllowedTargetHost("::ffff:8.8.8.8"))
    }

    @Test
    fun testLoopbackAddressesAllowed() {
        assertTrue(LanAddressValidator.isLocalOrLanAddress("127.0.0.1"))
        assertTrue(LanAddressValidator.isLocalOrLanAddress("127.0.0.2"))
        assertTrue(LanAddressValidator.isLocalOrLanAddress("127.1.2.3"))
        assertTrue(LanAddressValidator.isLocalOrLanAddress("127.255.255.254"))
        assertTrue(LanAddressValidator.isAllowedTargetHost("127.0.0.1"))
    }

    @Test
    fun testPrivateSiteLocalIpsAllowed() {
        // 10.0.0.0/8
        assertTrue(LanAddressValidator.isLocalOrLanAddress("10.0.0.1"))
        assertTrue(LanAddressValidator.isLocalOrLanAddress("10.1.2.3"))
        assertTrue(LanAddressValidator.isLocalOrLanAddress("10.255.255.254"))

        // 172.16.0.0/12 (172.16.0.0 - 172.31.255.255)
        assertTrue(LanAddressValidator.isLocalOrLanAddress("172.16.0.1"))
        assertTrue(LanAddressValidator.isLocalOrLanAddress("172.20.10.2"))
        assertTrue(LanAddressValidator.isLocalOrLanAddress("172.31.255.254"))

        // 192.168.0.0/16
        assertTrue(LanAddressValidator.isLocalOrLanAddress("192.168.0.1"))
        assertTrue(LanAddressValidator.isLocalOrLanAddress("192.168.1.1"))
        assertTrue(LanAddressValidator.isLocalOrLanAddress("192.168.1.200"))
        assertTrue(LanAddressValidator.isLocalOrLanAddress("192.168.255.254"))
    }

    @Test
    fun testPublicInternetIpsBlocked() {
        assertFalse(LanAddressValidator.isLocalOrLanAddress("8.8.8.8"))
        assertFalse(LanAddressValidator.isLocalOrLanAddress("1.1.1.1"))
        assertFalse(LanAddressValidator.isLocalOrLanAddress("142.250.190.46"))
        assertFalse(LanAddressValidator.isLocalOrLanAddress("208.67.222.222"))
        assertFalse(LanAddressValidator.isLocalOrLanAddress("172.15.255.255")) // outside RFC1918 172.16
        assertFalse(LanAddressValidator.isLocalOrLanAddress("172.32.0.1")) // outside RFC1918 172.31
    }

    @Test
    fun testIpv6LoopbackBlockedByLiteralRegex() {
        // IPv6 literal rejected to avoid DNS / IPv6 scoping vulnerabilities
        assertFalse(LanAddressValidator.isLocalOrLanAddress("::1"))
        assertFalse(LanAddressValidator.isLocalOrLanAddress("[::1]"))
        assertFalse(LanAddressValidator.isAllowedTargetHost("::1"))
    }

    @Test
    fun testDomainNamesAndSsrfBlocked() {
        assertFalse(LanAddressValidator.isLocalOrLanAddress("localhost"))
        assertFalse(LanAddressValidator.isLocalOrLanAddress("google.com"))
        assertFalse(LanAddressValidator.isLocalOrLanAddress("mywhoosh.local"))
        assertFalse(LanAddressValidator.isLocalOrLanAddress("127.0.0.1.nip.io"))
        assertFalse(LanAddressValidator.isLocalOrLanAddress(""))
        assertFalse(LanAddressValidator.isLocalOrLanAddress("256.0.0.1"))
        assertFalse(LanAddressValidator.isLocalOrLanAddress("192.168.1.300"))
        assertFalse(LanAddressValidator.isLocalOrLanAddress("192.168.1"))
    }
}
