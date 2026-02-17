// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VfsUtil
import software.aws.toolkit.core.utils.getLogger
import software.aws.toolkit.core.utils.warn
import software.aws.toolkits.jetbrains.core.explorer.ExplorerTreeToolWindowDataKeys
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.explorer.nodes.StackNode
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.CreateValidationParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DescribeStackParams
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.Parameter
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.Tag
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.TemplateParameter
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CFN_SUPPORTED_EXTENSIONS
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CfnLspServerDescriptor
import software.aws.toolkits.jetbrains.services.cfnlsp.ui.ValidateAndDeployDialog
import java.io.File
import java.util.UUID

internal class ValidateAndDeployAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val clientService = CfnClientService.getInstance(project)

        val selectedNode = e.getData(ExplorerTreeToolWindowDataKeys.SELECTED_NODES)?.firstOrNull()
        val templateFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: FileEditorManager.getInstance(project).selectedEditor?.file?.takeIf {
                it.extension?.lowercase() in CFN_SUPPORTED_EXTENSIONS
            }

        val prefilledTemplate = templateFile?.path
        val prefilledStackName = (selectedNode as? StackNode)?.stack?.stackName

        // Fetch template info if we have a template
        var templateParams: List<TemplateParameter> = emptyList()
        var detectedCaps: List<String> = emptyList()
        var hasArtifacts = false
        var existingParams: List<Parameter>? = null
        var existingTags: List<Tag>? = null
        var templateResources: List<software.aws.toolkits.jetbrains.services.cfnlsp.protocol.TemplateResource> = emptyList()
        var isExistingStack = false

        if (templateFile != null) {
            val descriptor = CfnLspServerDescriptor.getInstance(project)
            val uri = descriptor.getFileUri(templateFile)
            clientService.ensureDocumentOpen(templateFile, project)

            try {
                templateParams = clientService.getParameters(uri).get()?.parameters ?: emptyList()
            } catch (ex: Exception) {
                LOG.warn(ex) { "Failed to fetch template parameters" }
            }
            try {
                detectedCaps = clientService.getCapabilities(uri).get()?.capabilities ?: emptyList()
            } catch (ex: Exception) {
                LOG.warn(ex) { "Failed to fetch capabilities" }
            }
            try {
                val artifactsResult = clientService.getTemplateArtifacts(uri).get()
                val artifacts = artifactsResult?.artifacts ?: emptyList()
                hasArtifacts = artifacts.isNotEmpty()
                val templateDir = templateFile.parent?.path ?: ""
                for (artifact in artifacts) {
                    val artifactPath = if (artifact.filePath.startsWith("/")) {
                        artifact.filePath
                    } else {
                        "$templateDir/${artifact.filePath}"
                    }
                    if (!File(artifactPath).exists()) {
                        software.aws.toolkit.jetbrains.utils.notifyError(
                            "CloudFormation",
                            "Artifact path does not exist: ${artifact.filePath}",
                            project = project
                        )
                        return
                    }
                }
            } catch (ex: Exception) {
                LOG.warn(ex) { "Failed to check artifacts" }
            }
            try {
                templateResources = clientService.getTemplateResources(uri).get()?.resources ?: emptyList()
            } catch (ex: Exception) {
                LOG.warn(ex) { "Failed to fetch template resources" }
            }
        }

        if (prefilledStackName != null) {
            try {
                val stackResult = clientService.describeStack(DescribeStackParams(prefilledStackName)).get()
                existingParams = stackResult?.stack?.parameters
                existingTags = stackResult?.stack?.tags
                isExistingStack = stackResult?.stack != null
            } catch (ex: Exception) {
                LOG.warn(ex) { "Failed to fetch stack details" }
            }
        }

        runInEdt {
            val dialog = ValidateAndDeployDialog(
                project = project,
                prefilledTemplatePath = prefilledTemplate,
                prefilledStackName = prefilledStackName,
                templateParameters = templateParams,
                detectedCapabilities = detectedCaps,
                existingParameters = existingParams,
                existingTags = existingTags,
                hasArtifacts = hasArtifacts,
                templateResources = templateResources,
                isExistingStack = isExistingStack,
            )

            if (!dialog.showAndGet()) return@runInEdt

            val settings = dialog.getSettings()
            val templateVFile = VfsUtil.findFileByIoFile(File(settings.templatePath), true) ?: return@runInEdt

            val desc = CfnLspServerDescriptor.getInstance(project)
            clientService.ensureDocumentOpen(templateVFile, project)

            val params = CreateValidationParams(
                id = UUID.randomUUID().toString(),
                uri = desc.getFileUri(templateVFile),
                stackName = settings.stackName,
                parameters = settings.parameters.ifEmpty { null },
                capabilities = settings.capabilities.ifEmpty { null },
                tags = settings.tags.ifEmpty { null },
                resourcesToImport = settings.resourcesToImport,
                keepChangeSet = true,
                onStackFailure = settings.onStackFailure,
                includeNestedStacks = settings.includeNestedStacks,
                importExistingResources = settings.importExistingResources,
                deploymentMode = settings.deploymentMode,
                s3Bucket = settings.s3Bucket,
                s3Key = settings.s3Key,
            )

            ValidationWorkflow(project).validate(params)
        }
    }

    companion object {
        private val LOG = getLogger<ValidateAndDeployAction>()
    }
}
