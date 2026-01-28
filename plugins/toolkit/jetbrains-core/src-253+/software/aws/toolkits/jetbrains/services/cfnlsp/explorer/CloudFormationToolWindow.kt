// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServerManager
import software.aws.toolkit.jetbrains.ToolkitPlaces
import software.aws.toolkits.jetbrains.core.explorer.AbstractExplorerTreeToolWindow
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CfnLspServerDescriptor
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CfnLspServerSupportProvider
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.StacksManager
import javax.swing.JComponent

@Service(Service.Level.PROJECT)
internal class CloudFormationToolWindow(private val project: Project) : AbstractExplorerTreeToolWindow(
    CloudFormationTreeStructure(project),
    initialTreeExpandDepth = 1
) {
    override val actionPlace = ToolkitPlaces.CFN_TOOL_WINDOW

    init {
        setupToolbar()
        StacksManager.getInstance(project).addListener {
            runInEdt {
                redrawContent()
            }
        }
        ensureLspServerStarted()
    }

    private fun setupToolbar() {
        val toolbarGroup = DefaultActionGroup().apply {
            add(RegionComboBoxAction(project))
        }
        val toolbar = com.intellij.openapi.actionSystem.ActionManager.getInstance()
            .createActionToolbar(ActionPlaces.TOOLBAR, toolbarGroup, true)
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

private class RegionComboBoxAction(private val project: Project) : ComboBoxAction(), DumbAware {
    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT

    override fun createPopupActionGroup(button: JComponent, context: com.intellij.openapi.actionSystem.DataContext): DefaultActionGroup {
        val regionManager = CloudFormationRegionManager.getInstance()
        val currentRegion = regionManager.getSelectedRegion()
        val regionProvider = software.aws.toolkit.jetbrains.core.region.AwsRegionProvider.getInstance()

        return DefaultActionGroup().apply {
            regionProvider.regions(regionProvider.defaultPartition().id).values
                .groupBy { it.category }
                .forEach { (category, categoryRegions) ->
                    addSeparator(category)
                    categoryRegions.sortedBy { it.displayName }.forEach { region ->
                        add(object : com.intellij.openapi.actionSystem.AnAction(region.displayName) {
                            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                                if (region.id != currentRegion.id) {
                                    regionManager.setSelectedRegion(region)
                                    val stacksManager = StacksManager.getInstance(project)
                                    stacksManager.clear()
                                    software.aws.toolkits.jetbrains.services.cfnlsp.CfnCredentialsService.getInstance(project).sendCredentials()
                                    // Reload stacks with new region
                                    stacksManager.reload()
                                }
                            }

                            override fun update(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                                e.presentation.isEnabled = region.id != currentRegion.id
                            }

                            override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
                        })
                    }
                }
        }
    }

    override fun update(e: com.intellij.openapi.actionSystem.AnActionEvent) {
        val region = CloudFormationRegionManager.getInstance().getSelectedRegion()
        e.presentation.text = region.id
    }
}
