package com.v2ray.ang.util

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

object ComposeToast {
    private val mainHandler = Handler(Looper.getMainLooper())

    private const val BG_NORMAL_LIGHT  = 0xB3353A3E.toInt()
    private const val BG_NORMAL_DARK   = 0xB34A4F54.toInt()
    private const val BG_SUCCESS_LIGHT = 0xB3388E3C.toInt()
    private const val BG_SUCCESS_DARK  = 0xB3388E3C.toInt()
    private const val BG_ERROR_LIGHT   = 0xB3D50000.toInt()
    private const val BG_ERROR_DARK    = 0xB3D50000.toInt()
    private const val BG_INFO_LIGHT    = 0xB33F51B5.toInt()
    private const val BG_INFO_DARK     = 0xB33F51B5.toInt()

    private const val TEXT_COLOR       = 0xFFFFFFFF.toInt()
    private const val ICON_COLOR       = 0xFFFFFFFF.toInt()

    private const val TOAST_BOTTOM_OFFSET_DP = 100f

    private const val ICON_SUCCESS = "✓"
    private const val ICON_ERROR   = "✕"
    private const val ICON_INFO    = "ℹ"

    enum class ToastType {
        NORMAL, SUCCESS, ERROR, INFO
    }

    private fun isDarkMode(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    private fun getBackgroundColor(context: Context, type: ToastType): Int {
        val dark = isDarkMode(context)
        return when (type) {
            ToastType.NORMAL  -> if (dark) BG_NORMAL_DARK  else BG_NORMAL_LIGHT
            ToastType.SUCCESS -> if (dark) BG_SUCCESS_DARK else BG_SUCCESS_LIGHT
            ToastType.ERROR   -> if (dark) BG_ERROR_DARK   else BG_ERROR_LIGHT
            ToastType.INFO    -> if (dark) BG_INFO_DARK    else BG_INFO_LIGHT
        }
    }

    private fun getIconText(type: ToastType): String? = when (type) {
        ToastType.SUCCESS -> ICON_SUCCESS
        ToastType.ERROR   -> ICON_ERROR
        ToastType.INFO    -> ICON_INFO
        ToastType.NORMAL  -> null
    }

    private fun dpToPx(context: Context, dp: Float): Float =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, dp,
            context.resources.displayMetrics
        )

    private class IconCircleView(
        context: Context,
        private val symbol: String,
        private val circleColor: Int
    ) : View(context) {
        private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33FFFFFF
            style = Paint.Style.FILL
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ICON_COLOR
            textSize = dpToPx(context, 14f)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = minOf(cx, cy)

            canvas.drawCircle(cx, cy, radius, circlePaint)

            val textY = cy - (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(symbol, cx, textY, textPaint)
        }
    }

    @Suppress("DEPRECATION")
    private fun showStyledToast(
        context: Context,
        message: CharSequence,
        duration: Int,
        type: ToastType
    ) {
        val appContext = context.applicationContext
        val action = Runnable {
            try {
                val toast = Toast(appContext)
                toast.duration = duration

                val bgColor = getBackgroundColor(appContext, type)
                val cornerRadius = dpToPx(appContext, 24f)

                val container = LinearLayout(appContext).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = GradientDrawable().apply {
                        setColor(bgColor)
                        this.cornerRadius = cornerRadius
                    }
                    val hPad = dpToPx(appContext, 16f).toInt()
                    val vPad = dpToPx(appContext, 12f).toInt()
                    setPadding(hPad, vPad, hPad, vPad)
                }

                val iconText = getIconText(type)
                if (iconText != null) {
                    val iconSize = dpToPx(appContext, 24f).toInt()
                    val iconView = IconCircleView(appContext, iconText, bgColor)
                    container.addView(
                        iconView,
                        LinearLayout.LayoutParams(iconSize, iconSize).apply {
                            marginEnd = dpToPx(appContext, 10f).toInt()
                        })
                }

                val textView = TextView(appContext).apply {
                    text = message
                    setTextColor(TEXT_COLOR)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    maxLines = 8
                    maxWidth = (appContext.resources.displayMetrics.widthPixels * 0.75).toInt()
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                }

                container.addView(
                    textView, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )

                toast.view = container
                toast.setGravity(
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                    0,
                    dpToPx(appContext, TOAST_BOTTOM_OFFSET_DP).toInt()
                )
                toast.show()
            } catch (_: Exception) {
                Toast.makeText(appContext, message, duration).show()
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) action.run()
        else mainHandler.post(action)
    }

    fun normal(context: Context, message: CharSequence, duration: Int = Toast.LENGTH_SHORT) =
        showStyledToast(context, message, duration, ToastType.NORMAL)

    fun normal(context: Context, resId: Int, duration: Int = Toast.LENGTH_SHORT) =
        normal(context, context.getString(resId), duration)

    fun success(context: Context, message: CharSequence, duration: Int = Toast.LENGTH_SHORT) =
        showStyledToast(context, message, duration, ToastType.SUCCESS)

    fun success(context: Context, resId: Int, duration: Int = Toast.LENGTH_SHORT) =
        success(context, context.getString(resId), duration)

    fun error(context: Context, message: CharSequence, duration: Int = Toast.LENGTH_SHORT) =
        showStyledToast(context, message, duration, ToastType.ERROR)

    fun error(context: Context, resId: Int, duration: Int = Toast.LENGTH_SHORT) =
        error(context, context.getString(resId), duration)

    fun info(context: Context, message: CharSequence, duration: Int = Toast.LENGTH_LONG) =
        showStyledToast(context, message, duration, ToastType.INFO)

    fun info(context: Context, resId: Int, duration: Int = Toast.LENGTH_LONG) =
        info(context, context.getString(resId), duration)
}
