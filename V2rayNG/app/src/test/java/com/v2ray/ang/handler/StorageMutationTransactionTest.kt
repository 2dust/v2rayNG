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
                mutate(
                    change = {
                        values["first"] = "after-first"
                        true
                    },
                    restore = {
                        values["first"] = "before-first"
                        true
                    },
                    failureMessage = "first write",
                )
                mutate(
                    change = {
                        values["second"] = "partial-second"
                        false
                    },
                    restore = {
                        values["second"] = "before-second"
                        true
                    },
                    failureMessage = "second write",
                )
            }
        }

        assertEquals("before-first", values["first"])
        assertEquals("before-second", values["second"])
    }

    @Test
    fun `restores a mutation when the write throws`() {
        val values = mutableMapOf("profile" to "before")

        expectFailure<IllegalArgumentException> {
            runStorageMutationTransaction {
                mutate(
                    change = {
                        values["profile"] = "partial"
                        throw IllegalArgumentException("write failed")
                    },
                    restore = {
                        values["profile"] = "before"
                        true
                    },
                    failureMessage = "profile write",
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
                mutate(
                    change = {
                        values["profile"] = "new"
                        true
                    },
                    restore = {
                        values.remove("profile")
                        true
                    },
                    failureMessage = "profile write",
                )
                mutate(
                    change = { false },
                    restore = { true },
                    failureMessage = "index write",
                )
            }
        }

        assertFalse(values.containsKey("profile"))
    }

    @Test
    fun `committed mutations are not restored`() {
        val values = mutableMapOf("profile" to "before")

        runStorageMutationTransaction {
            mutate(
                change = {
                    values["profile"] = "after"
                    true
                },
                restore = {
                    values["profile"] = "before"
                    true
                },
                failureMessage = "profile write",
            )
        }

        assertEquals("after", values["profile"])
    }

    @Test
    fun `reports rollback failures without hiding the original failure`() {
        val failure = expectFailure<ProfileStorageException> {
            runStorageMutationTransaction {
                mutate(
                    change = { true },
                    restore = { false },
                    failureMessage = "first write",
                )
                mutate(
                    change = { false },
                    restore = { true },
                    failureMessage = "second write",
                )
            }
        }

        assertEquals("second write", failure.message)
        assertEquals(1, failure.suppressed.size)
        assertTrue(failure.suppressed.single().message.orEmpty().contains("first write"))
    }

    @Test
    fun `publishes the authoritative index after every preparation step`() {
        val writes = mutableListOf<String>()

        runStorageMutationTransaction {
            prepareThenPublish(
                prepare = {
                    writes += "raw payload"
                    writes += "profile payload"
                    writes += "selected profile"
                },
                publish = { writes += "profile index" },
            )
        }

        assertEquals(
            listOf("raw payload", "profile payload", "selected profile", "profile index"),
            writes,
        )
    }

    @Test
    fun `does not publish the index when preparation fails and rollback also fails`() {
        val values = mutableMapOf(
            "payload" to "old payload",
            "index" to "old index",
        )

        val failure = expectFailure<ProfileStorageException> {
            runStorageMutationTransaction {
                prepareThenPublish(
                    prepare = {
                        mutate(
                            change = {
                                values["payload"] = "extra payload"
                                true
                            },
                            restore = { false },
                            failureMessage = "payload write",
                        )
                        mutate(
                            change = { false },
                            restore = { true },
                            failureMessage = "metadata write",
                        )
                    },
                    publish = {
                        values["index"] = "new index"
                    },
                )
            }
        }

        assertEquals("metadata write", failure.message)
        assertEquals("extra payload", values["payload"])
        assertEquals("old index", values["index"])
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
