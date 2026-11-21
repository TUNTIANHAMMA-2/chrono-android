package com.personal.cardvault

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Source-contract checks only: JVM unit tests cannot exercise an Android DocumentsProvider.
 * Reads the current production file, never a copied implementation or old build output.
 * These bounded source-shape guards do not execute a provider, dialog, or Android lifecycle;
 * provider capability and exception behavior still require the pending M29 device regression.
 */
class BackupSafContractTest {
    private val source by lazy {
        listOf(
            File("src/main/java/com/personal/cardvault/MainActivity.kt"),
            File("app/src/main/java/com/personal/cardvault/MainActivity.kt"),
        ).first { it.isFile }.readText()
            // Remove full-line comments and KDoc so comments cannot satisfy a contract.
            .replace(Regex("(?m)^\\s*//[^\\n]*|(?s:/\\*\\*.*?\\*/)"), "")
    }

    @Test fun displayNameQueriesOpenableColumnAndUsesUriTailOnlyAsFallback() {
        val helper = function("backupDocumentDisplayName")
        assertTrue(helper.contains("query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)"))
        assertTrue(helper.contains("moveToFirst()"))
        assertTrue(helper.contains("getString(column)?.trim()?.takeIf { it.isNotEmpty() }"))
        assertTrue(helper.contains("return displayName ?: uri.lastPathSegment"))
        for (callback in listOf(
            source.substringAfter("private val openBackupDocument =")
                .substringBefore("private val createBackupDocument ="),
            source.substringAfter("private val createBackupDocument =")
                .substringBefore("private val requestNotificationPermission ="),
        )) {
            assertTrue("Each SAF callback must query its own returned URI's display name",
                callback.contains("contentResolver.backupDocumentDisplayName(uri)"))
            assertFalse("Callbacks must not substitute a document ID for metadata",
                callback.contains("lastPathSegment"))
        }
    }

    @Test fun pendingCleanupUsesDocumentsContractAndClassifiesAllFailures() {
        val helper = function("deletePendingBackupDocument")
        assertTrue(helper.contains("DocumentsContract.Document.COLUMN_FLAGS"))
        assertTrue(helper.contains("DocumentsContract.Document.FLAG_SUPPORTS_DELETE"))
        assertTrue(helper.contains("DocumentsContract.deleteDocument(this, uri)"))
        assertEquals("One SAF deletion path in MainActivity", 1,
            source.countOccurrences("DocumentsContract.deleteDocument("))
        assertFalse("No generic resolver deletion outside the helper either",
            Regex("""contentResolver\s*\.\s*delete\s*\(""").containsMatchIn(source))
        assertFalse("Do not use generic ContentResolver.delete for SAF cleanup",
            helper.contains(".delete(uri,"))
        for (outcome in listOf(
            "DeleteNotSupported", "DeleteDenied", "InvalidDocument", "DeleteFailed",
        )) assertTrue(helper.contains("BackupExportCleanupResult.$outcome"))
        for ((exception, outcome) in mapOf(
            "UnsupportedOperationException" to "DeleteNotSupported",
            "SecurityException" to "DeleteDenied",
            "IllegalArgumentException" to "InvalidDocument",
            "Exception" to "DeleteFailed",
        )) {
            // The helper has a separate query catch; inspect the deletion catch after the call.
            val deletion = helper.substringAfter("DocumentsContract.deleteDocument(this, uri)")
            assertEquals("$exception must remain a classified failure",
                "{\n        BackupExportCleanupResult.$outcome\n    }",
                blockAfter(deletion, "catch (_: $exception)"))
        }
        assertTrue("false from deleteDocument is a failure", helper.contains("else {\n            BackupExportCleanupResult.DeleteFailed"))
    }

    @Test fun completedExportLeavesPendingSetBeforeFormStateResetAndDoesNotCleanUp() {
        val export = function("exportBackup")
        val success = export.substringAfter("if (result.isSuccess) {").substringBefore("} else {")
        assertOrdered(success, "backupExportUri = null", "clearBackupExportState()")
        assertFalse("Successful export must not invoke pending cleanup", success.contains("discardBackupExportFile("))
    }

