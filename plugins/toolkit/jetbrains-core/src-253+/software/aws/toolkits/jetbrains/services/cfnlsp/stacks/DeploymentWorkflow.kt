// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.openapi.project.Project
import software.aws.toolkit.jetbrains.utils.notifyError
import software.aws.toolkit.jetbrains.utils.notifyInfo
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.CreateDeploymentParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.Identifiable
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackActionPhase
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackActionState
import software.aws.toolkits.resources.AwsToolkitBundle.message
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class DeploymentWorkflow(
    private val project: Project,
    private val clientService: CfnClientService = CfnClientService.getInstance(project),
) {
    fun deploy(stackName: String, changeSetName: String): CompletableFuture<Boolean> {
        val id = UUID.randomUUID().toString()
        val params = CreateDeploymentParams(id, changeSetName, stackName)

        return clientService.createDeployment(params).thenCompose { result ->
            if (result == null) {
                notifyError(message("cloudformation.deployment.title"), message("cloudformation.deployment.failed", stackName, "Failed to start deployment"), project = project)
                CompletableFuture.completedFuture(false)
            } else {
                notifyInfo(message("cloudformation.deployment.title"), message("cloudformation.deployment.started", stackName), project = project)
                pollForCompletion(id, stackName)
            }
        }
    }

    private fun pollForCompletion(id: String, stackName: String): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        val scheduler = Executors.newSingleThreadScheduledExecutor()

        scheduler.scheduleWithFixedDelay(
            {
                clientService.getDeploymentStatus(Identifiable(id))
                    .thenAccept { status ->
                        if (status == null) {
                            notifyError(message("cloudformation.deployment.title"), message("cloudformation.deployment.failed", stackName, "Failed to get status"), project = project)
                            future.complete(false)
                            scheduler.shutdown()
                            return@thenAccept
                        }
                        when (status.phase) {
                            StackActionPhase.DEPLOYMENT_COMPLETE -> {
                                if (status.state == StackActionState.SUCCESSFUL) {
                                    notifyInfo(message("cloudformation.deployment.title"), message("cloudformation.deployment.success", stackName), project = project)
                                    future.complete(true)
                                } else {
                                    clientService.describeDeploymentStatus(Identifiable(id)).thenAccept { details ->
                                        notifyError(message("cloudformation.deployment.title"), message("cloudformation.deployment.failed", stackName, details?.failureReason ?: "Unknown"), project = project)
                                        future.complete(false)
                                    }
                                }
                                StacksManager.getInstance(project).reload()
                                scheduler.shutdown()
                            }
                            StackActionPhase.DEPLOYMENT_FAILED, StackActionPhase.VALIDATION_FAILED -> {
                                clientService.describeDeploymentStatus(Identifiable(id)).thenAccept { details ->
                                    notifyError(message("cloudformation.deployment.title"), message("cloudformation.deployment.failed", stackName, details?.failureReason ?: "Unknown"), project = project)
                                    future.complete(false)
                                }
                                StacksManager.getInstance(project).reload()
                                scheduler.shutdown()
                            }
                            else -> {}
                        }
                    }
                    .exceptionally { error ->
                        notifyError(message("cloudformation.deployment.title"), message("cloudformation.deployment.failed", stackName, error.message ?: "Unknown"), project = project)
                        future.complete(false)
                        scheduler.shutdown()
                        null
                    }
            },
            POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS
        )
        return future
    }

    companion object {
        private const val POLL_INTERVAL_MS = 1000L
    }
}
