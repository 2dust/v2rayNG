package com.v2ray.ang.dto

import com.v2ray.ang.util.JsonUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GitHubReleaseTest {

    @Test
    fun parsesRawAndHtmlReleaseNotes() {
        val release = JsonUtil.fromJson(
            """
            {
              "tag_name": "2.3.3",
              "body": "### Changes",
              "body_html": "<h3>Changes</h3>",
              "assets": [],
              "prerelease": true
            }
            """.trimIndent(),
            GitHubRelease::class.java
        )

        assertNotNull(release)
        assertEquals("### Changes", release?.body)
        assertEquals("<h3>Changes</h3>", release?.bodyHtml)
    }
}
