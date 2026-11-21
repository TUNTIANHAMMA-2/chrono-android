package com.personal.cardvault

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.os.SystemClock
import android.provider.DocumentsContract
import android.view.WindowManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import com.personal.cardvault.expiry.expiryReminderWorkName
import com.personal.cardvault.expiry.runExpiryReminderStartup
import com.personal.cardvault.ui.preview.DebugPreviewHubScreen
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.KeyStore
import java.text.DateFormat
import java.util.Date
import java.security.SecureRandom
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/** 仅供 .proto 调试包使用；后续阶段 1 任务在此添加验证按钮。 */
class PrototypeActivity : FragmentActivity() {
    private var cryptoResult by mutableStateOf("尚未执行加密／解密验证")
    private var simulatedUnlocked by mutableStateOf(false)
    private var lockRecord by mutableStateOf("尚未触发上锁")
    private var authenticationDialogVisible = false
    /** Main-thread-only state; never persisted because it identifies only this process's requests. */
    private var nextCryptoRunId = 0L
    private var nextCryptoTokenId = 0L
    private var activeCryptoRequest: CryptoAuthenticationRequest? = null
    /**
     * The prompt is retained while its request is active so this Activity can cancel the session
     * before a finishing teardown. It is deliberately not recreated: a CLEAR_TOP replacement is
     * not a configuration change and does not share this Activity's request/Cipher ownership.
     */
    private var activeBiometricPrompt: BiometricPrompt? = null
    private var authenticationBusy = false
    private var pendingDecryptRunId: Long? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var createdTestDocumentUri: Uri? = null
    private var clearOwnClipboardOnNextFocus = false
    private var clipboardResult by mutableStateOf("尚未复制测试卡号")
    private var argon2Result by mutableStateOf("尚未执行 Argon2id 耗时测试")
    private var argon2Running by mutableStateOf(false)
    private var notificationResult by mutableStateOf("尚未发送测试通知")
    private var backgroundWorkResult by mutableStateOf("正在读取后台任务状态…")
    private var lastCryptoTest: CryptoTestMaterial? = null
    private val argon2Executor = Executors.newSingleThreadExecutor()