    @Test fun unknownDeleteCapabilityCannotFallThroughToDeleted() {
        val beforeDelete = function("deletePendingBackupDocument")
            .substringBefore("DocumentsContract.deleteDocument(this, uri)")
        // All null-producing query outcomes (exception, null cursor, no row, missing column)
        // must stop before deleteDocument. A later true cannot erase an earlier query failure.
        val rejectsUnknown = Regex("""if\s*\(\s*deleteCapability\s*==\s*null\s*\)\s*\{?\s*return\s+BackupExportCleanupResult\.(DeleteFailed|DeleteDenied|InvalidDocument|DeleteNotSupported)""")
            .containsMatchIn(beforeDelete) ||
            Regex("""\}\s*\?:\s*return\s+BackupExportCleanupResult\.(DeleteFailed|DeleteDenied|InvalidDocument|DeleteNotSupported)""")
                .containsMatchIn(beforeDelete)
        assertTrue("UI-003: unknown/query-failed delete capability must return failure before deleting",
            rejectsUnknown)
    }

    @Test fun discardDetachesOnlyExactPendingUriAndReleasesAllFormState() {
        val discard = function("discardBackupExportFile")
        assertOrdered(discard, "val uri = backupExportUri", "backupExportUri = null",
            "contentResolver.deletePendingBackupDocument(uri)", "clearBackupExportState()", "return result")
        assertEquals("Exactly one deletion of the captured URI", 1,
            discard.countOccurrences("deletePendingBackupDocument(uri)"))
        assertTrue(discard.contains("BackupExportCleanupResult.NoPendingDocument"))
        val clear = function("clearBackupExportState")
        for (assignment in listOf("backupExportUri = null", "backupExportFileName = null",
            "backupPassword = \"\"", "backupConfirmPassword = \"\"", "backupExporting = false")) {
            assertTrue("Discard must release $assignment even after provider failure", clear.contains(assignment))
        }
        assertTrue(source.contains("get() = this !is Deleted && this !is NoPendingDocument"))
    }

    @Test fun backBrokenButtonAndAuthenticationCancellationSurfaceCleanupFailure() {
        val screen = source.substringAfter("Destination.BackupExport -> BackupExportScreen(")
            .substringBefore("Destination.BackupRestore ->")
        val back = blockAfter(screen, "onBack =")
        assertOrdered(back, "discardBackupExportFile()", "showBackupExportCleanupFailure(cleanup)",
            "destinationState.value = Destination.Settings")
        val begin = function("beginBackupExport")
        for (cancel in listOf(
            blockAfter(begin, ".setNegativeButton(R.string.action_cancel)"),
            blockAfter(begin, "if (cards == null)"),
        )) {
            assertTrue("Cancellation must preserve and show a failed deletion result",
                cancel.contains("showBackupExportCleanupFailure(discardBackupExportFile(uri))"))
        }
        assertTrue(function("showBackupExportCleanupFailure").contains("if (result.isFailure)"))
        assertTrue(function("showBackupExportCleanupFailure").contains("无法自动删除"))
    }

    @Test fun exportFailureMustSurfaceCleanupFailureAsWellAsExportError() {
        val export = function("exportBackup")
        val failure = blockAfter(export, "} else")
        assertTrue("dev-plan 9.3: export failure must report when the new file could not be deleted",
            failure.contains("showBackupExportCleanupFailure(discardBackupExportFile(uri))") ||
                Regex("""val\s+(\w+)\s*=\s*discardBackupExportFile\(uri\)[\s\S]*showBackupExportCleanupFailure\(\1\)""")
                    .containsMatchIn(failure))
        assertTrue("The export error must also survive cleanup", failure.contains("backupExportError ="))
    }

    @Test fun brokenDialogSystemCancellationMustReleasePendingState() {
        val broken = blockAfter(function("beginBackupExport"), "if (vaultUiState == VaultUiState.BROKEN)")
        // Negative-button clicks do not cover BACK/outside-touch cancellation (9.3: any cancel).
        val cancel = if (broken.contains(".setOnCancelListener")) {
            blockAfter(broken, ".setOnCancelListener")
        } else ""
        assertTrue("UI-003: canceling the broken-vault dialog via the system must discard and release busy/password/URI",
            cancel.contains("showBackupExportCleanupFailure(discardBackupExportFile(uri))"))
    }

    @Test fun selectingAnotherLocationMustNotAbandonExistingPendingDocument() {
        val select = function("selectBackupExportLocation").substringBefore("createBackupDocument.launch(") +
            source.substringAfter("private val createBackupDocument =")
                .substringBefore("backupExportUri = uri")
        val refusesReplacement = Regex("""if\s*\([^)]*backupExportUri\s*!=\s*null[^)]*\)\s*\{?[\s\S]{0,200}?return""")
            .containsMatchIn(select)
        assertTrue("dev-plan 9.3: clean or retain the old pending target before another CreateDocument can replace it",
            select.contains("discardBackupExportFile()") || refusesReplacement)
    }

