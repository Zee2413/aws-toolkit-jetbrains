// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.explorer.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.ChangeSetsManager
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.StacksManager
import software.aws.toolkits.resources.AwsToolkitBundle.message

class RefreshStacksAction : AnAction(
    message("cloudformation.explorer.stacks.refresh"),
    null,
    AllIcons.Actions.Refresh,
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val stacksManager = StacksManager.getInstance(project)
        val changeSetsManager = ChangeSetsManager.getInstance(project)
        
        stacksManager.reload()
        
        val stacks = stacksManager.get()
        stacks.forEach { stack ->
            val stackName = stack.stackName ?: return@forEach
            if (changeSetsManager.isLoaded(stackName)) {
                changeSetsManager.refreshChangeSets(stackName)
            }
        }
    }
}
