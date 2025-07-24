package com.joel.animepp

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.joel.animepp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fullscreenContainer: FrameLayout? = null

    // ✅ Whitelisted domains
    private val allowedDomains = listOf(
        "hianime.to",
        "hianimes.ro",
        "vidstream.pro",
        "mcloud.to",
        "static.hianime.to",
        "gogohd.net"
    )

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        swipeRefresh = binding.swipeRefreshLayout

        Toast.makeText(this, "HelloAnime — Powered by HiAnime", Toast.LENGTH_LONG).show()

        val webSettings = binding.webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.allowFileAccess = true
        webSettings.mediaPlaybackRequiresUserGesture = false
        webSettings.useWideViewPort = true
        webSettings.loadWithOverviewMode = true
        webSettings.setSupportZoom(true)
        webSettings.builtInZoomControls = true
        webSettings.displayZoomControls = false
        webSettings.javaScriptCanOpenWindowsAutomatically = true
        webSettings.setSupportMultipleWindows(true)

        // ✅ WebClient — balanced handling
        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val host = Uri.parse(url).host ?: return true

                // ✅ Allow internal or video/CDN/download links
                return if (
                    allowedDomains.any { host.contains(it) } ||
                    url.endsWith(".mp4") || url.endsWith(".m3u8") ||
                    url.startsWith("blob:") || url.contains("download") ||
                    url.contains("stream") || url.contains("video")
                ) {
                    false
                } else {
                    Toast.makeText(this@MainActivity, "Blocked external link", Toast.LENGTH_SHORT).show()
                    true
                }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    Log.e("WebViewError", "Main frame error: ${error.description}")
                    view.loadData(
                        "<html><body><h2>AN ERROR OCCUERED PLEASE TRY AGAIN</h2></body></html>",
                        "text/html",
                        "UTF-8"
                    )
                } else {
                    // Background request like video segment — ignore
                    Log.w("WebViewWarning", "Non-main-frame error: ${error.description}")
                }
            }


            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                swipeRefresh.isRefreshing = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                swipeRefresh.isRefreshing = false
            }
        }

        // ✅ WebChromeClient — Fullscreen video
        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                customView = view
                customViewCallback = callback
                fullscreenContainer = FrameLayout(this@MainActivity).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    addView(view)
                }
                setContentView(fullscreenContainer)
                binding.webView.visibility = View.GONE
            }

            override fun onHideCustomView() {
                fullscreenContainer?.removeView(customView)
                customViewCallback?.onCustomViewHidden()
                setContentView(binding.root)
                binding.webView.visibility = View.VISIBLE
            }
        }

        // ✅ Load main site
        binding.webView.loadUrl("https://hianimes.ro/")

        // ✅ Pull-to-refresh
        swipeRefresh.setOnRefreshListener {
            binding.webView.reload()
        }
    }

    // ✅ Enter PiP if video is playing
    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && customView != null) {
            val pipParams = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(pipParams)
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        supportActionBar?.hide()
    }

    override fun onBackPressed() {
        if (customView != null) {
            (binding.webView.webChromeClient as? WebChromeClient)?.onHideCustomView()
        } else if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
