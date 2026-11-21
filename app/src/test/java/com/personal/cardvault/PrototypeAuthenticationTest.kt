package com.personal.cardvault

import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableStateOf
import java.io.File
import java.lang.reflect.Modifier
import javax.crypto.Cipher
import org.junit.Assert.*
import org.junit.Test
import sun.misc.Unsafe

/**
 * T1.3/T1.5, dev-plan 8.2–8.5/9.3, and the explicitly authorized T1.13 repair.
 * Runs only process-local bookkeeping with JVM Ciphers. No Android lifecycle, prompt,
 * Keystore, provider deletion, or cryptographic authorization is simulated here.
 * Source checks describe the teardown contract, not execution order observed on Android.
 * Neither these checks nor reflection/JVM bookkeeping prove that an AndroidX window is
 * dismissed or that the Windows API 37 CLEAR_TOP regression passes on a real device.
 */
class PrototypeAuthenticationTest {
    // Bypass the Android constructor, which requires a device/Looper. Only fields used by
    // requestMismatch, finishCryptoRun and the callback's bookkeeping are initialized.
    private val activity = (Unsafe::class.java.getDeclaredField("theUnsafe").apply {
        isAccessible = true
    }.get(null) as Unsafe).allocateInstance(PrototypeActivity::class.java) as PrototypeActivity

    init {
        set("cryptoResult\$delegate", mutableStateOf("current UI"))
    }

    @Test fun onlyTheActiveRequestAndItsCipherAreAccepted() {
        val cipher = cipher()
        val current = request(1, "ENCRYPT", cipher)
        set("activeCryptoRequest", current)
        assertNull(mismatch(current, cipher))
        assertNotNull(mismatch(request(1, "ENCRYPT", cipher), cipher))
        assertNotNull(mismatch(request(2, "ENCRYPT", cipher), cipher))
        assertNotNull(mismatch(current, null))
        assertNotNull(mismatch(current, cipher()))
    }

    @Test fun recordedInitializationModeMustMatchPhase() {
        val cipher = cipher()
        val current = request(1, "DECRYPT", cipher, Cipher.ENCRYPT_MODE)
        set("activeCryptoRequest", current)
        assertNotNull(mismatch(current, cipher))
    }

    @Test fun alreadyConsumedCipherIsRejected() {
        val cipher = cipher()
        val current = request(1, "ENCRYPT", cipher)
        current.javaClass.getDeclaredField("consumed").apply { isAccessible = true }.set(current, true)
        set("activeCryptoRequest", current)
        assertNotNull(mismatch(current, cipher))
    }

    @Test fun staleEncryptSuccessCannotTerminateDecryptWithTheSameRunId() {
        val oldCipher = cipher()
        val old = request(7, "ENCRYPT", oldCipher)
        val current = request(7, "DECRYPT", cipher())
        activate(current)
        val currentPrompt = get("activeBiometricPrompt")
        var terminalCalls = 0
        var successCalls = 0
        callback(old, { terminalCalls++; "deleted" }, { successCalls++ })
            .onAuthenticationSucceeded(result(oldCipher))
        assertSame(current, get("activeCryptoRequest"))
        assertSame(currentPrompt, get("activeBiometricPrompt"))
        assertEquals(true, get("authenticationBusy"))
        assertEquals(true, get("authenticationDialogVisible"))
        assertEquals("current UI", ui())
        assertEquals(0, terminalCalls)
        assertEquals(0, successCalls)
    }

    @Test fun staleSafSuccessCannotDeleteFileOrChangeAnotherRun() {
        val oldCipher = cipher()
        val old = request(1, "ENCRYPT", oldCipher)
        val current = request(2, "ENCRYPT", cipher())
        activate(current)
        var deletions = 0
        callback(old, { deletions++; "deleted" })
            .onAuthenticationSucceeded(result(oldCipher))
        assertSame(current, get("activeCryptoRequest"))
        assertEquals(true, get("authenticationDialogVisible"))
        assertEquals("current UI", ui())
        assertEquals(0, deletions)
    }

