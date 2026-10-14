package com.ankiride.bridge.mywhoosh

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LanAddressValidatorTest {

    @Test
    fun testLoopbackAddressesAllowed() {
        assertTrue(LanAddressValidator.isLocalOrLanAddress("127.0.0.1"))
        assertTrue(LanAddressValidator.isLocalOrLanAddress("127.0.0.2"))
        assertTrue(LanAddressValidator.isAllowedTargetHost("127.0.0.1"))
    }

    @Test
    fun testPrivateSiteLocalIpsAllowed() {
        // 10.0.0.0/8
        assertTrue(LanAddressValidator.isLocalOrLanAddress("10.0.0.1"))
        assertTrue(LanAddressValidator.isLocalOrLanAddress("10.1.2.3"))

        // 172.16.0.0/12
        assertTrue(LanAddressValidator.isLocalOrLanAddress("172.16.0.1"))
        assertTrue(LanAddressValidator.isLocalOrLanAddress("172.20.10.2"))
        assertTrue(LanAddressValidator.isLocalOrLanAddress("172.31.255.254"))

        // 192.168.0.0/16
        assertTrue(LanAddressValidator.isLocalOrLanAddress("192.168.1.1"))
        assertTrue(LanAddressValidator.isLocalOrLanAddress("192.168.1.200"))
    }

    @Test
    fun testPublicInternetIpsBlocked() {
        assertFalse(LanAddressValidator.isLocalOrLanAddress("8.8.8.8"))
        assertFalse(LanAddressValidator.isLocalOrLanAddress("1.1.1.1"))
        assertFalse(LanAddressValidator.isLocalOrLanAddress("142.250.190.46"))
        assertFalse(LanAddressValidator.isLocalOrLanAddress("208.67.222.222"))
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
        assertFalse(LanAddressValidator.isLocalOrLanAddress("::1")) // IPv6 literal blocked by IPv4 literal regex
    }
}
