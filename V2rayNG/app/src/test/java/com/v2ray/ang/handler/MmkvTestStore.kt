package com.v2ray.ang.handler

import com.tencent.mmkv.MMKV
import com.v2ray.ang.util.LogUtil
import net.bytebuddy.ByteBuddy
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy
import net.bytebuddy.implementation.StubMethod
import net.bytebuddy.matcher.ElementMatchers
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever

/** In-memory MMKV boundary: production manager transactions and serialization still execute. */
class MmkvTestStore : AutoCloseable {
    class Store {
        val values = linkedMapOf<String, Any>()
        var failWrite: (String) -> Boolean = { false }
        var failRemove = false
        val mmkv: MMKV = Mockito.mock(mmkvClass)

        init {
            whenever(mmkv.containsKey(any())).thenAnswer { it.getArgument<String>(0) in values }
            whenever(mmkv.decodeString(any())).thenAnswer { values[it.getArgument<String>(0)] as? String }
            whenever(mmkv.decodeBool(any(), any())).thenAnswer {
                values[it.getArgument<String>(0)] as? Boolean ?: it.getArgument<Boolean>(1)
            }
            whenever(mmkv.allKeys()).thenAnswer { values.keys.toTypedArray() }
            whenever(mmkv.encode(any<String>(), any<String>())).thenAnswer {
                val key = it.getArgument<String>(0)
                if (failWrite(key)) false else { values[key] = it.getArgument<String>(1); true }
            }
            whenever(mmkv.encode(any<String>(), any<Boolean>())).thenAnswer {
                val key = it.getArgument<String>(0)
                if (failWrite(key)) false else { values[key] = it.getArgument<Boolean>(1); true }
            }
            doAnswer {
                if (!failRemove) values.remove(it.getArgument<String>(0))
                null
            }.whenever(mmkv).remove(any())
            doAnswer {
                if (!failRemove) it.getArgument<Array<String>>(0).forEach(values::remove)
                null
            }.whenever(mmkv).removeValuesForKeys(any())
        }
    }

    private val restore = mutableListOf<() -> Unit>()
    val main = install("mainStorage")
    val profiles = install("profileFullStorage")
    val raw = install("serverRawStorage")
    val affiliations = install("serverAffStorage")
    val subscriptions = install("subStorage")
    val settings = install("settingsStorage")

    init {
        val field = LogUtil::class.java.getDeclaredField("cachedMinPriority").apply { isAccessible = true }
        val previous = field.get(null)
        field.set(null, Int.MAX_VALUE)
        restore += { field.set(null, previous) }
    }

    private fun install(name: String): Store {
        val delegate = MmkvManager::class.java.getDeclaredField("${name}\$delegate")
            .apply { isAccessible = true }.get(null)
        val value = delegate.javaClass.getDeclaredField("_value").apply { isAccessible = true }
        val previous = value.get(delegate)
        val store = Store()
        value.set(delegate, store.mmkv)
        restore += { value.set(delegate, previous) }
        return store
    }

    override fun close() = restore.asReversed().forEach { it() }

    companion object {
        // Mockito cannot intercept JNI methods. Override them before installing normal mock answers.
        private val mmkvClass = ByteBuddy()
            .subclass(MMKV::class.java, ConstructorStrategy.Default.NO_CONSTRUCTORS)
            .method(ElementMatchers.isNative())
            .intercept(StubMethod.INSTANCE)
            .make().load(MMKV::class.java.classLoader).loaded
    }
}
