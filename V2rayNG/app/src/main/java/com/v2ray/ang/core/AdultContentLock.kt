package com.v2ray.ang.core

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.entities.RulesetItem
import com.v2ray.ang.handler.MmkvManager

/**
 * Matixray: the "lock adult content" setting. While it is on, a block rule for well-known porn and sexual-content
 * sites stands in front of every routing rule the user has, so a rule of their own cannot let one through. The rule is
 * not stored with the user's routing rules: it is added when a configuration is built, so switching the setting off
 * leaves their rules exactly as they were.
 *
 * Both the routing rule and the DNS answer (a blocked name resolves to loopback) are made from this one rule. Names are
 * written as plain domains and keywords on purpose: a geosite category would make the core fail to start on a device
 * whose geosite file lacks it.
 */
object AdultContentLock {
    private const val RULE_ID = "matixray_adult_lock"

    /** Sites blocked with their subdomains; the first run are "domain:" rules, the last are keyword rules. */
    internal val BLOCKED_DOMAINS: List<String> = listOf(
        "domain:pornhub.com", "domain:xvideos.com", "domain:xnxx.com", "domain:xhamster.com",
        "domain:redtube.com", "domain:youporn.com", "domain:tube8.com", "domain:spankbang.com",
        "domain:eporner.com", "domain:brazzers.com", "domain:chaturbate.com", "domain:stripchat.com",
        "domain:bongacams.com", "domain:livejasmin.com", "domain:camsoda.com", "domain:cam4.com",
        "domain:onlyfans.com", "domain:fansly.com", "domain:porn.com", "domain:4tube.com",
        "domain:tnaflix.com", "domain:beeg.com", "domain:motherless.com", "domain:thumbzilla.com",
        "domain:hclips.com", "domain:txxx.com", "domain:daftsex.com", "domain:rule34.xxx",
        "domain:e-hentai.org", "domain:exhentai.org", "domain:nhentai.net", "domain:hanime.tv",
        "domain:xtube.com", "domain:sex.com", "domain:xvideos2.com", "domain:xhamster2.com",
        "keyword:pornhub", "keyword:xvideos", "keyword:xnxx", "keyword:xhamster",
        "keyword:porn", "keyword:hentai", "keyword:redtube", "keyword:youporn",
    )

    fun isEnabled(): Boolean = MmkvManager.decodeSettingsBool(AppConfig.PREF_ADULT_LOCK, false)

    /** [rulesets] with the lock rule in front when the setting is on; [rulesets] itself, unchanged, when it is off. */
    fun withLock(rulesets: List<RulesetItem>?): List<RulesetItem>? = withLock(rulesets, isEnabled())

    internal fun withLock(rulesets: List<RulesetItem>?, enabled: Boolean): List<RulesetItem>? {
        if (!enabled) return rulesets
        val lock = RulesetItem(
            id = RULE_ID,
            remarks = "Adult content lock",
            domain = BLOCKED_DOMAINS,
            outboundTag = AppConfig.TAG_BLOCKED,
            enabled = true,
            locked = true,
        )
        return listOf(lock) + rulesets.orEmpty()
    }
}
