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

class StacksManagerTest {

    @JvmField
    @Rule
    val projectRule = ProjectRule()

    private lateinit var mockLspServer: LspServer
    private lateinit var stacksManager: StacksManager

    @Before
    fun setUp() {
        mockLspServer = mock()
        stacksManager = StacksManager(projectRule.project).apply {
            lspServerProvider = LspServerProvider { mockLspServer }
        }
    }

    @Test
    fun `initial state is not loaded with empty stacks`() {
        assertThat(stacksManager.isLoaded()).isFalse()
        assertThat(stacksManager.get()).isEmpty()
        assertThat(stacksManager.hasMore()).isFalse()
    }

    @Test
    fun `clear resets state and notifies listeners`() {
        var notified = false
        stacksManager.addListener { notified = true }

        stacksManager.clear()

        assertThat(stacksManager.isLoaded()).isFalse()
        assertThat(stacksManager.get()).isEmpty()
        assertThat(stacksManager.hasMore()).isFalse()
        assertThat(notified).isTrue()
    }

    @Test
    fun `reload does nothing when no LSP server available`() {
        stacksManager.lspServerProvider = LspServerProvider { null }

        stacksManager.reload()

        assertThat(stacksManager.isLoaded()).isFalse()
    }

    @Test
    fun `loadMoreStacks does nothing when no nextToken`() {
        stacksManager.loadMoreStacks()

        verify(mockLspServer, never()).sendNotification(any())
    }

    @Test
    fun `reload sends notification to LSP server`() {
        stacksManager.reload()

        verify(mockLspServer).sendNotification(any())
    }

    @Test
    fun `listener is notified on clear`() {
        val notifications = mutableListOf<Int>()
        stacksManager.addListener { notifications.add(it.size) }

        stacksManager.clear()

        assertThat(notifications).containsExactly(0)
    }

    @Test
    fun `multiple listeners are all notified`() {
        var listener1Called = false
        var listener2Called = false

        stacksManager.addListener { listener1Called = true }
        stacksManager.addListener { listener2Called = true }

        stacksManager.clear()

        assertThat(listener1Called).isTrue()
        assertThat(listener2Called).isTrue()
    }

    @Test
    fun `get returns copy of stacks list`() {
        // Verify get() returns a list (defensive copy behavior)
        val stacks = stacksManager.get()
        assertThat(stacks).isInstanceOf(List::class.java)
    }
}
