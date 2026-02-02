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
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ChangeSetInfo
import software.aws.toolkits.jetbrains.services.cfnlsp.protocol.ListChangeSetsResult
import java.util.concurrent.CompletableFuture

class ChangeSetsManagerTest {

    @JvmField
    @Rule
    val projectRule = ProjectRule()

    private lateinit var mockClientService: CfnClientService
    private lateinit var changeSetsManager: ChangeSetsManager

    @Before
    fun setUp() {
        mockClientService = mock()
        changeSetsManager = ChangeSetsManager(projectRule.project).apply {
            clientServiceProvider = { mockClientService }
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
    fun `fetchChangeSets calls CfnClientService listChangeSets`() {
        whenever(mockClientService.listChangeSets(any())).thenReturn(
            CompletableFuture.completedFuture(ListChangeSetsResult(emptyList()))
        )

        changeSetsManager.fetchChangeSets("my-stack")

        verify(mockClientService).listChangeSets(any())
    }

    @Test
    fun `fetchChangeSets processes successful response and updates state`() {
        val testChangeSets = listOf(
            ChangeSetInfo("TestChangeSet1", "CREATE_COMPLETE"),
            ChangeSetInfo("TestChangeSet2", "UPDATE_COMPLETE")
        )
        val result = ListChangeSetsResult(testChangeSets, "next-token")
        
        whenever(mockClientService.listChangeSets(any())).thenReturn(
            CompletableFuture.completedFuture(result)
        )

        var notified = false
        changeSetsManager.addListener { notified = true }

        changeSetsManager.fetchChangeSets("my-stack")

        // Wait for async completion
        Thread.sleep(100)

        assertThat(changeSetsManager.isLoaded("my-stack")).isTrue()
        assertThat(changeSetsManager.get("my-stack")).hasSize(2)
        assertThat(changeSetsManager.get("my-stack")[0].changeSetName).isEqualTo("TestChangeSet1")
        assertThat(changeSetsManager.get("my-stack")[1].changeSetName).isEqualTo("TestChangeSet2")
        assertThat(changeSetsManager.hasMore("my-stack")).isTrue()
        assertThat(notified).isTrue()
    }

    @Test
    fun `fetchChangeSets handles null response gracefully`() {
        whenever(mockClientService.listChangeSets(any())).thenReturn(
            CompletableFuture.completedFuture(null)
        )

        var notified = false
        changeSetsManager.addListener { notified = true }

        changeSetsManager.fetchChangeSets("my-stack")

        // Wait for async completion
        Thread.sleep(100)

        assertThat(changeSetsManager.isLoaded("my-stack")).isTrue()
        assertThat(changeSetsManager.get("my-stack")).isEmpty()
        assertThat(changeSetsManager.hasMore("my-stack")).isFalse()
        assertThat(notified).isTrue()
    }

    @Test
    fun `fetchChangeSets handles exception gracefully`() {
        val failedFuture = CompletableFuture<ListChangeSetsResult?>()
        failedFuture.completeExceptionally(RuntimeException("Test error"))
        whenever(mockClientService.listChangeSets(any())).thenReturn(failedFuture)

        var notified = false
        changeSetsManager.addListener { notified = true }

        changeSetsManager.fetchChangeSets("my-stack")

        // Wait for async completion
        Thread.sleep(100)

        assertThat(changeSetsManager.isLoaded("my-stack")).isTrue()
        assertThat(changeSetsManager.get("my-stack")).isEmpty()
        assertThat(changeSetsManager.hasMore("my-stack")).isFalse()
        assertThat(notified).isTrue()
    }

    @Test
    fun `loadMoreChangeSets does nothing when no cached data`() {
        changeSetsManager.loadMoreChangeSets("unknown-stack")

        verify(mockClientService, never()).listChangeSets(any())
    }

    @Test
    fun `loadMoreChangeSets appends new change sets to existing ones`() {
        // First, set up initial data
        val initialChangeSets = listOf(
            ChangeSetInfo("TestChangeSet1", "CREATE_COMPLETE")
        )
        val initialResult = ListChangeSetsResult(initialChangeSets, "next-token")
        whenever(mockClientService.listChangeSets(any())).thenReturn(
            CompletableFuture.completedFuture(initialResult)
        )

        changeSetsManager.fetchChangeSets("my-stack")
        Thread.sleep(100) // Wait for initial load

        // Now set up the "load more" response
        val moreChangeSets = listOf(
            ChangeSetInfo("TestChangeSet2", "UPDATE_COMPLETE")
        )
        val moreResult = ListChangeSetsResult(moreChangeSets, null)
        whenever(mockClientService.listChangeSets(any())).thenReturn(
            CompletableFuture.completedFuture(moreResult)
        )

        changeSetsManager.loadMoreChangeSets("my-stack")
        Thread.sleep(100) // Wait for load more

        assertThat(changeSetsManager.get("my-stack")).hasSize(2)
        assertThat(changeSetsManager.get("my-stack")[0].changeSetName).isEqualTo("TestChangeSet1")
        assertThat(changeSetsManager.get("my-stack")[1].changeSetName).isEqualTo("TestChangeSet2")
        assertThat(changeSetsManager.hasMore("my-stack")).isFalse()
    }
}
