package com.example

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.data.model.UserWallet
import com.example.ui.screens.DepositScreen
import com.example.ui.theme.KairoIaTheme
import com.example.ui.viewmodel.BankActionState
import com.example.ui.viewmodel.UiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DepositScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun depositRequiresMinimumTenEurosAndValidIbanBeforeSubmitting() {
        var submittedHolder = ""
        var submittedIban = ""
        var submittedAmount = ""

        val wallet = UserWallet(
            userId = "uid_1",
            displayName = "Camille Laurent",
            email = "camille@example.com",
            balanceCents = 0L,
        )

        composeRule.setContent {
            KairoIaTheme {
                DepositScreen(
                    walletState = UiState.Success(wallet),
                    bankActionState = BankActionState.Idle,
                    isStripeConfigured = true,
                    isStripeTestMode = true,
                    onSubmitSepaDeposit = { holder, iban, amount ->
                        submittedHolder = holder
                        submittedIban = iban
                        submittedAmount = amount
                    },
                    onClearFeedback = {},
                )
            }
        }

        // Initially IBAN is empty so submit is disabled
        composeRule.onNodeWithTag("submit_deposit_button").performScrollTo().assertIsNotEnabled()

        // Partial FR IBAN shows real-time length counter and keeps submit disabled
        composeRule.onNodeWithTag("deposit_iban_input").performScrollTo().performTextReplacement("FR763000")
        composeRule.onNodeWithTag("deposit_iban_validation_text", useUnmergedTree = true)
            .assertTextContains("France (FR) : 8/27 caractères", substring = true)
        composeRule.onNodeWithTag("submit_deposit_button").performScrollTo().assertIsNotEnabled()

        // Wrong format (letters in DE BBAN) shows country format error in real time
        composeRule.onNodeWithTag("deposit_iban_input").performScrollTo().performTextReplacement("DE89ABCD00440532013000")
        composeRule.onNodeWithTag("deposit_iban_validation_text", useUnmergedTree = true)
            .assertTextContains("Format Allemagne invalide", substring = true)
        composeRule.onNodeWithTag("submit_deposit_button").performScrollTo().assertIsNotEnabled()

        // Enter valid IBAN via test chip, then test < 10 EUR amount
        composeRule.onNodeWithTag("deposit_test_iban_chip").performScrollTo().performClick()
        composeRule.onNodeWithTag("deposit_iban_validation_text", useUnmergedTree = true)
            .assertTextContains("MOD-97 vérifié", substring = true)
        composeRule.onNodeWithTag("deposit_amount_input").performScrollTo().performTextReplacement("9.50")
        composeRule.onNodeWithTag("submit_deposit_button").performScrollTo().assertIsNotEnabled()

        // Enter >= 10 EUR amount -> button becomes enabled and submits cleanly
        composeRule.onNodeWithTag("deposit_amount_input").performScrollTo().performTextReplacement("25.50")
        composeRule.onNodeWithTag("submit_deposit_button").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("submit_deposit_button").performClick()

        assertEquals("Camille Laurent", submittedHolder)
        assertEquals("DE89370400440532013000", submittedIban)
        assertEquals("25.50", submittedAmount)
    }
}