    @Test fun successIsConsumedOnceAndDuplicateCannotErasePendingDecrypt() {
        val cipher = cipher()
        val current = request(3, "ENCRYPT", cipher)
        assertTrue("New requests allow success", current.javaClass.getDeclaredField("successEligible")
            .apply { isAccessible = true }.getBoolean(current))
        activate(current)
        var successes = 0
        var deletions = 0
        val callback = callback(current, { deletions++; "deleted" }) {
            assertSame(cipher, it)
            successes++
            set("pendingDecryptRunId", 3L)
        }
        callback.onAuthenticationSucceeded(result(cipher))
        assertEquals(true, current.javaClass.getDeclaredField("consumed").apply {
            isAccessible = true
        }.get(current))
        callback.onAuthenticationSucceeded(result(cipher))
        assertEquals(1, successes)
        assertEquals(0, deletions)
        assertEquals(3L, get("pendingDecryptRunId"))
        assertEquals(true, get("authenticationBusy"))
        assertNull(get("activeCryptoRequest"))
    }

    @Test fun revokedSuccessDoesNotConsumeCipherOrRunClientWorkAndTerminatesOnce() {
        val cipher = cipher()
        val current = request(12, "ENCRYPT", cipher)
        activate(current)
        var terminalCalls = 0
        var successCalls = 0
        // The success spy represents client work, including SAF writes/URI clearing.
        // The terminal spy observes cleanup dispatch, not actual provider I/O.
        val callback = callback(current, { terminalCalls++; "未能删除新建文件，请手动删除" }, { successCalls++ })
        current.javaClass.getDeclaredField("successEligible").apply { isAccessible = true }
            .setBoolean(current, false)

        // Deliver success captured before cancellation, while this request still owns the run.
        callback.onAuthenticationSucceeded(result(cipher))
        assertEquals(0, successCalls)
        assertFalse("Revoked success must leave the Cipher unconsumed",
            current.javaClass.getDeclaredField("consumed").apply { isAccessible = true }.getBoolean(current))
        assertEquals(1, terminalCalls)
        assertReleased()
        assertTrue(ui().contains("未能删除新建文件，请手动删除"))

        callback.onAuthenticationSucceeded(result(cipher))
        callback.onAuthenticationError(5, "late cancel")
        assertEquals(0, successCalls)
        assertEquals(1, terminalCalls)
        assertReleased()
    }

    @Test fun currentCipherMismatchTerminatesOnceAndReleasesBusy() {
        val current = request(4, "ENCRYPT", cipher())
        activate(current)
        var deletions = 0
        var successes = 0
        val callback = callback(current, { deletions++; "deletion outcome" }, { successes++ })
        callback.onAuthenticationSucceeded(result(null))
        callback.onAuthenticationError(5, "late cancel")
        assertEquals(1, deletions)
        assertEquals(0, successes)
        assertReleased()
        assertTrue(ui().contains("deletion outcome"))
    }

    @Test fun cancelReleasesBusyAndPreservesSafDeletionOutcome() {
        val current = request(5, "ENCRYPT", cipher())
        activate(current)
        var deletions = 0
        val callback = callback(current, { deletions++; "未能删除新建文件，请手动删除" })
        callback.onAuthenticationError(5, "cancel")
        callback.onAuthenticationError(5, "duplicate")
        assertEquals(1, deletions)
        assertReleased()
        assertTrue(ui().contains("未能删除新建文件，请手动删除"))
    }

    @Test fun failedAttemptRemainsRetryableAndStaleErrorsAreIgnored() {
        val current = request(6, "ENCRYPT", cipher())
        activate(current)
        val currentPrompt = get("activeBiometricPrompt")
        var terminalCalls = 0
        callback(current, { terminalCalls++; "deleted" }).onAuthenticationFailed()
        assertSame(current, get("activeCryptoRequest"))
        assertEquals(true, get("authenticationBusy"))
        assertEquals(true, get("authenticationDialogVisible"))
        val message = ui()
        val old = callback(request(5, "ENCRYPT", cipher()), { terminalCalls++; "deleted" })
        old.onAuthenticationError(5, "stale")
        old.onAuthenticationFailed()
        assertEquals(message, ui())
        assertSame(currentPrompt, get("activeBiometricPrompt"))
        assertEquals(0, terminalCalls)
    }

