// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.warn
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnLspServer
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ChangeSetInfo
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListChangeSetsParams
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CfnLspServerSupportProvider

@Service(Service.Level.PROJECT)
internal class ChangeSetsManager(private val project: Project) {
    private val stackChangeSets = mutableMapOf<String, StackChangeSets>()

    private data class StackChangeSets(
        val changeSets: List<ChangeSetInfo>,
        val nextToken: String? = null,
    )

    fun getChangeSets(stackName: String): List<ChangeSetInfo> {
        val server = findLspServer() ?: return emptyList()

        server.sendNotification { lsp ->
            val cfnServer = lsp as? CfnLspServer ?: return@sendNotification
            cfnServer.listChangeSets(ListChangeSetsParams(stackName))
                .whenComplete { result, error ->
                    if (error != null) {
                        LOG.warn(error) { "Failed to load change sets for $stackName" }
                    } else if (result != null) {
                        stackChangeSets[stackName] = StackChangeSets(result.changeSets, result.nextToken)
                    }
                }
        }

        return stackChangeSets[stackName]?.changeSets ?: emptyList()
    }

    fun get(stackName: String): List<ChangeSetInfo> =
        stackChangeSets[stackName]?.changeSets ?: emptyList()

    fun hasMore(stackName: String): Boolean =
        stackChangeSets[stackName]?.nextToken != null

    @Suppress("UnstableApiUsage")
    private fun findLspServer(): LspServer? =
        LspServerManager.getInstance(project)
            .getServersForProvider(CfnLspServerSupportProvider::class.java)
            .firstOrNull()

    companion object {
        private val LOG = getLogger<ChangeSetsManager>()
        fun getInstance(project: Project): ChangeSetsManager = project.service()
    }
}
