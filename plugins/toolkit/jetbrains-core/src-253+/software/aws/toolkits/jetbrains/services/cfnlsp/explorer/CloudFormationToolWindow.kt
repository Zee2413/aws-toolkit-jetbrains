// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.util.ui.tree.TreeUtil
import software.aws.toolkit.jetbrains.ToolkitPlaces
import software.aws.toolkits.jetbrains.core.explorer.AbstractExplorerTreeToolWindow
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CfnLspServerDescriptor
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CfnLspServerSupportProvider
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.StacksManager

@Service(Service.Level.PROJECT)
internal class CloudFormationToolWindow(private val project: Project) : AbstractExplorerTreeToolWindow(
    CloudFormationTreeStructure(project)
) {
    override val actionPlace = ToolkitPlaces.CFN_TOOL_WINDOW

    init {
        setupToolbar()
        StacksManager.getInstance(project).addListener {
            runInEdt {
                redrawContent()
                TreeUtil.expandAll(getTree())
            }
        }
        ensureLspServerStarted()
    }

    private fun setupToolbar() {
        val actionManager = ActionManager.getInstance()
        val toolbarGroup = DefaultActionGroup().apply {
            add(actionManager.getAction("aws.toolkit.cloudformation.stacks.refresh"))
        }
        val toolbar = actionManager.createActionToolbar(ActionPlaces.TOOLBAR, toolbarGroup, true)
        toolbar.targetComponent = this
        setToolbar(toolbar.component)
    }

    @Suppress("UnstableApiUsage")
    private fun ensureLspServerStarted() {
        LspServerManager.getInstance(project).ensureServerStarted(
            CfnLspServerSupportProvider::class.java,
            CfnLspServerDescriptor.getInstance(project)
        )
    }

    companion object {
        fun getInstance(project: Project): CloudFormationToolWindow = project.service()
    }
}
