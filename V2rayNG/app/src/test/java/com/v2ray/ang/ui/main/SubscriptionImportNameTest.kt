package com.v2ray.ang.ui.main

import org.junit.Assert.assertEquals
import org.junit.Test

class SubscriptionImportNameTest {
    @Test
    fun `uses the placeholder when it is available`() {
        assertEquals("import sub", uniqueSubscriptionName("import sub", setOf("Other", "import sub 2")))
    }

    @Test
    fun `suffix starts at two and skips existing names`() {
        assertEquals("import sub 4", uniqueSubscriptionName("import sub", setOf("import sub", "import sub 2", "import sub 3")))
    }

    @Test
    fun `uses the first free suffix`() {
        assertEquals("import sub 3", uniqueSubscriptionName("import sub", setOf("import sub", "import sub 2", "import sub 4")))
    }

    @Test
    fun `preserves names supplied by the subscription link`() {
        assertEquals("My group", uniqueSubscriptionName("My group", emptySet()))
        assertEquals("My group 2", uniqueSubscriptionName("My group", setOf("My group")))
    }

    @Test
    fun `supports localized placeholders`() {
        assertEquals("Подписка 2", uniqueSubscriptionName("Подписка", setOf("Подписка")))
    }
}
