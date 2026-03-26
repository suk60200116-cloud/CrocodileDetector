package com.smishing.crocodiledetector

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class OverlayView(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var rootView: FrameLayout? = null
    var isShowing = false

    private var cardView: LinearLayout? = null
    private var statusBadge: LinearLayout? = null
    private var statusDot: View? = null
    private var statusLabel: TextView? = null
    private var warningBanner: LinearLayout? = null
    private var sttTextView: TextView? = null
    private var scriptTextView: TextView? = null
    private var actionIconsRow: LinearLayout? = null
    private var closeBtnNormal: TextView? = null

    enum class Stage { WARNING, DANGER }

    fun show() {
        if (isShowing) return
        mainHandler.post {
            buildView()
            val params = WindowManager.LayoutParams(
                dpToPx(360),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dpToPx(145)
            }
            windowManager.addView(rootView, params)
            isShowing = true
        }
    }

    fun hide() {
        if (!isShowing) return
        mainHandler.post {
            try { windowManager.removeView(rootView) } catch (e: Exception) {}
            isShowing = false
            rootView = null
        }
    }

    fun updateSttText(text: String) {
        mainHandler.post { sttTextView?.text = text }
    }

    fun setStage(stage: Stage) {
        mainHandler.post { applyStage(stage) }
    }

    private fun buildView() {
        rootView = FrameLayout(context).apply {
            setPadding(0, dpToPx(10), 0, 0)
        }

        cardView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = buildCardBg(Color.parseColor("#F59E0B"))
        }

        // 경고 배너
        warningBanner = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#26DC2626"))
            setPadding(dpToPx(12), dpToPx(9), dpToPx(12), dpToPx(9))
            visibility = View.GONE
        }.also { banner ->
            val warningTexts = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            TextView(context).apply {
                text = "경고: 보이스피싱 의심 통화"
                textSize = 12f
                setTextColor(Color.parseColor("#EF4444"))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                warningTexts.addView(this)
            }
            TextView(context).apply {
                text = "즉시 대응이 필요합니다"
                textSize = 10f
                setTextColor(Color.parseColor("#F87171"))
                warningTexts.addView(this)
            }
            val closeBtn = TextView(context).apply {
                text = "×"
                textSize = 18f
                setTextColor(Color.parseColor("#AAAAAA"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(28)).also {
                    it.marginStart = dpToPx(8)
                }
                setOnClickListener { hide() }
            }
            banner.addView(warningTexts)
            banner.addView(closeBtn)
        }

        // 일반 닫기 버튼 행
        val normalCloseRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(dpToPx(10), dpToPx(6), dpToPx(10), 0)
        }
        closeBtnNormal = TextView(context).apply {
            text = "×"
            textSize = 18f
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpToPx(28), dpToPx(28))
            setOnClickListener { hide() }
        }
        normalCloseRow.addView(closeBtnNormal)

        // STT 영역
        val sttSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(8))
        }
        val sttLabelRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(5))
        }
        View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(6), dpToPx(6)).also { it.marginEnd = dpToPx(5) }
            background = buildRoundBg(Color.parseColor("#EF4444"), dpToPx(3).toFloat())
            sttLabelRow.addView(this)
        }
        TextView(context).apply {
            text = "상대방 발언"
            textSize = 10f
            setTextColor(Color.parseColor("#777777"))
            sttLabelRow.addView(this)
        }
        val sttBg = FrameLayout(context).apply {
            background = buildRoundBg(Color.parseColor("#0DFFFFFF"), dpToPx(10).toFloat())
            setPadding(dpToPx(10), dpToPx(7), dpToPx(10), dpToPx(7))
        }
        sttTextView = TextView(context).apply {
            text = "키워드가 감지되었습니다..."
            textSize = 12f
            setTextColor(Color.parseColor("#DDDDDD"))
            setLineSpacing(0f, 1.4f)
        }
        sttBg.addView(sttTextView)
        sttSection.addView(sttLabelRow)
        sttSection.addView(sttBg)

        // 대응 스크립트 영역
        val scriptSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(12), 0, dpToPx(12), dpToPx(12))
        }
        val scriptLabelRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(5))
        }
        TextView(context).apply {
            text = "대응 스크립트"
            textSize = 10f
            setTextColor(Color.parseColor("#777777"))
            scriptLabelRow.addView(this)
        }
        val scriptBg = FrameLayout(context).apply {
            minimumHeight = dpToPx(80)
            background = buildRoundBg(Color.parseColor("#0AFFFFFF"), dpToPx(12).toFloat())
            setPadding(dpToPx(12), dpToPx(14), dpToPx(12), dpToPx(14))
        }
        scriptTextView = TextView(context).apply {
            text = "LLM 서버 연동 후 표시됩니다"
            textSize = 11f
            setTextColor(Color.parseColor("#555555"))
            gravity = Gravity.CENTER
        }
        scriptBg.addView(scriptTextView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        ))
        scriptSection.addView(scriptLabelRow)
        scriptSection.addView(scriptBg)

        // 행동 아이콘 (위험 단계만)
        actionIconsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dpToPx(12), 0, dpToPx(12), dpToPx(12))
            visibility = View.GONE
        }.also { row ->
            row.addView(buildActionIcon("전화끊기", Color.parseColor("#EF4444")))
            row.addView(buildActionIcon("메모하기", Color.parseColor("#F59E0B")))
            row.addView(buildActionIcon("112신고", Color.parseColor("#3B82F6")))
        }

        // 카드 조립
        cardView!!.addView(warningBanner)
        cardView!!.addView(normalCloseRow)
        cardView!!.addView(sttSection)
        cardView!!.addView(scriptSection)
        cardView!!.addView(actionIconsRow)

        // 상태 뱃지
        statusBadge = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = buildBadgeBg(Color.parseColor("#F59E0B"))
            setPadding(dpToPx(6), dpToPx(3), dpToPx(10), dpToPx(3))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).also {
                it.marginStart = dpToPx(12)
                it.topMargin = dpToPx(2)
            }
        }
        statusDot = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpToPx(7), dpToPx(7)).also { it.marginEnd = dpToPx(5) }
            background = buildRoundBg(Color.parseColor("#F59E0B"), dpToPx(4).toFloat())
        }
        statusLabel = TextView(context).apply {
            text = "주의"
            textSize = 11f
            setTextColor(Color.parseColor("#F59E0B"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        statusBadge!!.addView(statusDot)
        statusBadge!!.addView(statusLabel)

        // 루트 조립
        rootView!!.addView(cardView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).also { it.topMargin = dpToPx(8) })
        rootView!!.addView(statusBadge)

        applyStage(Stage.WARNING)
    }

    private fun applyStage(stage: Stage) {
        val borderColor = if (stage == Stage.DANGER) Color.parseColor("#EF4444")
        else Color.parseColor("#F59E0B")

        cardView?.background = buildCardBg(borderColor)
        statusBadge?.background = buildBadgeBg(borderColor)
        statusDot?.background = buildRoundBg(borderColor, dpToPx(4).toFloat())

        when (stage) {
            Stage.WARNING -> {
                statusLabel?.text = "주의"
                statusLabel?.setTextColor(Color.parseColor("#F59E0B"))
                warningBanner?.visibility = View.GONE
                closeBtnNormal?.visibility = View.VISIBLE
                actionIconsRow?.visibility = View.GONE
                scriptTextView?.textSize = 13f
                scriptTextView?.setTextColor(Color.parseColor("#F59E0B"))
                scriptTextView?.setTypeface(scriptTextView?.typeface, android.graphics.Typeface.NORMAL)
            }
            Stage.DANGER -> {
                statusLabel?.text = "위험"
                statusLabel?.setTextColor(Color.parseColor("#EF4444"))
                warningBanner?.visibility = View.VISIBLE
                closeBtnNormal?.visibility = View.GONE
                actionIconsRow?.visibility = View.VISIBLE
                scriptTextView?.textSize = 15f
                scriptTextView?.setTextColor(Color.WHITE)
                scriptTextView?.setTypeface(scriptTextView?.typeface, android.graphics.Typeface.BOLD)
            }
        }
    }

    private fun buildActionIcon(label: String, color: Int): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(10).toFloat()
                setColor(Color.argb(25, Color.red(color), Color.green(color), Color.blue(color)))
                setStroke(dpToPx(1), Color.argb(50, Color.red(color), Color.green(color), Color.blue(color)))
            }
            setPadding(dpToPx(14), dpToPx(8), dpToPx(14), dpToPx(8))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.marginEnd = dpToPx(8) }
            TextView(context).apply {
                text = label
                textSize = 10f
                setTextColor(color)
                gravity = Gravity.CENTER
                addView(this)
            }
        }
    }

    private fun buildCardBg(borderColor: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpToPx(16).toFloat()
        setColor(Color.parseColor("#F0101023"))
        setStroke(dpToPx(2), borderColor)
    }

    private fun buildBadgeBg(borderColor: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dpToPx(20).toFloat()
        setColor(Color.parseColor("#FF1A1A2E"))
        setStroke(dpToPx(2), borderColor)
    }

    private fun buildRoundBg(color: Int, radius: Float) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius
        setColor(color)
    }

    private fun dpToPx(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
}