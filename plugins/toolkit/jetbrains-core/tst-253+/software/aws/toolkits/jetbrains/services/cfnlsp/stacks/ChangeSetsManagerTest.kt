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
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import software.aws.toolkits.jetbrains.services.cfnlsp.LspServerProvider

class ChangeSetsManagerTest {

    @JvmField
    @Rule
    val projectRule = ProjectRule()

    private lateinit var mockLspServer: LspServer
    private lateinit var changeSetsManager: `ChangeSetsManager.kt`

    @Before
    fun setUp() {
        mockLspServer = mock()
        changeSetsManager = `ChangeSetsManager.kt`(projectRule.project).apply {
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
    fun `fetchChangeSets does nothing when no LSP server`() {
        changeSetsManager.lspServerProvider = LspServerProvider { null }

        changeSetsManager.fetchChangeSets("my-stack")

        // No exception thrown, returns gracefully
        assertThat(changeSetsManager.get("my-stack")).isEmpty()
    }

    @Test
    fun `fetchChangeSets sends notification to LSP server`() {
        changeSetsManager.fetchChangeSets("my-stack")

        verify(mockLspServer).sendNotification(any())
    }

    @Test
    fun `loadMoreChangeSets does nothing when no cached data`() {
        changeSetsManager.loadMoreChangeSets("unknown-stack")

        verify(mockLspServer, never()).sendNotification(any())
    }
}
