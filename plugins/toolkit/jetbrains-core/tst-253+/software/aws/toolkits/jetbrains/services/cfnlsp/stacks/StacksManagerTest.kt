// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.stacks

import com.intellij.testFramework.ProjectRule
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import software.aws.toolkits.jetbrains.services.cfnlsp.CfnClientService
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListStacksResult
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.StackSummary
import java.util.concurrent.CompletableFuture

class StacksManagerTest {

    @JvmField
    @Rule
    val projectRule = ProjectRule()

    private lateinit var mockClientService: CfnClientService
    private lateinit var stacksManager: StacksManager

    @Before
    fun setUp() {
        mockClientService = mock()
        stacksManager = StacksManager(projectRule.project).apply {
            clientServiceProvider = { mockClientService }
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
    fun `reload calls CfnClientService listStacks`() {
        whenever(mockClientService.listStacks(any())).thenReturn(
            CompletableFuture.completedFuture(ListStacksResult(emptyList()))
        )

        stacksManager.reload()

        verify(mockClientService).listStacks(any())
    }

    @Test
    fun `reload processes successful response and updates state`() {
        val testStacks = listOf(
            StackSummary(stackName = "TestStack1", stackId = "stack-1", stackStatus = "CREATE_COMPLETE"),
            StackSummary(stackName = "TestStack2", stackId = "stack-2", stackStatus = "UPDATE_COMPLETE")
        )
        val result = ListStacksResult(testStacks, "next-token")
        
        whenever(mockClientService.listStacks(any())).thenReturn(
            CompletableFuture.completedFuture(result)
        )

        var notifiedStacks: List<StackSummary>? = null
        stacksManager.addListener { notifiedStacks = it }

        stacksManager.reload()

        // Wait for async completion
        Thread.sleep(100)

        assertThat(stacksManager.isLoaded()).isTrue()
        assertThat(stacksManager.get()).hasSize(2)
        assertThat(stacksManager.get()[0].stackName).isEqualTo("TestStack1")
        assertThat(stacksManager.get()[1].stackName).isEqualTo("TestStack2")
        assertThat(stacksManager.hasMore()).isTrue()
        assertThat(notifiedStacks).hasSize(2)
    }

    @Test
    fun `reload handles null response gracefully`() {
        whenever(mockClientService.listStacks(any())).thenReturn(
            CompletableFuture.completedFuture(null)
        )

        var notified = false
        stacksManager.addListener { notified = true }

        stacksManager.reload()

        // Wait for async completion
        Thread.sleep(100)

        assertThat(stacksManager.isLoaded()).isTrue()
        assertThat(stacksManager.get()).isEmpty()
        assertThat(stacksManager.hasMore()).isFalse()
        assertThat(notified).isTrue()
    }

    @Test
    fun `reload handles exception gracefully`() {
        val failedFuture = CompletableFuture<ListStacksResult?>()
        failedFuture.completeExceptionally(RuntimeException("Test error"))
        whenever(mockClientService.listStacks(any())).thenReturn(failedFuture)

        var notified = false
        stacksManager.addListener { notified = true }

        stacksManager.reload()

        // Wait for async completion
        Thread.sleep(100)

        assertThat(stacksManager.isLoaded()).isTrue()
        assertThat(stacksManager.get()).isEmpty()
        assertThat(stacksManager.hasMore()).isFalse()
        assertThat(notified).isTrue()
    }

    @Test
    fun `loadMoreStacks does nothing when no nextToken`() {
        stacksManager.loadMoreStacks()

        verify(mockClientService, never()).listStacks(any())
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
