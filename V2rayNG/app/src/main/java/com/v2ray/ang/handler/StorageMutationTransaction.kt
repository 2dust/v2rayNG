package com.v2ray.ang.handler

internal open class ProfileStorageException(message: String) : IllegalStateException(message)

/** Checked mutation/rollback mechanics only; callers own storage locks and publication order. */
internal class StorageMutationTransaction {
    private data class RollbackAction(
        val restore: () -> Boolean,
        val failureMessage: String,
    )

    private val rollbackActions = mutableListOf<RollbackAction>()

    fun mutate(
        change: () -> Boolean,
        restore: () -> Boolean,
        failureMessage: String,
    ) {
        rollbackActions.add(RollbackAction(restore, failureMessage))
        if (!change()) throw ProfileStorageException(failureMessage)
    }

    fun rollback(failure: Throwable) {
        rollbackActions.asReversed().forEach { action ->
            try {
                if (!action.restore()) {
                    failure.addSuppressed(
                        ProfileStorageException("Rollback failed: ${action.failureMessage}"),
                    )
                }
            } catch (rollbackFailure: Throwable) {
                failure.addSuppressed(rollbackFailure)
            }
        }
    }
}

internal fun <T> runStorageMutationTransaction(
    block: StorageMutationTransaction.() -> T,
): T {
    val transaction = StorageMutationTransaction()
    return try {
        transaction.block()
    } catch (failure: Throwable) {
        transaction.rollback(failure)
        throw failure
    }
}