    @Test fun finishingUnrelatedRunCannotHideCurrentPrompt() {
        val current = request(9, "DECRYPT", cipher())
        activate(current)
        val currentPrompt = get("activeBiometricPrompt")
        finish(8)
        assertSame(current, get("activeCryptoRequest"))
        assertSame(currentPrompt, get("activeBiometricPrompt"))
        assertEquals(true, get("authenticationBusy"))
        assertEquals(true, get("authenticationDialogVisible"))
    }

    @Test fun finishingOwnedRunReleasesActiveOrPendingState() {
        activate(request(10, "DECRYPT", cipher()))
        finish(10)
        assertReleased()
        set("pendingDecryptRunId", 11L)
        set("authenticationBusy", true)
        finish(11)
        assertReleased()
    }

    @Test fun teardownCancellationIsGuardedInOnStopBeforeSuperStop() {
        val stop = methodSource("onStop")
        val cancel = "cancelOwnedAuthenticationForFinishingActivity()"
        assertTrue("Only finishing/configuration teardown requests cancellation; an ordinary stop must not",
            Regex("""if\s*\(\s*isFinishing\s*\|\|\s*isChangingConfigurations\s*\)\s*(?:\{\s*)?${Regex.escape(cancel)}""")
                .containsMatchIn(stop))
        assertEquals("One teardown cancellation call", 1, Regex(Regex.escape(cancel)).findAll(stop).count())
        assertTrue("Request cancellation before FragmentActivity's stop teardown",
            stop.indexOf(cancel) < stop.indexOf("super.onStop()"))
        // onStop is the earlier lifecycle hook. This does not execute an Android lifecycle.
        assertFalse("Destroy must not request cancellation", methodSource("onDestroy").contains(cancel))
        assertEquals("No additional cancellation call sites outside the owned helper", 1,
            Regex("""\.cancelAuthentication\s*\(""").findAll(codeSource()).count())
    }

    @Test fun destroyOnlyCleansBookkeepingAndTheExactPendingUri() {
        val destroy = methodSource("onDestroy")
        assertTrue(destroy.contains("createdTestDocumentUri?.let(::deleteTestDocument)"))
        for (assignment in listOf("activeBiometricPrompt = null", "activeCryptoRequest = null",
            "pendingDecryptRunId = null", "authenticationBusy = false", "authenticationDialogVisible = false")) {
            assertTrue("Destroy must clear $assignment before superclass teardown",
                destroy.indexOf(assignment) in 0 until destroy.indexOf("super.onDestroy()"))
        }
        // Deliberately bound calls to cleanup: reject UI/Fragment work, cancellation, and
        // indirect new helper calls that would need review. No Android method is invoked here.
        val allowedCleanupCalls = setOf("onDestroy", "removeCallbacksAndMessages", "let", "get",
            "removeObserver", "shutdownNow")
        assertEquals("Destroy must not operate UI/Fragments or cancel/authenticate", emptySet<String>(),
            callNames(destroy) - allowedCleanupCalls)
        assertFalse("Destroy must not access UI or Fragment state",
            Regex("""\b(supportFragmentManager|fragmentManager|window|cryptoResult|simulatedUnlocked)\b""")
                .containsMatchIn(destroy))
        assertEquals(setOf("deleteTestDocument"),
            Regex("""::(\w+)""").findAll(destroy).map { it.groupValues[1] }.toSet())
    }

    @Test fun requestAndPromptAreOwnedBeforeAuthenticateUsesThatRequestsCipher() {
        val startup = methodSource("authenticate")
        val claim = startup.indexOf("activeCryptoRequest = request")
        val construct = startup.indexOf("val prompt = BiometricPrompt(")
        val retain = startup.indexOf("activeBiometricPrompt = prompt")
        val authenticate = startup.indexOf("prompt.authenticate(")
        assertTrue("Claim request before constructing its prompt", claim >= 0 && construct > claim)
        assertTrue("Retain that prompt before authenticate can dispatch callbacks", retain > construct && authenticate > retain)
        assertTrue("The prompt is hosted by this Activity",
            Regex("""val prompt = BiometricPrompt\(\s*this,""").containsMatchIn(startup))
        assertTrue("Authentication must use the owned request's Cipher",
            startup.contains("prompt.authenticate(promptInfoBuilder.build(), BiometricPrompt.CryptoObject(request.cipher))"))
        assertTrue("Startup failure must finish its run", startup.substringAfterLast("catch (")
            .contains("finishCryptoRun(request.runId)"))
    }

