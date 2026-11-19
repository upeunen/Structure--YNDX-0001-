package ru.yandex.station.remote.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val RUTRACKER_COLLECTIONS_URL = "https://rutracker.net/forum/index.php?c=2"

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    viewModel: RemoteViewModel,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var currentTitle by remember { mutableStateOf("Подборки RuTracker") }
    var fullPageTitle by remember { mutableStateOf("") }

    // Перехват системной кнопки «Назад» для навигации внутри RuTracker
    BackHandler {
        val wv = webViewRef
        if (wv != null && wv.canGoBack()) {
            wv.goBack()
        } else {
            onBack()
        }
    }

    fun downloadAndPlayTorrent(torrentUrl: String, referer: String?, userAgent: String?) {
        scope.launch {
            viewModel.showMessage("Получение торрента с RuTracker...")
            val result = withContext(Dispatchers.IO) {
                try {
                    var currentUrl = torrentUrl
                    var currentReferer = referer
                    var responseCode = 0
                    var contentType = ""
                    var bytes = ByteArray(0)

                    for (step in 0..5) {
                        val cookies = CookieManager.getInstance().getCookie(currentUrl)
                        val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 10000
                            readTimeout = 15000
                            instanceFollowRedirects = false
                            if (!cookies.isNullOrBlank()) setRequestProperty("Cookie", cookies)
                            if (!currentReferer.isNullOrBlank()) setRequestProperty("Referer", currentReferer)
                            if (!userAgent.isNullOrBlank()) setRequestProperty("User-Agent", userAgent)
                        }
                        responseCode = conn.responseCode
                        if (responseCode in 300..399) {
                            val location = conn.getHeaderField("Location")
                            conn.disconnect()
                            if (location.isNullOrBlank()) break
                            currentReferer = currentUrl
                            currentUrl = URL(URL(currentUrl), location).toString()
                            continue
                        }
                        contentType = conn.contentType ?: ""
                        bytes = conn.inputStream.use { it.readBytes() }
                        conn.disconnect()
                        break
                    }

                    if (responseCode !in 200..299 || bytes.isEmpty()) {
                        Result.failure(Exception("Ошибка загрузки с RuTracker (код $responseCode)"))
                    } else if (contentType.contains("text/html", ignoreCase = true) ||
                        bytes.firstOrNull() != 'd'.code.toByte()
                    ) {
                        Result.failure(Exception("Войдите в аккаунт RuTracker на странице, чтобы запускать раздачи"))
                    } else {
                        Result.success(bytes)
                    }
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            result.onSuccess { bytes ->
                val topicId = Regex("[?&]t=(\\d+)").find(torrentUrl)?.groupValues?.get(1)
                    ?: System.currentTimeMillis().toString()
                val safeTitle = fullPageTitle
                    .replace(Regex("[\\\\/:*?\"<>|]"), " ")
                    .trim()
                    .take(80)
                val finalFileName = if (safeTitle.length >= 3 && !safeTitle.contains("RuTracker", ignoreCase = true)) {
                    "$safeTitle.torrent"
                } else {
                    "rutracker_$topicId.torrent"
                }
                viewModel.uploadTorrentFileAndPlay(bytes, finalFileName)
            }.onFailure { err ->
                viewModel.showMessage(err.message ?: "Не удалось загрузить раздачу")
            }
        }
    }

    Scaffold(
        containerColor = OledBlack,
        topBar = {
            Column {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = {
                            val wv = webViewRef
                            if (wv != null && wv.canGoBack()) {
                                wv.goBack()
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Назад",
                                tint = TextPrimary
                            )
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = currentTitle,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1
                            )
                            Text(
                                text = "rutracker.net • Нажмите для запуска на ТВ",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                maxLines = 1
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            viewModel.saveLastRutrackerUrl(RUTRACKER_COLLECTIONS_URL)
                            webViewRef?.loadUrl(RUTRACKER_COLLECTIONS_URL)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = "Раздел кино",
                                tint = AccentYandex
                            )
                        }
                        IconButton(onClick = { webViewRef?.reload() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Обновить",
                                tint = TextPrimary
                            )
                        }
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Закрыть",
                                tint = TextSecondary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkSurface
                    )
                )
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { (progress.coerceIn(5, 100)) / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = AccentYandex,
                        trackColor = DarkSurface
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(android.graphics.Color.BLACK)
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        addJavascriptInterface(
                            object {
                                @android.webkit.JavascriptInterface
                                fun bookmark(url: String, title: String) {
                                    viewModel.addToWatchLaterFromBrowser(url, title)
                                }

                                @android.webkit.JavascriptInterface
                                fun saveTopicToCatalog(topicId: String, title: String, posterUrl: String, overview: String, year: String, category: String) {
                                    viewModel.saveRuTrackerTopicToShowcase(topicId, title, posterUrl, overview, year, category)
                                }
                            },
                            "PultBridge"
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            loadsImagesAutomatically = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            builtInZoomControls = true
                            displayZoomControls = false
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                                isLoading = newProgress < 100
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                if (!title.isNullOrBlank()) {
                                    val clean = title.substringBefore(" :: ").trim()
                                    fullPageTitle = clean
                                    currentTitle = clean.take(32)
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val targetUrl = request?.url?.toString() ?: return false
                                return when {
                                    targetUrl.startsWith("magnet:", ignoreCase = true) -> {
                                        viewModel.playMagnetUrl(targetUrl)
                                        true
                                    }
                                    targetUrl.contains("dl.php", ignoreCase = true) ||
                                        targetUrl.endsWith(".torrent", ignoreCase = true) -> {
                                        downloadAndPlayTorrent(
                                            torrentUrl = targetUrl,
                                            referer = view?.url,
                                            userAgent = view?.settings?.userAgentString
                                        )
                                        true
                                    }
                                    else -> false
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                CookieManager.getInstance().flush()
                                url?.let { currentUrl ->
                                    if (currentUrl.startsWith("http", ignoreCase = true) && !currentUrl.contains("dl.php", ignoreCase = true)) {
                                        val cookies = CookieManager.getInstance().getCookie(currentUrl) ?: ""
                                        val ua = view?.settings?.userAgentString ?: ""
                                        viewModel.onRuTrackerPageVisited(currentUrl, cookies, ua)
                                    }
                                }
                                val js = """
                                    (function() {
                                        var links = document.querySelectorAll('a.dl-link, a[href*="dl.php"], a.magnet-link, a[href^="magnet:"]');
                                        for (var i = 0; i < links.length; i++) {
                                            var a = links[i];
                                            if (a.dataset.tvPatched) continue;
                                            a.dataset.tvPatched = '1';
                                            a.textContent = '▶ Запустить на ТВ';
                                            a.style.background = '#FFCC00';
                                            a.style.color = '#000000';
                                            a.style.fontWeight = 'bold';
                                            a.style.padding = '6px 12px';
                                            a.style.borderRadius = '8px';
                                            a.style.display = 'inline-block';
                                            a.style.textDecoration = 'none';
                                            a.style.margin = '4px 0';
                                            a.style.userSelect = 'none';

                                            (function(elem) {
                                                var pressTimer = null;
                                                var isLong = false;

                                                function startTimer() {
                                                    isLong = false;
                                                    pressTimer = setTimeout(function() {
                                                        isLong = true;
                                                        if (window.PultBridge) {
                                                            window.PultBridge.bookmark(elem.href, document.title);
                                                        }
                                                        var origBg = elem.style.background;
                                                        var origText = elem.textContent;
                                                        elem.textContent = '📌 В «Смотреть позже»!';
                                                        elem.style.background = '#4CAF50';
                                                        elem.style.color = '#FFFFFF';
                                                        setTimeout(function() {
                                                            elem.textContent = origText;
                                                            elem.style.background = origBg;
                                                            elem.style.color = '#000000';
                                                        }, 2000);
                                                    }, 500);
                                                }

                                                function clearTimer() {
                                                    if (pressTimer) {
                                                        clearTimeout(pressTimer);
                                                        pressTimer = null;
                                                    }
                                                }

                                                elem.addEventListener('touchstart', startTimer, {passive: true});
                                                elem.addEventListener('touchend', function(e) {
                                                    if (isLong) {
                                                        e.preventDefault();
                                                        e.stopPropagation();
                                                    }
                                                    clearTimer();
                                                });
                                                elem.addEventListener('touchmove', clearTimer, {passive: true});
                                                elem.addEventListener('touchcancel', clearTimer, {passive: true});
                                                elem.addEventListener('contextmenu', function(e) {
                                                    e.preventDefault();
                                                    if (window.PultBridge) {
                                                        window.PultBridge.bookmark(elem.href, document.title);
                                                    }
                                                });
                                            })(a);
                                        }

                                        // Тихий сбор данных раздачи в каталог находок
                                        try {
                                            if (window.location.href.indexOf('viewtopic.php') !== -1) {
                                                var titleEl = document.querySelector('#topic-title, .maintitle, h1');
                                                var rawTitle = titleEl ? titleEl.textContent.trim() : document.title;
                                                var firstPost = document.querySelector('.post_body, .message');
                                                var posterImg = firstPost ? firstPost.querySelector('var.postImg, img.postImg, img') : null;
                                                var pUrl = '';
                                                if (posterImg) {
                                                    pUrl = posterImg.getAttribute('title') || posterImg.src || '';
                                                }
                                                var descText = firstPost ? firstPost.innerText.substring(0, 450).trim() : '';
                                                var ym = rawTitle.match(/\b(19\d\d|20\d\d)\b/);
                                                var y = ym ? ym[1] : '';
                                                var tm = window.location.href.match(/[?&]t=(\d+)/);
                                                var tid = tm ? tm[1] : '';
                                                if (window.PultBridge && window.PultBridge.saveTopicToCatalog && tid && rawTitle) {
                                                    window.PultBridge.saveTopicToCatalog(tid, rawTitle, pUrl, descText, y, 'RuTracker');
                                                }
                                            } else if (window.location.href.indexOf('tracker.php') !== -1 || window.location.href.indexOf('viewforum.php') !== -1) {
                                                // Сбор тем со страницы поиска / списка раздач форума
                                                var topicLinks = document.querySelectorAll('a.tt-text, a[href*="viewtopic.php?t="]');
                                                for (var j = 0; j < Math.min(topicLinks.length, 12); j++) {
                                                    var tl = topicLinks[j];
                                                    var href = tl.getAttribute('href') || '';
                                                    var tm2 = href.match(/[?&]t=(\d+)/);
                                                    if (tm2 && tl.textContent) {
                                                        var tid2 = tm2[1];
                                                        var title2 = tl.textContent.trim();
                                                        if (window.PultBridge && window.PultBridge.saveTopicToCatalog && tid2 && title2) {
                                                            window.PultBridge.saveTopicToCatalog(tid2, title2, '', '', '', 'RuTracker');
                                                        }
                                                    }
                                                }
                                            }
                                        } catch (e) {}
                                    })();
                                """.trimIndent()
                                view?.evaluateJavascript(js, null)
                            }
                        }

                        setDownloadListener { url, userAgent, _, mimeType, _ ->
                            if (url.startsWith("magnet:", ignoreCase = true)) {
                                viewModel.playMagnetUrl(url)
                            } else if (url.contains("dl.php", ignoreCase = true) ||
                                url.endsWith(".torrent", ignoreCase = true) ||
                                mimeType.contains("bittorrent", ignoreCase = true)
                            ) {
                                downloadAndPlayTorrent(
                                    torrentUrl = url,
                                    referer = this.url,
                                    userAgent = userAgent
                                )
                            }
                        }

                        val startUrl = viewModel.getLastRutrackerUrl().ifEmpty { RUTRACKER_COLLECTIONS_URL }
                        loadUrl(startUrl)
                        webViewRef = this
                    }
                }
            )
        }
    }
}
