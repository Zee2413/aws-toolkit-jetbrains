// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp.protocol

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class StackProtocolTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `StackSummary deserializes PascalCase JSON correctly`() {
        val json = """
            {
                "StackName": "my-stack",
                "StackId": "arn:aws:cloudformation:us-east-1:123456789:stack/my-stack/guid",
                "StackStatus": "CREATE_COMPLETE",
                "CreationTime": "2025-01-27T10:00:00Z",
                "LastUpdatedTime": "2025-01-27T12:00:00Z",
                "TemplateDescription": "My test stack"
            }
        """.trimIndent()

        val stack = mapper.readValue(json, StackSummary::class.java)

        assertThat(stack.stackName).isEqualTo("my-stack")
        assertThat(stack.stackId).isEqualTo("arn:aws:cloudformation:us-east-1:123456789:stack/my-stack/guid")
        assertThat(stack.stackStatus).isEqualTo("CREATE_COMPLETE")
        assertThat(stack.creationTime).isEqualTo("2025-01-27T10:00:00Z")
        assertThat(stack.lastUpdatedTime).isEqualTo("2025-01-27T12:00:00Z")
        assertThat(stack.templateDescription).isEqualTo("My test stack")
    }

    @Test
    fun `StackSummary handles missing optional fields`() {
        val json = """{"StackName": "minimal-stack"}"""

        val stack = mapper.readValue(json, StackSummary::class.java)

        assertThat(stack.stackName).isEqualTo("minimal-stack")
        assertThat(stack.stackId).isNull()
        assertThat(stack.stackStatus).isNull()
        assertThat(stack.creationTime).isNull()
        assertThat(stack.lastUpdatedTime).isNull()
        assertThat(stack.templateDescription).isNull()
    }

    @Test
    fun `ListStacksResult deserializes correctly`() {
        val json = """
            {
                "stacks": [
                    {"StackName": "stack-1", "StackStatus": "CREATE_COMPLETE"},
                    {"StackName": "stack-2", "StackStatus": "UPDATE_IN_PROGRESS"}
                ],
                "nextToken": "token123"
            }
        """.trimIndent()

        val result = mapper.readValue(json, ListStacksResult::class.java)

        assertThat(result.stacks).hasSize(2)
        assertThat(result.stacks[0].stackName).isEqualTo("stack-1")
        assertThat(result.stacks[1].stackName).isEqualTo("stack-2")
        assertThat(result.nextToken).isEqualTo("token123")
    }

    @Test
    fun `ListStacksResult handles null nextToken`() {
        val json = """{"stacks": []}"""

        val result = mapper.readValue(json, ListStacksResult::class.java)

        assertThat(result.stacks).isEmpty()
        assertThat(result.nextToken).isNull()
    }

    @Test
    fun `ListStacksParams serializes correctly`() {
        val params = ListStacksParams(
            statusToExclude = listOf("DELETE_COMPLETE"),
            loadMore = true
        )

        val json = mapper.writeValueAsString(params)

        assertThat(json).contains(""""statusToExclude":["DELETE_COMPLETE"]""")
        assertThat(json).contains(""""loadMore":true""")
    }

    @Test
    fun `ChangeSetInfo deserializes correctly`() {
        val json = """
            {
                "changeSetName": "my-changeset",
                "status": "CREATE_COMPLETE",
                "creationTime": "2025-01-27T10:00:00Z",
                "description": "Test changeset"
            }
        """.trimIndent()

        val changeSet = mapper.readValue(json, ChangeSetInfo::class.java)

        assertThat(changeSet.changeSetName).isEqualTo("my-changeset")
        assertThat(changeSet.status).isEqualTo("CREATE_COMPLETE")
        assertThat(changeSet.creationTime).isEqualTo("2025-01-27T10:00:00Z")
        assertThat(changeSet.description).isEqualTo("Test changeset")
    }

    @Test
    fun `ListChangeSetsResult deserializes correctly`() {
        val json = """
            {
                "changeSets": [
                    {"changeSetName": "cs-1", "status": "CREATE_COMPLETE"},
                    {"changeSetName": "cs-2", "status": "FAILED"}
                ],
                "nextToken": "next-page"
            }
        """.trimIndent()

        val result = mapper.readValue(json, ListChangeSetsResult::class.java)

        assertThat(result.changeSets).hasSize(2)
        assertThat(result.changeSets[0].changeSetName).isEqualTo("cs-1")
        assertThat(result.changeSets[1].changeSetName).isEqualTo("cs-2")
        assertThat(result.nextToken).isEqualTo("next-page")
    }
}
