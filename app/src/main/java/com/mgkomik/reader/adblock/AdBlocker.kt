package com.mgkomik.reader.adblock

/**
 * AdBlocker: blocks requests matching a host/domain list and hides
 * common ad containers by injecting a stylesheet.
 *
 * The host list is deliberately compact but covers the trackers/ad networks
 * commonly seen on Indonesian manga/streaming sites.
 */
object AdBlocker {

    /** Hosts whose requests are blocked (subdomains included via suffix match). */
    val BLOCKED_HOSTS: Set<String> = setOf(
        // Ad networks / exchanges
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com",
        "adservice.google.com", "pagead2.googlesyndication.com",
        "adnxs.com", "advertising.com", "adtechus.com", "adcolony.com",
        "adsrvr.org", "adform.net", "adroll.com", "adsterra.com",
        "adcash.com", "adk2x.com", "adbrite.com", "adf.ly", "adfly.io",
        "adpopa.com", "adpushup.com", "adsafeprotected.com", "adultad.net",
        "amazon-adsystem.com", "amazonadsystem.com", "bidswitch.net",
        "criteo.com", "criteo.net", "taboola.com", "outbrain.com",
        "propellerads.com", "pushnative.com", "popads.net", "popunder.net",
        "trafficjunky.net", "trafficfactory.biz", "exoclick.com",
        "exosrv.com", "media.net", "mgid.com", "mopub.com", "openx.net",
        "pubmatic.com", "rubiconproject.com", "smartadserver.com",
        "sovrn.com", "spotxchange.com", "stackadapt.com", "teads.tv",
        "tidaltv.com", "undertone.com", "yieldmo.com", "zedo.com",
        "quantserve.com", "scorecardresearch.com", "chartbeat.com",
        "histats.com", "statcounter.com", "cpmstar.com", "ayads.co",
        "onclickads.net", "adbucks.io", "trafficforce.com", "zeroads.net",
        "adkernel.com", "ad-maven.com", "adbit.co", "mediavine.com",
        // Analytics / misc
        "hotjar.com", "clarity.ms", "newrelic.com", "segment.com",
        "mixpanel.com", "amplitude.com", "fullstory.com", "crazyegg.com",
        "mouseflow.com", "luckyorange.com", "tapjoy.com", "inmobi.com",
        "unity3d.com", "playtem.com", "supersonicads.com", "fyber.com",
        "vungle.com", "applovin.com", "ironsrc.com", "chartboost.com",
        "startapp.com", "flurry.com", "branch.io", "adjust.com", "kochava.com",
        // Common on mgkomik-type sites
        "mgkomik.net", "web2.mgkomik.cc",
        "komikcast.id", "komikindo.id", "bato.to", "mangadop.net",
        "cekcopyright.com", "disqus.com", "disquscdn.com", "spot.im",
        "pubfuture.com", "shorte.st", "linkvertise.com", "ouo.io", "cuon.io",
        "exe.io", "shorte.st", "shrinke.me", "bcvc.net", "adfoc.us",
        "adbla.com", "blogherads.com", "partner.googleadservices.com",
        "staticxx.facebook.com", "connect.facebook.net", "www.facebook.com",
        "platform.twitter.com", "syndication.twitter.com", "t.co",
        "cdn.taboola.com", "trc.taboola.com", "cdn.outbrain.com", "amplifypixel.outbrain.com",
        // Streaming/video ad
        "imasdk.googleapis.com", "gstatic.com/dfp", "adservice.google.com",
        "pubads.g.doubleclick.net", "securepubads.g.doubleclick.net",
        "ads.yahoo.com", "gemini.yahoo.com", "advertising.yahoo.com",
        "bing.com/aclick", "clickserve.dartsearch.net"
    )

    private val hostSet = BLOCKED_HOSTS

    private val CSS_HIDE = """
        .ads, .adsbygoogle, .ad-container, .ad-wrapper, .ad-banner, .ad-slot,
        .advertisement, .advert, .ad-box, .ad-placeholder, .ad_728x90, .ad_300x250,
        .ad-300x250, .ad-728x90, .ad-responsive, .adsense, .ad-footer, .ad-header,
        .ad-mod, .ad-wrap, .ad-unit, .advertise, .advertising, .sponsored, .sponsor,
        .promo-box, .banner-ads, .banner-ad, .top-ad, .bottom-ad, .side-ad,
        #ads, #ad-banner, #ad-container, #ad-wrapper, #ad-slot, #advert,
        #advertisement, #ad-box, #ad-300x250, #ad-728x90, #adsense, #banner-ad,
        #top-ad, #bottom-ad, .ic-cpm, .ic-pm, .a-cpm, .a-pm, .popin, .popunder,
        .video-ads, .vjs-ad, .ad-overlay, [class*="adsbygoogle"],
        [class*="ad-slot"], [class*="advert"], [class*="ad-container"],
        [id*="adsense"], [id*="ad-slot"], iframe[src*="doubleclick"],
        iframe[src*="adservice"], iframe[src*="googlesyndication"],
        iframe[src*="adsterra"], iframe[src*="propellerads"], iframe[src*="taboola"],
        iframe[src*="outbrain"], iframe[src*="exoclick"], iframe[src*="mgid"],
        iframe[src*="advertising"], iframe[src*="adsystem"],
        img[src*="doubleclick"], img[src*="adservice"], img[src*="googlesyndication"],
        img[src*="adsterra"], img[src*="propellerads"], img[src*="exoclick"],
        /* in-house banner strips + gambling images on the site's own domain */
        img[src*="/banner/"], img[src*="/banner_"], img[src*="/banner-"],
        a[href*="/banner/"], a[href*="/banner_"], a[href*="/banner-"],
        img[src*="koko88"], img[src*="rusia777"], img[src*="kaiko"], img[src*="arab777"],
        img[src*="gaza88"], img[src*="judi89"], img[src*="ratu89"], img[src*="indo666"],
        img[src*="klikhoki"], img[src*="slot"], img[src*="togel"], img[src*="judi"],
        /* in-page gambling/slot ad blocks common on ID manga sites */
        [class*="gamble"], [class*="slot-online"], [class*="judi"], [class*="casino"],
        [class*="togel"], [id*="gamble"], [id*="slot-online"], [id*="judi"], [id*="casino"],
        [class*="banner-slot"], [class*="slot-banner"], [class*="game-slot"],
        .ads-here, .banner-iklan, .advert-banner, .iklan-banner, .box-iklan,
        [class*="situs-slot"], [class*="slot-online-terpercaya"],
        a[href*="koko88"], a[href*="penta"], a[href*="slot-online"], a[href*="togel"],
        a[href*="judi-online"], a[href*="casino-online"]
    """.trimIndent().replace(Regex("""\s+"""), " ")

