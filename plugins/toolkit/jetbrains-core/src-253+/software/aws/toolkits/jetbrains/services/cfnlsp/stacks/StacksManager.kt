// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnLspServer
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListStacksParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackSummary
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CfnLspServerSupportProvider

typealias StacksChangeListener = (List<StackSummary>) -> Unit

@Service(Service.Level.PROJECT)
internal class StacksManager(private val project: Project) : Disposable {
    private var stacks: List<StackSummary> = emptyList()
    private var nextToken: String? = null
    private var loaded = false
    private val listeners = mutableListOf<StacksChangeListener>()

    fun addListener(listener: StacksChangeListener) {
        listeners.add(listener)
    }

    fun get(): List<StackSummary> = stacks.toList()

    fun hasMore(): Boolean = nextToken != null

    fun isLoaded(): Boolean = loaded

    fun reload() {
        loadStacks(loadMore = false)
    }

    fun clear() {
        stacks = emptyList()
        nextToken = null
        loaded = false
        notifyListeners()
    }

    fun loadMoreStacks() {
        if (nextToken == null) return
        loadStacks(loadMore = true)
    }

    private fun loadStacks(loadMore: Boolean) {
        val server = findLspServer()
        if (server == null) {
            LOG.warn { "No LSP server found for loading stacks" }
            return
        }

        LOG.info { "Loading stacks (loadMore=$loadMore)" }

        server.sendNotification { lsp ->
            val cfnServer = lsp as? CfnLspServer
            if (cfnServer == null) {
                LOG.warn { "LSP server is not CfnLspServer: ${lsp::class.java}" }
                return@sendNotification
            }

            LOG.info { "Sending listStacks request" }
            cfnServer.listStacks(
                ListStacksParams(
                    statusToExclude = listOf("DELETE_COMPLETE"),
                    loadMore = loadMore
                )
            ).whenComplete { result, error ->
                if (error != null) {
                    LOG.warn(error) { "Failed to load stacks" }
                    if (!loadMore) {
                        stacks = emptyList()
                        nextToken = null
                        loaded = true // Mark as loaded to prevent retry loop
                    }
                } else if (result != null) {
                    LOG.info { "Loaded ${result.stacks.size} stacks" }
                    stacks = result.stacks
                    nextToken = result.nextToken
                    loaded = true
                }
                notifyListeners()
            }
        }
    }

    @Suppress("UnstableApiUsage")
    private fun findLspServer(): LspServer? =
        LspServerManager.getInstance(project)
            .getServersForProvider(CfnLspServerSupportProvider::class.java)
            .firstOrNull()

    private fun notifyListeners() {
        listeners.forEach { it(stacks) }
    }

    override fun dispose() {
        listeners.clear()
    }

    companion object {
        private val LOG = getLogger<StacksManager>()
        fun getInstance(project: Project): StacksManager = project.service()
    }
}
