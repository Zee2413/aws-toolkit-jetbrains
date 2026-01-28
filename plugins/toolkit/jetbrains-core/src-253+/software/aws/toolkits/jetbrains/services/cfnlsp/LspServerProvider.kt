// Copyright 2025 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import software.aws.toolkits.jetbrains.services.cfnlsp.server.CfnLspServerSupportProvider

internal fun interface LspServerProvider {
    fun getServer(): LspServer?
}

internal fun defaultLspServerProvider(project: Project) = LspServerProvider {
    LspServerManager.getInstance(project)
        .getServersForProvider(CfnLspServerSupportProvider::class.java)
        .firstOrNull()
}
