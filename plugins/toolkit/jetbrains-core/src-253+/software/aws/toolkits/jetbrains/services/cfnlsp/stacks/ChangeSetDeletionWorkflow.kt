// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.openapi.project.Project
import software.aws.toolkit.jetbrains.utils.notifyError
import software.aws.toolkit.jetbrains.utils.notifyInfo
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DeleteChangeSetParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.Identifiable
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackActionPhase
import software.aws.toolkits.resources.AwsToolkitBundle.message
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

internal class ChangeSetDeletionWorkflow(
    private val project: Project,
    private val clientService: CfnClientService = CfnClientService.getInstance(project),
) {
    fun delete(stackName: String, changeSetName: String): CompletableFuture<Boolean> {
        val id = UUID.randomUUID().toString()
        val params = DeleteChangeSetParams(id, changeSetName, stackName)

        return clientService.deleteChangeSet(params).thenCompose { result ->
            if (result == null) {
                notifyError(message("cloudformation.changeset.deletion.title"), message("cloudformation.changeset.deletion.failed", changeSetName, stackName, "Failed to start deletion"), project = project)
                CompletableFuture.completedFuture(false)
            } else {
                notifyInfo(message("cloudformation.changeset.deletion.title"), message("cloudformation.changeset.deletion.started", changeSetName, stackName), project = project)
                pollForCompletion(id, stackName, changeSetName)
            }
        }
    }

    private fun pollForCompletion(id: String, stackName: String, changeSetName: String): CompletableFuture<Boolean> {
        val future = CompletableFuture<Boolean>()
        val scheduler = Executors.newSingleThreadScheduledExecutor()

        scheduler.scheduleWithFixedDelay(
            {
                clientService.getChangeSetDeletionStatus(Identifiable(id))
                    .thenAccept { status ->
                        if (status == null) {
                            future.complete(false)
                            scheduler.shutdown()
                            return@thenAccept
                        }
                        when (status.phase) {
                            StackActionPhase.DELETION_COMPLETE -> {
                                notifyInfo(message("cloudformation.changeset.deletion.title"), message("cloudformation.changeset.deletion.success", changeSetName, stackName), project = project)
                                ChangeSetsManager.getInstance(project).refreshChangeSets(stackName)
                                future.complete(true)
                                scheduler.shutdown()
                            }
                            StackActionPhase.DELETION_FAILED -> {
                                clientService.describeChangeSetDeletionStatus(Identifiable(id)).thenAccept { details ->
                                    notifyError(message("cloudformation.changeset.deletion.title"), message("cloudformation.changeset.deletion.failed", changeSetName, stackName, details?.failureReason ?: "Unknown"), project = project)
                                    future.complete(false)
                                }
                                scheduler.shutdown()
                            }
                            else -> {}
                        }
                    }
                    .exceptionally { error ->
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
