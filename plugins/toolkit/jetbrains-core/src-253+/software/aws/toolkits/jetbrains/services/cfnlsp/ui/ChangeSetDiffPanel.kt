// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.ui

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.Splitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceChange
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ResourceChangeDetail
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackChange
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.ChangeSetDeletionWorkflow
import software.aws.toolkits.jetbrains.services.cfnlsp.stacks.DeploymentWorkflow
import software.aws.toolkits.jetbrains.services.cloudformation.toolwindow.CloudFormationToolWindow
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionEvent
import javax.swing.table.AbstractTableModel

internal class ChangeSetDiffPanel(
    private val project: Project,
    private val stackName: String,
    private val changeSetName: String,
    private val changes: List<StackChange>,
    private val enableDeploy: Boolean,
) : SimpleToolWindowPanel(false, true) {

    private val resourceChanges = changes.mapNotNull { it.resourceChange }
    private val resourceTable = JBTable(ResourceTableModel(resourceChanges)).apply {
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        setShowGrid(false)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) onResourceDoubleClick()
            }
        })
    }

    private val detailTable = JBTable().apply { setShowGrid(false) }
    private val detailPanel = JPanel(BorderLayout()).apply {
        add(JBLabel("  Select a resource to view property changes"), BorderLayout.CENTER)
    }

    init {
        resourceTable.selectionModel.addListSelectionListener { e: ListSelectionEvent ->
            if (!e.valueIsAdjusting) onResourceSelected()
        }

        val splitter = Splitter(true, 0.55f).apply {
            firstComponent = JBScrollPane(resourceTable)
            secondComponent = detailPanel
        }

        toolbar = createToolbar()
        setContent(splitter)
    }

    private fun onResourceSelected() {
        val row = resourceTable.selectedRow
        if (row < 0 || row >= resourceChanges.size) return
        val rc = resourceChanges[row]
        val details = rc.details

        detailPanel.removeAll()
        if (details.isNullOrEmpty()) {
            detailPanel.add(JBLabel("  No property changes"), BorderLayout.CENTER)
        } else {
            detailTable.model = DetailTableModel(details)
            detailPanel.add(JBScrollPane(detailTable), BorderLayout.CENTER)
        }
        detailPanel.revalidate()
        detailPanel.repaint()
    }

    private fun onResourceDoubleClick() {
        val row = resourceTable.selectedRow
        if (row < 0 || row >= resourceChanges.size) return
        val rc = resourceChanges[row]
        val before = formatJson(rc.beforeContext ?: "")
        val after = formatJson(rc.afterContext ?: "")
        if (before.isEmpty() && after.isEmpty()) return

        val factory = DiffContentFactory.getInstance()
        DiffManager.getInstance().showDiff(
            project,
            SimpleDiffRequest(
                "${rc.logicalResourceId ?: "Resource"} — $stackName",
                factory.create(before),
                factory.create(after),
                "Before",
                "After"
            )
        )
    }

    private fun formatJson(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            GsonBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(raw))
        } catch (_: Exception) {
            raw
        }
    }

    private fun createToolbar() = ActionManager.getInstance().createActionToolbar(
        "ChangeSetDiff",
        DefaultActionGroup().apply {
            if (enableDeploy) {
                add(object : AnAction("Deploy", "Execute this change set", AllIcons.Actions.Execute) {
                    override fun actionPerformed(e: AnActionEvent) {
                        DeploymentWorkflow(project).deploy(stackName, changeSetName)
                    }
                })
            }
            add(object : AnAction("Delete Change Set", "Delete this change set", AllIcons.Actions.GC) {
                override fun actionPerformed(e: AnActionEvent) {
                    if (Messages.showYesNoDialog(project, "Delete change set '$changeSetName'?", "Delete Change Set", null) == Messages.YES) {
                        ChangeSetDeletionWorkflow(project).delete(stackName, changeSetName).thenAccept { success ->
                            if (success) {
                                val toolWindow = CloudFormationToolWindow.getInstance(project)
                                toolWindow.find("changeset-$stackName-$changeSetName")?.let { toolWindow.removeContent(it) }
                            }
                        }
                    }
                }
            })
        },
        true
    ).apply {
        targetComponent = this@ChangeSetDiffPanel
    }.component

    companion object {
        fun show(
            project: Project,
            stackName: String,
            changeSetName: String,
            changes: List<StackChange>,
            enableDeploy: Boolean,
        ) {
            val panel = ChangeSetDiffPanel(project, stackName, changeSetName, changes, enableDeploy)
            val toolWindow = CloudFormationToolWindow.getInstance(project)
            val tabId = "changeset-$stackName-$changeSetName"

            if (!toolWindow.showExistingContent(tabId)) {
                toolWindow.addTab(
                    title = "$stackName: $changeSetName",
                    component = panel,
                    activate = true,
                    id = tabId,
                )
            }
        }
    }
}

private class ResourceTableModel(private val resources: List<ResourceChange>) : AbstractTableModel() {
    private val columns = arrayOf("Action", "Logical ID", "Physical ID", "Type", "Replacement")

    override fun getRowCount() = resources.size
    override fun getColumnCount() = columns.size
    override fun getColumnName(col: Int) = columns[col]
    override fun getValueAt(row: Int, col: Int): Any {
        val rc = resources[row]
        return when (col) {
            0 -> rc.action ?: ""
            1 -> rc.logicalResourceId ?: ""
            2 -> rc.physicalResourceId ?: ""
            3 -> rc.resourceType ?: ""
            4 -> rc.replacement ?: ""
            else -> ""
        }
    }
}

private class DetailTableModel(private val details: List<ResourceChangeDetail>) : AbstractTableModel() {
    private val columns = arrayOf("Attribute Change Type", "Name", "Requires Recreation", "Before Value", "After Value", "Change Source", "Causing Entity")

    override fun getRowCount() = details.size
    override fun getColumnCount() = columns.size
    override fun getColumnName(col: Int) = columns[col]
    override fun getValueAt(row: Int, col: Int): Any {
        val d = details[row]
        val t = d.target
        return when (col) {
            0 -> t?.attributeChangeType ?: ""
            1 -> t?.name ?: t?.attribute ?: ""
            2 -> t?.requiresRecreation ?: ""
            3 -> t?.beforeValue ?: ""
            4 -> t?.afterValue ?: ""
            5 -> d.changeSource ?: ""
            6 -> d.causingEntity ?: ""
            else -> ""
        }
    }
}