    @Test fun cancellationUsesExistingActiveOwnershipWithoutStartingOrTransferringWork() {
        val cancel = methodSource("cancelOwnedAuthenticationForFinishingActivity")
        val invoke = cancel.indexOf("prompt.cancelAuthentication()")
        for (guard in listOf("val request = activeCryptoRequest ?: return",
            "val prompt = activeBiometricPrompt ?: return", "if (!authenticationBusy) return")) {
            assertTrue("Cancellation requires current ownership: $guard", cancel.indexOf(guard) in 0 until invoke)
        }
        // Cancel remains callback-driven. In particular, it must not finish/delete synchronously,
        // construct a replacement prompt, restart crypto, or transfer state to another Activity.
        assertEquals(emptySet<String>(), callNames(cancel) - setOf(
            "cancelOwnedAuthenticationForFinishingActivity", "if", "catch", "cancelAuthentication", "diagnostics"))
        assertFalse("Cancellation must retain ownership until callback/destroy", cancel.contains("activeCryptoRequest ="))
        assertFalse("Cancellation must retain the owning prompt", cancel.contains("activeBiometricPrompt ="))
        assertEquals("Only startup constructs a prompt; no recreation on replacement/teardown", 1,
            Regex("""\bBiometricPrompt\s*\(""").findAll(codeSource()).count())
        for (field in listOf("activeCryptoRequest", "activeBiometricPrompt", "pendingDecryptRunId")) {
            assertFalse("Ownership must be Activity-local: $field",
                Modifier.isStatic(PrototypeActivity::class.java.getDeclaredField(field).modifiers))
            assertFalse("A replacement Activity must not restore $field", methodSource("onCreate").contains(field))
        }
    }

    @Test fun cancellationRevokesSuccessBeforePromptCancellationOrEarlyReturn() {
        val cancel = methodSource("cancelOwnedAuthenticationForFinishingActivity")
        val claim = cancel.indexOf("val request = activeCryptoRequest ?: return")
        val revoke = Regex("""\brequest\.successEligible\s*=\s*false\b""")
            .find(cancel)?.range?.first ?: error("Cancellation must revoke success eligibility")
        assertTrue("Revoke the currently owned request", claim >= 0 && revoke > claim)
        for (later in listOf("val prompt = activeBiometricPrompt ?: return",
            "if (!authenticationBusy) return", "prompt.cancelAuthentication()")) {
            assertTrue("Revoke success synchronously before $later", cancel.indexOf(later) > revoke)
        }
    }

    @Test fun safTerminalCleanupTargetsOnlyTheUriReturnedForThisDocument() {
        val saf = methodSource("authenticateAfterDocumentCreated")
        assertTrue("Terminal cleanup captures this request's URI", saf.contains("onTerminal = { deleteTestDocument(uri) }"))
        val deletion = methodSource("deleteTestDocument")
        assertTrue("Delete exactly the supplied URI", deletion.contains("DocumentsContract.deleteDocument(contentResolver, uri)"))
        assertTrue("Only clear matching URI bookkeeping", deletion.contains("if (createdTestDocumentUri == uri) createdTestDocumentUri = null"))
        assertEquals("Deletion must not enumerate or remove other documents", emptySet<String>(),
            callNames(deletion) - setOf("deleteTestDocument", "runCatching", "deleteDocument", "getOrDefault", "if"))
        // This is a source contract, not provider I/O or a simulated terminal write fault.
    }

