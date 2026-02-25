// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.runInEdtAndWait
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.services.LanguageServer
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CfnLspServerDescriptor
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private const val LSP_STARTUP_TIMEOUT_MS = 120_000L
private const val LSP_REQUEST_TIMEOUT_S = 30L

/**
 * Manages CloudFormation LSP server lifecycle and document operations for integration tests.
 */
internal class CfnLspTestFixture(private val fixture: CodeInsightTestFixture) {

    fun tearDown() = fixture.tearDown()

    fun openTemplate(name: String, content: String): VirtualFile {
        var file: VirtualFile? = null
        runWriteActionAndWait { file = fixture.tempDirFixture.createFile(name, content) }
        val vf = file!!
        runInEdtAndWait { fixture.openFileInEditor(vf) }
        ensureRunning()
        request { lsp ->
            lsp.textDocumentService.didOpen(
                DidOpenTextDocumentParams(TextDocumentItem(fileUri(vf), "yaml", 1, content))
            )
            CompletableFuture.completedFuture(Unit)
        }
        return vf
    }

    fun fileUri(file: VirtualFile): String = file.toNioPath().toUri().toString()

    fun <T> request(block: (LanguageServer) -> CompletableFuture<T>): T {
        val future = CompletableFuture<T>()
        runningServer().sendNotification { lsp ->
            block(lsp).whenComplete { result, error ->
                if (error != null) future.completeExceptionally(error)
                else future.complete(result)
            }
        }
        return future.get(LSP_REQUEST_TIMEOUT_S, TimeUnit.SECONDS)
    }

    private fun runningServer() =
        LspServerManager.getInstance(fixture.project)
            .getServersForProvider(CfnLspServerDescriptor.providerClass())
            .first { it.state == LspServerState.Running }

    private fun ensureRunning() {
        val providerClass = CfnLspServerDescriptor.providerClass()
        LspServerManager.getInstance(fixture.project)
            .ensureServerStarted(providerClass, CfnLspServerDescriptor.getInstance(fixture.project))

        val deadline = System.currentTimeMillis() + LSP_STARTUP_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val servers = LspServerManager.getInstance(fixture.project).getServersForProvider(providerClass)
            if (servers.any { it.state == LspServerState.Running }) return
            Thread.sleep(1_000)
        }
        throw AssertionError("CloudFormation LSP server did not reach Running state within ${LSP_STARTUP_TIMEOUT_MS / 1000}s")
    }
}
