package com.addemma.focuslock

import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

data class NsfwDetectionResult(
    val isBlocked: Boolean,
    val layer: String,          // "SEARCH_URL_FILTER" or "VISUAL_AI_SCANNER" or "DNS_VPN"
    val category: String,       // "EXPLICIT_PORN", "SEXY_HALF_NAKED", "BLOCKED_HANDLE"
    val matchedTrigger: String,
    val confidence: Float,
    val details: String
)

object NsfwContentFilter {

    // Browser & Social Media packages where content/search inspection is prioritized
    val MONITORED_VISUAL_AND_SEARCH_PACKAGES = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.brave.browser",
        "org.mozilla.firefox",
        "org.mozilla.focus",
        "com.sec.android.app.sbrowser",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "com.instagram.android",
        "com.instagram.lite",
        "com.zhiliaoapp.musically",      // TikTok
        "com.ss.android.ugc.trill",     // TikTok regional
        "com.twitter.android",          // X / Twitter
        "com.reddit.frontpage",         // Reddit
        "com.facebook.katana",          // Facebook
        "com.facebook.lite",
        "com.snapchat.android",         // Snapchat
        "com.pinterest",                // Pinterest
        "com.google.android.youtube",   // YouTube
        "com.google.android.googlequicksearchbox", // Google Search app
        "org.telegram.messenger",       // Telegram
        "com.discord"                   // Discord
    )

    // System packages that must never be scanned or blocked
    val SAFE_SYSTEM_PACKAGES = setOf(
        "com.addemma.focuslock",
        "com.android.systemui",
        "com.android.settings",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.miui.securitycenter",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller"
    )

    // Core explicit porn domains
    private val EXPLICIT_DOMAINS = setOf(
        "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com",
        "youporn.com", "spankbang.com", "eporner.com", "hqporner.com", "tube8.com",
        "beeg.com", "motherless.com", "chaturbate.com", "stripchat.com", "bongacams.com",
        "livejasmin.com", "cam4.com", "myfreecams.com", "onlyfans.com", "fansly.com",
        "manyvids.com", "brazzers.com", "realitykings.com", "naughtyamerica.com",
        "bangbros.com", "vixen.com", "tushy.com", "blacked.com", "rule34.xxx",
        "rule34.paheal.net", "nhentai.net", "hanime.tv", "hentaihaven.xxx",
        "erome.com", "fapello.com", "coomer.su", "simpcity.su", "literotica.com",
        "sex.com", "porn.com", "xvideos2.com", "xnxx.tv", "daftsex.com", "sxyprn.com",
        "fuq.com", "thumbzilla.com", "tnaflix.com", "drtuber.com", "sunporno.com"
    )

    // Explicit / Porn search queries & keywords
    private val EXPLICIT_KEYWORDS = listOf(
        "pornhub", "xvideos", "xnxx", "xhamster", "redtube", "youporn", "spankbang",
        "eporner", "chaturbate", "stripchat", "onlyfans leak", "onlyfans nude",
        "porn video", "porn videos", "watch porn", "free porn", "hd porn", "xxx video",
        "xxx videos", "sex video", "sex videos", "sex tape", "leaked nudes",
        "nude girls", "nude women", "naked girls", "naked women", "full nude",
        "boobs", "big tits", "titties", "blowjob", "handjob", "cumshot", "creampie",
        "deepthroat", "doggystyle", "gangbang", "hardcore sex", "anal sex",
        "lesbian porn", "milf porn", "hentai porn", "uncensored hentai", "rule34",
        "camgirl", "camgirls", "pussy", "erotic video", "nsfw video", "nsfw pics"
    )

    // Strict mode: Sexy / Half-Naked / Bikini / Thirst-trap search & profile keywords
    private val SEXY_HALF_NAKED_KEYWORDS = listOf(
        "half naked", "half-naked", "sexy girl", "sexy girls", "sexy women",
        "sexy lady", "sexy babe", "sexy model", "sexy pic", "sexy pictures",
        "sexy photos", "sexy video", "sexy dance", "sexy twerk", "booty twerk",
        "twerking video", "big booty girls", "thick curvy girls", "bikini girl",
        "bikini girls", "bikini model", "bikini haul", "bikini try on",
        "micro bikini", "string bikini", "thong bikini", "g-string", "gstring",
        "lingerie model", "lingerie try on", "lingerie haul", "see through top",
        "see-through bra", "no bra challenge", "nobraclub", "underboob",
        "sideboob", "deep cleavage", "cleavage pic", "thirst trap", "thirsttrap",
        "ig baddie", "insta baddies", "hot baddies", "hot girls in bikini",
        "#thirsttrap", "#bikinimodel", "#lingeriemodel", "#underboob", "#cleavage"
    )

    // Pre-configured flagged Instagram/social handles (including the ones from the user's screenshots)
    private val DEFAULT_BLOCKED_HANDLES = setOf(
        "buhle89127",
        "just_sh1vekaaa",
        "h34rtsfor_ditheto",
        "yasmine_kbf"
    )

    @Volatile
    var customBlockedKeywords: Set<String> = DEFAULT_BLOCKED_HANDLES

    fun updateCustomKeywords(keywords: List<String>) {
        val merged = mutableSetOf<String>()
        merged.addAll(DEFAULT_BLOCKED_HANDLES)
        for (item in keywords) {
            val cleaned = item.trim().lowercase(Locale.ROOT).removePrefix("@")
            if (cleaned.isNotEmpty()) {
                merged.add(cleaned)
            }
        }
        customBlockedKeywords = merged
    }

    /**
     * Inspects the AccessibilityNodeInfo hierarchy for:
     * 1. Browser URLs matching adult domains or search queries
     * 2. Search bars / EditTexts where the user is typing/searching explicit or sexy terms
     * 3. Visible social media handles, hashtags, or captions matching blocked triggers
     */
    fun inspectWindowTree(
        rootNode: AccessibilityNodeInfo?,
        packageName: String,
        blockSexyContent: Boolean
    ): NsfwDetectionResult? {
        if (rootNode == null) return null
        if (SAFE_SYSTEM_PACKAGES.contains(packageName)) return null

        val isBrowserOrSocial = MONITORED_VISUAL_AND_SEARCH_PACKAGES.contains(packageName) ||
            packageName.contains("browser", ignoreCase = true) ||
            packageName.contains("chrome", ignoreCase = true) ||
            packageName.contains("instagram", ignoreCase = true) ||
            packageName.contains("tiktok", ignoreCase = true) ||
            packageName.contains("musically", ignoreCase = true) ||
            packageName.contains("twitter", ignoreCase = true) ||
            packageName.contains("reddit", ignoreCase = true)

        return traverseNode(rootNode, isBrowserOrSocial, blockSexyContent, depth = 0)
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo?,
        isBrowserOrSocial: Boolean,
        blockSexyContent: Boolean,
        depth: Int
    ): NsfwDetectionResult? {
        if (node == null || depth > 35) return null

        val viewId = node.viewIdResourceName?.lowercase(Locale.ROOT) ?: ""
        val className = node.className?.toString()?.lowercase(Locale.ROOT) ?: ""
        val rawText = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""

        val isUrlBar = viewId.contains("url") ||
            viewId.contains("location_bar") ||
            viewId.contains("omnibox") ||
            viewId.contains("address_bar") ||
            viewId.contains("mozac_browser_toolbar")

        val isSearchOrEditable = node.isEditable ||
            className.contains("edittext") ||
            className.contains("autocompletetextview") ||
            viewId.contains("search") ||
            viewId.contains("query")

        // Check URL bar, search inputs, or social/browser visible content
        if (rawText.isNotBlank() && (isUrlBar || isSearchOrEditable || isBrowserOrSocial)) {
            val match = evaluateText(
                text = rawText,
                isUrlOrSearchInput = isUrlBar || isSearchOrEditable,
                blockSexyContent = blockSexyContent
            )
            if (match != null) return match
        }

        if (contentDesc.isNotBlank() && isBrowserOrSocial) {
            val match = evaluateText(
                text = contentDesc,
                isUrlOrSearchInput = false,
                blockSexyContent = blockSexyContent
            )
            if (match != null) return match
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = try {
                node.getChild(i)
            } catch (e: Exception) {
                null
            } ?: continue

            val result = traverseNode(child, isBrowserOrSocial, blockSexyContent, depth + 1)
            if (result != null) {
                return result
            }
        }

        return null
    }

    fun evaluateText(
        text: String,
        isUrlOrSearchInput: Boolean,
        blockSexyContent: Boolean
    ): NsfwDetectionResult? {
        val normalized = text.lowercase(Locale.ROOT).trim()
        if (normalized.length < 3) return null

        // Decode URL-encoded spaces (+ or %20) so search URLs like google.com/search?q=half+naked+girls are caught
        val decoded = normalized
            .replace("%20", " ")
            .replace("+", " ")
            .replace("%23", "#")

        // 1. Check Custom Blocked Handles / Keywords / Domains
        for (custom in customBlockedKeywords) {
            if (custom.length >= 3 && containsWordOrPath(decoded, custom)) {
                return NsfwDetectionResult(
                    isBlocked = true,
                    layer = "SEARCH_URL_FILTER",
                    category = "BLOCKED_HANDLE",
                    matchedTrigger = custom,
                    confidence = 1.0f,
                    details = "Blocked account, handle, or custom keyword detected: \"$custom\""
                )
            }
        }

        // 2. Check Explicit Adult / Porn Domains
        for (domain in EXPLICIT_DOMAINS) {
            if (decoded.contains(domain)) {
                return NsfwDetectionResult(
                    isBlocked = true,
                    layer = "SEARCH_URL_FILTER",
                    category = "EXPLICIT_PORN",
                    matchedTrigger = domain,
                    confidence = 1.0f,
                    details = "Adult/Porn domain blocked: $domain"
                )
            }
        }

        // 3. Check Explicit Porn Search / Text Keywords
        for (keyword in EXPLICIT_KEYWORDS) {
            if (containsWordOrPhrase(decoded, keyword)) {
                return NsfwDetectionResult(
                    isBlocked = true,
                    layer = "SEARCH_URL_FILTER",
                    category = "EXPLICIT_PORN",
                    matchedTrigger = keyword,
                    confidence = 0.98f,
                    details = if (isUrlOrSearchInput) {
                        "Explicit search or URL blocked: \"$keyword\""
                    } else {
                        "Explicit on-screen content blocked: \"$keyword\""
                    }
                )
            }
        }

        // 4. Check Sexy / Half-Naked / Bikini Keywords (Strict Mode)
        if (blockSexyContent) {
            for (keyword in SEXY_HALF_NAKED_KEYWORDS) {
                if (containsWordOrPhrase(decoded, keyword)) {
                    return NsfwDetectionResult(
                        isBlocked = true,
                        layer = "SEARCH_URL_FILTER",
                        category = "SEXY_HALF_NAKED",
                        matchedTrigger = keyword,
                        confidence = 0.92f,
                        details = if (isUrlOrSearchInput) {
                            "Restricted sexy/half-naked search blocked: \"$keyword\""
                        } else {
                            "Restricted sexy/half-naked content blocked: \"$keyword\""
                        }
                    )
                }
            }
        }

        return null
    }

    private fun containsWordOrPath(haystack: String, needle: String): Boolean {
        val index = haystack.indexOf(needle)
        if (index == -1) return false
        val beforeOk = index == 0 || !haystack[index - 1].isLetterOrDigit()
        val endIndex = index + needle.length
        val afterOk = endIndex >= haystack.length || !haystack[endIndex].isLetterOrDigit()
        return beforeOk && afterOk
    }

    private fun containsWordOrPhrase(haystack: String, phrase: String): Boolean {
        if (phrase.startsWith("#")) {
            return haystack.contains(phrase)
        }
        val index = haystack.indexOf(phrase)
        if (index == -1) return false
        val beforeOk = index == 0 || !haystack[index - 1].isLetterOrDigit()
        val endIndex = index + phrase.length
        val afterOk = endIndex >= haystack.length || !haystack[endIndex].isLetterOrDigit()
        return beforeOk && afterOk
    }
}