    @Test fun recentsScreenshotPolicyFollowsSecureScreenSwitch() {
        val onCreate = methodSource("onCreate")
        // Bound both simple branches to this SideEffect; missing delimiters must fail rather
        // than let another onCreate branch satisfy the assertions. Comments are already stripped.
        val policy = Regex(
            """\bSideEffect\s*\{\s*if\s*\(\s*secureScreenEnabled\s*\)\s*\{([^{}]*)}\s*""" +
                """else\s*\{([^{}]*)}\s*}"""
        ).find(onCreate) ?: error("Missing secureScreenEnabled if/else in SideEffect")
        val secureBranch = policy.groupValues[1]
        val openBranch = policy.groupValues[2]
        assertTrue("Secure mode must disable Recents screenshots",
            secureBranch.contains("setRecentsScreenshotEnabled(false)"))
        assertTrue("Secure mode must retain FLAG_SECURE",
            secureBranch.contains("window.setFlags"))
        assertTrue("Normal mode must re-enable Recents screenshots",
            openBranch.contains("setRecentsScreenshotEnabled(true)"))
        assertTrue("Normal mode must clear FLAG_SECURE",
            openBranch.contains("window.clearFlags"))
        // Source/API usage contract only; no Android UI or Recents snapshot is exercised.
        // Passing does not establish C10 on an API 37 device; Windows physical-device retest is required.
    }

    @Test fun lifecyclePreflightPrecedesBusyAndEntirePromptStartupIsCaught() {
        val body = source().substringAfter("private fun authenticate(").substringBefore("private fun requestMismatch(")
        val busy = body.indexOf("activeCryptoRequest = request")
        for (guard in listOf("isFinishing", "isDestroyed", "supportFragmentManager.isDestroyed", "supportFragmentManager.isStateSaved")) {
            assertTrue("Missing startup preflight before busy: $guard", body.indexOf(guard) in 0 until busy)
        }
        // Prompt construction and PromptInfo building can throw too, before authenticate().
        val startupTry = body.indexOf("try {")
        assertTrue("Prompt constructor must be caught", startupTry in 0 until body.indexOf("val prompt = BiometricPrompt("))
        val failure = body.substringAfter("catch (")
        assertTrue("Startup exception must release busy", failure.contains("finishCryptoRun(request.runId)"))
    }

    @Test fun destroyRemovesDeferredDecryptAndRunnableChecksLifecycleBeforeStarting() {
        val source = source()
        val destroy = source.substringAfter("override fun onDestroy() {").substringBefore("super.onDestroy()")
        assertTrue("Destroy must remove queued decrypt", destroy.contains("mainHandler.removeCallbacksAndMessages(null)"))
        assertTrue("Destroy must invalidate active callback", destroy.contains("activeCryptoRequest = null"))
        assertTrue("Destroy must invalidate pending decrypt", destroy.contains("pendingDecryptRunId = null"))
        assertTrue("Destroy must release busy", destroy.contains("authenticationBusy = false"))
        val posted = source.substringAfter("mainHandler.post {").substringBefore("authenticate(decryptRequest")
        assertTrue("Deferred decrypt must reject destroyed/finishing Activity", posted.contains("isDestroyed") && posted.contains("isFinishing"))
        assertTrue("Stale runnable must return without finishing another request", Regex("if \\(pendingDecryptRunId != runId\\) return@post").containsMatchIn(posted))
    }

    @Test fun initializationModeComesFromActualInitBoundaryRatherThanPhase() {
        val source = source()
        val factory = source.substringAfter("private fun newCryptoRequest(").substringBefore("private fun authenticate(")
        assertTrue("Factory must accept actual init mode", factory.contains("initializationMode: Int"))
        assertFalse("Mode must not be inferred from phase", factory.contains("if (phase =="))
        for (mode in listOf("encryptMode", "decryptMode", "writeMode")) {
            assertTrue("Mode must be passed to Cipher.init: $mode", source.contains("init($mode, kek"))
            assertTrue("Recorded mode must reach request: $mode", source.contains("initializationMode = $mode"))
        }
    }

    private fun cipher(): Cipher = Cipher.getInstance("AES/GCM/NoPadding")

    private fun request(runId: Long, phase: String, cipher: Cipher, mode: Int = if (phase == "ENCRYPT") Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE): Any {
        val type = Class.forName("com.personal.cardvault.PrototypeActivity\$CryptoAuthenticationRequest")
        val phaseType = Class.forName("com.personal.cardvault.PrototypeActivity\$CryptoPhase")
        val phaseValue = phaseType.enumConstants.single { (it as Enum<*>).name == phase }
        val constructor = type.declaredConstructors.single { it.parameterTypes.last() == Boolean::class.javaPrimitiveType }
        var longs = 0
        val args = constructor.parameterTypes.map {
            when (it) {
                java.lang.Long.TYPE -> if (longs++ == 0) runId else 1L
                phaseType -> phaseValue
                Cipher::class.java -> cipher
                Integer.TYPE -> mode
                java.lang.Boolean.TYPE -> false
                String::class.java -> "test"
                Any::class.java -> Any() // Legacy unused token; identity is the request itself.
                else -> error("Unexpected request constructor parameter $it")
            }
        }
        return constructor.apply { isAccessible = true }.newInstance(*args.toTypedArray())
    }

