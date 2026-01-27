// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import software.aws.toolkits.resources.AwsToolkitBundle.message

class SignInNode(nodeProject: Project) : AbstractTreeNode<String>(nodeProject, "sign-in") {
    override fun update(presentation: PresentationData) {
        presentation.addText(message("cloudformation.explorer.sign_in"), SimpleTextAttributes.REGULAR_ATTRIBUTES)
        presentation.setIcon(AllIcons.General.User)
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()
}
