package com.personal.cardvault

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import android.Manifest
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Surface
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.cardvault.backup.BackupContent
import com.personal.cardvault.backup.BackupFileCodec
import com.personal.cardvault.backup.toExpiryItemEntity
import com.personal.cardvault.backup.toBackupCard
import com.personal.cardvault.backup.toBackupExpiryItem
import com.personal.cardvault.data.AppSettings
import com.personal.cardvault.data.expiryDatabase
import com.personal.cardvault.expiry.ExpiryEditViewModel
import com.personal.cardvault.expiry.ExpiryHomeViewModel
import com.personal.cardvault.expiry.ExpiryNotificationAction
import com.personal.cardvault.expiry.ExpiryNotificationTarget
import com.personal.cardvault.expiry.createExpiryNotificationChannel
import com.personal.cardvault.expiry.parseExpiryNotificationUri
import com.personal.cardvault.expiry.runExpiryReminderStartup
import com.personal.cardvault.ui.components.CardFacePreset
import com.personal.cardvault.ui.components.NavTab
import com.personal.cardvault.ui.screens.expiry.ExpiryDeleteConfirmScreen
import com.personal.cardvault.ui.screens.expiry.ExpiryEditScreen
import com.personal.cardvault.ui.screens.backup.BackupExportScreen
import com.personal.cardvault.ui.screens.backup.BackupRestoreScreen
import com.personal.cardvault.ui.screens.backup.RestoreStage
import com.personal.cardvault.ui.screens.expiry.ExpiryHomeScreen
import com.personal.cardvault.ui.screens.settings.SettingsScreen
import com.personal.cardvault.ui.screens.vault.CandidateSkinUiItem
import com.personal.cardvault.ui.screens.vault.CardDetailScreen
import com.personal.cardvault.ui.screens.vault.CardEditScreen
import com.personal.cardvault.ui.screens.vault.CardItemUiModel
import com.personal.cardvault.ui.screens.vault.VaultCardLayoutMode
import com.personal.cardvault.ui.screens.vault.VaultScreen
import com.personal.cardvault.ui.theme.AppThemePreset
import com.personal.cardvault.ui.theme.CardVaultTheme
import com.personal.cardvault.ui.theme.ThemeDarkMode
import com.personal.cardvault.ui.theme.resolveDarkTheme
import com.personal.cardvault.ui.screens.vault.VaultUiState
import com.personal.cardvault.vault.BinInfo
import com.personal.cardvault.vault.BinLookupEngine
import com.personal.cardvault.vault.CardArtRepository
import com.personal.cardvault.vault.CardPayload
import com.personal.cardvault.vault.RemoteCardSkin
import com.personal.cardvault.vault.UnlockedPaymentCard
import com.personal.cardvault.vault.VaultClipboardController
import com.personal.cardvault.vault.VaultCreationController
import com.personal.cardvault.vault.VaultSessionController
import com.personal.cardvault.vault.VaultRestoreController
import com.personal.cardvault.vault.VaultKeyStore
import com.personal.cardvault.vault.VaultKekSecurityLevel
import com.personal.cardvault.vault.formatExpiry
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : FragmentActivity() {
    private lateinit var appSettings: AppSettings
    private var earlyReminderEnabled by mutableStateOf(true)
    private var hideNameOnLockScreen by mutableStateOf(false)
    private var screenshotProtectionEnabled by mutableStateOf(true)
    private var themePreset by mutableStateOf(AppThemePreset.MONET)
    private var themeDarkMode by mutableStateOf(ThemeDarkMode.SYSTEM)
    private var amoledDarkEnabled by mutableStateOf(false)
    private var customSeedArgb by mutableStateOf(AppThemePreset.DEFAULT_CUSTOM_SEED_ARGB)
    private var cardFaceServerUrl by mutableStateOf("")
    private var notificationPermissionGranted by mutableStateOf(false)
    private var notificationTarget by mutableStateOf<ExpiryNotificationTarget?>(null)
    private var vaultUiState by mutableStateOf(VaultUiState.NOT_CREATED)
    private var vaultCards by mutableStateOf<List<CardItemUiModel>>(emptyList())
    private var vaultLayoutMode by mutableStateOf(VaultCardLayoutMode.WALLET_STACK)
    private var vaultFocusedCardId by mutableStateOf<Int?>(null)
    private var pendingVaultAction by mutableStateOf<PendingVaultAction?>(null)
    private var selectedVaultCard by mutableStateOf<UnlockedPaymentCard?>(null)
    private var vaultNumberMasked by mutableStateOf(true)
    private var vaultCvvMasked by mutableStateOf(true)
    private var editingVaultCardId: Int? = null
    private var editingVaultCardPayload by mutableStateOf(CardPayload())
    private var editingCardDetectedBin by mutableStateOf<BinInfo?>(null)
    private var editingCardSkinQuery by mutableStateOf("")
    private var editingCardCandidateSkins by mutableStateOf<List<CandidateSkinUiItem>>(emptyList())
    private var editingCardSearchingSkins by mutableStateOf(false)
    private var cardArtCache by mutableStateOf<Map<String, ImageBitmap>>(emptyMap())
    private var binLookupJob: Job? = null
    private var skinSearchJob: Job? = null
    private var vaultHasSecureScreenLock by mutableStateOf(true)
    private var vaultSecurityLevel by mutableStateOf(VaultSecurityLevel.NOT_CREATED)
    private var vaultDeletionConfirmDialog: android.app.AlertDialog? = null
    private var vaultResetConfirmDialog: android.app.AlertDialog? = null
    private lateinit var vaultMetaDao: com.personal.cardvault.data.VaultMetaDao
    private lateinit var vaultClipboardController: VaultClipboardController
    private lateinit var vaultCreationController: VaultCreationController
    private lateinit var vaultSessionController: VaultSessionController
    private lateinit var binLookupEngine: BinLookupEngine
    private lateinit var cardArtRepository: CardArtRepository
    private var backupExportUri: Uri? = null
    private var backupExportFileName by mutableStateOf<String?>(null)
    private var backupPassword by mutableStateOf("")
    private var backupConfirmPassword by mutableStateOf("")
    private var backupExporting by mutableStateOf(false)
    private var backupExportError by mutableStateOf<String?>(null)
    private var backupRestoreFile: ByteArray? = null
    private var backupRestoreContent: BackupContent? = null
    private var backupRestoreFileName by mutableStateOf<String?>(null)
    private var backupRestorePassword by mutableStateOf("")
    private var backupRestoreStage by mutableStateOf(RestoreStage.INPUT)
    private var backupRestoreError by mutableStateOf<String?>(null)
    private var backupRestoreReturnDestination: Destination = Destination.Settings
    private lateinit var vaultRestoreController: VaultRestoreController

    private val openBackupDocument = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val file = runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Cannot open backup source")
                }
            }.getOrNull()
            if (file == null || runCatching { BackupFileCodec.preflight(file) }.isFailure) {
                clearBackupRestoreState(keepError = true)
                backupRestoreError = "不是支持的备份文件"
                return@launch
            }
            backupRestoreFile = file
            backupRestoreFileName = contentResolver.backupDocumentDisplayName(uri)
            backupRestoreError = null
        }
    }

    private val createBackupDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        backupExportUri = uri
        backupExportFileName = contentResolver.backupDocumentDisplayName(uri)
        backupExportError = null
    }

    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { notificationPermissionGranted = hasNotificationPermission() }

    private val pickCardArt = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val fileName = cardArtRepository.importFromUri(uri)
            if (fileName == null) {
                Toast.makeText(this@MainActivity, "无法读取该图片", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val bitmap = cardArtRepository.loadLocalArt(fileName)
            if (bitmap != null) {
                cardArtCache = cardArtCache + (fileName to bitmap)
            }
            updateEditingVaultCard { copy(artFileName = fileName) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        createExpiryNotificationChannel(this)
        appSettings = AppSettings(this)
        earlyReminderEnabled = appSettings.earlyReminderEnabled
        hideNameOnLockScreen = appSettings.hideNameOnLockScreen
        screenshotProtectionEnabled = appSettings.screenshotProtectionEnabled
        themePreset = AppThemePreset.fromNameOrDefault(
            name = appSettings.themePreset,
            fallbackDynamic = appSettings.dynamicColorEnabled,
        )
        themeDarkMode = runCatching {
            ThemeDarkMode.valueOf(appSettings.themeDarkMode)
        }.getOrDefault(ThemeDarkMode.SYSTEM)
        amoledDarkEnabled = appSettings.amoledDarkEnabled
        customSeedArgb = appSettings.customSeedArgb
        cardFaceServerUrl = appSettings.cardFaceServerUrl
        vaultLayoutMode = runCatching {
            VaultCardLayoutMode.valueOf(appSettings.vaultLayoutMode)
        }.getOrDefault(VaultCardLayoutMode.WALLET_STACK)
        binLookupEngine = BinLookupEngine.fromAssets(assets)
        cardArtRepository = CardArtRepository(applicationContext) { appSettings.cardFaceServerUrl }
        notificationPermissionGranted = hasNotificationPermission()
        notificationTarget = intent.data?.let(::parseExpiryNotificationUri)
        val database = expiryDatabase(this)
        vaultHasSecureScreenLock = getSystemService(android.app.KeyguardManager::class.java)
            ?.isDeviceSecure == true
        vaultMetaDao = database.vaultMetaDao()
        vaultClipboardController = VaultClipboardController(this)
        vaultSessionController = VaultSessionController(
            activity = this,
            vaultMetaDao = database.vaultMetaDao(),
            paymentCardDao = database.paymentCardDao(),
            executor = ContextCompat.getMainExecutor(this),
            onUnlocked = { cards ->
                refreshVaultCardsUi(cards)
                vaultUiState = VaultUiState.UNLOCKED
            },
            onLocked = {
                vaultDeletionConfirmDialog?.dismiss()
                vaultDeletionConfirmDialog = null
                editingVaultCardId = null
                editingVaultCardPayload = CardPayload()
                clearCardEditAuxState()
                selectedVaultCard = null
                pendingVaultAction = null
                loadCachedCardCovers()
                if (vaultUiState == VaultUiState.UNLOCKED) vaultUiState = VaultUiState.LOCKED
            },
            onBroken = { vaultUiState = VaultUiState.BROKEN },
        )
        vaultRestoreController = VaultRestoreController(
            activity = this,
            executor = ContextCompat.getMainExecutor(this),
            onPrepared = { cards, meta -> commitBackupRestore(cards, meta) },
            onNoSecureScreenLock = {
                backupRestoreStage = RestoreStage.CONFIRM
                backupRestoreError = "手机未设置安全锁屏（密码、PIN 或图案），请先前往系统设置开启。"
            },
            onCancelledOrFailed = {
                backupRestoreStage = RestoreStage.CONFIRM
            },
        )
        vaultCreationController = VaultCreationController(
            activity = this,
            vaultMetaDao = database.vaultMetaDao(),
            executor = ContextCompat.getMainExecutor(this),
            onCreated = { dek ->
                vaultSessionController.startCreatedSession(dek)
                refreshVaultSecurityLevel()
            },
            onNoSecureScreenLock = {
                vaultHasSecureScreenLock = false
                vaultUiState = VaultUiState.NOT_CREATED
            },
        )
        lifecycleScope.launch(Dispatchers.IO) {
            runExpiryReminderStartup(this@MainActivity)
            if (database.vaultMetaDao().get() != null) {
                launch(Dispatchers.Main) {
                    vaultUiState = VaultUiState.LOCKED
                    loadCachedCardCovers()
                    refreshVaultSecurityLevel()
                }
            }
        }
        setContent {
            val editor: ExpiryEditViewModel = viewModel()
            val home: ExpiryHomeViewModel = viewModel()
            androidx.compose.runtime.DisposableEffect(lifecycle) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        home.refreshToday()
                    }
                }
                lifecycle.addObserver(observer)
                onDispose { lifecycle.removeObserver(observer) }
            }
            val editorState = editor.state.collectAsStateWithLifecycle().value
            val homeState = home.state.collectAsStateWithLifecycle().value
            val destinationState = rememberDestination()

            androidx.compose.runtime.LaunchedEffect(destinationState.value, screenshotProtectionEnabled) {
                if (screenshotProtectionEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            androidx.compose.runtime.LaunchedEffect(vaultUiState) {
                if (
                    vaultUiState != VaultUiState.UNLOCKED &&
                    (destinationState.value == Destination.CardEdit ||
                        destinationState.value == Destination.CardDetail)
                ) {
                    destinationState.value = Destination.Vault
                }
            }

            androidx.compose.runtime.LaunchedEffect(vaultUiState, pendingVaultAction) {
                if (vaultUiState != VaultUiState.UNLOCKED) return@LaunchedEffect
                val action = pendingVaultAction ?: return@LaunchedEffect
                pendingVaultAction = null
                when (action) {
                    is PendingVaultAction.OpenCard -> {
                        vaultFocusedCardId = action.cardId
                        vaultLayoutMode = VaultCardLayoutMode.CAROUSEL
                        appSettings.vaultLayoutMode = VaultCardLayoutMode.CAROUSEL.name
                    }
                    is PendingVaultAction.EditCard -> {
                        val card = vaultSessionController.cards().firstOrNull { it.id == action.cardId }
                        if (card != null) {
                            vaultFocusedCardId = card.id
                            beginCardEdit(card.id, card.payload)
                            destinationState.value = Destination.CardEdit
                        }
                    }
                    is PendingVaultAction.DeleteCard -> {
                        val card = vaultSessionController.cards().firstOrNull { it.id == action.cardId }
                        if (card != null) {
                            confirmVaultCardDeletion(card) {
                                destinationState.value = Destination.Vault
                            }
                        }
                    }
                    PendingVaultAction.AddCard -> {
                        beginCardEdit(null, CardPayload())
                        destinationState.value = Destination.CardEdit
                    }
                    PendingVaultAction.SwitchCarousel -> {
                        vaultLayoutMode = VaultCardLayoutMode.CAROUSEL
                        appSettings.vaultLayoutMode = VaultCardLayoutMode.CAROUSEL.name
                    }
                }
            }

            androidx.compose.runtime.LaunchedEffect(notificationTarget) {
                val target = notificationTarget ?: return@LaunchedEffect
                when (target.action) {
                    ExpiryNotificationAction.OPEN,
                    ExpiryNotificationAction.RENEW,
                    -> editor.load(target.id) { found ->
                        if (found) {
                            destinationState.value = Destination.Edit
                        } else {
                            closeMissingNotificationItem()
                        }
                    }
                    ExpiryNotificationAction.DELETE -> editor.load(target.id) { found ->
                        if (found) {
                            destinationState.value = Destination.DeleteConfirm
                        } else {
                            closeMissingNotificationItem()
                        }
                    }
                    ExpiryNotificationAction.PAUSE -> Unit
                }
            }

            val effectiveDarkTheme = resolveDarkTheme(themeDarkMode)
            androidx.compose.runtime.LaunchedEffect(effectiveDarkTheme) {
                val barStyle = if (effectiveDarkTheme) {
                    SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }

            CardVaultTheme(
                themePreset = themePreset,
                darkMode = themeDarkMode,
                amoledDark = amoledDarkEnabled,
                customSeedArgb = customSeedArgb,
            ) {
                // 统一系统返回手势/物理返回键导航，避免在二级页面或轮播态误触直接退出应用
                BackHandler(
                    enabled = destinationState.value != Destination.Home,
                ) {
                    when (destinationState.value) {
                        Destination.Edit -> destinationState.value = Destination.Home
                        Destination.DeleteConfirm -> destinationState.value = Destination.Edit
                        Destination.CardDetail -> destinationState.value = Destination.Vault
                        Destination.CardEdit -> {
                            clearCardEditAuxState()
                            destinationState.value = Destination.Vault
                        }
                        Destination.BackupExport -> {
                            if (backupExporting) {
                                Toast.makeText(this@MainActivity, "备份导出进行中，请等待完成。", Toast.LENGTH_SHORT).show()
                            } else {
                                val cleanup = discardBackupExportFile()
                                showBackupExportCleanupFailure(cleanup)
                                destinationState.value = Destination.Settings
                            }
                        }
                        Destination.BackupRestore -> {
                            clearBackupRestoreState()
                            destinationState.value = backupRestoreReturnDestination
                        }
                        Destination.Vault -> {
                            if (vaultLayoutMode != VaultCardLayoutMode.WALLET_STACK) {
                                vaultLayoutMode = VaultCardLayoutMode.WALLET_STACK
                                appSettings.vaultLayoutMode = VaultCardLayoutMode.WALLET_STACK.name
                            } else {
                                destinationState.value = Destination.Home
                            }
                        }
                        Destination.Settings -> destinationState.value = Destination.Home
                        Destination.Home -> Unit
                    }
                }
                Surface {
                    when (destinationState.value) {
                        Destination.Home -> ExpiryHomeScreen(
                            expiredItems = homeState.expiredItems,
                            dueSoonItems = homeState.dueSoonItems,
                            activeItems = homeState.activeItems,
                            pausedItems = homeState.pausedItems,
                            hasNotificationPermission = notificationPermissionGranted,
                            onItemClick = { id -> editor.load(id); destinationState.value = Destination.Edit },
                            onRenewClick = { item ->
                                if (item.cycleText.isBlank()) {
                                    editor.load(item.id)
                                    destinationState.value = Destination.Edit
                                } else {
                                    home.renew(item.id) { name, newDate ->
                                        Toast.makeText(
                                            this@MainActivity,
                                            "「$name」已续期至 $newDate",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            },
                            onPauseClick = { item ->
                                home.setPaused(item.id, true)
                                Toast.makeText(
                                    this@MainActivity,
                                    "已暂停「${item.name}」的提醒",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onResumeClick = { item ->
                                home.setPaused(item.id, false)
                                Toast.makeText(
                                    this@MainActivity,
                                    "已恢复「${item.name}」的提醒",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onDeleteClick = { item ->
                                editor.load(item.id)
                                destinationState.value = Destination.DeleteConfirm
                            },
                            onCreateClick = { editor.beginCreate(); destinationState.value = Destination.Edit },
                            onRequestNotificationPermission = ::openNotificationSettings,
                            onTabSelected = { tab -> destinationState.value = tab.toDestination() },
                        )
                        Destination.Edit -> ExpiryEditScreen(
                            name = editorState.name,
                            category = editorState.category,
                            startsOn = editorState.startsOn,
                            validThrough = editorState.validThrough,
                            cycle = editorState.cycle.name,
                            notes = editorState.notes,
                            isNewItem = editorState.id == null,
                            showDateWarning = editorState.startsOn.toLocalDateOrNull()?.let { start ->
                                editorState.validThrough.toLocalDateOrNull()?.let(start::isAfter)
                            } == true,
                            onNameChange = editor::updateName,
                            onCategoryChange = editor::updateCategory,
                            onStartsOnChange = editor::updateStartsOn,
                            onValidThroughChange = editor::updateValidThrough,
                            onCycleChange = editor::updateCycle,
                            onNotesChange = editor::updateNotes,
                            onSave = { editor.save {
                                requestNotificationPermissionIfNeeded()
                                destinationState.value = Destination.Home
                            } },
                            onDelete = { destinationState.value = Destination.DeleteConfirm },
                            onBack = { destinationState.value = Destination.Home },
                        )
                        Destination.DeleteConfirm -> ExpiryDeleteConfirmScreen(
                            itemExists = editorState.id != null,
                            itemName = editorState.name,
                            validThrough = editorState.validThrough,
                            onConfirmDelete = { editor.delete { destinationState.value = Destination.Home } },
                            onDismiss = { destinationState.value = Destination.Edit },
                        )
                        Destination.Vault -> VaultScreen(
                            state = vaultUiState,
                            cards = vaultCards,
                            layoutMode = vaultLayoutMode,
                            focusedCardId = vaultFocusedCardId,
                            hasSecureScreenLock = vaultHasSecureScreenLock,
                            onCreateVault = vaultCreationController::create,
                            onUnlock = vaultSessionController::unlock,
                            onLock = vaultSessionController::lock,
                            onToggleLayoutMode = ::toggleVaultLayoutMode,
                            onReorderCards = ::reorderVaultCards,
                            onCopyText = { text ->
                                vaultClipboardController.copy(text)
                                Toast.makeText(
                                    this@MainActivity,
                                    "已复制，切出应用后将自动清空剪贴板",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            },
                            onRestoreFromBackup = {
                                openBackupRestore(Destination.Vault)
                                destinationState.value = Destination.BackupRestore
                            },
                            onResetVault = ::confirmVaultReset,
                            onAddCard = {
                                when (vaultUiState) {
                                    VaultUiState.UNLOCKED -> {
                                        beginCardEdit(null, CardPayload())
                                        destinationState.value = Destination.CardEdit
                                    }
                                    VaultUiState.LOCKED -> {
                                        pendingVaultAction = PendingVaultAction.AddCard
                                        vaultSessionController.unlock()
                                    }
                                    else -> Unit
                                }
                            },
                            onCardClick = { id ->
                                // 叠放列表点按卡片后，直接切换至合并后的轮播详情视图并精准定位到该卡（不再跳转冗余独立详情页）
                                vaultFocusedCardId = id
                                vaultLayoutMode = VaultCardLayoutMode.CAROUSEL
                                appSettings.vaultLayoutMode = VaultCardLayoutMode.CAROUSEL.name
                            },
                            onEditCard = { id ->
                                when (vaultUiState) {
                                    VaultUiState.UNLOCKED -> {
                                        val card = vaultSessionController.cards().firstOrNull { it.id == id }
                                        if (card != null) {
                                            vaultFocusedCardId = card.id
                                            beginCardEdit(card.id, card.payload)
                                            destinationState.value = Destination.CardEdit
                                        }
                                    }
                                    VaultUiState.LOCKED -> {
                                        pendingVaultAction = PendingVaultAction.EditCard(id)
                                        vaultSessionController.unlock()
                                    }
                                    else -> Unit
                                }
                            },
                            onDeleteCard = { id ->
                                when (vaultUiState) {
                                    VaultUiState.UNLOCKED -> {
                                        val card = vaultSessionController.cards().firstOrNull { it.id == id }
                                        if (card != null) {
                                            confirmVaultCardDeletion(card) {
                                                destinationState.value = Destination.Vault
                                            }
                                        }
                                    }
                                    VaultUiState.LOCKED -> {
                                        pendingVaultAction = PendingVaultAction.DeleteCard(id)
                                        vaultSessionController.unlock()
                                    }
                                    else -> Unit
                                }
                            },
                            onTabSelected = { tab -> destinationState.value = tab.toDestination() },
                        )
                        Destination.CardDetail -> {
                            val card = selectedVaultCard
                            if (card == null) {
                                destinationState.value = Destination.Vault
                            } else {
                                val cardPreset = CardFacePreset.entries[
                                    card.id.coerceAtLeast(0) % CardFacePreset.entries.size
                                ]
                                CardDetailScreen(
                                    name = card.payload.name,
                                    issuer = card.payload.issuer,
                                    number = card.payload.number,
                                    expMonth = card.payload.expMonth,
                                    expYear = card.payload.expYear,
                                    cvv = card.payload.cvv,
                                    notes = card.payload.notes,
                                    network = card.payload.network,
                                    cardType = card.payload.cardType,
                                    preset = cardPreset,
                                    artBitmap = cardArtCache[card.payload.artFileName],
                                    isNumberMasked = vaultNumberMasked,
                                    isCvvMasked = vaultCvvMasked,
                                    onToggleNumberMask = { vaultNumberMasked = !vaultNumberMasked },
                                    onToggleCvvMask = { vaultCvvMasked = !vaultCvvMasked },
                                    onCopyNumber = { vaultClipboardController.copy(card.payload.number) },
                                    onCopyExpiry = {
                                        vaultClipboardController.copy(
                                            formatExpiry(card.payload.expMonth, card.payload.expYear),
                                        )
                                    },
                                    onCopyCvv = { vaultClipboardController.copy(card.payload.cvv) },
                                    onEdit = {
                                        if (vaultUiState == VaultUiState.UNLOCKED) {
                                            beginCardEdit(card.id, card.payload)
                                            destinationState.value = Destination.CardEdit
                                        }
                                    },
                                    onDelete = {
                                        confirmVaultCardDeletion(card) {
                                            destinationState.value = Destination.Vault
                                        }
                                    },
                                    onBack = { destinationState.value = Destination.Vault },
                                )
                            }
                        }
                        Destination.CardEdit -> CardEditScreen(
                            name = editingVaultCardPayload.name,
                            issuer = editingVaultCardPayload.issuer,
                            number = editingVaultCardPayload.number,
                            expMonth = editingVaultCardPayload.expMonth,
                            expYear = editingVaultCardPayload.expYear,
                            cvv = editingVaultCardPayload.cvv,
                            notes = editingVaultCardPayload.notes,
                            network = editingVaultCardPayload.network,
                            cardType = editingVaultCardPayload.cardType,
                            isNewCard = editingVaultCardId == null,
                            artBitmap = cardArtCache[editingVaultCardPayload.artFileName],
                            detectedBin = editingCardDetectedBin,
                            candidateSkins = editingCardCandidateSkins,
                            skinSearchQuery = editingCardSkinQuery,
                            isSearchingSkins = editingCardSearchingSkins,
                            onNameChange = { updateEditingVaultCard { copy(name = it) } },
                            onIssuerChange = { updateEditingVaultCard { copy(issuer = it) } },
                            onNumberChange = { number ->
                                updateEditingVaultCard { copy(number = number) }
                                triggerOfflineBinLookup(number, autoFillIssuer = true)
                            },
                            onExpMonthChange = { updateEditingVaultCard { copy(expMonth = it) } },
                            onExpYearChange = { updateEditingVaultCard { copy(expYear = it) } },
                            onCvvChange = { updateEditingVaultCard { copy(cvv = it) } },
                            onNotesChange = { updateEditingVaultCard { copy(notes = it) } },
                            onSkinSearchQueryChange = { editingCardSkinQuery = it },
                            onApplyDetectedBin = { info ->
                                updateEditingVaultCard {
                                    copy(
                                        issuer = info.issuerDisplay.ifBlank { issuer },
                                        network = info.brand.ifBlank { network },
                                        cardType = info.cardType.ifBlank { cardType },
                                    )
                                }
                            },
                            onSearchOnlineArt = { searchOnlineCardSkins(useBinPrefix = false) },
                            onSearchOnlineArtWithBin = { searchOnlineCardSkins(useBinPrefix = true) },
                            onPickLocalArt = { pickCardArt.launch("image/*") },
                            onSelectCandidateSkin = ::selectRemoteCardSkin,
                            onSubmitCommunityArt = ::submitCurrentCardArtToCommunity,
                            onClearArt = { updateEditingVaultCard { copy(artFileName = "") } },
                            onSave = {
                                val rawPayload = editingVaultCardPayload
                                val resolvedName = rawPayload.name.trim().ifBlank {
                                    val tail = rawPayload.number.filter { it.isDigit() }.takeLast(4)
                                    val issuerPrefix = rawPayload.issuer.trim()
                                    when {
                                        issuerPrefix.isNotBlank() && tail.isNotEmpty() -> "$issuerPrefix $tail"
                                        issuerPrefix.isNotBlank() -> issuerPrefix
                                        tail.isNotEmpty() -> "银行卡 $tail"
                                        else -> "未命名卡片"
                                    }
                                }
                                vaultSessionController.saveCard(
                                    editingVaultCardId,
                                    rawPayload.copy(name = resolvedName),
                                ) {
                                    clearCardEditAuxState()
                                    destinationState.value = Destination.Vault
                                }
                            },
                            onDelete = editingVaultCardId?.let { id ->
                                {
                                    confirmVaultCardDeletion(id, editingVaultCardPayload.name) {
                                        clearCardEditAuxState()
                                        destinationState.value = Destination.Vault
                                    }
                                }
                            },
                            onBack = {
                                clearCardEditAuxState()
                                destinationState.value = Destination.Vault
                            },
                        )
                        Destination.BackupExport -> BackupExportScreen(
                            selectedFileName = backupExportFileName,
                            password = backupPassword,
                            confirmPassword = backupConfirmPassword,
                            isVaultBroken = vaultUiState == VaultUiState.BROKEN,
                            isExporting = backupExporting,
                            errorMessage = backupExportError,
                            onSelectLocation = ::selectBackupExportLocation,
                            onPasswordChange = { backupPassword = it },
                            onConfirmPasswordChange = { backupConfirmPassword = it },
                            onConfirmExport = ::beginBackupExport,
                            onBack = {
                                if (backupExporting) {
                                    Toast.makeText(this@MainActivity, "备份导出进行中，请等待完成。", Toast.LENGTH_SHORT).show()
                                    return@BackupExportScreen
                                }
                                val cleanup = discardBackupExportFile()
                                showBackupExportCleanupFailure(cleanup)
                                destinationState.value = Destination.Settings
                            },
                        )
                        Destination.BackupRestore -> BackupRestoreScreen(
                            stage = backupRestoreStage,
                            selectedFileName = backupRestoreFileName,
                            password = backupRestorePassword,
                            itemCount = backupRestoreContent?.expiryItems?.size ?: 0,
                            cardCount = backupRestoreContent?.cards?.size ?: 0,
                            errorMessage = backupRestoreError,
                            onSelectFile = { openBackupDocument.launch(arrayOf("application/octet-stream", "application/*")) },
                            onPasswordChange = { backupRestorePassword = it },
                            onSubmitPassword = ::decryptBackupForRestore,
                            onConfirmRestore = ::confirmBackupRestore,
                            onBack = {
                                clearBackupRestoreState()
                                destinationState.value = backupRestoreReturnDestination
                            },
                        )
                        Destination.Settings -> SettingsScreen(
                            earlyReminderEnabled = earlyReminderEnabled,
                            hideNameOnLockScreen = hideNameOnLockScreen,
                            screenshotProtectionEnabled = screenshotProtectionEnabled,
                            themePreset = themePreset,
                            themeDarkMode = themeDarkMode,
                            amoledDarkEnabled = amoledDarkEnabled,
                            customSeedArgb = customSeedArgb,
                            notificationPermissionGranted = notificationPermissionGranted,
                            securityLevel = vaultSecurityLevel.displayName,
                            appVersion = appVersionName(),
                            cardFaceServerUrl = cardFaceServerUrl,
                            onEarlyReminderChange = { enabled ->
                                appSettings.earlyReminderEnabled = enabled
                                earlyReminderEnabled = enabled
                            },
                            onHideNameOnLockScreenChange = { enabled ->
                                appSettings.hideNameOnLockScreen = enabled
                                hideNameOnLockScreen = enabled
                            },
                            onScreenshotProtectionChange = { enabled ->
                                appSettings.screenshotProtectionEnabled = enabled
                                screenshotProtectionEnabled = enabled
                            },
                            onThemePresetChange = { preset ->
                                appSettings.themePreset = preset.name
                                themePreset = preset
                            },
                            onThemeDarkModeChange = { mode ->
                                appSettings.themeDarkMode = mode.name
                                themeDarkMode = mode
                            },
                            onAmoledDarkChange = { enabled ->
                                appSettings.amoledDarkEnabled = enabled
                                amoledDarkEnabled = enabled
                            },
                            onCustomSeedChange = { argb ->
                                appSettings.customSeedArgb = argb
                                customSeedArgb = argb
                            },
                            onCardFaceServerUrlChange = { url ->
                                appSettings.cardFaceServerUrl = url
                                cardFaceServerUrl = url
                            },
                            onRequestNotificationPermission = ::openNotificationSettings,
                            onOpenAppInfo = ::openAppInfo,
                            onExportBackupClick = {
                                clearBackupExportState()
                                destinationState.value = Destination.BackupExport
                            },
                            onRestoreBackupClick = {
                                openBackupRestore(Destination.Settings)
                                destinationState.value = Destination.BackupRestore
                            },
                            onTabSelected = { tab -> destinationState.value = tab.toDestination() },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationTarget = intent.data?.let(::parseExpiryNotificationUri)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        vaultSessionController.resetInactivityTimer()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) vaultClipboardController.clearOwnClipAfterForegroundReturn()
    }

    override fun onResume() {
        super.onResume()
        notificationPermissionGranted = hasNotificationPermission()
        vaultHasSecureScreenLock = getSystemService(android.app.KeyguardManager::class.java)
            ?.isDeviceSecure == true
    }

    private fun selectBackupExportLocation() {
        if (backupExporting || backupExportUri != null) {
            Toast.makeText(this, "已有备份目标，请先完成或返回取消。", Toast.LENGTH_SHORT).show()
            return
        }
        val filename = "cardvault-" + LocalDateTime.now().format(
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"),
        ) + ".cvb"
        createBackupDocument.launch(filename)
    }

    private fun beginBackupExport() {
        val uri = backupExportUri ?: return
        if (backupPassword.isEmpty() || backupPassword != backupConfirmPassword || backupExporting) return
        backupExporting = true
        backupExportError = null
        if (vaultUiState == VaultUiState.BROKEN) {
            // Both button and system cancellation release exactly this export attempt; repeated
            // cancellation is harmless because discard requires the exact current pending URI.
            android.app.AlertDialog.Builder(this)
                .setMessage(R.string.backup_broken_vault_notice)
                .setNegativeButton(R.string.action_cancel) { _, _ ->
                    showBackupExportCleanupFailure(discardBackupExportFile(uri))
                }
                .setPositiveButton(R.string.action_confirm) { _, _ -> exportBackup(uri, emptyList()) }
                .setOnCancelListener {
                    showBackupExportCleanupFailure(discardBackupExportFile(uri))
                }
                .show()
                .window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else if (vaultUiState == VaultUiState.NOT_CREATED) {
            exportBackup(uri, emptyList())
        } else {
            vaultSessionController.exportCards { cards ->
                if (cards == null) {
                    showBackupExportCleanupFailure(discardBackupExportFile(uri))
                    return@exportCards
                }
                exportBackup(uri, cards)
            }
        }
    }

    private fun exportBackup(uri: Uri, cards: List<UnlockedPaymentCard>) {
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val content = BackupContent(
                        exportedAt = LocalDateTime.now().toString(),
                        expiryItems = expiryDatabase(this@MainActivity).expiryItemDao().getAll()
                            .map { it.toBackupExpiryItem() },
                        cards = cards.map { it.payload.toBackupCard() },
                    )
                    val password = backupPassword.toCharArray()
                    try {
                        val encrypted = BackupFileCodec.encrypt(
                            Json.encodeToString(BackupContent.serializer(), content).encodeToByteArray(),
                            password,
                        )
                        contentResolver.openOutputStream(uri)?.use { it.write(encrypted) }
                            ?: error("Cannot open backup destination")
                    } finally {
                        password.fill('\u0000')
                    }
                }
            }
            if (result.isSuccess) {
                // Do not detach a different target if an asynchronous callback ever races this one.
                if (backupExportUri == uri) {
                    backupExportUri = null
                    clearBackupExportState()
                }
                Toast.makeText(this@MainActivity, "备份导出成功", Toast.LENGTH_SHORT).show()
            } else {
                val cleanup = discardBackupExportFile(uri)
                showBackupExportCleanupFailure(cleanup)
                // Keep the export failure, irrespective of whether provider cleanup also failed.
                backupExportError = "备份导出失败"
            }
        }
    }

    private fun decryptBackupForRestore() {
        val file = backupRestoreFile ?: return
        if (backupRestorePassword.isEmpty()) return
        backupRestoreError = null
        lifecycleScope.launch {
            val password = backupRestorePassword.toCharArray()
            val content = try {
                withContext(Dispatchers.IO) {
                    val plaintext = BackupFileCodec.decrypt(file, password)
                    try {
                        Json.decodeFromString(BackupContent.serializer(), plaintext.decodeToString())
                    } finally {
                        plaintext.fill(0)
                    }
                }
            } catch (_: Exception) {
                null
            } finally {
                password.fill('\u0000')
                backupRestorePassword = ""
            }
            if (content == null) {
                backupRestoreError = getString(R.string.backup_restore_err_failed)
            } else {
                backupRestoreContent = content
                backupRestoreStage = RestoreStage.CONFIRM
            }
        }
    }

    private fun confirmBackupRestore() {
        val content = backupRestoreContent ?: return
        backupRestoreStage = RestoreStage.RESTORING
        backupRestoreError = null
        if (content.cards.isEmpty()) {
            commitBackupRestore(emptyList(), null)
        } else {
            vaultRestoreController.prepare(content.cards)
        }
    }

    private fun commitBackupRestore(
        cards: List<com.personal.cardvault.data.PaymentCardEntity>,
        meta: com.personal.cardvault.data.VaultMetaEntity?,
    ) {
        val content = backupRestoreContent ?: return
        lifecycleScope.launch {
            val restored = runCatching {
                withContext(Dispatchers.IO) {
                    expiryDatabase(this@MainActivity).replaceWithBackup(
                        content.expiryItems.map { it.toExpiryItemEntity() },
                        cards,
                        meta,
                    )
                }
            }
            if (restored.isSuccess) {
                clearBackupRestoreState()
                appSettings.vaultCardCoversJson = ""
                vaultSessionController.lock()
                vaultCards = emptyList()
                vaultUiState = if (meta == null) VaultUiState.NOT_CREATED else VaultUiState.LOCKED
                refreshVaultSecurityLevel()
                Toast.makeText(this@MainActivity, "恢复成功", Toast.LENGTH_SHORT).show()
                // Post-commit maintenance cannot make a successfully committed restore fail.
                lifecycleScope.launch(Dispatchers.IO) {
                    runCatching {
                        NotificationManagerCompat.from(this@MainActivity).cancelAll()
                        runExpiryReminderStartup(this@MainActivity)
                    }
                }
            } else {
                backupRestoreStage = RestoreStage.CONFIRM
                backupRestoreError = "恢复失败"
            }
        }
    }

    private fun openBackupRestore(returnDestination: Destination) {
        clearBackupRestoreState()
        backupRestoreReturnDestination = returnDestination
    }

    private fun clearBackupRestoreState(keepError: Boolean = false) {
        backupRestoreFile?.fill(0)
        backupRestoreFile = null
        backupRestoreContent = null
        backupRestoreFileName = null
        backupRestorePassword = ""
        backupRestoreStage = RestoreStage.INPUT
        if (!keepError) backupRestoreError = null
    }

    /** Deletes only [expectedUri] when it is still the current unfinished CreateDocument target. */
    private fun discardBackupExportFile(expectedUri: Uri? = backupExportUri): BackupExportCleanupResult {
        val uri = backupExportUri
        if (expectedUri != uri) return BackupExportCleanupResult.StalePendingDocument
        // Detach before I/O: provider failure cannot leave a retryable URI or affect another target.
        backupExportUri = null
        val result = if (uri == null) {
            BackupExportCleanupResult.NoPendingDocument
        } else {
            contentResolver.deletePendingBackupDocument(uri)
        }
        clearBackupExportState()
        return result
    }

    private fun showBackupExportCleanupFailure(result: BackupExportCleanupResult) {
        if (result.isFailure) {
            Toast.makeText(this, "无法自动删除新建的备份文件，请手动删除。", Toast.LENGTH_SHORT).show()
        }
    }

    private fun clearBackupExportState() {
        backupExportUri = null
        backupExportFileName = null
        backupPassword = ""
        backupConfirmPassword = ""
        backupExporting = false
        backupExportError = null
    }

    private fun updateEditingVaultCard(update: CardPayload.() -> CardPayload) {
        editingVaultCardPayload = editingVaultCardPayload.update()
    }

    private fun toggleVaultLayoutMode() {
        val next = if (vaultLayoutMode == VaultCardLayoutMode.WALLET_STACK) {
            VaultCardLayoutMode.CAROUSEL
        } else {
            VaultCardLayoutMode.WALLET_STACK
        }
        vaultLayoutMode = next
        appSettings.vaultLayoutMode = next.name
    }

    private fun loadCachedCardCovers() {
        val raw = appSettings.vaultCardCoversJson
        if (raw.isBlank()) {
            vaultCards = emptyList()
            return
        }
        val covers = runCatching {
            Json.decodeFromString(ListSerializer(VaultCardCover.serializer()), raw)
        }.getOrDefault(emptyList())
        val rank = appSettings.vaultCardOrder.withIndex().associate { (idx, id) -> id to idx }
        val sorted = covers.sortedBy { rank[it.id] ?: Int.MAX_VALUE }

        fun mapCovers(cache: Map<String, ImageBitmap>): List<CardItemUiModel> = sorted.map { cover ->
            CardItemUiModel(
                id = cover.id,
                name = cover.name,
                issuer = cover.issuer,
                numberPreview = if (cover.tail4.isNotEmpty()) "•••• ${cover.tail4}" else "",
                artBitmap = cache[cover.artFileName],
                network = cover.network,
                cardType = cover.cardType,
            )
        }

        vaultCards = mapCovers(cardArtCache)
        val missingArtFiles = sorted
            .map { it.artFileName }
            .filter { it.isNotBlank() && !cardArtCache.containsKey(it) }
            .distinct()
        if (missingArtFiles.isEmpty()) return

        lifecycleScope.launch {
            val loaded = mutableMapOf<String, ImageBitmap>()
            for (fileName in missingArtFiles) {
                cardArtRepository.loadLocalArt(fileName)?.let { loaded[fileName] = it }
            }
            if (loaded.isNotEmpty()) {
                cardArtCache = cardArtCache + loaded
                if (vaultUiState == VaultUiState.LOCKED) {
                    vaultCards = mapCovers(cardArtCache)
                }
            }
        }
    }

    private fun reorderVaultCards(newOrderIds: List<Int>) {
        appSettings.vaultCardOrder = newOrderIds
        val rank = newOrderIds.withIndex().associate { (idx, id) -> id to idx }
        vaultCards = vaultCards.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
    }

    private fun refreshVaultCardsUi(cards: List<UnlockedPaymentCard>) {
        // 同步保存非敏感卡面封面索引（绝不保存完整卡号/有效期/CVV/备注），用于进入保险箱直接展示卡包，点开卡片再触发解锁
        val covers = cards.map { card ->
            val digits = card.payload.number.filter { it.isDigit() }
            VaultCardCover(
                id = card.id,
                name = card.payload.name,
                issuer = card.payload.issuer,
                tail4 = if (digits.length >= 4) digits.takeLast(4) else "",
                artFileName = card.payload.artFileName,
                network = card.payload.network,
                cardType = card.payload.cardType,
            )
        }
        runCatching {
            appSettings.vaultCardCoversJson = Json.encodeToString(
                ListSerializer(VaultCardCover.serializer()),
                covers,
            )
        }

        val rank = appSettings.vaultCardOrder.withIndex().associate { (idx, id) -> id to idx }
        val sortedCards = cards.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
        fun mapCards(cache: Map<String, ImageBitmap>): List<CardItemUiModel> = sortedCards.map { card ->
            val digits = card.payload.number.filter { it.isDigit() }
            val preview = if (digits.length >= 4) "•••• ${digits.takeLast(4)}" else card.payload.number
            CardItemUiModel(
                id = card.id,
                name = card.payload.name,
                issuer = card.payload.issuer,
                numberPreview = preview,
                artBitmap = cache[card.payload.artFileName],
                number = card.payload.number,
                expMonth = card.payload.expMonth,
                expYear = card.payload.expYear,
                cvv = card.payload.cvv,
                notes = card.payload.notes,
                network = card.payload.network,
                cardType = card.payload.cardType,
            )
        }

        vaultCards = mapCards(cardArtCache)
        selectedVaultCard?.let { current ->
            selectedVaultCard = cards.firstOrNull { it.id == current.id }
        }

        val missingArtFiles = cards
            .map { it.payload.artFileName }
            .filter { it.isNotBlank() && !cardArtCache.containsKey(it) }
            .distinct()
        if (missingArtFiles.isEmpty()) return

        lifecycleScope.launch {
            val loaded = mutableMapOf<String, ImageBitmap>()
            for (fileName in missingArtFiles) {
                cardArtRepository.loadLocalArt(fileName)?.let { loaded[fileName] = it }
            }
            if (loaded.isNotEmpty()) {
                cardArtCache = cardArtCache + loaded
                if (vaultUiState == VaultUiState.UNLOCKED) {
                    vaultCards = mapCards(cardArtCache)
                }
            }
        }
    }

    private fun beginCardEdit(id: Int?, payload: CardPayload) {
        editingVaultCardId = id
        editingVaultCardPayload = payload
        clearCardEditAuxState()
        if (payload.artFileName.isNotBlank() && !cardArtCache.containsKey(payload.artFileName)) {
            lifecycleScope.launch {
                cardArtRepository.loadLocalArt(payload.artFileName)?.let { bmp ->
                    cardArtCache = cardArtCache + (payload.artFileName to bmp)
                }
            }
        }
        if (payload.number.filter { it.isDigit() }.length >= 6) {
            triggerOfflineBinLookup(payload.number, autoFillIssuer = false)
        }
    }

    private fun clearCardEditAuxState() {
        binLookupJob?.cancel()
        binLookupJob = null
        skinSearchJob?.cancel()
        skinSearchJob = null
        editingCardDetectedBin = null
        editingCardSkinQuery = ""
        editingCardCandidateSkins = emptyList()
        editingCardSearchingSkins = false
    }

    private fun triggerOfflineBinLookup(cardNumber: String, autoFillIssuer: Boolean) {
        binLookupJob?.cancel()
        val digits = cardNumber.filter { it.isDigit() }
        if (digits.length < 6) {
            editingCardDetectedBin = null
            return
        }
        binLookupJob = lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) { binLookupEngine.lookup(digits) }
            editingCardDetectedBin = info
            if (info != null && autoFillIssuer) {
                updateEditingVaultCard {
                    copy(
                        issuer = if (issuer.isBlank() && info.issuerDisplay.isNotBlank()) info.issuerDisplay else issuer,
                        network = if (network.isBlank() && info.brand.isNotBlank()) info.brand else network,
                        cardType = if (cardType.isBlank() && info.cardType.isNotBlank()) info.cardType else cardType,
                    )
                }
            }
        }
    }

    private fun searchOnlineCardSkins(useBinPrefix: Boolean = false) {
        val baseUrl = appSettings.cardFaceServerUrl.trim()
        if (baseUrl.isBlank()) {
            Toast.makeText(
                this,
                "尚未配置在线卡面服务，可先点「相册导入」选择本地卡面图片",
                Toast.LENGTH_SHORT,
            ).show()
            return
        }
        skinSearchJob?.cancel()
        editingCardSearchingSkins = true
        skinSearchJob = lifecycleScope.launch {
            val detected = editingCardDetectedBin
            val issuer = editingVaultCardPayload.issuer.ifBlank { detected?.issuerDisplay.orEmpty() }
            val network = editingVaultCardPayload.network.ifBlank { detected?.brand.orEmpty() }
            val cardType = editingVaultCardPayload.cardType.ifBlank { detected?.cardType.orEmpty() }
            val binPrefix = if (useBinPrefix) {
                editingVaultCardPayload.number.filter { it.isDigit() }.take(6)
            } else {
                ""
            }
            val skins = cardArtRepository.searchRemoteSkins(
                issuer = issuer,
                network = network,
                cardType = cardType,
                query = editingCardSkinQuery.trim(),
                binPrefix = binPrefix,
            )
            editingCardSearchingSkins = false
            editingCardCandidateSkins = skins.map { CandidateSkinUiItem(skin = it) }
            if (skins.isEmpty()) {
                Toast.makeText(this@MainActivity, "未匹配到在线卡面，可使用兜底卡面或相册导入", Toast.LENGTH_SHORT).show()
                return@launch
            }
            for (skin in skins) {
                val preview = cardArtRepository.fetchPreviewBitmap(skin.previewUrl) ?: continue
                editingCardCandidateSkins = editingCardCandidateSkins.map { item ->
                    if (item.skin.id == skin.id) item.copy(previewBitmap = preview) else item
                }
            }
        }
    }

    private fun selectRemoteCardSkin(skin: RemoteCardSkin) {
        lifecycleScope.launch {
            val fileName = cardArtRepository.downloadAndSaveSkin(skin)
            if (fileName == null) {
                Toast.makeText(this@MainActivity, "卡面下载失败，请稍后重试", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val bitmap = cardArtRepository.loadLocalArt(fileName)
            if (bitmap != null) {
                cardArtCache = cardArtCache + (fileName to bitmap)
            }
            updateEditingVaultCard { copy(artFileName = fileName) }
            Toast.makeText(this@MainActivity, "已应用卡面", Toast.LENGTH_SHORT).show()
        }
    }

    private fun submitCurrentCardArtToCommunity() {
        if (appSettings.cardFaceServerUrl.isBlank()) {
            Toast.makeText(this, "请先在「设置 → 在线卡面服务地址」配置服务", Toast.LENGTH_SHORT).show()
            return
        }
        val fileName = editingVaultCardPayload.artFileName
        if (fileName.isBlank()) return
        lifecycleScope.launch {
            val ok = cardArtRepository.submitCommunitySkin(
                artFileName = fileName,
                title = editingVaultCardPayload.name,
                issuer = editingVaultCardPayload.issuer,
                network = editingVaultCardPayload.network,
                cardType = editingVaultCardPayload.cardType,
            )
            Toast.makeText(
                this@MainActivity,
                if (ok) "已提交至审核队列，审核通过后将对全部用户可见" else "投稿失败，请检查服务地址与网络",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    private fun confirmVaultCardDeletion(card: UnlockedPaymentCard, onDeleted: () -> Unit) {
        confirmVaultCardDeletion(card.id, card.payload.name, onDeleted)
    }

    private fun confirmVaultCardDeletion(id: Int, name: String, onDeleted: () -> Unit) {
        if (vaultUiState != VaultUiState.UNLOCKED) return
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(R.string.card_delete_confirm_title)
            .setMessage(getString(R.string.card_delete_confirm_msg, name))
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                val currentIndex = vaultCards.indexOfFirst { it.id == id }
                val nextFocusedId = when {
                    vaultCards.size <= 1 -> null
                    currentIndex > 0 -> vaultCards[currentIndex - 1].id
                    else -> vaultCards.getOrNull(1)?.id
                }
                vaultSessionController.deleteCard(id) {
                    vaultFocusedCardId = nextFocusedId
                    selectedVaultCard = null
                    onDeleted()
                }
            }
            .create()
        dialog.setOnDismissListener {
            if (vaultDeletionConfirmDialog === dialog) {
                vaultDeletionConfirmDialog = null
            }
        }
        vaultDeletionConfirmDialog = dialog
        dialog.show()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun confirmVaultReset() {
        if (vaultUiState != VaultUiState.BROKEN) return
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle(R.string.vault_reset_confirm_title)
            .setMessage(R.string.vault_reset_confirm_msg)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.vault_broken_reset_btn) { _, _ -> resetVault() }
            .create()
        dialog.setOnDismissListener {
            if (vaultResetConfirmDialog === dialog) vaultResetConfirmDialog = null
        }
        vaultResetConfirmDialog = dialog
        dialog.show()
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    private fun resetVault() {
        lifecycleScope.launch {
            val reset = runCatching {
                withContext(Dispatchers.IO) {
                    vaultMetaDao.resetVault()
                    VaultKeyStore().deleteKek()
                }
            }
            if (reset.isSuccess) {
                appSettings.vaultCardCoversJson = ""
                vaultSessionController.lock()
                vaultCards = emptyList()
                vaultUiState = VaultUiState.NOT_CREATED
                vaultSecurityLevel = VaultSecurityLevel.NOT_CREATED
            }
        }
    }

    private fun refreshVaultSecurityLevel() {
        lifecycleScope.launch(Dispatchers.IO) {
            val level = runCatching { VaultKeyStore().getKekSecurityLevel() }
                .getOrNull()
            val displayLevel = level?.toVaultSecurityLevel() ?: when {
                vaultMetaDao.get() == null -> VaultSecurityLevel.NOT_CREATED
                else -> VaultSecurityLevel.UNKNOWN
            }
            launch(Dispatchers.Main) { vaultSecurityLevel = displayLevel }
        }
    }

    private fun closeMissingNotificationItem() {
        Toast.makeText(this, R.string.item_not_found_message, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun appVersionName(): String =
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()

    private fun hasNotificationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun requestNotificationPermissionIfNeeded() {
        if (!hasNotificationPermission()) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                data = Uri.parse("package:$packageName")
            },
        )
    }

    private fun openAppInfo() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            },
        )
    }
}

/** Result of trying to remove the exact, unfinished SAF document selected for export. */
private sealed interface BackupExportCleanupResult {
    data object Deleted : BackupExportCleanupResult
    data object NoPendingDocument : BackupExportCleanupResult
    data object DeleteNotSupported : BackupExportCleanupResult
    data object DeleteDenied : BackupExportCleanupResult
    data object InvalidDocument : BackupExportCleanupResult
    data object DeleteFailed : BackupExportCleanupResult
    data object StalePendingDocument : BackupExportCleanupResult

    val isFailure: Boolean
        get() = this !is Deleted && this !is NoPendingDocument
}

private fun ContentResolver.backupDocumentDisplayName(uri: Uri): String? {
    val displayName = try {
        query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null else {
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column < 0) null else cursor.getString(column)?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
    } catch (_: Exception) {
        null
    }
    // Some providers cannot query metadata. A nonblank URI tail is only a last-resort label.
    return displayName ?: uri.lastPathSegment?.substringAfterLast('/')?.trim()?.takeIf { it.isNotEmpty() }
}

private fun ContentResolver.deletePendingBackupDocument(uri: Uri): BackupExportCleanupResult {
    val deleteCapability = try {
        query(uri, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) null else {
                val column = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
                if (column < 0) null else cursor.getInt(column)
            }
        }
    } catch (_: Exception) {
        null
    }
    if (deleteCapability == null) {
        return BackupExportCleanupResult.DeleteFailed
    }
    if (deleteCapability and DocumentsContract.Document.FLAG_SUPPORTS_DELETE == 0) {
        return BackupExportCleanupResult.DeleteNotSupported
    }

    return try {
        if (DocumentsContract.deleteDocument(this, uri)) {
            BackupExportCleanupResult.Deleted
        } else {
            BackupExportCleanupResult.DeleteFailed
        }
    } catch (_: UnsupportedOperationException) {
        BackupExportCleanupResult.DeleteNotSupported
    } catch (_: SecurityException) {
        BackupExportCleanupResult.DeleteDenied
    } catch (_: IllegalArgumentException) {
        BackupExportCleanupResult.InvalidDocument
    } catch (_: Exception) {
        BackupExportCleanupResult.DeleteFailed
    }
}

@Serializable
private data class VaultCardCover(
    val id: Int,
    val name: String = "",
    val issuer: String = "",
    val tail4: String = "",
    val artFileName: String = "",
    val network: String = "",
    val cardType: String = "",
)

private sealed interface PendingVaultAction {
    data class OpenCard(val cardId: Int) : PendingVaultAction
    data class EditCard(val cardId: Int) : PendingVaultAction
    data class DeleteCard(val cardId: Int) : PendingVaultAction
    data object AddCard : PendingVaultAction
    data object SwitchCarousel : PendingVaultAction
}

private enum class VaultSecurityLevel(val displayName: String) {
    NOT_CREATED("保险箱尚未建立"),
    STRONGBOX("StrongBox"),
    TEE("TEE"),
    SOFTWARE("软件"),
    UNKNOWN("未知"),
}

private fun VaultKekSecurityLevel.toVaultSecurityLevel(): VaultSecurityLevel = when (this) {
    VaultKekSecurityLevel.STRONGBOX -> VaultSecurityLevel.STRONGBOX
    VaultKekSecurityLevel.TEE -> VaultSecurityLevel.TEE
    VaultKekSecurityLevel.SOFTWARE -> VaultSecurityLevel.SOFTWARE
    VaultKekSecurityLevel.UNKNOWN -> VaultSecurityLevel.UNKNOWN
}

private sealed interface Destination {
    data object Home : Destination
    data object Edit : Destination
    data object DeleteConfirm : Destination
    data object Vault : Destination
    data object CardDetail : Destination
    data object CardEdit : Destination
    data object Settings : Destination
    data object BackupExport : Destination
    data object BackupRestore : Destination
}

@androidx.compose.runtime.Composable
private fun rememberDestination(): androidx.compose.runtime.MutableState<Destination> {
    return androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<Destination>(Destination.Home)
    }
}

private fun Destination.isVaultSensitive(): Boolean = when (this) {
    Destination.BackupExport,
    Destination.BackupRestore,
    Destination.Vault,
    Destination.CardDetail,
    Destination.CardEdit,
    -> true
    else -> false
}

private fun NavTab.toDestination(): Destination = when (this) {
    NavTab.EXPIRY -> Destination.Home
    NavTab.VAULT -> Destination.Vault
    NavTab.SETTINGS -> Destination.Settings
}

private fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { LocalDate.parse(this) }.getOrNull()