    private fun callback(request: Any, terminal: () -> String = { "deleted" }, success: (Cipher) -> Unit = {}): BiometricPrompt.AuthenticationCallback {
        val type = Class.forName("com.personal.cardvault.PrototypeActivity\$authenticate\$prompt\$1")
        val constructor = type.declaredConstructors.single().apply { isAccessible = true }
        val args = constructor.parameterTypes.map {
            when (it) {
                PrototypeActivity::class.java -> activity
                request.javaClass -> request
                kotlin.jvm.functions.Function0::class.java -> terminal
                kotlin.jvm.functions.Function1::class.java -> success
                else -> error("Unexpected callback parameter $it")
            }
        }
        return constructor.newInstance(*args.toTypedArray()) as BiometricPrompt.AuthenticationCallback
    }

    private fun result(cipher: Cipher?): BiometricPrompt.AuthenticationResult =
        BiometricPrompt.AuthenticationResult::class.java.declaredConstructors.single().apply {
            isAccessible = true
        }.newInstance(cipher?.let { BiometricPrompt.CryptoObject(it) }, 2) as BiometricPrompt.AuthenticationResult

    private fun mismatch(request: Any, cipher: Cipher?): Any? = PrototypeActivity::class.java
        .getDeclaredMethod("requestMismatch", request.javaClass, Cipher::class.java)
        .apply { isAccessible = true }.invoke(activity, request, cipher)

    private fun finish(runId: Long) = PrototypeActivity::class.java.getDeclaredMethod("finishCryptoRun", java.lang.Long.TYPE)
        .apply { isAccessible = true }.invoke(activity, runId)

    private fun activate(request: Any) {
        set("activeCryptoRequest", request)
        // Identity marker only: do not construct/show/cancel an AndroidX prompt on the JVM.
        val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as Unsafe
        set("activeBiometricPrompt", unsafe.allocateInstance(BiometricPrompt::class.java))
        set("authenticationBusy", true)
        set("authenticationDialogVisible", true)
    }

    private fun assertReleased() {
        assertNull(get("activeCryptoRequest"))
        assertNull(get("activeBiometricPrompt"))
        assertNull(get("pendingDecryptRunId"))
        assertEquals(false, get("authenticationBusy"))
        assertEquals(false, get("authenticationDialogVisible"))
    }

    private fun set(name: String, value: Any?) = PrototypeActivity::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.set(activity, value)

    private fun get(name: String): Any? = PrototypeActivity::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(activity)

    private fun ui(): String = (get("cryptoResult\$delegate") as androidx.compose.runtime.MutableState<*>).value as String

    // Bounded source-shape guards, not a Kotlin parser. Ignore comments so prose cannot satisfy
    // assertions; the four-space closing brace selects only this class member, not later helpers.
    private fun codeSource(): String = source().replace(Regex("/\\*[\\s\\S]*?\\*/|//[^\\n]*"), "")

    private fun methodSource(name: String): String {
        val code = codeSource()
        val declaration = Regex("(?m)^    (?:override|private) fun ${Regex.escape(name)}\\(")
            .find(code) ?: error("Missing method $name")
        val end = code.indexOf("\n    }", declaration.range.last)
        check(end >= 0) { "Missing closing brace for $name" }
        return code.substring(declaration.range.first, end + "\n    }".length)
    }

    private fun callNames(code: String): Set<String> = Regex("""\b(\w+)\s*\(""")
        .findAll(code).map { it.groupValues[1] }.toSet()

    private fun source(): String {
        val relative = "src/debug/java/com/personal/cardvault/PrototypeActivity.kt"
        return listOf(File(relative), File("app/$relative")).first { it.isFile }.readText()
    }
}
