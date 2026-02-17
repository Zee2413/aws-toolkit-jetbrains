// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.DeploymentMode
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.Parameter
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceToImport
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.Tag
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.TemplateParameter
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.TemplateResource
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CFN_SUPPORTED_EXTENSIONS
import software.aws.toolkits.resources.AwsToolkitBundle.message
import java.io.File
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

internal data class ValidateAndDeploySettings(
    val templatePath: String,
    val stackName: String,
    val s3Bucket: String?,
    val s3Key: String?,
    val parameters: List<Parameter>,
    val capabilities: List<String>,
    val tags: List<Tag>,
    val onStackFailure: String?,
    val includeNestedStacks: Boolean,
    val importExistingResources: Boolean,
    val deploymentMode: DeploymentMode?,
    val resourcesToImport: List<ResourceToImport>?,
)

internal class ValidateAndDeployDialog(
    private val project: Project,
    private val prefilledTemplatePath: String? = null,
    private val prefilledStackName: String? = null,
    private val templateParameters: List<TemplateParameter> = emptyList(),
    private val detectedCapabilities: List<String> = emptyList(),
    private val existingParameters: List<Parameter>? = null,
    private val existingTags: List<Tag>? = null,
    private val hasArtifacts: Boolean = false,
    private val templateResources: List<TemplateResource> = emptyList(),
    private val isExistingStack: Boolean = false,
) : DialogWrapper(project) {

    private val persistence = ValidateAndDeployPersistence.getInstance(project)
    private val savedState = persistence.state

    private val descriptor = FileChooserDescriptor(true, false, false, false, false, false).withFileFilter {
        it.extension?.lowercase() in CFN_SUPPORTED_EXTENSIONS
    }

    private val templateField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(project, descriptor)
        text = prefilledTemplatePath ?: savedState.lastTemplatePath ?: ""
    }

    private val stackNameField = JBTextField().apply {
        text = prefilledStackName ?: savedState.lastStackName ?: ""
        emptyText.text = message("cloudformation.deploy.dialog.stack_name.placeholder")
    }

    private val s3BucketField = JBTextField().apply {
        text = savedState.s3Bucket ?: ""
        emptyText.text = "S3 bucket name (optional)"
    }

    private val s3KeyField = JBTextField().apply {
        val defaultKey = if (prefilledTemplatePath != null) {
            val fileName = File(prefilledTemplatePath).nameWithoutExtension
            val ext = File(prefilledTemplatePath).extension
            "$fileName-${System.currentTimeMillis()}.$ext"
        } else {
            null
        }
        text = savedState.s3Key ?: ""
        emptyText.text = defaultKey ?: "S3 object key (optional)"
    }

    private val parameterFields = templateParameters.map { param ->
        val prefill = existingParameters?.find { it.parameterKey == param.name }?.parameterValue
            ?: param.default?.toString() ?: ""
        param to JBTextField().apply {
            text = prefill
            emptyText.text = param.description ?: param.type ?: "String"
        }
    }

    private val capabilityIam = JBCheckBox("CAPABILITY_IAM").apply {
        isSelected = "CAPABILITY_IAM" in detectedCapabilities || savedState.capabilities?.contains("CAPABILITY_IAM") == true
    }
    private val capabilityNamedIam = JBCheckBox("CAPABILITY_NAMED_IAM").apply {
        isSelected = "CAPABILITY_NAMED_IAM" in detectedCapabilities || savedState.capabilities?.contains("CAPABILITY_NAMED_IAM") == true
    }
    private val capabilityAutoExpand = JBCheckBox("CAPABILITY_AUTO_EXPAND").apply {
        isSelected = "CAPABILITY_AUTO_EXPAND" in detectedCapabilities || savedState.capabilities?.contains("CAPABILITY_AUTO_EXPAND") == true
    }

    private val tagsField = JBTextField().apply {
        val existingTagStr = existingTags?.joinToString(",") { "${it.key}=${it.value}" }
        text = existingTagStr ?: savedState.tags ?: ""
        emptyText.text = "key1=value1,key2=value2 (optional)"
    }

    private val onStackFailureCombo = ComboBox(DefaultComboBoxModel(arrayOf("DO_NOTHING", "ROLLBACK", "DELETE"))).apply {
        selectedItem = savedState.onStackFailure ?: "DO_NOTHING"
    }

    private val includeNestedStacksCheckbox = JBCheckBox("Include nested stacks").apply {
        isSelected = savedState.includeNestedStacks
    }

    private val deploymentModeCombo = ComboBox(DefaultComboBoxModel(arrayOf("Standard", "Revert Drift"))).apply {
        selectedItem = "Standard"
        isEnabled = isExistingStack
    }

    private val importResourcesCheckbox = JBCheckBox("Import existing resources")

    private val resourceCheckboxList = CheckBoxList<TemplateResource>().apply {
        templateResources.forEach { addItem(it, "${it.logicalId} (${it.type})", false) }
    }

    private val resourceIdentifierFields = mutableMapOf<String, MutableMap<String, JBTextField>>()

    init {
        title = message("cloudformation.deploy.dialog.title")
        init()
    }

    override fun getDimensionServiceKey(): String = "aws.toolkit.cloudformation.validateAndDeploy"

    override fun createCenterPanel(): JComponent = panel {
        group("Template & Stack") {
            row(message("cloudformation.deploy.dialog.template.label")) {
                cell(templateField).align(Align.FILL)
            }
            row(message("cloudformation.deploy.dialog.stack_name.label")) {
                cell(stackNameField).align(Align.FILL)
            }
        }

        if (hasArtifacts || s3BucketField.text.isNotBlank()) {
            group("S3 Upload") {
                row("Bucket:") { cell(s3BucketField).align(Align.FILL) }
                row("Key:") { cell(s3KeyField).align(Align.FILL) }
            }
        } else {
            collapsibleGroup("S3 Upload") {
                row("Bucket:") { cell(s3BucketField).align(Align.FILL) }
                row("Key:") { cell(s3KeyField).align(Align.FILL) }
            }
        }

        if (parameterFields.isNotEmpty()) {
            group("Parameters") {
                parameterFields.forEach { (param, field) ->
                    val label = if (param.allowedValues != null) {
                        "${param.name} (${param.allowedValues.joinToString(", ")}):"
                    } else {
                        "${param.name}:"
                    }
                    row(label) { cell(field).align(Align.FILL) }
                }
            }
        }

        group("Capabilities") {
            row { cell(capabilityIam) }
            row { cell(capabilityNamedIam) }
            row { cell(capabilityAutoExpand) }
        }

        if (templateResources.isNotEmpty()) {
            collapsibleGroup("Import Resources") {
                row { cell(importResourcesCheckbox) }
                row {
                    cell(JBScrollPane(resourceCheckboxList).apply {
                        preferredSize = JBUI.size(500, 120)
                    }).align(Align.FILL)
                }
            }
        }

        collapsibleGroup("Advanced Options") {
            row("Tags:") { cell(tagsField).align(Align.FILL) }
            row("On stack failure:") { cell(onStackFailureCombo) }
            if (isExistingStack) {
                row("Deployment mode:") { cell(deploymentModeCombo) }
            }
            row { cell(includeNestedStacksCheckbox) }
        }
    }.apply {
        preferredSize = JBUI.size(550, 550)
    }

    override fun getPreferredFocusedComponent(): JComponent =
        if (templateField.text.isNotBlank()) stackNameField else templateField.textField

    override fun doValidate(): ValidationInfo? {
        val path = templateField.text.trim()
        if (path.isBlank()) return ValidationInfo(message("cloudformation.deploy.dialog.template.required"), templateField)
        val file = File(path)
        if (!file.isFile) return ValidationInfo(message("cloudformation.deploy.dialog.template.not_found"), templateField)
        if (file.extension.lowercase() !in CFN_SUPPORTED_EXTENSIONS) return ValidationInfo(message("cloudformation.deploy.dialog.template.invalid_extension"), templateField)

        val name = stackNameField.text.trim()
        if (name.isBlank()) return ValidationInfo(message("cloudformation.deploy.dialog.stack_name.required"), stackNameField)
        if (name.length > 128) return ValidationInfo(message("cloudformation.deploy.dialog.stack_name.too_long"), stackNameField)
        if (!STACK_NAME_PATTERN.matches(name)) return ValidationInfo(message("cloudformation.deploy.dialog.stack_name.invalid"), stackNameField)

        if (hasArtifacts && s3BucketField.text.isBlank()) return ValidationInfo("S3 bucket is required because template contains artifacts", s3BucketField)

        val tags = tagsField.text.trim()
        if (tags.isNotBlank() && !TAGS_PATTERN.matches(tags)) return ValidationInfo("Tags format: key1=value1,key2=value2", tagsField)

        for ((param, field) in parameterFields) {
            val value = field.text.trim()
            validateParameter(value, param)?.let { return ValidationInfo(it, field) }
        }

        return null
    }

    private fun validateParameter(value: String, param: TemplateParameter): String? {
        val actual = value.ifBlank { param.default?.toString() ?: "" }
        if (param.allowedValues != null && actual !in param.allowedValues.map { it.toString() }) {
            return "Must be one of: ${param.allowedValues.joinToString(", ")}"
        }
        if (param.allowedPattern != null && !Regex(param.allowedPattern).matches(actual)) {
            return "Must match pattern: ${param.allowedPattern}"
        }
        if (param.minLength != null && actual.length < param.minLength) return "Min length: ${param.minLength}"
        if (param.maxLength != null && actual.length > param.maxLength) return "Max length: ${param.maxLength}"
        if (param.type == "Number") {
            val num = actual.toDoubleOrNull() ?: return "Must be a number"
            if (param.minValue != null && num < param.minValue.toDouble()) return "Min value: ${param.minValue}"
            if (param.maxValue != null && num > param.maxValue.toDouble()) return "Max value: ${param.maxValue}"
        }
        return null
    }

    fun getSettings(): ValidateAndDeploySettings {
        val caps = mutableListOf<String>()
        if (capabilityIam.isSelected) caps.add("CAPABILITY_IAM")
        if (capabilityNamedIam.isSelected) caps.add("CAPABILITY_NAMED_IAM")
        if (capabilityAutoExpand.isSelected) caps.add("CAPABILITY_AUTO_EXPAND")

        val params = parameterFields.map { (param, field) ->
            Parameter(param.name, field.text.trim().ifBlank { param.default?.toString() ?: "" })
        }

        val tags = parseTags(tagsField.text.trim())
        val onFailure = onStackFailureCombo.selectedItem as? String

        val resourcesToImport = if (importResourcesCheckbox.isSelected) {
            collectResourcesToImport()
        } else {
            null
        }

        val deployMode = if (isExistingStack && deploymentModeCombo.selectedItem == "Revert Drift") {
            DeploymentMode.REVERT_DRIFT
        } else {
            null
        }

        val settings = ValidateAndDeploySettings(
            templatePath = templateField.text.trim(),
            stackName = stackNameField.text.trim(),
            s3Bucket = s3BucketField.text.trim().ifBlank { null },
            s3Key = s3KeyField.text.trim().ifBlank {
                if (s3BucketField.text.isNotBlank()) {
                    val f = File(templateField.text.trim())
                    "${f.nameWithoutExtension}-${System.currentTimeMillis()}.${f.extension}"
                } else {
                    null
                }
            },
            parameters = params,
            capabilities = caps,
            tags = tags,
            onStackFailure = onFailure,
            includeNestedStacks = includeNestedStacksCheckbox.isSelected,
            importExistingResources = importResourcesCheckbox.isSelected,
            deploymentMode = deployMode,
            resourcesToImport = resourcesToImport,
        )

        savedState.lastTemplatePath = settings.templatePath
        savedState.lastStackName = settings.stackName
        savedState.s3Bucket = settings.s3Bucket
        savedState.s3Key = settings.s3Key
        savedState.onStackFailure = onFailure
        savedState.includeNestedStacks = settings.includeNestedStacks
        savedState.importExistingResources = importResourcesCheckbox.isSelected
        savedState.tags = tagsField.text.trim().ifBlank { null }
        savedState.capabilities = caps.joinToString(",").ifBlank { null }

        return settings
    }

    private fun collectResourcesToImport(): List<ResourceToImport>? {
        val selected = templateResources.filter { resourceCheckboxList.isItemSelected(it) }
        if (selected.isEmpty()) return null

        val result = mutableListOf<ResourceToImport>()
        for (resource in selected) {
            val keys = resource.primaryIdentifierKeys ?: continue
            val identifiers = mutableMapOf<String, String>()

            if (!resource.primaryIdentifier.isNullOrEmpty()) {
                identifiers.putAll(resource.primaryIdentifier)
            } else {
                for (key in keys) {
                    val value = Messages.showInputDialog(
                        project,
                        "Enter $key for ${resource.logicalId} (${resource.type}):",
                        "Resource Identifier",
                        null
                    ) ?: return null
                    if (value.isBlank()) return null
                    identifiers[key] = value
                }
            }

            result.add(
                ResourceToImport(
                    resourceType = resource.type,
                    logicalResourceId = resource.logicalId,
                    resourceIdentifier = identifiers,
                )
            )
        }
        return result
    }

    private fun parseTags(input: String): List<Tag> {
        if (input.isBlank()) return emptyList()
        return input.split(",").mapNotNull { pair ->
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) Tag(parts[0].trim(), parts[1].trim()) else null
        }
    }

    companion object {
        private val STACK_NAME_PATTERN = Regex("^[a-zA-Z][-a-zA-Z0-9]*$")
        private val TAGS_PATTERN = Regex("^[^=,]+=[^=,]+(,[^=,]+=[^=,]+)*$")
    }
}