    fun isBlocked(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val host = hostOf(url) ?: return false
        return hostSet.any { host == it || host.endsWith(".$it") }
    }

    /**
     * Detects in-house ad resources served from the site's own domain:
     * banner strips under /banner/ and images whose file name advertises
     * gambling/slot sites (koko88, rusia777, gaza88, judi89, ...).
     */
    fun isAdResource(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.contains("/banner/")) return true
        if (lower.contains("/banner_")) return true
        if (lower.contains("/banner-")) return true
        // Gambling/slot brand or keyword in file name (covers .gif/.png/.jpg)
        val fileName = lower.substringAfterLast('/')
        return AD_FILE_KEYWORDS.any { fileName.contains(it) }
    }

    private val AD_FILE_KEYWORDS = listOf(
        "koko88", "rusia777", "kaiko", "arab777", "gaza88", "indo666",
        "judi89", "ratu89", "klikhoki", "pentaslot", "super-scatter",
        "slot", "togel", "judi", "casino", "gamble", "jackpot", "scatter",
        "bandar", "situs-slot", "slot-online", "bolagila", "m88", "sbobet",
        "w88", "qqslot", "pragmatic", "pgsoft", "habanero", "spadegaming"
    )

    fun hostOf(url: String): String? {
        val u = url.substringAfter("://")
        val host = u.substringBefore('/').substringBefore(':')
        return host.lowercase().ifBlank { null }
    }

    /**
     * Removes ad elements from the DOM entirely (not just hides them), so no
     * leftover space and no clickable ad links remain. Re-runs after a short
     * delay to catch late-inserted ads.
     */
    fun removeAdsScript(): String = """
        (function(){
          if (window.__mgAdRemoveStarted) return;
          window.__mgAdRemoveStarted = true;

          var AD_IMG = /\/banner[\/_-]|koko88|rusia777|kaiko|arab777|gaza88|judi89|ratu89|indo666|klikhoki|penta|cina777|slot|togel|judi|casino|gamble|jackpot|scatter|bandar/i;
          var AD_HOST = /(doubleclick|googlesyndication|googleadservices|adsterra|propellerads|exoclick|taboola|outbrain|mgid|popads|trafficjunky|adcash|adbucks|onclickads|pushnative|linkvertise|ouo\.io|shorte\.st|adfly|adf\.ly)/i;

          function removeNode(el) {
            if (el && el.parentNode) el.parentNode.removeChild(el);
          }

          function clean() {
            // 1) Ad images: remove the wrapping <a> and the <img> itself.
            var imgs = document.querySelectorAll('img');
            for (var i = 0; i < imgs.length; i++) {
              var img = imgs[i];
              var src = img.currentSrc || img.src || img.getAttribute('data-src') || '';
              if (AD_IMG.test(src)) {
                var a = img.closest('a');
                if (a) removeNode(a); else removeNode(img);
              }
            }
            // 2) Links pointing to ad networks / gambling sites: remove them.
            var links = document.querySelectorAll('a[href]');
            for (var i = 0; i < links.length; i++) {
              var a = links[i];
              var href = a.href || '';
              if (AD_IMG.test(href) || AD_HOST.test(href)) removeNode(a);
            }
            // 3) Iframes from ad networks.
            var frames = document.querySelectorAll('iframe[src]');
            for (var i = 0; i < frames.length; i++) {
              var f = frames[i];
              if (AD_HOST.test(f.src)) removeNode(f);
            }
            // 4) Generic ad containers that survived.
            var generic = document.querySelectorAll(
              '.ad-banner, .ad-container, .ad-wrapper, .ad-slot, .adsbygoogle, ' +
              '.advertisement, .advert, .banner-ads, .banner-ad, .top-ad, .bottom-ad, ' +
              '.ads, .ads-here, .banner-iklan, .box-iklan, #ads, #ad-banner, #ad-slot, ' +
              '[class*="slot-online"], [class*="situs-slot"], [class*="gamble"], ' +
              '[class*="judi"], [class*="casino"], [class*="togel"]'
            );
            for (var i = 0; i < generic.length; i++) removeNode(generic[i]);
          }

          clean();
          // Catch ads injected after initial load.
          setTimeout(clean, 1500);
          setTimeout(clean, 4000);
        })();
    """.trimIndent()

    fun cssHideScript(): String {
        val style = CSS_HIDE
        return """
            (function(){
                if (window.__mgAdCss) return;
                var s = document.createElement('style');
                s.id = '__mgAdCss';
                s.textContent = '$style { display:none !important; }';
                (document.head || document.documentElement).appendChild(s);
                window.__mgAdCss = true;
            })();
        """.trimIndent()
    }
}
