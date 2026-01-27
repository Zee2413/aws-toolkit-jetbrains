// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes

import com.intellij.icons.AllIcons
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes
import software.aws.toolkit.jetbrains.core.credentials.AwsConnectionManager
import software.aws.toolkits.resources.AwsToolkitBundle.message

class RegionSelectorNode(nodeProject: Project) : AbstractTreeNode<String>(nodeProject, "region") {
    override fun update(presentation: PresentationData) {
        val region = AwsConnectionManager.getInstance(project).selectedRegion?.id ?: "us-east-1"
        presentation.addText(message("cloudformation.explorer.region", region), SimpleTextAttributes.REGULAR_ATTRIBUTES)
        presentation.setIcon(AllIcons.General.GearPlain)
    }

    override fun getChildren(): Collection<AbstractTreeNode<*>> = emptyList()
    override fun isAlwaysLeaf(): Boolean = true
}
