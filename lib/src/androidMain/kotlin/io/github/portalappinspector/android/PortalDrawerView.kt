package io.github.portalappinspector.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class PortalDrawerView(
    context: Context,
    desktopPortalUrl: String?,
    localPortalUrl: String?,
    onSwipeBack: () -> Unit,
) : FrameLayout(context) {
    val springMotion = SpringyViewMotion(this)
    private var webView: WebView? = null

    init {
        isClickable = true
        isFocusable = true
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = context.dp(18).toFloat()
            setColor(Color.rgb(248, 250, 252))
        }
        clipToOutline = true

        addView(
            createEntryPanel(
                context = context,
                desktopPortalUrl = desktopPortalUrl,
                localPortalUrl = localPortalUrl,
                onOpenHere = { url -> showWebView(url, onSwipeBack) },
            ),
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun destroyWebView() {
        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            destroy()
        }
        webView = null
    }

    override fun onDetachedFromWindow() {
        destroyWebView()
        super.onDetachedFromWindow()
    }

    private fun showWebView(portalUrl: String, onSwipeBack: () -> Unit) {
        removeAllViews()
        addView(
            WebView(context).apply {
                webView = this
                webViewClient = WebViewClient()
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                attachWebViewSwipeBack(onSwipeBack)
                loadUrl(portalUrl)
            },
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun createEntryPanel(
        context: Context,
        desktopPortalUrl: String?,
        localPortalUrl: String?,
        onOpenHere: (String) -> Unit,
    ): View {
        val outer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(context.dp(22), context.dp(34), context.dp(22), context.dp(28))
        }

        outer.addView(
            PortalMarkView(context),
            LinearLayout.LayoutParams(
                context.dp(44),
                context.dp(44),
            ),
        )

        outer.addView(
            TextView(context).apply {
                text = "Portal"
                setTextColor(Color.rgb(15, 23, 42))
                textSize = 24f
                gravity = Gravity.CENTER
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).withTopMargin(context.dp(12)),
        )

        outer.addView(
            TextView(context).apply {
                text = "Inspect this app from your browser or continue here."
                setTextColor(Color.rgb(71, 85, 105))
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, context.dp(8), 0, 0)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        outer.addView(
            createUrlPill(context, desktopPortalUrl ?: "Portal is starting..."),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                context.dp(44),
            ).withTopMargin(context.dp(24)),
        )

        outer.addView(
            createActionRow(context, desktopPortalUrl),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).withTopMargin(context.dp(14)),
        )

        outer.addView(
            createDivider(context),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).withTopMargin(context.dp(30)),
        )

        outer.addView(
            createOpenHereSection(context, localPortalUrl, onOpenHere),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).withTopMargin(context.dp(26)),
        )

        return outer
    }

    private fun createUrlPill(context: Context, textValue: String): View =
        TextView(context).apply {
            text = textValue
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.MIDDLE
            setTextColor(Color.rgb(30, 41, 59))
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(context.dp(14), 0, context.dp(14), 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.dp(22).toFloat()
                setColor(Color.WHITE)
                setStroke(context.dp(1), Color.rgb(203, 213, 225))
            }
        }

    private fun createActionRow(context: Context, desktopPortalUrl: String?): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER

            addView(
                createThinButton(context, "Desktop URL") {
                    desktopPortalUrl?.let { copyToClipboard(context, it) }
                },
                LinearLayout.LayoutParams(0, context.dp(38), 1f),
            )
            addView(
                createThinButton(context, "Copy URL") {
                    desktopPortalUrl?.let { copyToClipboard(context, it) }
                },
                LinearLayout.LayoutParams(0, context.dp(38), 1f).withLeftMargin(context.dp(10)),
            )
        }

    private fun createDivider(context: Context): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(createDividerLine(context), LinearLayout.LayoutParams(0, context.dp(1), 1f))
            addView(
                TextView(context).apply {
                    text = "or"
                    setTextColor(Color.rgb(100, 116, 139))
                    textSize = 12f
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(context.dp(42), LinearLayout.LayoutParams.WRAP_CONTENT),
            )
            addView(createDividerLine(context), LinearLayout.LayoutParams(0, context.dp(1), 1f))
        }

    private fun createDividerLine(context: Context): View =
        View(context).apply {
            setBackgroundColor(Color.rgb(226, 232, 240))
        }

    private fun createOpenHereSection(
        context: Context,
        localPortalUrl: String?,
        onOpenHere: (String) -> Unit,
    ): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL

            addView(
                TextView(context).apply {
                    text = "Open here"
                    setTextColor(Color.rgb(15, 23, 42))
                    textSize = 18f
                    gravity = Gravity.CENTER
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                TextView(context).apply {
                    text = "Launch the portal inside this overlay."
                    setTextColor(Color.rgb(71, 85, 105))
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setPadding(0, context.dp(6), 0, 0)
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                createPrimaryThinButton(context, "Open portal") {
                    localPortalUrl?.let(onOpenHere)
                }.apply {
                    isEnabled = localPortalUrl != null
                    alpha = if (localPortalUrl == null) 0.55f else 1f
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    context.dp(40),
                ).withTopMargin(context.dp(16)),
            )
        }

    private fun createThinButton(context: Context, label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            setTextColor(Color.rgb(30, 41, 59))
            textSize = 13f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.dp(10).toFloat()
                setColor(Color.WHITE)
                setStroke(context.dp(1), Color.rgb(203, 213, 225))
            }
            setOnClickListener { onClick() }
        }

    private fun createPrimaryThinButton(context: Context, label: String, onClick: () -> Unit): TextView =
        TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = context.dp(10).toFloat()
                setColor(Color.rgb(31, 41, 55))
            }
            setOnClickListener { onClick() }
        }

    private fun copyToClipboard(context: Context, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Portal URL", value))
    }

    private fun View.attachWebViewSwipeBack(onSwipeBack: () -> Unit) {
        val swipeThreshold = context.dp(80)
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var dragging = false
        var velocityTracker: VelocityTracker? = null

        setOnTouchListener { view, event ->
            val drawer = view.parent as? PortalDrawerView
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    drawer?.springMotion?.cancel()
                    downRawX = event.rawX
                    downRawY = event.rawY
                    dragging = false
                    velocityTracker = VelocityTracker.obtain().apply {
                        addMovement(event)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    velocityTracker?.addMovement(event)
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && dx > touchSlop && abs(dx) > abs(dy)) {
                        dragging = true
                    }
                    if (dragging) {
                        drawer?.let {
                            PortalOverlayController.setExpandedSurfaceDismissProgress(
                                drawer = it,
                                progress = dx.coerceAtLeast(0f) / it.width.coerceAtLeast(1),
                            )
                        }
                        return@setOnTouchListener true
                    }
                }

                MotionEvent.ACTION_UP -> {
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val xVelocity = velocityTracker?.xVelocity ?: 0f
                    velocityTracker?.recycle()
                    velocityTracker = null
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (
                        (dragging && ((1f - (drawer?.alpha ?: 1f)) * (drawer?.width ?: 0) > swipeThreshold || xVelocity > context.dp(700))) ||
                        (dx > swipeThreshold && abs(dx) > abs(dy))
                    ) {
                        onSwipeBack()
                        return@setOnTouchListener true
                    } else if (dragging) {
                        drawer?.let { PortalOverlayController.springOpenSurface(it) }
                        return@setOnTouchListener true
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    velocityTracker?.recycle()
                    velocityTracker = null
                    if (dragging) {
                        drawer?.let { PortalOverlayController.springOpenSurface(it) }
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }
}
