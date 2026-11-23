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
import com.example.ui.screens.WithdrawalScreen
import com.example.ui.theme.KairoIaTheme
import com.example.ui.viewmodel.BankActionState
import com.example.ui.viewmodel.UiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WithdrawalScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun withdrawalEnforcesTenEuroMinAvailableBalanceAndValidIban() {
        var submittedHolder = ""
        var submittedIban = ""
        var submittedAmount = ""

        val wallet = UserWallet(
            userId = "uid_1",
            displayName = "Camille Laurent",
            email = "camille@example.com",
            balanceCents = 5000L, // 50,00 EUR available
        )

        composeRule.setContent {
            KairoIaTheme {
                WithdrawalScreen(
                    walletState = UiState.Success(wallet),
                    bankActionState = BankActionState.Idle,
                    isStripeConfigured = true,
                    isStripeTestMode = true,
                    onSubmitSepaWithdrawal = { holder, iban, amount ->
                        submittedHolder = holder
                        submittedIban = iban
                        submittedAmount = amount
                    },
                    onClearFeedback = {},
                )
            }
        }

        // Initially IBAN is empty -> disabled
        composeRule.onNodeWithTag("submit_withdraw_button").performScrollTo().assertIsNotEnabled()

        // Partial FR IBAN shows real-time length counter and keeps submit disabled
        composeRule.onNodeWithTag("withdraw_iban_input").performScrollTo().performTextReplacement("FR763000")
        composeRule.onNodeWithTag("withdraw_iban_validation_text", useUnmergedTree = true)
            .assertTextContains("France (FR) : 8/27 caractères", substring = true)
        composeRule.onNodeWithTag("submit_withdraw_button").performScrollTo().assertIsNotEnabled()

        // Bad MOD-97 checksum shows error in real time
        composeRule.onNodeWithTag("withdraw_iban_input").performScrollTo().performTextReplacement("DE89370400440532013001")
        composeRule.onNodeWithTag("withdraw_iban_validation_text", useUnmergedTree = true)
            .assertTextContains("MOD-97", substring = true)
        composeRule.onNodeWithTag("submit_withdraw_button").performScrollTo().assertIsNotEnabled()

        // Fill valid test IBAN
        composeRule.onNodeWithTag("withdraw_test_iban_chip").performScrollTo().performClick()
        composeRule.onNodeWithTag("withdraw_iban_validation_text", useUnmergedTree = true)
            .assertTextContains("MOD-97 vérifié", substring = true)

        // Below 10 EUR -> disabled
        composeRule.onNodeWithTag("withdraw_amount_input").performScrollTo().performTextReplacement("9.99")
        composeRule.onNodeWithTag("submit_withdraw_button").performScrollTo().assertIsNotEnabled()

        // Above available balance (60 > 50) -> disabled
        composeRule.onNodeWithTag("withdraw_amount_input").performScrollTo().performTextReplacement("60")
        composeRule.onNodeWithTag("submit_withdraw_button").performScrollTo().assertIsNotEnabled()

        // Valid amount (25 <= 50 and >= 10) -> enabled & submits cleanly
        composeRule.onNodeWithTag("withdraw_amount_input").performScrollTo().performTextReplacement("25")
        composeRule.onNodeWithTag("submit_withdraw_button").performScrollTo().assertIsEnabled()
        composeRule.onNodeWithTag("submit_withdraw_button").performClick()

        assertEquals("Camille Laurent", submittedHolder)
        assertEquals("DE89370400440532013000", submittedIban)
        assertEquals("25", submittedAmount)
    }
}

