package com.wendigo.ayhalkasi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class BearView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private enum class Mode { IDLE, HAPPY, GIGGLE, FALL }
    private enum class Region { HEAD, TUMMY, BODY }

    private data class Heart(
        var x: Float, var y: Float, val vy: Float, val size: Float,
        var life: Float, var rot: Float, val swirl: Float
    )

    private val fur = Color.rgb(185, 138, 94)
    private val furDark = Color.rgb(150, 108, 69)
    private val inner = Color.rgb(232, 205, 169)
    private val dark = Color.rgb(58, 42, 34)
    private val blush = Color.rgb(242, 166, 184)
    private val heartPink = Color.rgb(233, 120, 156)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private var mode = Mode.IDLE
    private var modeStart = 0L
    private var modeEnd = 0L

    private var lastFrame = 0L
    private var blinkAt = 0L

    private var rot = 0f
    private var tx = 0f
    private var ty = 0f
    private var sc = 1f

    private val hearts = ArrayList<Heart>()
    private var lastHeartSpawn = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var lastTap = 0L
    private var pendingTap: Runnable? = null
    private var downOnHead = false
    private var downOnTummy = false
    private var downTime = 0L

    private var soundPool: SoundPool? = null
    private var sHappy = 0
    private var sGiggle = 0
    private var sFall = 0
    var soundEnabled = true

    init {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build()
        soundPool?.let {
            sHappy = it.load(context, R.raw.bear_happy, 1)
            sGiggle = it.load(context, R.raw.bear_giggle, 1)
            sFall = it.load(context, R.raw.bear_fall, 1)
        }
        isClickable = true
    }

    private fun now() = SystemClock.uptimeMillis()

    private fun setMode(m: Mode, dur: Long) {
        mode = m
        modeStart = now()
        modeEnd = modeStart + dur
    }

    fun triggerHappy(fromPet: Boolean = false) {
        val entering = mode != Mode.HAPPY
        if (mode == Mode.FALL) return
        setMode(Mode.HAPPY, 1900)
        if (entering && !fromPet) {
            play(sHappy, 0.7f)
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
        if (entering) play(sHappy, 0.55f)
    }

    private fun triggerGiggle() {
        if (mode == Mode.FALL) return
        setMode(Mode.GIGGLE, 1600)
        play(sGiggle, 0.75f)
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    private fun triggerWiggle() {
        if (mode == Mode.FALL) return
        setMode(Mode.HAPPY, 900)
        play(sHappy, 0.4f)
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun triggerFall() {
        setMode(Mode.FALL, 2800)
        play(sFall, 0.8f)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun play(id: Int, vol: Float) {
        if (!soundEnabled || id == 0) return
        soundPool?.play(id, vol, vol, 1, 0, 1f)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
        soundPool?.release()
        soundPool = null
    }

    // ---- touch ----

    private fun fx(x: Float): Float = ox + (x - 50f) * s
    private fun fy(y: Float): Float = oy + (y - 55f) * s
    private fun fr(r: Float): Float = r * s

    private var ox = 0f
    private var oy = 0f
    private var s = 1f

    private fun region(x: Float, y: Float): Region {
        val dx = (x - fx(50f)) / fr(1f)
        val dy = (y - fy(40f)) / fr(1f)
        if (Math.hypot(dx.toDouble(), dy.toDouble()) < 28.0) return Region.HEAD
        val bdx = (x - fx(50f)) / fr(1f)
        val bdy = (y - fy(83f)) / fr(1f)
        if ((bdx * bdx) / (16f * 16f) + (bdy * bdy) / (16f * 16f) < 1f) return Region.TUMMY
        return Region.BODY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downOnHead = region(event.x, event.y) == Region.HEAD
                downOnTummy = region(event.x, event.y) == Region.TUMMY
                downTime = now()
                if (downOnHead) triggerHappy(fromPet = true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (downOnHead && mode != Mode.FALL) {
                    if (mode != Mode.HAPPY) triggerHappy(fromPet = true)
                    else modeEnd = now() + 1900
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val quick = now() - downTime < 400
                if (downOnHead) {
                    // already happy; nothing more
                } else if (quick) {
                    if (now() - lastTap < 360) {
                        pendingTap?.let { handler.removeCallbacks(it) }
                        pendingTap = null
                        triggerFall()
                    } else {
                        val tummy = downOnTummy
                        val r = Runnable {
                            if (tummy) triggerGiggle() else triggerWiggle()
                        }
                        pendingTap = r
                        handler.postDelayed(r, 240)
                    }
                    lastTap = now()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    // ---- draw ----

    override fun onDraw(canvas: Canvas) {
        val t = now()
        val dt = if (lastFrame == 0L) 0L else (t - lastFrame)
        lastFrame = t
        update(dt)

        val w = width.toFloat()
        val h = height.toFloat()
        ox = w / 2f
        oy = h * 0.54f
        s = min(w / 100f, h / 118f) * 0.94f

        val elapsed = t - modeStart
        rot = 0f; tx = 0f; ty = 0f; sc = 1f
        when (mode) {
            Mode.IDLE -> {
                val b = sin(t / 900.0).toFloat()
                ty = b * fr(1.2f)
                sc = 1f + 0.012f * b
            }
            Mode.HAPPY -> {
                val e = elapsed.toFloat()
                val bounce = abs(sin(e / 150.0)).toFloat() * fr(6f)
                ty = -bounce
                sc = 1f + 0.05f * abs(sin(e / 150.0)).toFloat()
                rot = sin(e / 220.0).toFloat() * 3f
            }
            Mode.GIGGLE -> {
                val e = elapsed.toFloat()
                tx = sin(e / 55.0).toFloat() * fr(4.5f)
                ty = -abs(sin(e / 110.0)).toFloat() * fr(3f)
                rot = sin(e / 70.0).toFloat() * 6f
            }
            Mode.FALL -> {
                val e = elapsed.toFloat()
                if (e < 520f) {
                    rot = sin(e / 45.0).toFloat() * 9f
                } else if (e < 1220f) {
                    val p = (e - 520f) / 700f
                    rot = 80f * easeIn(p)
                    ty = fr(6f) * p
                } else if (e < 2100f) {
                    rot = 80f
                    ty = fr(6f)
                } else {
                    val p = ((e - 2100f) / 700f).coerceIn(0f, 1f)
                    rot = 80f * (1f - easeOut(p))
                    ty = fr(6f) * (1f - easeOut(p))
                }
            }
        }

        spawnHearts(t)

        canvas.save()
        canvas.translate(tx, ty)
        canvas.rotate(rot, ox, oy)
        canvas.scale(sc, sc, ox, oy)
        drawBear(canvas, t, elapsed)
        canvas.restore()

        drawHearts(canvas)

        if (mode == Mode.GIGGLE) {
            text.color = furDark
            text.textSize = fr(9f)
            canvas.drawText("hihi!", fx(72f), fy(28f), text)
        }
        if (mode == Mode.FALL && elapsed > 560f && elapsed < 1500f) {
            text.color = furDark
            text.textSize = fr(9f)
            canvas.drawText("ay!", fx(74f), fy(30f), text)
        }

        postInvalidateOnAnimation()
    }

    private fun easeIn(p: Float) = p * p
    private fun easeOut(p: Float) = 1f - (1f - p) * (1f - p)

    private fun drawBear(canvas: Canvas, t: Long, elapsed: Long) {
        // legs
        fill.color = fur
        canvas.drawCircle(fx(37f), fy(101f), fr(11f), fill)
        canvas.drawCircle(fx(63f), fy(101f), fr(11f), fill)
        fill.color = inner
        canvas.drawCircle(fx(37f), fy(102f), fr(6f), fill)
        canvas.drawCircle(fx(63f), fy(102f), fr(6f), fill)

        // ears
        fill.color = fur
        canvas.drawCircle(fx(30f), fy(27f), fr(12f), fill)
        canvas.drawCircle(fx(70f), fy(27f), fr(12f), fill)
        fill.color = inner
        canvas.drawCircle(fx(30f), fy(27f), fr(6f), fill)
        canvas.drawCircle(fx(70f), fy(27f), fr(6f), fill)

        // body
        val body = RectF(fx(50f) - fr(24f), fy(80f) - fr(23f), fx(50f) + fr(24f), fy(80f) + fr(23f))
        fill.color = fur
        canvas.drawOval(body, fill)

        // arms
        canvas.save()
        canvas.rotate(-22f, fx(26f), fy(80f))
        canvas.drawOval(RectF(fx(26f) - fr(9f), fy(80f) - fr(14f), fx(26f) + fr(9f), fy(80f) + fr(14f)), fill)
        canvas.restore()
        canvas.save()
        canvas.rotate(22f, fx(74f), fy(80f))
        canvas.drawOval(RectF(fx(74f) - fr(9f), fy(80f) - fr(14f), fx(74f) + fr(9f), fy(80f) + fr(14f)), fill)
        canvas.restore()

        // belly
        fill.color = inner
        canvas.drawOval(RectF(fx(50f) - fr(15f), fy(83f) - fr(16f), fx(50f) + fr(15f), fy(83f) + fr(16f)), fill)

        // head
        fill.color = fur
        canvas.drawCircle(fx(50f), fy(40f), fr(27f), fill)

        // muzzle
        fill.color = inner
        canvas.drawOval(RectF(fx(50f) - fr(13f), fy(50f) - fr(10f), fx(50f) + fr(13f), fy(50f) + fr(10f)), fill)

        val happy = mode == Mode.HAPPY || mode == Mode.GIGGLE
        val lying = mode == Mode.FALL && elapsed > 560f && elapsed < 2100f

        // blush
        if (happy) {
            fill.color = blush
            canvas.drawOval(RectF(fx(31f) - fr(6f), fy(48f) - fr(4f), fx(31f) + fr(6f), fy(48f) + fr(4f)), fill)
            canvas.drawOval(RectF(fx(69f) - fr(6f), fy(48f) - fr(4f), fx(69f) + fr(6f), fy(48f) + fr(4f)), fill)
        }

        // eyes
        drawEyes(canvas, t, happy, lying)

        // nose
        fill.color = dark
        canvas.drawOval(RectF(fx(50f) - fr(4f), fy(45f) - fr(3f), fx(50f) + fr(4f), fy(45f) + fr(3f)), fill)

        // mouth
        line.color = dark
        line.strokeWidth = fr(1.6f)
        val mouth = Path()
        if (mode == Mode.GIGGLE) {
            fill.color = dark
            canvas.drawOval(RectF(fx(50f) - fr(5f), fy(52f) - fr(4f), fx(50f) + fr(5f), fy(52f) + fr(6f)), fill)
            fill.color = Color.rgb(214, 96, 120)
            canvas.drawOval(RectF(fx(50f) - fr(3f), fy(54f) - fr(1f), fx(50f) + fr(3f), fy(56f) + fr(1f)), fill)
        } else if (happy) {
            mouth.moveTo(fx(44f), fy(50f))
            mouth.quadTo(fx(50f), fy(56f), fx(56f), fy(50f))
            canvas.drawPath(mouth, line)
        } else {
            mouth.moveTo(fx(50f), fy(48f))
            mouth.lineTo(fx(50f), fy(51f))
            mouth.moveTo(fx(46f), fy(53f))
            mouth.quadTo(fx(50f), fy(56f), fx(54f), fy(53f))
            canvas.drawPath(mouth, line)
        }
    }

    private fun drawEyes(canvas: Canvas, t: Long, happy: Boolean, lying: Boolean) {
        val leftX = fx(40f)
        val rightX = fx(60f)
        val ey = fy(36f)
        if (lying) {
            line.color = dark
            line.strokeWidth = fr(1.8f)
            for (cxp in listOf(leftX, rightX)) {
                canvas.drawLine(cxp - fr(3f), ey - fr(3f), cxp + fr(3f), ey + fr(3f), line)
                canvas.drawLine(cxp - fr(3f), ey + fr(3f), cxp + fr(3f), ey - fr(3f), line)
            }
            return
        }
        if (happy) {
            line.color = dark
            line.strokeWidth = fr(2f)
            for (cxp in listOf(leftX, rightX)) {
                val p = Path()
                p.moveTo(cxp - fr(4f), ey + fr(1f))
                p.quadTo(cxp, ey - fr(4f), cxp + fr(4f), ey + fr(1f))
                canvas.drawPath(p, line)
            }
            return
        }
        val blink = t >= blinkAt && t < blinkAt + 150
        if (blink) {
            line.color = dark
            line.strokeWidth = fr(2f)
            canvas.drawLine(leftX - fr(3.5f), ey, leftX + fr(3.5f), ey, line)
            canvas.drawLine(rightX - fr(3.5f), ey, rightX + fr(3.5f), ey, line)
        } else {
            fill.color = dark
            canvas.drawCircle(leftX, ey, fr(3.2f), fill)
            canvas.drawCircle(rightX, ey, fr(3.2f), fill)
            fill.color = Color.WHITE
            canvas.drawCircle(leftX + fr(1f), ey - fr(1f), fr(1f), fill)
            canvas.drawCircle(rightX + fr(1f), ey - fr(1f), fr(1f), fill)
        }
        if (t >= blinkAt + 160) blinkAt = t + (1800 + Random.nextLong(2600))
    }

    private fun spawnHearts(t: Long) {
        if (mode != Mode.HAPPY && mode != Mode.GIGGLE) return
        val interval = if (mode == Mode.GIGGLE) 150L else 230L
        if (t - lastHeartSpawn < interval) return
        lastHeartSpawn = t
        val base = if (mode == Mode.GIGGLE) 0.9f else 0.7f
        repeat(if (mode == Mode.GIGGLE) 2 else 1) {
            val cx = fx(50f) + (Random.nextFloat() - 0.5f) * fr(44f)
            val cy = fy(30f) + (Random.nextFloat() - 0.5f) * fr(20f)
            hearts.add(
                Heart(
                    x = cx, y = cy,
                    vy = -fr(0.6f) - Random.nextFloat() * fr(0.5f),
                    size = fr(5f) + Random.nextFloat() * fr(3f),
                    life = 1f, rot = (Random.nextFloat() - 0.5f) * 30f,
                    swirl = fr(base) * (Random.nextFloat() - 0.3f)
                )
            )
        }
        if (hearts.size > 60) hearts.subList(0, hearts.size - 60).clear()
    }

    private fun drawHearts(canvas: Canvas) {
        for (h in hearts) {
            fill.color = Color.argb((h.life * 235f).toInt().coerceIn(0, 255),
                Color.red(heartPink), Color.green(heartPink), Color.blue(heartPink))
            val p = Path()
            val sz = h.size
            p.moveTo(h.x, h.y + sz * 0.3f)
            p.cubicTo(h.x - sz, h.y - sz * 0.6f, h.x - sz * 0.4f, h.y - sz * 1.3f, h.x, h.y - sz * 0.55f)
            p.cubicTo(h.x + sz * 0.4f, h.y - sz * 1.3f, h.x + sz, h.y - sz * 0.6f, h.x, h.y + sz * 0.3f)
            p.close()
            canvas.save()
            canvas.rotate(h.rot, h.x, h.y)
            canvas.drawPath(p, fill)
            canvas.restore()
        }
    }

    private fun update(dt: Long) {
        if (dt <= 0L) return
        if (mode != Mode.IDLE && now() >= modeEnd) mode = Mode.IDLE
        val it = hearts.iterator()
        val step = dt / 16.6f
        while (it.hasNext()) {
            val h = it.next()
            h.y += h.vy * step
            h.x += h.swirl * step
            h.life -= 0.014f * step
            h.rot += 1.6f * step
            if (h.life <= 0f || h.y < -fr(20f)) it.remove()
        }
    }
}
