// Copyright 2026 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.cfnlsp

/**
 * Retries [block] until it completes without throwing, or [timeoutMs] elapses.
 * On timeout, rethrows the last caught exception with a timeout suffix.
 */
internal fun waitFor(
    timeoutMs: Long = 10_000L,
    intervalMs: Long = 500L,
    block: () -> Unit,
) {
    var lastError: Throwable? = null
    val deadline = System.currentTimeMillis() + timeoutMs

    while (System.currentTimeMillis() < deadline) {
        try {
            block()
            return
        } catch (e: Throwable) {
            lastError = e
        }
        Thread.sleep(intervalMs)
    }

    throw AssertionError("${lastError?.message} [Timed out after ${timeoutMs}ms]", lastError)
}
