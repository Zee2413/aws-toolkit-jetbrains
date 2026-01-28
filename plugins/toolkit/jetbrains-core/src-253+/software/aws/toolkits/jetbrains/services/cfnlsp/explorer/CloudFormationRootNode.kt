// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import software.aws.toolkit.jetbrains.core.credentials.ToolkitConnectionManager
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.SignInNode
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.StacksNode
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.ChangeSetsManager
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.StacksManager

class CloudFormationRootNode(private val nodeProject: Project) : AbstractTreeNode<Any>(nodeProject, Object()) {
    override fun update(presentation: PresentationData) {}

    override fun getChildren(): Collection<AbstractTreeNode<*>> {
        val connection = ToolkitConnectionManager.getInstance(nodeProject).activeConnection()
        if (connection == null) {
            return listOf(SignInNode(nodeProject))
        }

        val stacksManager = StacksManager.getInstance(nodeProject)
        val changeSetsManager = ChangeSetsManager.getInstance(nodeProject)

        return listOf(
            StacksNode(nodeProject, stacksManager, changeSetsManager)
        )
    }
}