    @Test fun selectingLocationWhileExportingMustNotReplaceTheInFlightTarget() {
        val select = function("selectBackupExportLocation").substringBefore("createBackupDocument.launch(")
        assertTrue("UI-003: freeze target selection during asynchronous export; completion uses the shared pending URI",
            Regex("""if\s*\([^)]*backupExporting[^)]*\)\s*\{?[\s\S]{0,200}?return""").containsMatchIn(select))
    }

    @Test fun staleCleanupReturnsBeforeMutatingPendingTarget() {
        val discard = function("discardBackupExportFile")
        val rejection = "if (expectedUri != uri) return BackupExportCleanupResult.StalePendingDocument"
        assertTrue("Cleanup must accept the attempt's expected URI",
            discard.contains("expectedUri: Uri? = backupExportUri"))
        assertOrdered(discard, "val uri = backupExportUri", rejection, "backupExportUri = null",
            "contentResolver.deletePendingBackupDocument(uri)", "clearBackupExportState()")
        val beforeRejection = discard.substringBefore(rejection)
        assertFalse("A stale callback must not release another attempt's state",
            beforeRejection.contains("clearBackupExportState("))
        for (field in listOf("backupExportUri", "backupExportFileName", "backupPassword",
            "backupConfirmPassword", "backupExporting", "backupExportError")) {
            assertFalse("A stale callback must not assign $field before rejecting the URI",
                Regex("""\b${Regex.escape(field)}\s*=(?!=)""").containsMatchIn(beforeRejection))
        }
    }

    @Test fun successfulCompletionOnlyResetsMatchingPendingTarget() {
        val export = function("exportBackup")
        val success = blockAfter(export, "if (result.isSuccess)")
        val matching = blockAfter(success, "if (backupExportUri == uri)")
        assertOrdered(matching, "backupExportUri = null", "clearBackupExportState()")
        for (reset in listOf("backupExportUri = null", "clearBackupExportState()")) {
            assertEquals("All success resets must be inside the matching-URI guard: $reset", 1,
                success.countOccurrences(reset))
            assertFalse("No unconditional reset before the completion branch: $reset",
                export.substringBefore("if (result.isSuccess)").contains(reset))
        }
        for (field in listOf("backupPassword", "backupConfirmPassword", "backupExporting")) {
            assertFalse("Completion must not reset $field outside the matching-URI guard",
                Regex("""\b${Regex.escape(field)}\s*=(?!=)""")
                    .containsMatchIn(export.substringBefore("if (result.isSuccess)") + success))
        }
        val failure = blockAfter(export.substringAfter("if (result.isSuccess)"), "} else")
        assertTrue("Failure cleanup must capture the operation's URI",
            failure.contains("discardBackupExportFile(uri)"))
    }

    @Test fun busyBackDoesNotCleanUpOrNavigate() {
        val screen = source.substringAfter("Destination.BackupExport -> BackupExportScreen(")
            .substringBefore("Destination.BackupRestore ->")
        val back = blockAfter(screen, "onBack =")
        val busy = blockAfter(back, "if (backupExporting)")
        assertTrue("Busy back must return from this callback", busy.contains("return@BackupExportScreen"))
        for (action in listOf("discardBackupExportFile(", "clearBackupExportState(",
            "destinationState.value =")) {
            assertFalse("Busy back must not perform $action", busy.contains(action))
        }
        assertOrdered(back, "if (backupExporting)", "return@BackupExportScreen",
            "discardBackupExportFile()", "destinationState.value = Destination.Settings")
    }

    private fun assertOrdered(body: String, vararg parts: String) {
        var previous = -1
        for (part in parts) {
            val index = body.indexOf(part)
            assertTrue("Missing or out of order: $part", index >= 0 && index > previous)
            previous = index
        }
    }

    private fun blockAfter(body: String, marker: String): String {
        val markerIndex = body.indexOf(marker)
        check(markerIndex >= 0) { "Missing $marker" }
        val openBrace = body.indexOf('{', markerIndex + marker.length)
        check(openBrace >= 0) { "Missing block after $marker" }
        return bracedBlock(body, openBrace)
    }

    private fun function(name: String): String {
        val start = Regex("(?:private )?fun (?:ContentResolver\\.)?${Regex.escape(name)}\\(")
            .find(source)?.range?.first ?: error("Missing function $name")
        val openBrace = source.indexOf('{', start)
        return source.substring(start, openBrace) + bracedBlock(source, openBrace)
    }

    private fun bracedBlock(body: String, openBrace: Int): String {
        var depth = 0
        for (index in openBrace until body.length) {
            when (body[index]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return body.substring(openBrace, index + 1)
            }
        }
        error("Unclosed block at $openBrace")
    }

    private fun String.countOccurrences(part: String): Int = split(part).size - 1
}
