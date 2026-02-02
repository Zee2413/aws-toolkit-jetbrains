// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.info
import software.aws.toolkit.core.utils.warn
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListStacksParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackSummary

typealias StacksChangeListener = (List<StackSummary>) -> Unit

@Service(Service.Level.PROJECT)
internal class StacksManager(private val project: Project) : Disposable {
    internal var clientServiceProvider: () -> CfnClientService = { CfnClientService.getInstance(project) }
    
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
        LOG.info { "Loading stacks (loadMore=$loadMore)" }

        val params = ListStacksParams(
            statusToExclude = listOf("DELETE_COMPLETE"),
            loadMore = loadMore
        )

        clientServiceProvider().listStacks(params)
            .thenAccept { result ->
                if (result != null) {
                    LOG.info { "Loaded ${result.stacks.size} stacks" }
                    stacks = result.stacks
                    nextToken = result.nextToken
                    loaded = true
                } else {
                    LOG.warn { "Received null result from listStacks" }
                    if (!loadMore) {
                        stacks = emptyList()
                        nextToken = null
                        loaded = true
                    }
                }
                notifyListeners()
            }
            .exceptionally { error ->
                LOG.warn(error) { "Failed to load stacks" }
                if (!loadMore) {
                    stacks = emptyList()
                    nextToken = null
                    loaded = true
                }
                notifyListeners()
                null
            }
    }

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
