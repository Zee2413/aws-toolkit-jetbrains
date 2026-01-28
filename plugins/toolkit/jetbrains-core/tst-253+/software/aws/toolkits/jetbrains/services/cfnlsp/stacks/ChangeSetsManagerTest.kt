// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.platform.lsp.api.LspServer
import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import software.aws.toolkits.jetbrains.services.cfnlsp.LspServerProvider

class ChangeSetsManagerTest {

    @JvmField
    @Rule
    val projectRule = ProjectRule()

    private lateinit var mockLspServer: LspServer
    private lateinit var changeSetsManager: ChangeSetsManager

    @Before
    fun setUp() {
        mockLspServer = mock()
        changeSetsManager = ChangeSetsManager(projectRule.project).apply {
            lspServerProvider = LspServerProvider { mockLspServer }
        }
    }

    @Test
    fun `get returns empty list for unknown stack`() {
        assertThat(changeSetsManager.get("unknown-stack")).isEmpty()
    }

    @Test
    fun `hasMore returns false for unknown stack`() {
        assertThat(changeSetsManager.hasMore("unknown-stack")).isFalse()
    }

    @Test
    fun `getChangeSets returns empty when no LSP server`() {
        changeSetsManager.lspServerProvider = LspServerProvider { null }

        val result = changeSetsManager.getChangeSets("my-stack")

        assertThat(result).isEmpty()
    }

    @Test
    fun `getChangeSets sends notification to LSP server`() {
        changeSetsManager.getChangeSets("my-stack")

        verify(mockLspServer).sendNotification(any())
    }
}