    private val createTestDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri == null) {
            cryptoResult = "SAF：已取消选择文件"
        } else {
            createdTestDocumentUri = uri
            authenticateAfterDocumentCreated(uri)
        }
    }

    private val processLifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            // BiometricPrompt 是系统页面，但认证框本身不应结束本次会话。
            if (!authenticationDialogVisible) {
                lockSimulatedSession()
                // 只在应用确实进过后台后，才在回来时的第一次获得焦点检查剪贴板。
                clearOwnClipboardOnNextFocus = true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
        lifecycleScope.launch(Dispatchers.IO) {
            runExpiryReminderStartup(this@PrototypeActivity)
            refreshBackgroundWorkStatus()
        }
        setContent {
            var showPreview by remember { mutableStateOf(false) }
            var biometricResult by remember { mutableStateOf("尚未执行验证") }
            var keySecurityLevel by remember { mutableStateOf("尚未生成 KEK") }
            var secureScreenEnabled by remember { mutableStateOf(false) }
            SideEffect {
                // API 33+ prototype policy: this source change cannot prove the target device's
                // physical Recents result; Windows must retest C10 with a newly built APK.
                if (secureScreenEnabled) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE,
                    )
                    setRecentsScreenshotEnabled(false)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    setRecentsScreenshotEnabled(true)
                }
            }
            if (showPreview) {
                Column {
                    Button(onClick = { showPreview = false }) { Text("返回原型") }
                    DebugPreviewHubScreen()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("安全原型", style = MaterialTheme.typography.headlineMedium)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = { showPreview = true }) { Text("查看页面预览") }
                    }
                    Button(
                        onClick = {
                            startActivity(Intent(this@PrototypeActivity, MainActivity::class.java))
                        },
                    ) {
                        Text("进入真实应用流程")
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Switch(
                            checked = secureScreenEnabled,
                            onCheckedChange = { secureScreenEnabled = it },
                        )
                        Text(
                            text = "禁止截屏和最近任务预览",
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                    Button(onClick = { simulatedUnlocked = true }) {
                        Text("模拟已解锁")
                    }
                    Text("模拟会话：${if (simulatedUnlocked) "已解锁" else "已上锁"}")
                    Text("上锁记录：$lockRecord", modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            val result = androidx.biometric.BiometricManager
                                .from(this@PrototypeActivity)
                                .canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
                            biometricResult = biometricResultText(result)
                        }
                    ) {
                        Text("检查强生物识别")
                    }
                    Button(
                        onClick = {
                            keySecurityLevel = runCatching {
                                securityLevelText(getOrCreateKek())
                            }.getOrElse { "生成或读取 KEK 失败：${it.javaClass.simpleName}" }
                        }
                    ) {
                        Text("生成 KEK 并读取保护级别")
                    }
                    Button(
                        onClick = {
                            if (canUseStrongBiometric()) {
                                runCryptoRoundTrip(
                                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG,
                                    "强生物识别",
                                )
                            } else {
                                cryptoResult = "强生物识别不可用；按计划仅验证锁屏密码路径"
                            }
                        }
                    ) {
                        Text("用强生物识别验证加密／解密")
                    }
                    Button(
                        onClick = {
                            runCryptoRoundTrip(
                                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                                "锁屏密码",
                            )
                        }
                    ) {
                        Text("用锁屏密码验证加密／解密")
                    }
                    Button(onClick = ::deleteKekAndVerifyMissingForDecryption) {
                        Text("删除本应用 Keystore 别名")
                    }
                    Button(
                        onClick = ::measureArgon2ThreeTimes,
                        enabled = !argon2Running,
                    ) {
                        Text(if (argon2Running) "正在计算 Argon2id…" else "连续计算三次 Argon2id 耗时")
                    }
                    Text("Argon2id：$argon2Result", modifier = Modifier.fillMaxWidth())
                    Button(onClick = ::requestAndPostPrototypeNotification) {
                        Text("申请通知权限并发送测试通知")
                    }
                    Text("通知：$notificationResult", modifier = Modifier.fillMaxWidth())
                    Text("后台任务：$backgroundWorkResult", modifier = Modifier.fillMaxWidth())
                    Button(onClick = ::copySensitiveTestCardNumber) {
                        Text("复制敏感测试卡号")
                    }
                    Text("剪贴板：$clipboardResult", modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            cryptoResult = "SAF：请选择新建测试文件"
                            createTestDocument.launch("cardvault-saf-auth-test.cvb")
                        }
                    ) {
                        Text("新建文件后认证并写入测试字节")
                    }
                    Text("验证结果", style = MaterialTheme.typography.titleMedium)
                    Text("BIOMETRIC_STRONG：$biometricResult", modifier = Modifier.fillMaxWidth())
                    Text("KEK 安全级别：$keySecurityLevel", modifier = Modifier.fillMaxWidth())
                    Text("CryptoObject：$cryptoResult", modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.data?.lastPathSegment == NOTIFICATION_ACTION_OPEN) {
            notificationResult = "已由测试通知的“打开测试页面”按钮进入页面"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                postPrototypeNotification()
            } else {
                notificationResult = "通知权限未授予，未发送测试通知"
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshBackgroundWorkStatus()
    }

    override fun onStop() {
        // AndroidX 1.1.0 permits cancellation through the owning BiometricPrompt. Do it while
        // this Activity is still stopped rather than from onDestroy, where its FragmentManager may
        // already be destroyed. This prototype cannot transfer its process-local request/Cipher to
        // a replacement Activity, so it cancels instead of pretending to recreate that session.
        if (isFinishing || isChangingConfigurations) cancelOwnedAuthenticationForFinishingActivity()
        super.onStop()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        // A URI is retained only for the exact document returned by this CreateDocument request.
        // Delete it on teardown if authentication never reached a successful write.
        createdTestDocumentUri?.let(::deleteTestDocument)
        activeBiometricPrompt = null
        activeCryptoRequest = null
        pendingDecryptRunId = null
        authenticationBusy = false
        authenticationDialogVisible = false
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
        argon2Executor.shutdownNow()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && clearOwnClipboardOnNextFocus) {
            clearOwnClipboardOnNextFocus = false
            clearOwnClipboardIfStillPresent()
        }
    }

    private fun refreshBackgroundWorkStatus() {
        WorkManager.getInstance(this).getWorkInfosForUniqueWork(expiryReminderWorkName())
            .addListener(
                {
                    backgroundWorkResult = runCatching {
                        val work = WorkManager.getInstance(this)
                            .getWorkInfosForUniqueWork(expiryReminderWorkName()).get().firstOrNull()
                        if (work == null) {
                            "未找到任务"
                        } else {
                            "${work.state}；下次计划：${DateFormat.getDateTimeInstance().format(Date(work.nextScheduleTimeMillis))}"
                        }
                    }.getOrElse { "读取失败：${it.javaClass.simpleName}" }
                },
                ContextCompat.getMainExecutor(this),
            )
    }

    private fun requestAndPostPrototypeNotification() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            postPrototypeNotification()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE,
            )
        }
    }

    /** T1.9：两个广播按钮需认证；第三个按钮直接打开本 Activity。 */
    private fun postPrototypeNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                PROTOTYPE_NOTIFICATION_CHANNEL_ID,
                "原型通知验证",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val renewIntent = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, PrototypeNotificationReceiver::class.java)
                .setData(prototypeUri(NOTIFICATION_ACTION_RENEW)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val pauseIntent = PendingIntent.getBroadcast(
            this,
            2,
            Intent(this, PrototypeNotificationReceiver::class.java)
                .setData(prototypeUri(NOTIFICATION_ACTION_PAUSE)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openIntent = PendingIntent.getActivity(
            this,
            3,
            Intent(this, PrototypeActivity::class.java)
                .setData(prototypeUri(NOTIFICATION_ACTION_OPEN))
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val authenticatedAction = { title: String, pendingIntent: PendingIntent ->
            NotificationCompat.Action.Builder(0, title, pendingIntent)
                .setAuthenticationRequired(true)
                .build()
        }
        val notification = NotificationCompat.Builder(this, PROTOTYPE_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("通知按钮测试")
            .setContentText("展开后测试三个按钮；锁屏上分别点击。")
            .setContentIntent(openIntent)
            .setAutoCancel(false)
            .addAction(authenticatedAction("续期（广播）", renewIntent))
            .addAction(authenticatedAction("暂停（广播）", pauseIntent))
            .addAction(NotificationCompat.Action.Builder(0, "打开测试页面", openIntent).build())
            .build()
        notificationManager.notify(PROTOTYPE_NOTIFICATION_ID, notification)
        notificationResult = "测试通知已发送；请在通知栏和锁屏上分别验证"
    }

    private fun prototypeUri(action: String): Uri =
        Uri.parse("cardvault://prototype/notification/$action")

    /** 按备份格式 v1 的固定参数连续计算三次；hash() 是同步调用，必须留在后台线程。 */
    private fun measureArgon2ThreeTimes() {
        argon2Running = true
        argon2Result = "正在计算第 1 次／共 $ARGON2_RUNS 次…"
        argon2Executor.execute {
            val salt = ByteArray(ARGON2_SALT_BYTES).also(SecureRandom()::nextBytes)
            val password = "argon2-prototype-password".encodeToByteArray()
            val durations = mutableListOf<Long>()
            val failure = runCatching {
                val argon2 = Argon2Kt()
                repeat(ARGON2_RUNS) { index ->
                    runOnUiThread {
                        argon2Result = "正在计算第 ${index + 1} 次／共 $ARGON2_RUNS 次…"
                    }
                    val startedAt = SystemClock.elapsedRealtimeNanos()
                    argon2.hash(
                        mode = Argon2Mode.ARGON2_ID,
                        password = password,
                        salt = salt,
                        tCostInIterations = ARGON2_ITERATIONS,
                        mCostInKibibyte = ARGON2_MEMORY_KIB,
                        parallelism = ARGON2_PARALLELISM,
                        hashLengthInBytes = ARGON2_HASH_BYTES,
                    )
                    durations += SystemClock.elapsedRealtimeNanos() - startedAt
                }
            }.exceptionOrNull()
            runOnUiThread {
                argon2Running = false
                argon2Result = if (failure == null) {
                    durations.mapIndexed { index, nanos ->
                        "第 ${index + 1} 次：%.3f 秒".format(nanos / NANOS_PER_SECOND.toDouble())
                    }.joinToString("；")
                } else {
                    "计算失败：${failure.javaClass.simpleName}"
                }
            }
        }
    }

    private fun copySensitiveTestCardNumber() {
        val clip = ClipData.newPlainText(CLIP_LABEL, TEST_CARD_NUMBER).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        (getSystemService(ClipboardManager::class.java)).setPrimaryClip(clip)
        clipboardResult = "已复制带敏感标记的测试卡号"
    }

    /** 只读取描述；标签不再是本应用的固定值时绝不读取或清除用户的新内容。 */
    private fun clearOwnClipboardIfStillPresent() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.label == CLIP_LABEL) {
            clipboard.clearPrimaryClip()
            clipboardResult = "回到前台后已清除本应用的剪贴板内容"
        } else {
            clipboardResult = "回到前台后保留了非本应用的剪贴板内容"
        }
    }

    private fun lockSimulatedSession() {
        simulatedUnlocked = false
        lockRecord = "${DateFormat.getDateTimeInstance().format(Date())}：ON_STOP，模拟会话已上锁"
    }

    /**
     * 使用同一把要求逐次认证的 KEK 完成一次加密再解密。
     * 两次 doFinal 都只使用 BiometricPrompt 回调交还的 Cipher。
     */
    private fun runCryptoRoundTrip(authenticators: Int, pathName: String) {
        if (authenticationBusy) {
            cryptoResult = "安全诊断：认证正在进行，拒绝重叠 round"
            return
        }
        val runId = ++nextCryptoRunId
        val kek = runCatching { getOrCreateKek() }.getOrElse {
            cryptoResult = "$pathName：读取 KEK 失败：${it.javaClass.simpleName}；runId=$runId"
            return
        }
        val plaintext = ByteArray(32).also(SecureRandom()::nextBytes)
        val encryptMode = Cipher.ENCRYPT_MODE
        val encryptCipher = runCatching {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(encryptMode, kek) }
        }.getOrElse {
            cryptoResult = "$pathName：初始化加密失败：${it.javaClass.simpleName}；runId=$runId"
            return
        }
        val request = newCryptoRequest(
            runId = runId, phase = CryptoPhase.ENCRYPT, cipher = encryptCipher,
            initializationMode = encryptMode, pathName = pathName, operation = "加密",
        )
        cryptoResult = "$pathName：请认证以加密随机字节；${request.diagnostics()}"
        authenticate(request, authenticators) { authorizedCipher ->
            val ciphertext = runCatching { authorizedCipher.doFinal(plaintext) }.getOrElse {
                cryptoResult = "$pathName：加密失败：${it.javaClass.simpleName}；${request.diagnostics()}"
                finishCryptoRun(request.runId)
                return@authenticate
            }
            val decryptMode = Cipher.DECRYPT_MODE
            val decryptCipher = runCatching {
                Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(decryptMode, kek, GCMParameterSpec(128, authorizedCipher.iv))
                }
            }.getOrElse {
                cryptoResult = "$pathName：初始化解密失败：${it.javaClass.simpleName}；${request.diagnostics()}"
                finishCryptoRun(request.runId)
                return@authenticate
            }
            lastCryptoTest = CryptoTestMaterial(ciphertext, authorizedCipher.iv, pathName)
            val decryptRequest = newCryptoRequest(
                runId = runId, phase = CryptoPhase.DECRYPT, cipher = decryptCipher,
                initializationMode = decryptMode, pathName = pathName, operation = "解密",
            )
            pendingDecryptRunId = runId
            cryptoResult = "$pathName：加密成功；认证框正在清理，随后请再次认证以解密；${decryptRequest.diagnostics()}"
            // AndroidX 1.1.0 dispatches success before asynchronous Fragment removal. Post rather
            // than re-enter authenticate from this callback; no arbitrary time delay is used.
            mainHandler.post {
                // The pending state owns busy until authenticate has claimed this same run. Keeping
                // it until then lets authenticate clean up this run if preflight/startup fails.
                if (pendingDecryptRunId != runId) return@post
                if (isFinishing || isDestroyed || !authenticationBusy) {
                    finishCryptoRun(runId)
                    return@post
                }
                if (activeCryptoRequest != null) return@post
                authenticate(decryptRequest, authenticators) { decryptingCipher ->
                    val decrypted = runCatching { decryptingCipher.doFinal(ciphertext) }.getOrElse {
                        cryptoResult = "$pathName：解密失败：${it.javaClass.simpleName}；${decryptRequest.diagnostics()}"
                        finishCryptoRun(runId)
                        return@authenticate
                    }
                    cryptoResult = if (plaintext.contentEquals(decrypted)) {
                        "$pathName：加密、解密均成功，随机字节一致；${decryptRequest.diagnostics()}"
                    } else {
                        "$pathName：解密完成，但随机字节不一致；${decryptRequest.diagnostics()}"
                    }
                    finishCryptoRun(runId)
                }
                if (activeCryptoRequest === decryptRequest) {
                    pendingDecryptRunId = null
                } else if (pendingDecryptRunId == runId) {
                    // authenticate rejected or failed before claiming the pending phase.
                    finishCryptoRun(runId)
                }
            }
        }
    }

    /**
     * T1.10：只删除本应用的 KEK，然后对 T1.3 已加密的测试数据重新开始解密。
     * 不调用 getOrCreateKek()，避免检查时意外重建别名。
     */
    private fun deleteKekAndVerifyMissingForDecryption() {
        val test = lastCryptoTest
        if (test == null) {
            cryptoResult = "请先完成一次 T1.3 加密，才能在删除后执行解密"
            return
        }
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(KEK_ALIAS)
        }.onFailure {
            cryptoResult = "删除 Keystore 别名失败：${it.javaClass.simpleName}"
            return
        }

        val keyStore = runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        }.getOrElse {
            cryptoResult = "删除后读取 Keystore 失败：${it.javaClass.simpleName}"
            return
        }
        val deletedKek = keyStore.getKey(KEK_ALIAS, null) as? SecretKey
        if (deletedKek == null) {
            cryptoResult = "已判断别名不存在；${test.pathName}：T1.3 解密无法开始（预期），应用未崩溃"
            return
        }

        // 这里不应发生；即使别名仍存在，也实际重新初始化解密并只报告结果。
        val decryptInit = runCatching {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, deletedKek, GCMParameterSpec(128, test.iv))
            }
        }
        cryptoResult = if (decryptInit.isSuccess) {
            "删除后别名仍存在，T1.3 解密初始化意外成功"
        } else {
            "删除后别名仍存在，T1.3 解密初始化失败：${decryptInit.exceptionOrNull()?.javaClass?.simpleName}"
        }
    }

    /**
     * SAF 选择器会令应用进入后台并清掉会话，因此必须在其返回之后才发起认证。
     * 认证被取消、失败或写入失败时，删除本次 CreateDocument 刚创建的文件。
     */
    private fun authenticateAfterDocumentCreated(uri: Uri) {
        val kek = runCatching { getOrCreateKek() }.getOrElse {
            cryptoResult = "SAF：读取 KEK 失败：${it.javaClass.simpleName}；${deleteTestDocument(uri)}"
            return
        }
        val writeMode = Cipher.ENCRYPT_MODE
        val cipher = runCatching {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(writeMode, kek) }
        }.getOrElse {
            cryptoResult = "SAF：初始化认证操作失败：${it.javaClass.simpleName}；${deleteTestDocument(uri)}"
            return
        }
        if (authenticationBusy) {
            cryptoResult = "SAF：安全诊断：认证正在进行，未写入文件；${deleteTestDocument(uri)}"
            return
        }
        val request = newCryptoRequest(
            ++nextCryptoRunId, CryptoPhase.ENCRYPT, cipher, initializationMode = writeMode, pathName = "SAF", operation = "写入",
        )
        cryptoResult = "SAF：文件已创建，请认证后写入测试字节；${request.diagnostics()}"
        authenticate(
            request = request,
            authenticators = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            onTerminal = { deleteTestDocument(uri) },
        ) { authorizedCipher ->
            runCatching {
                // 消耗经认证授权的 Cipher；测试文件本身只写固定测试字节。
                authorizedCipher.doFinal(ByteArray(1))
                contentResolver.openOutputStream(uri)?.use { it.write(SAF_TEST_BYTES) }
                    ?: error("无法打开测试文件")
            }.onSuccess {
                createdTestDocumentUri = null
                cryptoResult = "SAF：认证成功，已写入测试字节；${request.diagnostics()}"
            }.onFailure {
                cryptoResult = "SAF：写入失败：${it.javaClass.simpleName}；${request.diagnostics()}；${deleteTestDocument(uri)}"
            }
            finishCryptoRun(request.runId)
        }
    }

    private fun deleteTestDocument(uri: Uri): String {
        val deleted = runCatching { DocumentsContract.deleteDocument(contentResolver, uri) }.getOrDefault(false)
        if (createdTestDocumentUri == uri) createdTestDocumentUri = null
        return if (deleted) "已删除新建文件" else "未能删除新建文件，请手动删除"
    }

    private fun newCryptoRequest(
        runId: Long,
        phase: CryptoPhase,
        cipher: Cipher,
        initializationMode: Int,
        pathName: String,
        operation: String,
    ) = CryptoAuthenticationRequest(
        runId = runId,
        phase = phase,
        token = Any(),
        tokenId = ++nextCryptoTokenId,
        cipher = cipher,
        initializationMode = initializationMode,
        providerName = cipher.provider?.name ?: "unknown",
        pathName = pathName,
        operation = operation,
    )

    private fun authenticate(
        request: CryptoAuthenticationRequest,
        authenticators: Int,
        onTerminal: (() -> String)? = null,
        onSuccess: (Cipher) -> Unit,
    ) {
        fun terminalOutcome(): String = runCatching { onTerminal?.invoke() }
            .fold(
                onSuccess = { it?.let { outcome -> "；$outcome" } ?: "" },
                onFailure = { "；清理失败：${it.javaClass.simpleName}" },
            )
        fun finishOwnedRun() {
            if (activeCryptoRequest === request || pendingDecryptRunId == request.runId) {
                finishCryptoRun(request.runId)
            }
        }
        if (isFinishing || isDestroyed || supportFragmentManager.isDestroyed || supportFragmentManager.isStateSaved) {
            cryptoResult = "${request.pathName}：启动${request.operation}认证已取消（Activity 状态不可用）；${request.diagnostics()}${terminalOutcome()}"
            finishOwnedRun()
            return
        }
        if (activeCryptoRequest != null || (pendingDecryptRunId != null && pendingDecryptRunId != request.runId)) {
            cryptoResult = "${request.pathName}：安全诊断：拒绝重叠认证；${request.diagnostics()}${terminalOutcome()}"
            finishOwnedRun()
            return
        }
        activeCryptoRequest = request
        authenticationBusy = true
        try {
            val prompt = BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (activeCryptoRequest !== request) return
                        // cancelAuthentication() cannot retract a success already queued by AndroidX.
                        // The finishing path revokes this request before asking AndroidX to cancel,
                        // so a later success must neither consume its Cipher nor invoke client work.
                        if (!request.successEligible) {
                            cryptoResult = "${request.pathName}：结束中的认证成功已丢弃；${request.diagnostics()}${terminalOutcome()}"
                            finishCryptoRun(request.runId)
                            return
                        }
                        val authorizedCipher = result.cryptoObject?.cipher
                        val mismatch = requestMismatch(request, authorizedCipher)
                        if (mismatch != null) {
                            cryptoResult = "${request.pathName}：安全诊断：认证关联失败（$mismatch）；${request.diagnostics()}${terminalOutcome()}"
                            finishCryptoRun(request.runId)
                            return
                        }
                        request.consumed = true
                        // Retain active ownership while client code completes this phase. In
                        // particular, finishCryptoRun() at the end of a successful decrypt must
                        // still be able to release busy; stale callbacks cannot own this request.
                        authenticationDialogVisible = false
                        try {
                            onSuccess(authorizedCipher!!)
                        } catch (error: Throwable) {
                            cryptoResult = "${request.pathName}：${request.operation}认证后处理失败：${error.javaClass.simpleName}；${request.diagnostics()}${terminalOutcome()}"
                            finishCryptoRun(request.runId)
                        } finally {
                            if (activeCryptoRequest === request) {
                                activeCryptoRequest = null
                                authenticationDialogVisible = false
                                if (pendingDecryptRunId == null) authenticationBusy = false
                            }
                        }
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (activeCryptoRequest !== request) return
                        cryptoResult = "${request.pathName}：${request.operation}认证取消或出错（$errorCode：$errString）；${request.diagnostics()}${terminalOutcome()}"
                        finishCryptoRun(request.runId)
                    }

                    override fun onAuthenticationFailed() {
                        if (activeCryptoRequest === request) {
                            cryptoResult = "${request.pathName}：${request.operation}认证未通过，可重试或取消；${request.diagnostics()}"
                        }
                    }
                },
            )
            val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
                .setTitle("验证 ${request.operation}")
                .setAllowedAuthenticators(authenticators)
            if (authenticators and androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL == 0) {
                promptInfoBuilder.setNegativeButtonText("取消")
            }
            activeBiometricPrompt = prompt
            authenticationDialogVisible = true
            prompt.authenticate(promptInfoBuilder.build(), BiometricPrompt.CryptoObject(request.cipher))
        } catch (error: Throwable) {
            cryptoResult = "${request.pathName}：启动${request.operation}认证失败：${error.javaClass.simpleName}；${request.diagnostics()}${terminalOutcome()}"
            finishCryptoRun(request.runId)
        }
    }

    /**
     * Cancels only the session this Activity still owns while it is being destroyed. BiometricPrompt
     * 1.1.0 routes cancelAuthentication() to its existing BiometricFragment cancellation signal; it does not
     * require a FragmentTransaction here. Cancellation does not retract an already queued success,
     * so revoke this request's success/write eligibility first. Completion remains callback-driven
     * and idempotent; onDestroy independently removes an unwritten SAF URI. This prototype cancels
     * configuration changes rather than transferring its process-local request/Cipher to a replacement.
     */
    private fun cancelOwnedAuthenticationForFinishingActivity() {
        val request = activeCryptoRequest ?: return
        request.successEligible = false
        val prompt = activeBiometricPrompt ?: return
        if (!authenticationBusy) return
        try {
            prompt.cancelAuthentication()
        } catch (error: Throwable) {
            // Teardown still deletes only the exact pending SAF URI in onDestroy. Do not invoke
            // terminal handling here because cancelAuthentication may yet dispatch its callback.
            cryptoResult = "${request.pathName}：结束中的认证取消请求失败：${error.javaClass.simpleName}；${request.diagnostics()}"
        }
    }

    /** Must run before doFinal; Cipher has no public getOpmode(), so mode is recorded at init. */
    private fun requestMismatch(request: CryptoAuthenticationRequest, returnedCipher: Cipher?): String? = when {
        activeCryptoRequest !== request -> "request is no longer active"
        request.initializationMode != request.phase.expectedInitializationMode -> "initialization mode does not match phase"
        returnedCipher == null -> "callback returned no Cipher"
        returnedCipher !== request.cipher -> "callback Cipher is not this request's Cipher"
        request.consumed -> "Cipher was already consumed"
        else -> null
    }

    private fun finishCryptoRun(runId: Long) {
        val finishesActive = activeCryptoRequest?.runId == runId
        val finishesPending = pendingDecryptRunId == runId
        if (finishesActive) {
            activeCryptoRequest = null
            activeBiometricPrompt = null
        }
        if (finishesPending) pendingDecryptRunId = null
        if (finishesActive || finishesPending) authenticationDialogVisible = false
        if ((finishesActive || finishesPending) && activeCryptoRequest == null && pendingDecryptRunId == null) {
            authenticationBusy = false
        }
    }

    private fun canUseStrongBiometric(): Boolean = androidx.biometric.BiometricManager
        .from(this)
        .canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
        androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS

    private fun getOrCreateKek(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEK_ALIAS, null) as? SecretKey)?.let { return it }

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEK_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationParameters(
                        0,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                    )
                    .build(),
            )
        }.generateKey()
    }

    private fun securityLevelText(key: SecretKey): String {
        val keyInfo = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
            .getKeySpec(key, KeyInfo::class.java) as KeyInfo
        return when (keyInfo.securityLevel) {
            KeyProperties.SECURITY_LEVEL_STRONGBOX -> "StrongBox"
            KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "TEE"
            KeyProperties.SECURITY_LEVEL_SOFTWARE -> "软件"
            else -> "未知"
        }
    }

    private fun biometricResultText(result: Int): String = when (result) {
        androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS -> "可用（$result）"
        androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "没有硬件（$result）"
        androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "硬件暂不可用（$result）"
        androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "未录入（$result）"
        androidx.biometric.BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "需要安全更新（$result）"
        androidx.biometric.BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> "不支持（$result）"
        androidx.biometric.BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> "状态未知（$result）"
        else -> "未知结果（$result）"
    }

    private enum class CryptoPhase(val expectedInitializationMode: Int) {
        ENCRYPT(Cipher.ENCRYPT_MODE),
        DECRYPT(Cipher.DECRYPT_MODE),
    }

    /** Contains only process-local identities and safe diagnostics, never cryptographic material. */
    private data class CryptoAuthenticationRequest(
        val runId: Long,
        val phase: CryptoPhase,
        @Suppress("unused") val token: Any,
        val tokenId: Long,
        val cipher: Cipher,
        val initializationMode: Int,
        val providerName: String,
        val pathName: String,
        val operation: String,
        var consumed: Boolean = false,
    ) {
        /** Revoked synchronously when finishing starts; not a cryptographic authorization state. */
        var successEligible = true

        fun diagnostics(): String =
            "runId=$runId phase=$phase token=$tokenId mode=$initializationMode provider=$providerName consumed=$consumed eligible=$successEligible"
    }

    private data class CryptoTestMaterial(
        val ciphertext: ByteArray,
        val iv: ByteArray,
        val pathName: String,
    )

    private companion object {
        const val KEK_ALIAS = "vault_kek"
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1_009
        const val PROTOTYPE_NOTIFICATION_CHANNEL_ID = "prototype_notification_actions"
        const val PROTOTYPE_NOTIFICATION_ID = 1_009
        const val NOTIFICATION_ACTION_RENEW = "renew"
        const val NOTIFICATION_ACTION_PAUSE = "pause"
        const val NOTIFICATION_ACTION_OPEN = "open"
        const val CLIP_LABEL = "cardvault"
        const val TEST_CARD_NUMBER = "4111111111111111"
        const val ARGON2_ITERATIONS = 3
        const val ARGON2_MEMORY_KIB = 65_536
        const val ARGON2_PARALLELISM = 1
        const val ARGON2_HASH_BYTES = 32
        const val ARGON2_SALT_BYTES = 16
        const val ARGON2_RUNS = 3
        const val NANOS_PER_SECOND = 1_000_000_000L
        val SAF_TEST_BYTES = byteArrayOf(0x43, 0x56, 0x42, 0x4b)
    }
}
