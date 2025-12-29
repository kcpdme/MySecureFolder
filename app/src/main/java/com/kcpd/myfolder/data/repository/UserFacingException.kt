package com.kcpd.myfolder.data.repository

/**
 * Exception type meant to surface a clean, actionable message to the UI,
 * while still keeping the original exception available as [cause] for logging/debugging.
 */
class UserFacingException(
    override val message: String,
    override val cause: Throwable? = null
) : Exception(message, cause)
