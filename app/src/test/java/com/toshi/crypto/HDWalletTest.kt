package com.toshi.crypto

import android.content.SharedPreferences
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito

class HDWalletTest {

    private val expectedMasterSeed = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
    // Path `m/0'/1/0/0
    private val expectedOwnerAddress = "0xa391af6a522436f335b7c6486640153641847ea2"

    // Path `m/44'/60'/0'/0'
    private val expectedPaymentAddress = "0x9858effd232b4033e47d90003d41ec34ecaeda94"

    // Mocks
    private lateinit var sharedPreferencesMock: SharedPreferences

    @Before
    fun setup() {
        sharedPreferencesMock = Mockito.mock(SharedPreferences::class.java)
        Mockito
                .`when`(sharedPreferencesMock.getString(anyString(), any()))
                .thenReturn(expectedMasterSeed)
    }

    @Test
    fun walletCreatedFromSeedUsesThatSeed() {
        val wallet = HDWallet(sharedPreferencesMock)
                .existingWallet
                .toBlocking()
                .value()
        assertThat<String>(wallet.masterSeed, `is`<String>(expectedMasterSeed))
    }

    @Test
    fun walletCreatedFromSeedDerivesCorrectOwnerAddress() {
        val wallet = HDWallet(sharedPreferencesMock)
                .existingWallet
                .toBlocking()
                .value()
        assertThat<String>(wallet.ownerAddress, `is`<String>(expectedOwnerAddress))
    }

    @Test
    fun walletCreatedFromSeedDerivesCorrectPaymentAddress() {
        val wallet = HDWallet(sharedPreferencesMock)
                .existingWallet
                .toBlocking()
                .value()
        assertThat<String>(wallet.paymentAddress, `is`<String>(expectedPaymentAddress))
    }
}