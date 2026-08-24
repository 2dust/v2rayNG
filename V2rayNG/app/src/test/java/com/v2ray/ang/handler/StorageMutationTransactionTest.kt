package com.v2ray.ang.handler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StorageMutationTransactionTest {

    @Test
    fun `restores every mutation when a write reports failure`() {
        val values = mutableMapOf(
            "first" to "before-first",
            "second" to "before-second",
        )

        expectFailure<ProfileStorageException> {
            runStorageMutationTransaction {
                write(values, "first", "after-first")
                write(values, "second", "partial-second", succeeds = false)
            }
        }

        assertEquals(
            mapOf("first" to "before-first", "second" to "before-second"),
            values,
        )
    }

    @Test
    fun `restores a mutation when the write throws`() {
        val values = mutableMapOf("profile" to "before")

        expectFailure<IllegalArgumentException> {
            runStorageMutationTransaction {
                write(
                    values,
                    "profile",
                    "partial",
                    changeFailure = IllegalArgumentException("write failed"),
                )
            }
        }

        assertEquals("before", values["profile"])
    }

    @Test
    fun `removes a newly created value when a later write fails`() {
        val values = mutableMapOf<String, String>()

        expectFailure<ProfileStorageException> {
            runStorageMutationTransaction {
                write(values, "profile", "new")
                write(values, "index", "new", succeeds = false)
            }
        }

        assertFalse(values.containsKey("profile"))
    }

    @Test
    fun `committed mutations are not restored`() {
        val values = mutableMapOf("profile" to "before")

        runStorageMutationTransaction {
            write(values, "profile", "after")
        }

        assertEquals("after", values["profile"])
    }

    @Test
    fun `reports rollback failures without hiding the original failure`() {
        val values = mutableMapOf("first" to "before")

        val failure = expectFailure<ProfileStorageException> {
            runStorageMutationTransaction {
                write(values, "first", "after", restoreSucceeds = false)
                write(values, "second", "after", succeeds = false)
            }
        }

        assertEquals("second write", failure.message)
        assertEquals(1, failure.suppressed.size)
        assertTrue(failure.suppressed.single().message.orEmpty().contains("first write"))
        assertEquals("after", values["first"])
    }

    private fun StorageMutationTransaction.write(
        values: MutableMap<String, String>,
        key: String,
        value: String,
        succeeds: Boolean = true,
        restoreSucceeds: Boolean = true,
        changeFailure: Throwable? = null,
    ) {
        val previous = values[key]
        mutate(
            change = {
                values[key] = value
                changeFailure?.let { throw it }
                succeeds
            },
            restore = {
                if (!restoreSucceeds) {
                    false
                } else {
                    if (previous == null) values.remove(key) else values[key] = previous
                    true
                }
            },
            failureMessage = "$key write",
        )
    }

    private inline fun <reified T : Throwable> expectFailure(block: () -> Unit): T {
        try {
            block()
            fail("Expected ${T::class.java.simpleName}")
        } catch (failure: Throwable) {
            if (failure !is T) throw failure
            return failure
        }
        throw AssertionError("Unreachable")
    }
}
