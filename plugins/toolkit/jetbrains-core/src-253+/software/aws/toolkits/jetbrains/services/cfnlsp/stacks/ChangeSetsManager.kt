// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnLspServerProtocol
import software.aws.toolkits.jetbrains.services.cfnlsp.LspServerProvider
import software.aws.toolkits.jetbrains.services.cfnlsp.defaultLspServerProvider
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ChangeSetInfo
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListChangeSetsParams

@Service(Service.Level.PROJECT)
internal class ChangeSetsManager(private val project: Project) {
    internal var lspServerProvider: LspServerProvider = defaultLspServerProvider(project)
    
    private val stackChangeSets = mutableMapOf<String, StackChangeSets>()
    private val loadedStacks = mutableSetOf<String>()
    private val listeners = mutableListOf<() -> Unit>()

    private data class StackChangeSets(
        val changeSets: List<ChangeSetInfo>,
        val nextToken: String? = null,
    )

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun isLoaded(stackName: String): Boolean = loadedStacks.contains(stackName)

    fun fetchChangeSets(stackName: String) {
        if (loadedStacks.contains(stackName)) return
        
        val server = lspServerProvider.getServer() ?: return

        LOG.info { "Fetching change sets for $stackName" }

        server.sendNotification { lsp ->
            val cfnServer = lsp as? CfnLspServerProtocol ?: return@sendNotification
            cfnServer.listChangeSets(ListChangeSetsParams(stackName))
                .whenComplete { result, error ->
                    if (error != null) {
                        LOG.warn(error) { "Failed to load change sets for $stackName" }
                        loadedStacks.add(stackName) // Mark as loaded to prevent retry loop
                    } else if (result != null) {
                        LOG.info { "Loaded ${result.changeSets.size} change sets for $stackName" }
                        stackChangeSets[stackName] = StackChangeSets(result.changeSets, result.nextToken)
                        loadedStacks.add(stackName)
                    }
                    notifyListeners()
                }
        }
    }

    fun loadMoreChangeSets(stackName: String) {
        val current = stackChangeSets[stackName] ?: return
        val nextToken = current.nextToken ?: return

        val server = lspServerProvider.getServer() ?: return

        LOG.info { "Loading more change sets for $stackName" }

        server.sendNotification { lsp ->
            val cfnServer = lsp as? CfnLspServerProtocol ?: return@sendNotification
            cfnServer.listChangeSets(ListChangeSetsParams(stackName, nextToken))
                .whenComplete { result, error ->
                    if (error != null) {
                        LOG.warn(error) { "Failed to load more change sets for $stackName" }
                    } else if (result != null) {
                        LOG.info { "Loaded ${result.changeSets.size} more change sets for $stackName" }
                        stackChangeSets[stackName] = StackChangeSets(
                            current.changeSets + result.changeSets,
                            result.nextToken
                        )
                        notifyListeners()
                    }
                }
        }
    }

    fun get(stackName: String): List<ChangeSetInfo> =
        stackChangeSets[stackName]?.changeSets ?: emptyList()

    fun hasMore(stackName: String): Boolean =
        stackChangeSets[stackName]?.nextToken != null

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    companion object {
        private val LOG = getLogger<ChangeSetsManager>()
        fun getInstance(project: Project): ChangeSetsManager = project.service()
    }
}
