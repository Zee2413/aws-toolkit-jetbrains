// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListChangeSetsParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListChangeSetsResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListStacksParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListStacksResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.UpdateCredentialsParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.UpdateCredentialsResult
import java.util.concurrent.CompletableFuture

@Service(Service.Level.PROJECT)
class CfnClientService(private val project: Project) {
    
    private val lspServerProvider: LspServerProvider = defaultLspServerProvider(project)
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    
    fun restart() {
        LOG.info { "Restarting CloudFormation LSP server" }
        // TODO: Implement server restart logic
    }
    
    // Credential operations
    internal fun updateIamCredentials(params: UpdateCredentialsParams): CompletableFuture<UpdateCredentialsResult?> {
        val server = lspServerProvider.getServer()
        return if (server != null) {
            LOG.info { "Updating IAM credentials" }
            val future = CompletableFuture<UpdateCredentialsResult?>()
            coroutineScope.launch {
                try {
                    val result = server.sendRequest { lsp ->
                        (lsp as CfnLspServerProtocol).updateIamCredentials(params)
                    }
                    future.complete(result)
                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            }
            future
        } else {
            LOG.warn { "LSP server not available for request: Updating IAM credentials" }
            CompletableFuture.completedFuture(null)
        }
    }
    
    // Stack operations
    fun listStacks(params: ListStacksParams? = null): CompletableFuture<ListStacksResult?> {
        val server = lspServerProvider.getServer()
        return if (server != null) {
            LOG.info { "Requesting CloudFormation stacks" }
            val future = CompletableFuture<ListStacksResult?>()
            coroutineScope.launch {
                try {
                    val result = server.sendRequest { lsp ->
                        (lsp as CfnLspServerProtocol).listStacks(params ?: ListStacksParams())
                    }
                    future.complete(result)
                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            }
            future
        } else {
            LOG.warn { "LSP server not available for request: Requesting CloudFormation stacks" }
            CompletableFuture.completedFuture(null)
        }
    }
    
    fun listChangeSets(params: ListChangeSetsParams): CompletableFuture<ListChangeSetsResult?> {
        val server = lspServerProvider.getServer()
        return if (server != null) {
            LOG.info { "Requesting change sets for stack: ${params.stackName}" }
            val future = CompletableFuture<ListChangeSetsResult?>()
            coroutineScope.launch {
                try {
                    val result = server.sendRequest { lsp ->
                        (lsp as CfnLspServerProtocol).listChangeSets(params)
                    }
                    future.complete(result)
                } catch (e: Exception) {
                    future.completeExceptionally(e)
                }
            }
            future
        } else {
            LOG.warn { "LSP server not available for request: Requesting change sets for stack: ${params.stackName}" }
            CompletableFuture.completedFuture(null)
        }
    }
    
    // Configuration operations
    fun notifyConfigurationChanged() {
        val server = lspServerProvider.getServer()
        if (server != null) {
            LOG.info { "Sending configuration change notification" }
            server.sendNotification { lsp ->
                lsp.workspaceService.didChangeConfiguration(
                    org.eclipse.lsp4j.DidChangeConfigurationParams(emptyMap<String, Any>())
                )
            }
        } else {
            LOG.warn { "LSP server not available for notification: Sending configuration change notification" }
        }
    }
    
    companion object {
        private val LOG = getLogger<CfnClientService>()
        
        fun getInstance(project: Project): CfnClientService = project.service()
    }
}
