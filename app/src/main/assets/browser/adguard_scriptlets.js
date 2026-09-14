/**
 * GhostGuard - AdGuard-Grade General Scriptlets
 * Defuses Popups, Pop-unders, Clickjacking Invisible Overlays,
 * Fake Click Triggers, Catfish Banners and Anti-AdBlock Traps on all websites.
 */
(function() {
    'use strict';
    if (window.__blockads_scriptlets_injected) return;
    window.__blockads_scriptlets_injected = true;

    // Immediate unblur & scroll unlock
    try {
        if (document.documentElement) document.documentElement.classList.remove('xh-thumb-disabled');
        if (document.body) {
            document.body.classList.remove('xh-scroll-disabled');
            document.body.style.position = 'static';
            document.body.style.overflow = 'auto';
        }
    } catch(e) {}

    // Protect React / Next.js / Vue SPAs from removeChild NotFoundError during client-side hydration
    try {
        var origRemoveChild = Node.prototype.removeChild;
        Node.prototype.removeChild = function(child) {
            return (child && child.parentNode !== this) ? child : origRemoveChild.apply(this, arguments);
        };
        var origInsertBefore = Node.prototype.insertBefore;
        Node.prototype.insertBefore = function(newNode, referenceNode) {
            return (referenceNode && referenceNode.parentNode !== this) ? this.appendChild(newNode) : origInsertBefore.apply(this, arguments);
        };
    } catch(e) {}

    // --- 0. ADGUARD CONSTANT DEFUSER (set-constant) ---
    try {
        Object.defineProperty(window, 'show_adx', { get: function() { return 0; }, set: function() {}, configurable: false });
        Object.defineProperty(window, 'codeAdx', { get: function() { return function() {}; }, set: function() {}, configurable: false });
        window.timeclick = Date.now() + 864000000;
        Object.defineProperty(window, 'qyuuby', { get: function() { return function() {}; }, set: function() {}, configurable: false });
        window.devtoolsDetector = { addListener: function() {}, launch: function() {}, stop: function() {} };

        // Anti-Adblock defusers (ACRP plugin on tech/mod apk sites like LeeAPK, InstaMod)
        window.__acrpGuard = true;
        window.acrpAdsAllowed = true;
        window.ezstandalone = { cmd: { push: function() {} }, define: function() {}, enable: function() {}, display: function() {}, init: function() {}, refresh: function() {} };
        try {
            document.cookie = "acrp_is_premium=1; path=/";
            if (location.hostname.indexOf('instamod.app') !== -1) {
                document.cookie = "_DL=1; path=/; max-age=864000";
                if (document.body) document.body.classList.add('onDL');
            }
        } catch(e) {}
    } catch(e) {}

    // --- 1. POPUP & POPUNDER DEFUSER ---
    var originalOpen = window.open;
    var lastUserInteractionTime = 0;

    function recordUserInteraction() {
        lastUserInteractionTime = Date.now();
    }

    window.addEventListener('click', function(e) {
        recordUserInteraction();
        var btnDl = e.target && e.target.closest ? e.target.closest('.app_u > .a, .app_u > .xMirror') : null;
        if (btnDl) {
            e.stopImmediatePropagation(); e.stopPropagation();
            document.body.classList.add('onDL');
            var dl = document.querySelector('.app_l');
            if (dl) {
                var dAll = dl.querySelectorAll('details');
                for (var dIdx = 0; dIdx < dAll.length; dIdx++) dAll[dIdx].open = true;
                dl.scrollIntoView({ behavior: 'smooth' });
            }
            return;
        }
        var a = e.target && e.target.closest ? e.target.closest('a') : null;
        if (a) {
            var raw = a.getAttribute('href') || a.href || '';
            var isDl = isDownloadAnchor(a) || raw.indexOf('mediafire.com') !== -1 || raw.indexOf('freeup.io') !== -1 || raw.indexOf('google.com') !== -1 || raw.indexOf(',') !== -1;
            if (isDl) {
                var clean = cleanChainedUrl(raw);
                a.setAttribute('href', clean); a.href = clean;
                if (clean.indexOf('http') === 0 && clean.indexOf('instamod.app') === -1) {
                    e.stopImmediatePropagation(); e.stopPropagation();
                    window.location.href = clean;
                    return;
                }
            }
        }
    }, true);
    window.addEventListener('touchend', recordUserInteraction, true);
    window.addEventListener('keydown', recordUserInteraction, true);

    var BLOCKED_PATTERNS = [
        'shopee://', 'lazada://', 'tiki://', 'snssdk://', 'snssdk1128://', 'tiktok://', 'musically://',
        'affiliate', 'popads', 'popcash', 'propeller', 'adsterra', 'clickadu', 'exoclick', 'exosrv',
        'doubleclick', 'adnxs', 'mgid', 'taboola', 'adxcontent', 'adxmedia', 'vlit', 'catfish',
        'popunder', 'clumsy-whereas', 'bytedapm.com', 'a-ads.com', 'invl.me', 'involve.asia',
        'lu88', 'hbet', 'vu88', 'man88', 'k88.', 'tx88', 'du88', 'x1bet', 'bet88', 'kubet', 'shbet',
        '789bet', 'okvip', 'jun88', 'hi88', 'f8bet', 'mb66', '123b', 'fun88', 'bk8', 'rikvip', 'cm88',
        'bc.game', 'gamebaidoithuong', 'taixiu', 'baccarat', 'offerflowtogo',
        'campfirecroutondecorator', 'beholdjarhypnotize', 'gigglegrowlworrisome', 'portalfluently',
        'thedirecthor', 'vivodemisrentas', 'bionomysolera', 'bundlemoviepumice', 'fagoklaer', 'gahakoleir',
        'atoptions', 'fantastindents', 'excidekombu', 'cleverwebserver', 'adsboosters',
        '92mim', 'tzegilo', 'vr-gc', 'dd133', 'becorsolaom', 'apps2app', 'vignette'
    ];

    function isAdOrMaliciousUrl(url) {
        if (!url || typeof url !== 'string') return false;
        var lower = url.toLowerCase();
        for (var i = 0; i < BLOCKED_PATTERNS.length; i++) {
            if (lower.indexOf(BLOCKED_PATTERNS[i]) !== -1) return true;
        }
        return false;
    }

    function cleanChainedUrl(rawUrl) {
        if (!rawUrl || typeof rawUrl !== 'string') return rawUrl;
        if (rawUrl.indexOf(',') === -1) return rawUrl;
        var parts = rawUrl.split(',');
        for (var i = parts.length - 1; i >= 0; i--) {
            var p = parts[i].trim();
            if (!p.startsWith('http')) continue;
            if (p.indexOf('mediafire') !== -1 || p.indexOf('drive.google') !== -1 || p.indexOf('drive.usercontent') !== -1 || p.indexOf('freeup') !== -1 || p.indexOf('mega.nz') !== -1) {
                return p;
            }
        }
        for (var j = parts.length - 1; j >= 0; j--) {
            var u = parts[j].trim();
            if (u.startsWith('http') && !isAdOrMaliciousUrl(u)) {
                return u;
            }
        }
        return parts[parts.length - 1].trim();
    }

    // Dummy Window proxy to satisfy callers expecting a Window object
    function createDummyWindow() {
        var dummyLocation = {
            _href: 'about:blank',
            get href() { return this._href; },
            set href(val) { console.info('[GhostGuard] Blocked popup redirect to:', val); },
            replace: function() {},
            assign: function() {}
        };
        return {
            closed: false,
            focus: function() {},
            blur: function() {},
            close: function() {},
            postMessage: function() {},
            location: dummyLocation,
            document: { write: function() {}, close: function() {} }
        };
    }

    window.open = function(url, target, features) {
        var isRecentUserAction = (Date.now() - lastUserInteractionTime) < 1200;

        // Block if not explicit recent human tap OR points to ad/gambling URL
        if (!isRecentUserAction || isAdOrMaliciousUrl(url)) {
            console.info('[GhostGuard] Prevented suspicious popup:', url);
            return createDummyWindow();
        }

        return originalOpen.apply(this, arguments);
    };

    // --- 2. FAKE CLICK & CLICKJACKING DEFUSER (AdGuard prevent-popunder) ---
    var origDispatch = EventTarget.prototype.dispatchEvent;
    EventTarget.prototype.dispatchEvent = function(event) {
        if (event && (event.type === 'click' || event.type === 'mousedown')) {
            if (this instanceof HTMLAnchorElement) {
                if (isAdOrMaliciousUrl(this.href) || (this.id && this.id.indexOf('bb') === 0)) {
                    console.info('[GhostGuard] Blocked synthetic click on ad link:', this.href);
                    return false;
                }
            }
        }
        return origDispatch.apply(this, arguments);
    };

    function isDownloadAnchor(el) {
        if (!el) return false;
        var href = (el.getAttribute('href') || el.href || '').toLowerCase();
        if (el.hasAttribute && el.hasAttribute('download')) return true;
        if (/\.(apk|xapk|zip|rar|7z|tar|gz|pdf|mp3|mp4|bin|iso)(\?.*)?$/i.test(href)) return true;
        if (href.indexOf('d.apkpure.com') !== -1 || href.indexOf('winudf.com') !== -1 || href.indexOf('/download/') !== -1) return true;
        if (href.indexOf('mediafire.com') !== -1 || href.indexOf('drive.google.com') !== -1 || href.indexOf('drive.usercontent.google.com') !== -1 || href.indexOf('freeup.io') !== -1 || href.indexOf('mega.nz') !== -1) return true;
        var cls = (el.className || '').toString().toLowerCase();
        if (cls.indexOf('download') !== -1) return true;
        var txt = (el.innerText || el.textContent || '').toLowerCase();
        if (txt.indexOf('download') !== -1 || txt.indexOf('tải') !== -1) return true;
        return false;
    }

    var origAnchorClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function() {
        var isRecentUserAction = (Date.now() - lastUserInteractionTime) < 5000;
        var isDownload = isDownloadAnchor(this);

        // Same-origin navigation or internal SPA routing must NEVER be blocked as popunders
        var isSameOrigin = false;
        try {
            if (this.origin === location.origin || this.host === location.host || !this.host || (location.hostname.indexOf("youtube.com") !== -1 && (this.href || "").indexOf("youtube.com") !== -1)) {
                isSameOrigin = true;
            }
        } catch(e) {}
        if (isSameOrigin && !isAdOrMaliciousUrl(this.href)) return origAnchorClick.apply(this, arguments);

        // Check for hidden popunder triggers (e.g. <a id="bb0" style="opacity:0; width:1px">)
        var isHiddenPopunder = false;
        try {
            var s = window.getComputedStyle(this);
            var rect = this.getBoundingClientRect();
            if (s.opacity === '0' || s.display === 'none' || s.visibility === 'hidden' || (rect.width <= 5 && rect.height <= 5)) {
                isHiddenPopunder = true;
            }
        } catch(e) {}

        if (this.id && this.id.indexOf('bb') === 0) isHiddenPopunder = true;

        // Legitimate download links must never be blocked as popunders
        if (isDownload && !isAdOrMaliciousUrl(this.href) && !isHiddenPopunder) {
            console.info('[GhostGuard] Allowing download click:', this.href);
            return origAnchorClick.apply(this, arguments);
        }

        if (!isRecentUserAction || isHiddenPopunder || isAdOrMaliciousUrl(this.href)) {
            console.info('[GhostGuard] Blocked programmatic anchor popunder click:', this.href);
            return;
        }
        return origAnchorClick.apply(this, arguments);
    };

    // --- 3. INVISIBLE OVERLAY & CATFISH BANNER PURGE ---
    function purgeAdArtifacts() {
        try {
            // Clean chained links first & auto-expand details on instamod.app
            var chained = document.querySelectorAll('a[href*=","]');
            for (var ca = 0; ca < chained.length; ca++) {
                var cH = cleanChainedUrl(chained[ca].getAttribute('href') || '');
                chained[ca].setAttribute('href', cH);
                chained[ca].href = cH;
            }
            if (location.hostname.indexOf('instamod.app') !== -1) {
                if (document.body && !document.body.classList.contains('onDL')) document.body.classList.add('onDL');
                var det = document.querySelectorAll('.app_l details:not([open])');
                for (var di = 0; di < det.length; di++) det[di].open = true;
            }

            // A. Remove known catfish banners & popup containers
            var adSelectors = [
                '.catfish-top, .catfish-bottom, .banner-catfish-top, .banner-catfish-bottom',
                '.banner-preload, .banner-preload-container, .banner-preload-close, a[id^="bb"]',
                '#vl-top-adx, #vl-native-adx, #vl-underplayer-adx, #adx',
                '.▶, .▶__wrap, .▶__iframe, [class*="▶"], iframe[src*="clumsy-whereas"]',
                '.section_ads_300x250, .section_ads, .banner_mobile_300x250, #banner_top, #TOP_BANNER, div[id^="sis_"]',
                '#bottom-slider, .apkm-timed-slider, .ains, [class*="ains-"], .advertisement-text, [id*="ai_widget"]',
                '.js-ad-slot, .ad-adsense, [data-dt-ga-name*="resp_download_"], .share-open, .download-vip-subscribe-wrap',
                '#acrp-sticky-wrap, #acrp-sticky-inner, .acrp-sticky-close, .acrp-ad-box-1, [class*="acrp-ad"]',
                '#random-ad, [id*="random-ad"], iframe[src*="a-ads.com"], [id^="__clb-spot"], .banners-all, .my_banner',
                'aside.ezoic-ad-slot, section[aria-label="Sponsored offers"], div[role="dialog"][aria-label="Sponsored offers"]'
            ];
            var adEls = document.querySelectorAll(adSelectors.join(','));
            for (var i = 0; i < adEls.length; i++) {
                var el = adEls[i];
                el.style.setProperty('display', 'none', 'important');
                el.style.setProperty('pointer-events', 'none', 'important');
                el.style.setProperty('height', '0px', 'important');
                el.style.setProperty('min-height', '0px', 'important');
            }

            // B. Hide any anchors pointing to gambling or ad networks (preserve download links)
            var links = document.querySelectorAll('a[href]');
            for (var j = 0; j < links.length; j++) {
                var link = links[j];
                var rawH = link.getAttribute('href') || link.href || '';
                if (isDownloadAnchor(link) || rawH.indexOf('mediafire') !== -1 || rawH.indexOf('google') !== -1 || rawH.indexOf('freeup') !== -1) {
                    if (link.style.display === 'none') {
                        link.style.removeProperty('display');
                        link.style.removeProperty('pointer-events');
                    }
                    continue;
                }
                if (isAdOrMaliciousUrl(link.href)) {
                    var parent = link.closest('.catfish-top, .catfish-bottom, .banner-preload, [class*="catfish"], [class*="banner-ad"], [class*="banner-catfish"], [class*="banner-preload"], [class*="ads-banner"], .floating-banner');
                    if (parent) {
                        parent.style.setProperty('display', 'none', 'important');
                        parent.style.setProperty('pointer-events', 'none', 'important');
                    } else {
                        link.style.setProperty('display', 'none', 'important');
                        link.style.setProperty('pointer-events', 'none', 'important');
                    }
                }
            }

            // C. Scan full-viewport invisible overlays (Clickjacking traps)
            var screenW = window.innerWidth || document.documentElement.clientWidth || 360;
            var screenH = window.innerHeight || document.documentElement.clientHeight || 640;
            var minArea = screenW * screenH * 0.4;

            var candidates = document.querySelectorAll('a, div, span, section');
            for (var k = 0; k < candidates.length; k++) {
                var c = candidates[k];
                if (c.id === '__blockads_pip_box' || c.querySelector('[role="dialog"]') || c.getAttribute('role') === 'dialog') continue;

                var style = window.getComputedStyle(c);
                if (style.position === 'fixed' || style.position === 'absolute') {
                    var zIndex = parseInt(style.zIndex, 10);
                    var opacity = parseFloat(style.opacity);

                    if (zIndex >= 50 && (opacity <= 0.1 || style.visibility === 'hidden' || style.backgroundColor === 'rgba(0, 0, 0, 0)')) {
                        var r = c.getBoundingClientRect();
                        if ((r.width * r.height) >= minArea) {
                            c.style.setProperty('pointer-events', 'none', 'important');
                            c.style.setProperty('display', 'none', 'important');
                            console.info('[GhostGuard] Defused clickjacking overlay');
                        }
                    }
                }
            }

            // D. Remove Anti-Adblock popup modals only (never touch navigation menus/drawers)
            var dialogs = document.querySelectorAll('[role="dialog"], [role="alertdialog"]');
            for (var m = 0; m < dialogs.length; m++) {
                var dlg = dialogs[m];
                var txt = (dlg.innerText || '');
                if (txt.indexOf('Ad Blocker') > -1 || txt.indexOf('ad blocker') > -1 || txt.indexOf('Adblock') > -1) {
                    var parentModal = dlg.closest('[tabindex="-1"]') || dlg.parentElement;
                    if (parentModal) {
                        parentModal.style.setProperty('display', 'none', 'important');
                        parentModal.style.setProperty('pointer-events', 'none', 'important');
                    }
                    var modalBackdrops = document.querySelectorAll('.z-50.backdrop-blur-md.bg-black\\/80, .z-50.backdrop-blur-md.bg-black\\/70');
                    for (var b = 0; b < modalBackdrops.length; b++) {
                        modalBackdrops[b].style.setProperty('display', 'none', 'important');
                    }
                    document.documentElement.style.overflow = 'auto';
                    document.body.style.overflow = 'auto';
                    var hiddenNodes = document.querySelectorAll('[aria-hidden="true"]');
                    for (var h = 0; h < hiddenNodes.length; h++) {
                        if (hiddenNodes[h].getAttribute('data-overlay-container') === 'true') {
                            hiddenNodes[h].removeAttribute('aria-hidden');
                        }
                    }
                }
            }

            // Also remove shadow-root anti-adblock hosts & restore overflow (e.g. ACRP guard)
            var acrpHolster = document.querySelectorAll("[data-n^='s']");
            for (var aH = 0; aH < acrpHolster.length; aH++) {
                acrpHolster[aH].remove();
                if (document.documentElement) document.documentElement.style.overflow = 'auto';
                if (document.body) document.body.style.overflow = 'auto';
            }

            // E. Remove forced page blur & locked scroll (xHamster, age verification gates)
            if (document.documentElement && document.documentElement.classList.contains('xh-thumb-disabled')) {
                document.documentElement.classList.remove('xh-thumb-disabled');
            }
            if (document.body && document.body.classList.contains('xh-scroll-disabled')) {
                document.body.classList.remove('xh-scroll-disabled');
                document.body.style.position = 'static';
                document.body.style.overflow = 'auto';
            }
            var blurredWraps = document.querySelectorAll('.main-wrap[style*="blur"]');
            for (var bw = 0; bw < blurredWraps.length; bw++) {
                blurredWraps[bw].style.filter = 'none';
            }
            var cookieModals = document.querySelectorAll('[data-role="cookies-modal"], [data-role="dialog-manager"]');
            for (var cm = 0; cm < cookieModals.length; cm++) {
                cookieModals[cm].style.setProperty('display', 'none', 'important');
            }
            var adWidgets = document.querySelectorAll('.thumb-list-mobile-item--widget, [class*="thumb-list-mobile-item--widget"], [data-role="promo-messages-wrapper"]');
            for (var aw = 0; aw < adWidgets.length; aw++) {
                adWidgets[aw].style.setProperty('display', 'none', 'important');
            }

            // F. Hide streaming gambling popups & auto-skip video ads
            var motphimAds = document.querySelectorAll('div.fixed.inset-0.z-\\[9999\\], div[class*="fixed"][class*="inset-0"]:has(button), div[class*="fixed"]:has(img[src*="offa"]), div:has(> a.no-ads-under)');
            for (var ma = 0; ma < motphimAds.length; ma++) {
                motphimAds[ma].style.setProperty('display', 'none', 'important');
                motphimAds[ma].style.setProperty('pointer-events', 'none', 'important');
            }
            var skipBtn = document.querySelector('.jw-skip, .videoAdUiSkipButton, .ytp-ad-skip-button, .ytp-skip-ad-button');
            if (skipBtn) {
                try { skipBtn.click(); } catch(e) {}
            }
            if (window.jwplayer && typeof window.jwplayer === 'function') {
                try {
                    var jp = window.jwplayer();
                    if (jp && typeof jp.skipAd === 'function') {
                        jp.skipAd();
                    }
                } catch(e) {}
            }
            // G. Anti-adblock siteNotice & ad popups
            var siteNotice = document.getElementById('siteNotice');
            if (siteNotice) {
                siteNotice.remove();
                if (document.body && document.body.style.overflow === 'hidden') document.body.style.overflow = '';
                if (document.documentElement && document.documentElement.style.overflow === 'hidden') document.documentElement.style.overflow = '';
            }
            var adPopup = document.getElementById('ad-popup');
            if (adPopup) {
                adPopup.remove();
            }
            // H. Kill full-screen iframe overlays
            var tIframes = document.querySelectorAll('html > iframe, body > iframe[style*="fixed"], iframe[style*="2147483647"], div[style*="2147483647"]');
            for (var ti = 0; ti < tIframes.length; ti++) {
                tIframes[ti].remove();
            }
        } catch(e) {}
    }

    // Run purge immediately, repeatedly, and on DOM mutation
    purgeAdArtifacts();
    setInterval(purgeAdArtifacts, 600);

    if (document.body) {
        new MutationObserver(purgeAdArtifacts).observe(document.body, { childList: true, subtree: true });
    }

    // --- 4. ANTI-ADBLOCK DEFUSER & SPOOFING ---
    try {
        window.canRunAds = true; window.isAdBlockActive = false; window.adsBlocked = false;
        window.google_ad_status = 1; window.abp = false;
        window.adsbygoogle = window.adsbygoogle || [];
        window.adsbygoogle.loaded = true; window.adsbygoogle.push = function() {};
        window.showAdblockMessage = function() {};
        window.aclib = window.aclib || { runPop: function() {}, runInPagePush: function() {}, runAutoTag: function() {}, runBanner: function() {} };
        try { Object.defineProperty(window, 'AcrpConfig', { get: function() { return undefined; }, set: function() {}, configurable: true }); } catch(e) {}

        // Defeat getComputedStyle & offsetHeight inspection on bait elements
        try {
            var origGetComputedStyle = window.getComputedStyle;
            window.getComputedStyle = function(el, pseudo) {
                var style = origGetComputedStyle.apply(this, arguments);
                if (el && el.className && typeof el.className === 'string' && (el.className.indexOf('adsbox') !== -1 || el.className.indexOf('pub_300x250') !== -1 || el.className.indexOf('ad-placement') !== -1 || el.className.indexOf('banner_ad') !== -1)) {
                    return new Proxy(style, {
                        get: function(target, prop) {
                            if (prop === 'display') return 'block';
                            if (prop === 'visibility') return 'visible';
                            return typeof target[prop] === 'function' ? target[prop].bind(target) : target[prop];
                        }
                    });
                }
                return style;
            };

            var origOffsetHeight = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'offsetHeight');
            if (origOffsetHeight && origOffsetHeight.get) {
                Object.defineProperty(HTMLElement.prototype, 'offsetHeight', {
                    get: function() {
                        if (this.className && typeof this.className === 'string' && (this.className.indexOf('adsbox') !== -1 || this.className.indexOf('pub_300x250') !== -1 || this.className.indexOf('banner_ad') !== -1)) {
                            return 250;
                        }
                        return origOffsetHeight.get.apply(this);
                    },
                    configurable: true
                });
            }
        } catch(e) {}

        // Anti-Adblock Bait Unhide (defeat geometry detection on #banner_ad, .pub_300x250, etc.)
        try {
            var baitStyle = document.createElement('style');
            baitStyle.textContent = '#banner_ad, div#banner_ad, .pub_300x250, .adsbox, .adunit, .ad-zone, .ad-space { display: block !important; visibility: visible !important; width: 300px !important; min-width: 300px !important; max-width: 300px !important; height: 250px !important; min-height: 250px !important; max-height: 250px !important; left: -9999px !important; position: absolute !important; }';
            (document.head || document.documentElement).appendChild(baitStyle);
        } catch(e) {}

        if (!window.ga) {
            window.ga = function() {};
            window.ga.loaded = true;
        }

        if (!window.googletag) {
            window.googletag = {
                cmd: [],
                display: function() {},
                openConsole: function() {},
                enableServices: function() {},
                pubads: function() {
                    return { addEventListener: function() {}, clear: function() {}, collapseEmptyDivs: function() {}, disableInitialLoad: function() {}, enableSingleRequest: function() {}, refresh: function() {} };
                }
            };
        }
    } catch(e) {}

    // --- 5. BLOCK NAVIGATION HIJACKING & UNLOAD TRAPS ---
    window.addEventListener('beforeunload', function(e) {
        e.stopImmediatePropagation();
    }, true);

})();
