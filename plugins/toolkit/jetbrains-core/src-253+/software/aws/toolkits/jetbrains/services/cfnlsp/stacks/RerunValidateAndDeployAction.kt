// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import software.aws.toolkit.jetbrains.utils.notifyError
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.CreateValidationParams
import java.util.UUID

internal class RerunValidateAndDeployAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val (project, lastParams) = ValidationWorkflow.getLastValidation() ?: run {
            val p = e.project ?: return
            notifyError("CloudFormation", "No previous validation to rerun", project = p)
            return
        }

        val params = lastParams.copy(id = UUID.randomUUID().toString())
        ValidationWorkflow(project).validate(params)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = ValidationWorkflow.getLastValidation() != null
    }
}
