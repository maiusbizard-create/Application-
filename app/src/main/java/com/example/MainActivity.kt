package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.repository.AiRobotRepository
import com.example.data.repository.WalletRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOut
import com.example.ui.screens.BankFlowMode
import com.example.ui.screens.BankSepaScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.RobotIaScreen
import com.example.ui.theme.KairoIaTheme
import com.example.ui.viewmodel.KairoViewModel
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

enum class AppTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    DASHBOARD("Tableau", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard, "nav_dashboard"),
    ROBOT("Robot IA", Icons.Rounded.SmartToy, Icons.Outlined.SmartToy, "nav_robot"),
    BANK("Banque", Icons.Rounded.AccountBalance, Icons.Outlined.AccountBalance, "nav_bank"),
    PROFILE("Profil", Icons.Rounded.Person, Icons.Outlined.Person, "nav_profile"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // App Check MUST run before setContent, ViewModels, or Firebase operations.
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            KairoIaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent()
                }
            }
        }
    }
}

@Composable
fun AppContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                onUnauthenticated = {},
                scope = scope,
            )
        }
    }

    val user = currentUser
    if (user == null) {
        AuthScreen(
            onAuthSuccess = { currentUser = Firebase.auth.currentUser }
        )
    } else {
        key(user.uid) {
            AuthenticatedMainShell(
                user = user,
                onSignOut = {
                    signOut(
                        credentialManager = credentialManager,
                        scope = scope,
                        onSignOutComplete = { currentUser = null }
                    )
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedMainShell(
    user: FirebaseUser,
    onSignOut: () -> Unit,
) {
    val currentUserId = user.uid
    val displayName = user.displayName ?: "Investisseur KairoIA"
    val email = user.email ?: "investisseur@kairoia.app"

    val viewModel: KairoViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                KairoViewModel(
                    walletRepository = WalletRepository(db = db),
                    aiRobotRepository = AiRobotRepository(),
                    currentUserId = currentUserId,
                    displayName = displayName,
                    email = email,
                )
            }
        }
    )

    val walletState by viewModel.walletState.collectAsStateWithLifecycle()
    val transactionsState by viewModel.transactionsState.collectAsStateWithLifecycle()
    val analysesState by viewModel.analysesState.collectAsStateWithLifecycle()
    val botMessagesState by viewModel.botMessagesState.collectAsStateWithLifecycle()
    val aiPlanState by viewModel.aiPlanState.collectAsStateWithLifecycle()
    val chatStreamState by viewModel.chatStreamState.collectAsStateWithLifecycle()
    val bankActionState by viewModel.bankActionState.collectAsStateWithLifecycle()
    val refreshingTxId by viewModel.refreshingTxId.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(AppTab.DASHBOARD) }
    var bankFlowMode by remember { mutableStateOf(BankFlowMode.DEPOSIT) }

    if (selectedTab != AppTab.DASHBOARD) {
        BackHandler {
            selectedTab = AppTab.DASHBOARD
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useRail) {
                    NavigationBar {
                        AppTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail {
                        AppTab.entries.forEach { tab ->
                            val selected = selectedTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectedTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        AppTab.DASHBOARD -> DashboardScreen(
                            walletState = walletState,
                            transactionsState = transactionsState,
                            analysesState = analysesState,
                            isStripeConfigured = viewModel.isStripeConfigured,
                            isStripeTestMode = viewModel.isStripeTestMode,
                            refreshingTxId = refreshingTxId,
                            onNavigateToDeposit = {
                                bankFlowMode = BankFlowMode.DEPOSIT
                                selectedTab = AppTab.BANK
                            },
                            onNavigateToWithdraw = {
                                bankFlowMode = BankFlowMode.WITHDRAWAL
                                selectedTab = AppTab.BANK
                            },
                            onNavigateToRobot = { selectedTab = AppTab.ROBOT },
                            onRefreshTx = viewModel::refreshTransaction,
                        )
                        AppTab.ROBOT -> RobotIaScreen(
                            walletState = walletState,
                            aiPlanState = aiPlanState,
                            analysesState = analysesState,
                            botMessagesState = botMessagesState,
                            chatStreamState = chatStreamState,
                            onUpdateBotSettings = viewModel::updateBotSettings,
                            onGenerateAnalysis = viewModel::generateAiAnalysis,
                            onSendChat = viewModel::sendChatMessage,
                            onDismissChatError = viewModel::clearChatError,
                        )
                        AppTab.BANK -> BankSepaScreen(
                            initialMode = bankFlowMode,
                            walletState = walletState,
                            transactionsState = transactionsState,
                            bankActionState = bankActionState,
                            isStripeConfigured = viewModel.isStripeConfigured,
                            isStripeTestMode = viewModel.isStripeTestMode,
                            refreshingTxId = refreshingTxId,
                            onDeposit = viewModel::executeSepaDeposit,
                            onWithdraw = viewModel::executeSepaWithdrawal,
                            onRefreshTx = viewModel::refreshTransaction,
                            onClearFeedback = viewModel::clearBankActionFeedback,
                        )
                        AppTab.PROFILE -> ProfileScreen(
                            walletState = walletState,
                            userEmail = email,
                            userDisplayName = displayName,
                            onSignOutClick = onSignOut,
                        )
                    }
                }
            }
        }
    }
}
