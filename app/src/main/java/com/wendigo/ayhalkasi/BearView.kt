package com.wendigo.ayhalkasi

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class BearView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0
) : View(context, attrs, defStyle) {

    enum class PetAnimal { BEAR, CAT, RABBIT, PANDA }

    private enum class Mode { IDLE, HAPPY, GIGGLE, FALL, HUG, SLEEP, DANCE, JUMP, KISS, SULK }
    private enum class Region { HEAD, TUMMY, BODY }
    private enum class Arms { NORMAL, UP, WRAP, CROSS }

    private data class Palette(val fur: Int, val fur2: Int, val inner: Int, val dark: Int, val blush: Int)

    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float, var grav: Float,
        val kind: Int, var size: Float, var life: Float, var rot: Float, val spin: Float, val text: String?
    )

    private companion object {
        const val K_HEART = 0
        const val K_STAR = 1
        const val K_NOTE = 2
        const val K_ZZZ = 3
        const val K_PUFF = 4
        const val K_KISS = 5
        const val GOLD = 0xFFFFC94D.toInt()
        const val GRAY = 0xFFB9B2C0.toInt()
        const val ACCENT = 0xFFC77DBB.toInt()
        const val BUBBLE_BG = 0xFFFBE3F3.toInt()
        const val INK = 0xFF3A2E3F.toInt()
        const val HEART_PINK = 0xFFE9789C.toInt()
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
    }

    var animal: PetAnimal = PetAnimal.BEAR
        set(value) {
            field = value
            invalidate()
        }

    var petName: String = ""
    var messages: List<String> = emptyList()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
    private var scX = 1f
    private var scY = 1f

    private var ox = 0f
    private var oy = 0f
    private var s = 1f

    private var faceShiftX = 0f
    private var faceShiftY = 0f

    private var dragYaw = 0f
    private var dragPitch = 0f
    private var dragging = false

    private val parts = ArrayList<Particle>()
    private var lastSpawn = 0L

    private var bubbleText = ""
    private var bubbleShown = 0L
    private var bubbleUntil = 0L

    private val shaders = HashMap<String, Paint>()
    private var shaderScale = -1f

    private val handler = Handler(Looper.getMainLooper())
    private var lastTap = 0L
    private var lastRegion = Region.BODY
    private var pendingTap: Runnable? = null
    private var downRegion = Region.BODY
    private var downTime = 0L
    private var moved = false
    private var downX = 0f
    private var downY = 0f

    private var soundPool: SoundPool? = null
    private var sHappy = 0
    private var sGiggle = 0
    private var sFall = 0
    var soundEnabled = true

    init {
        val aa = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(aa).build()
        soundPool?.let {
            sHappy = it.load(context, R.raw.bear_happy, 1)
            sGiggle = it.load(context, R.raw.bear_giggle, 1)
            sFall = it.load(context, R.raw.bear_fall, 1)
        }
        isClickable = true
    }

    private fun now() = SystemClock.uptimeMillis()

    private fun palette(a: PetAnimal): Palette = when (a) {
        PetAnimal.BEAR -> Palette(0xFFB98A5E.toInt(), 0xFF976C45.toInt(), 0xFFE8CDA9.toInt(), 0xFF3A2A22.toInt(), 0xFFF2A6B8.toInt())
        PetAnimal.CAT -> Palette(0xFFE2A45A.toInt(), 0xFFC6853E.toInt(), 0xFFF6E3C8.toInt(), 0xFF3A2A22.toInt(), 0xFFF2A6B8.toInt())
        PetAnimal.RABBIT -> Palette(0xFFF3EDE6.toInt(), 0xFFDED4C8.toInt(), 0xFFF7C8D6.toInt(), 0xFF4A3A33.toInt(), 0xFFF4A9BE.toInt())
        PetAnimal.PANDA -> Palette(0xFFF4F4F4.toInt(), 0xFFDADADA.toInt(), 0xFFFFFFFF.toInt(), 0xFF2B2B2B.toInt(), 0xFFF2A6B8.toInt())
    }

    // ---------- reactions ----------

    private fun setMode(m: Mode, dur: Long) {
        mode = m
        modeStart = now()
        modeEnd = modeStart + dur
    }

    private fun say() {
        if (messages.isEmpty()) return
        bubbleText = messages[Random.nextInt(messages.size)]
        bubbleShown = now()
        bubbleUntil = bubbleShown + 2800
    }

    private fun currentArms(): Arms = when (mode) {
        Mode.HUG -> Arms.WRAP
        Mode.DANCE, Mode.JUMP -> Arms.UP
        Mode.SULK -> Arms.CROSS
        else -> Arms.NORMAL
    }

    fun happy() {
        if (mode == Mode.FALL) return
        setMode(Mode.HAPPY, 1900); play(sHappy, 0.6f); haptic(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun hug() {
        setMode(Mode.HUG, 2400); play(sHappy, 0.7f); haptic(HapticFeedbackConstants.LONG_PRESS); say()
    }

    fun giggle() {
        if (mode == Mode.FALL) return
        setMode(Mode.GIGGLE, 1600); play(sGiggle, 0.75f); haptic(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun dance() {
        setMode(Mode.DANCE, 2800); play(sGiggle, 0.7f); haptic(HapticFeedbackConstants.VIRTUAL_KEY); say()
    }

    fun jump() {
        setMode(Mode.JUMP, 1600); play(sHappy, 0.6f); haptic(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun kiss() {
        setMode(Mode.KISS, 1700); play(sHappy, 0.6f); haptic(HapticFeedbackConstants.VIRTUAL_KEY); say()
    }

    fun sleep() {
        setMode(Mode.SLEEP, 4200); play(sGiggle, 0.35f); haptic(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun sulk() {
        setMode(Mode.SULK, 2600); play(sFall, 0.5f); haptic(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun fall() {
        setMode(Mode.FALL, 2800); play(sFall, 0.8f); haptic(HapticFeedbackConstants.LONG_PRESS)
    }

    fun wiggle() {
        if (mode == Mode.FALL) return
        setMode(Mode.HAPPY, 900); play(sHappy, 0.4f)
    }

    private fun play(id: Int, vol: Float) {
        if (!soundEnabled || id == 0) return
        soundPool?.play(id, vol, vol, 1, 0, 1f)
    }

    private fun haptic(kind: Int) {
        performHapticFeedback(kind)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        handler.removeCallbacksAndMessages(null)
        soundPool?.release()
        soundPool = null
    }

    // ---------- geometry ----------

    private fun fx(x: Float): Float = ox + (x - 50f) * s
    private fun fy(y: Float): Float = oy + (y - 55f) * s
    private fun fr(r: Float): Float = r * s

    private fun region(x: Float, y: Float): Region {
        val dx = ((x - fx(50f)) / fr(1f)).toDouble()
        val dy = ((y - fy(40f)) / fr(1f)).toDouble()
        if (Math.hypot(dx, dy) < 29.0) return Region.HEAD
        val bx = (x - fx(50f)) / fr(1f)
        val by = (y - fy(83f)) / fr(1f)
        if ((bx * bx) / 256f + (by * by) / 256f < 1f) return Region.TUMMY
        return Region.BODY
    }

    // ---------- touch ----------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRegion = region(event.x, event.y)
                downTime = now()
                downX = event.x
                downY = event.y
                moved = false
                dragging = false
                if (downRegion == Region.HEAD && mode != Mode.FALL) happy()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dist = Math.hypot((event.x - downX).toDouble(), (event.y - downY).toDouble())
                if (downRegion == Region.HEAD && mode != Mode.FALL && !dragging) {
                    if (dist > fr(7f)) moved = true
                    if (mode != Mode.HAPPY && mode != Mode.HUG) happy() else modeEnd = now() + 1900
                } else if (downRegion != Region.HEAD && dist > fr(12f)) {
                    if (!dragging) {
                        dragging = true
                        pendingTap?.let { handler.removeCallbacks(it) }
                        pendingTap = null
                    }
                    dragYaw = (dragYaw + (event.x - downX) * 0.006f).coerceIn(-0.95f, 0.95f)
                    dragPitch = (dragPitch + (event.y - downY) * 0.004f).coerceIn(-0.5f, 0.5f)
                    downX = event.x
                    downY = event.y
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val held = now() - downTime >= 550L && !moved && !dragging
                when {
                    dragging -> { /* rotate release; yaw eases back */ }
                    moved -> { /* petting done */ }
                    held -> when (downRegion) {
                        Region.HEAD -> hug()
                        Region.TUMMY -> kiss()
                        Region.BODY -> sleep()
                    }
                    else -> {
                        if (now() - lastTap < 360L && downRegion == lastRegion) {
                            pendingTap?.let { handler.removeCallbacks(it) }
                            pendingTap = null
                            when (downRegion) {
                                Region.HEAD -> kiss()
                                Region.TUMMY -> dance()
                                Region.BODY -> fall()
                            }
                        } else {
                            val reg = downRegion
                            val r = Runnable {
                                when (reg) {
                                    Region.HEAD -> happy()
                                    Region.TUMMY -> giggle()
                                    Region.BODY -> jump()
                                }
                            }
                            pendingTap = r
                            handler.postDelayed(r, 210)
                            lastTap = now()
                            lastRegion = reg
                        }
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    // ---------- draw ----------

    override fun onDraw(canvas: Canvas) {
        val t = now()
        val dt = if (lastFrame == 0L) 0L else t - lastFrame
        lastFrame = t
        update(dt, t)

        val w = width.toFloat()
        val h = height.toFloat()
        ox = w / 2f
        oy = h * 0.56f
        s = min(w / 106f, h / 134f) * 0.98f
        if (s != shaderScale) { shaders.clear(); shaderScale = s }

        val e = (t - modeStart).toFloat()
        rot = 0f; tx = 0f; ty = 0f; scX = 1f; scY = 1f
        when (mode) {
            Mode.IDLE -> {
                val b = sin(t / 900.0).toFloat()
                ty = b * fr(1.2f); scX = 1f + 0.012f * b; scY = scX
            }
            Mode.HAPPY -> {
                val k = abs(sin(e / 150.0)).toFloat()
                ty = -k * fr(6f); scX = 1f + 0.05f * k; scY = scX
                rot = sin(e / 220.0).toFloat() * 3f
            }
            Mode.GIGGLE -> {
                tx = sin(e / 55.0).toFloat() * fr(4.5f)
                ty = -abs(sin(e / 110.0)).toFloat() * fr(3f)
                rot = sin(e / 70.0).toFloat() * 7f
            }
            Mode.HUG -> {
                val k = abs(sin(e / 300.0)).toFloat()
                scX = 1f + 0.05f * k; scY = scX; ty = -k * fr(2.5f)
            }
            Mode.SLEEP -> {
                tx = sin(e / 780.0).toFloat() * fr(2.2f)
                rot = sin(e / 900.0).toFloat() * 5f
                scY = 1f - 0.012f * abs(sin(e / 1500.0)).toFloat(); scX = 1f
            }
            Mode.DANCE -> {
                rot = sin(e / 170.0).toFloat() * 15f
                ty = -abs(sin(e / 170.0)).toFloat() * fr(5f)
            }
            Mode.JUMP -> {
                val hf = abs(sin(e / 260.0)).toFloat()
                ty = -hf * fr(22f); scY = 1f + 0.12f * hf; scX = 1f - 0.07f * hf
            }
            Mode.KISS -> {
                val k = abs(sin(e / 240.0)).toFloat()
                ty = -k * fr(3f); scX = 1f + 0.03f * k; scY = scX
            }
            Mode.SULK -> {
                rot = -7f; tx = -fr(1.5f)
            }
            Mode.FALL -> {
                if (e < 520f) {
                    rot = sin(e / 45.0).toFloat() * 9f
                } else if (e < 1220f) {
                    val p = (e - 520f) / 700f
                    rot = 80f * easeIn(p); ty = fr(6f) * p
                } else if (e < 2100f) {
                    rot = 80f; ty = fr(6f)
                } else {
                    val p = ((e - 2100f) / 700f).coerceIn(0f, 1f)
                    rot = 80f * (1f - easeOut(p)); ty = fr(6f) * (1f - easeOut(p))
                }
            }
        }

        val foreshorten = max(0.34f, cos(dragYaw))
        val pitchScale = (cos(dragPitch)).coerceIn(0.72f, 1f)
        faceShiftX = sin(dragYaw) * fr(7f)
        faceShiftY = sin(dragPitch) * fr(3f)

        spawn(t, e)

        canvas.save()
        canvas.translate(tx, ty)
        canvas.rotate(rot, ox, oy)
        canvas.scale(scX, scY, ox, oy)
        canvas.scale(foreshorten, pitchScale, ox, oy)
        drawShadow(canvas, foreshorten)
        drawPet(canvas, t, e)
        canvas.restore()

        drawParts(canvas)
        drawFloatingText(canvas, e)
        drawBubble(canvas, t)

        postInvalidateOnAnimation()
    }

    private fun easeIn(p: Float) = p * p
    private fun easeOut(p: Float) = 1f - (1f - p) * (1f - p)

    // ---------- soft-3D helpers ----------

    private fun lighten(c: Int, f: Float): Int = lerp(c, WHITE, f)
    private fun darken(c: Int, f: Float): Int = lerp(c, BLACK, f)

    private fun lerp(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * tt).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * tt).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * tt).toInt()
        )
    }

    private fun spherePaint(cx: Float, cy: Float, rx: Float, ry: Float, base: Int, ring: Boolean = true): Paint {
        val key = "s${cx.toInt()}_${cy.toInt()}_${rx.toInt()}_${ry.toInt()}_$base"
        return shaders.getOrPut(key) {
            val r = max(rx, ry) * 1.22f
            val sh = RadialGradient(
                cx - rx * 0.30f, cy - ry * 0.36f, r,
                intArrayOf(lighten(base, 0.42f), base, darken(base, 0.30f)),
                floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP
            )
            Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = sh }
        }
    }

    private fun blob(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, base: Int) {
        canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), spherePaint(cx, cy, rx, ry, base))
        if (rx > fr(2f)) {
            fill.color = withA(darken(base, 0.45f), 70)
            fill.style = Paint.Style.STROKE
            fill.strokeWidth = fr(0.6f)
            canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), fill)
            fill.style = Paint.Style.FILL
        }
    }

    private fun gradPath(canvas: Canvas, path: Path, top: Int, base: Int, bottom: Int) {
        val b = path.getBounds()
        if (b.isEmpty) return
        val key = "p${b.left.toInt()}_${b.top.toInt()}_${b.right.toInt()}_${b.bottom.toInt()}_$base"
        val p = shaders.getOrPut(key) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(0f, b.top, 0f, b.bottom, intArrayOf(lighten(base, 0.34f), base, darken(base, 0.30f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            }
        }
        canvas.drawPath(path, p)
    }

    // ---------- draw pet ----------

    private fun drawShadow(canvas: Canvas, foreshorten: Float) {
        val cx = fx(50f)
        val cy = fy(117f)
        val key = "gsh$s"
        val p = shaders.getOrPut(key) {
            val r = fr(30f)
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(cx, cy, r, intArrayOf(withA(BLACK, 90), withA(BLACK, 0)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
            }
        }
        canvas.save()
        canvas.scale(foreshorten.coerceIn(0.6f, 1f), 1f, cx, cy)
        canvas.drawOval(RectF(cx - fr(30f), cy - fr(7f), cx + fr(30f), cy + fr(7f)), p)
        canvas.restore()
    }

    private fun drawPet(canvas: Canvas, t: Long, e: Float) {
        val p = palette(animal)
        val lying = mode == Mode.FALL && e > 560f && e < 2100f
        val happyEyes = mode == Mode.HAPPY || mode == Mode.GIGGLE || mode == Mode.HUG ||
            mode == Mode.DANCE || mode == Mode.KISS
        val sleeping = mode == Mode.SLEEP
        val angry = mode == Mode.SULK

        // legs
        val limb = if (animal == PetAnimal.PANDA) p.dark else p.fur
        blob(canvas, fx(37f), fy(101f), fr(11f), fr(11f), limb)
        blob(canvas, fx(63f), fy(101f), fr(11f), fr(11f), limb)
        blob(canvas, fx(37f), fy(102f), fr(6f), fr(6f), p.inner)
        blob(canvas, fx(63f), fy(102f), fr(6f), fr(6f), p.inner)

        drawEars(canvas, p)

        // body
        blob(canvas, fx(50f), fy(80f), fr(24f), fr(23f), p.fur)

        drawArms(canvas, p)

        // belly
        blob(canvas, fx(50f), fy(83f), fr(15f), fr(16f), p.inner)

        // head
        blob(canvas, fx(50f), fy(40f), fr(27f), fr(27f), p.fur)

        if (animal == PetAnimal.PANDA) {
            fill.color = p.dark
            canvas.save(); canvas.rotate(-18f, fx(40f), fy(36f))
            canvas.drawOval(RectF(fx(40f) - fr(8f), fy(36f) - fr(9f), fx(40f) + fr(8f), fy(36f) + fr(9f)), fill)
            canvas.restore()
            canvas.save(); canvas.rotate(18f, fx(60f), fy(36f))
            canvas.drawOval(RectF(fx(60f) - fr(8f), fy(36f) - fr(9f), fx(60f) + fr(8f), fy(36f) + fr(9f)), fill)
            canvas.restore()
        }

        // muzzle
        blob(canvas, fx(50f) + faceShiftX, fy(50f) + faceShiftY, fr(13f), fr(10f), p.inner)

        if (happyEyes && !angry) {
            fill.color = withA(p.blush, 230)
            canvas.drawOval(RectF(fx(31f) + faceShiftX - fr(6f), fy(48f) - fr(4f), fx(31f) + faceShiftX + fr(6f), fy(48f) + fr(4f)), fill)
            canvas.drawOval(RectF(fx(69f) + faceShiftX - fr(6f), fy(48f) - fr(4f), fx(69f) + faceShiftX + fr(6f), fy(48f) + fr(4f)), fill)
        }

        drawEyes(canvas, t, p, happyEyes, sleeping, angry, lying)

        fill.color = p.dark
        canvas.drawOval(RectF(fx(50f) + faceShiftX - fr(4f), fy(45f) + faceShiftY - fr(3f), fx(50f) + faceShiftX + fr(4f), fy(45f) + faceShiftY + fr(3f)), fill)

        if (animal == PetAnimal.CAT) drawWhiskers(canvas, p)

        drawMouth(canvas, p)
    }

    private fun drawEars(canvas: Canvas, p: Palette) {
        val sx2 = faceShiftX * 0.4f
        when (animal) {
            PetAnimal.RABBIT -> {
                for (sgn in listOf(-1f, 1f)) {
                    val cx = fx(50f) + sgn * fr(17f) + sx2
                    val cy = fy(13f)
                    canvas.save()
                    canvas.rotate(sgn * 11f, cx, fy(27f))
                    blob(canvas, cx, cy, fr(6.5f), fr(16f), p.fur)
                    blob(canvas, cx, cy, fr(3f), fr(12f), p.inner)
                    canvas.restore()
                }
            }
            PetAnimal.CAT -> {
                for (sgn in listOf(-1f, 1f)) {
                    val path = Path()
                    path.moveTo(fx(50f) + sgn * fr(21f), fy(34f))
                    path.lineTo(fx(50f) + sgn * fr(16f), fy(6f))
                    path.lineTo(fx(50f) + sgn * fr(3f), fy(24f))
                    path.close()
                    gradPath(canvas, path, lighten(p.fur, 0.3f), p.fur, darken(p.fur, 0.3f))
                    val inner = Path()
                    inner.moveTo(fx(50f) + sgn * fr(17f), fy(29f))
                    inner.lineTo(fx(50f) + sgn * fr(15f), fy(14f))
                    inner.lineTo(fx(50f) + sgn * fr(8f), fy(24f))
                    inner.close()
                    fill.color = p.blush
                    canvas.drawPath(inner, fill)
                }
            }
            PetAnimal.PANDA -> {
                blob(canvas, fx(30f), fy(27f), fr(12f), fr(12f), p.dark)
                blob(canvas, fx(70f), fy(27f), fr(12f), fr(12f), p.dark)
            }
            PetAnimal.BEAR -> {
                blob(canvas, fx(30f), fy(27f), fr(12f), fr(12f), p.fur)
                blob(canvas, fx(70f), fy(27f), fr(12f), fr(12f), p.fur)
                blob(canvas, fx(30f), fy(27f), fr(6f), fr(6f), p.inner)
                blob(canvas, fx(70f), fy(27f), fr(6f), fr(6f), p.inner)
            }
        }
    }

    private fun drawArms(canvas: Canvas, p: Palette) {
        val color = if (animal == PetAnimal.PANDA) p.dark else p.fur
        when (currentArms()) {
            Arms.NORMAL -> {
                canvas.save(); canvas.rotate(-22f, fx(26f), fy(80f))
                blob(canvas, fx(26f), fy(80f), fr(9f), fr(14f), color)
                canvas.restore()
                canvas.save(); canvas.rotate(22f, fx(74f), fy(80f))
                blob(canvas, fx(74f), fy(80f), fr(9f), fr(14f), color)
                canvas.restore()
            }
            Arms.UP -> {
                canvas.save(); canvas.rotate(-40f, fx(26f), fy(70f))
                blob(canvas, fx(24f), fy(62f), fr(8f), fr(13f), color)
                canvas.restore()
                canvas.save(); canvas.rotate(40f, fx(74f), fy(70f))
                blob(canvas, fx(76f), fy(62f), fr(8f), fr(13f), color)
                canvas.restore()
            }
            Arms.WRAP -> {
                canvas.save(); canvas.rotate(58f, fx(26f), fy(80f))
                blob(canvas, fx(26f), fy(80f), fr(9f), fr(14f), color)
                canvas.restore()
                canvas.save(); canvas.rotate(-58f, fx(74f), fy(80f))
                blob(canvas, fx(74f), fy(80f), fr(9f), fr(14f), color)
                canvas.restore()
            }
            Arms.CROSS -> {
                canvas.save(); canvas.rotate(80f, fx(38f), fy(74f))
                blob(canvas, fx(38f), fy(74f), fr(9f), fr(12f), color)
                canvas.restore()
                canvas.save(); canvas.rotate(-80f, fx(62f), fy(70f))
                blob(canvas, fx(62f), fy(70f), fr(9f), fr(12f), color)
                canvas.restore()
            }
        }
    }

    private fun drawEyes(canvas: Canvas, t: Long, p: Palette, happy: Boolean, sleeping: Boolean, angry: Boolean, lying: Boolean) {
        val dx = faceShiftX
        val dy = faceShiftY
        val lx = fx(40f) + dx; val rx = fx(60f) + dx; val ey = fy(36f) + dy
        val ec = p.dark
        if (lying) {
            line.color = ec; line.strokeWidth = fr(1.8f)
            for (c in listOf(lx, rx)) {
                canvas.drawLine(c - fr(3f), ey - fr(3f), c + fr(3f), ey + fr(3f), line)
                canvas.drawLine(c - fr(3f), ey + fr(3f), c + fr(3f), ey - fr(3f), line)
            }
            return
        }
        if (sleeping) {
            line.color = ec; line.strokeWidth = fr(2f)
            for (c in listOf(lx, rx)) {
                val pth = Path(); pth.moveTo(c - fr(4f), ey); pth.quadTo(c, ey + fr(3.5f), c + fr(4f), ey)
                canvas.drawPath(pth, line)
            }
            return
        }
        if (angry) {
            line.color = ec; line.strokeWidth = fr(2.2f)
            canvas.drawLine(lx - fr(4f), ey - fr(3f), lx + fr(4f), ey + fr(1f), line)
            canvas.drawLine(rx - fr(4f), ey + fr(1f), rx + fr(4f), ey - fr(3f), line)
            return
        }
        if (happy) {
            line.color = ec; line.strokeWidth = fr(2f)
            for (c in listOf(lx, rx)) {
                val pth = Path(); pth.moveTo(c - fr(4f), ey + fr(1f)); pth.quadTo(c, ey - fr(4f), c + fr(4f), ey + fr(1f))
                canvas.drawPath(pth, line)
            }
            return
        }
        if (animal == PetAnimal.PANDA) {
            fill.color = WHITE
            canvas.drawCircle(lx, ey, fr(3.6f), fill)
            canvas.drawCircle(rx, ey, fr(3.6f), fill)
            fill.color = ec
            canvas.drawCircle(lx, ey, fr(1.7f), fill)
            canvas.drawCircle(rx, ey, fr(1.7f), fill)
            return
        }
        val blink = t >= blinkAt && t < blinkAt + 150
        if (blink) {
            line.color = ec; line.strokeWidth = fr(2f)
            canvas.drawLine(lx - fr(3.5f), ey, lx + fr(3.5f), ey, line)
            canvas.drawLine(rx - fr(3.5f), ey, rx + fr(3.5f), ey, line)
        } else {
            fill.color = ec
            canvas.drawCircle(lx, ey, fr(3.2f), fill)
            canvas.drawCircle(rx, ey, fr(3.2f), fill)
            fill.color = WHITE
            canvas.drawCircle(lx + fr(1f), ey - fr(1f), fr(1f), fill)
            canvas.drawCircle(rx + fr(1f), ey - fr(1f), fr(1f), fill)
        }
        if (t >= blinkAt + 160) blinkAt = t + 1800 + Random.nextLong(2600)
    }

    private fun drawWhiskers(canvas: Canvas, p: Palette) {
        line.color = p.fur2; line.strokeWidth = fr(0.9f)
        for (sgn in listOf(-1f, 1f)) {
            for (dy in listOf(-3f, 0f, 3f)) {
                canvas.drawLine(
                    fx(50f) + faceShiftX + sgn * fr(10f), fy(49f) + faceShiftY + fr(dy),
                    fx(50f) + faceShiftX + sgn * fr(24f), fy(49f) + faceShiftY + fr(dy * 1.6f), line
                )
            }
        }
    }

    private fun drawMouth(canvas: Canvas, p: Palette) {
        val dx = faceShiftX; val dy = faceShiftY
        line.color = p.dark; line.strokeWidth = fr(1.6f)
        when (mode) {
            Mode.GIGGLE -> {
                fill.color = p.dark
                canvas.drawOval(RectF(fx(50f) + dx - fr(5f), fy(52f) + dy - fr(4f), fx(50f) + dx + fr(5f), fy(52f) + dy + fr(6f)), fill)
                fill.color = 0xFFD66078.toInt()
                canvas.drawOval(RectF(fx(50f) + dx - fr(3f), fy(54f) + dy - fr(1f), fx(50f) + dx + fr(3f), fy(56f) + dy + fr(1f)), fill)
            }
            Mode.HAPPY, Mode.HUG, Mode.DANCE -> {
                val m = Path(); m.moveTo(fx(44f) + dx, fy(50f) + dy); m.quadTo(fx(50f) + dx, fy(56f) + dy, fx(56f) + dx, fy(50f) + dy)
                canvas.drawPath(m, line)
            }
            Mode.KISS -> {
                fill.color = p.dark
                canvas.drawOval(RectF(fx(50f) + dx - fr(3f), fy(51f) + dy - fr(2.5f), fx(50f) + dx + fr(3f), fy(51f) + dy + fr(3f)), fill)
            }
            Mode.SULK -> {
                val m = Path(); m.moveTo(fx(45f) + dx, fy(55f) + dy); m.quadTo(fx(50f) + dx, fy(51f) + dy, fx(55f) + dx, fy(55f) + dy)
                canvas.drawPath(m, line)
            }
            Mode.SLEEP -> {
                fill.color = p.dark
                canvas.drawOval(RectF(fx(50f) + dx - fr(2.5f), fy(51f) + dy - fr(2f), fx(50f) + dx + fr(2.5f), fy(51f) + dy + fr(3f)), fill)
            }
            else -> {
                val m = Path()
                m.moveTo(fx(50f) + dx, fy(48f) + dy); m.lineTo(fx(50f) + dx, fy(51f) + dy)
                m.moveTo(fx(46f) + dx, fy(53f) + dy); m.quadTo(fx(50f) + dx, fy(56f) + dy, fx(54f) + dx, fy(53f) + dy)
                canvas.drawPath(m, line)
            }
        }
    }

    // ---------- particles ----------

    private fun spawn(t: Long, e: Float) {
        val interval = when (mode) {
            Mode.HAPPY, Mode.HUG -> 200L
            Mode.GIGGLE -> 150L
            Mode.DANCE -> 220L
            Mode.JUMP -> 300L
            Mode.SLEEP -> 620L
            Mode.SULK -> 520L
            Mode.KISS -> 400L
            else -> 0L
        }
        if (interval == 0L || t - lastSpawn < interval) return
        lastSpawn = t
        when (mode) {
            Mode.HAPPY -> spawnHearts(1, 0.7f)
            Mode.GIGGLE -> spawnHearts(2, 0.9f)
            Mode.HUG -> { spawnHearts(3, 1.0f); if (Random.nextBoolean()) spawnKind(K_STAR, 1) }
            Mode.DANCE -> { spawnKind(K_NOTE, 2); if (Random.nextBoolean()) spawnKind(K_STAR, 1) }
            Mode.JUMP -> { spawnKind(K_STAR, 2); if (Random.nextBoolean()) spawnKind(K_STAR, 1) }
            Mode.SLEEP -> spawnKind(K_ZZZ, 1)
            Mode.SULK -> spawnKind(K_PUFF, 1)
            Mode.KISS -> spawnKiss()
            else -> {}
        }
    }

    private fun spawnHearts(n: Int, spread: Float) {
        repeat(n) {
            val cx = fx(50f) + (Random.nextFloat() - 0.5f) * fr(48f * spread)
            val cy = fy(30f) + (Random.nextFloat() - 0.5f) * fr(20f)
            parts.add(Particle(cx, cy, (Random.nextFloat() - 0.5f) * fr(0.5f), -fr(0.6f) - Random.nextFloat() * fr(0.6f),
                0f, K_HEART, fr(5f) + Random.nextFloat() * fr(4f), 1f, (Random.nextFloat() - 0.5f) * 30f, 1.6f, null))
        }
    }

    private fun spawnKind(kind: Int, n: Int) {
        repeat(n) {
            val right = Random.nextBoolean()
            val cx = fx(if (right) 68f else 32f) + (Random.nextFloat() - 0.5f) * fr(10f)
            val cy = fy(30f) + (Random.nextFloat() - 0.5f) * fr(24f)
            val grav = if (kind == K_STAR) fr(0.35f) else 0f
            val life = if (kind == K_PUFF) 1.4f else 1f
            parts.add(Particle(cx, cy, if (right) fr(0.35f) else -fr(0.35f), -fr(0.5f) - Random.nextFloat() * fr(0.5f),
                grav, kind, fr(5f) + Random.nextFloat() * fr(3f), life, (Random.nextFloat() - 0.5f) * 20f, 1.2f,
                when (kind) { K_ZZZ -> "z"; K_NOTE -> if (Random.nextBoolean()) "♪" else "♫"; else -> null }))
        }
    }

    private fun spawnKiss() {
        parts.add(Particle(fx(50f), fy(45f), fr(1.2f), -fr(1.6f), 0f, K_KISS, fr(9f), 1f, 0f, 1.5f, null))
    }

    private fun update(dt: Long, t: Long) {
        if (mode != Mode.IDLE && t >= modeEnd) mode = Mode.IDLE
        if (dt <= 0L) return
        val step = dt / 16.6f
        if (!dragging) {
            dragYaw += (0f - dragYaw) * 0.10f * step
            dragPitch += (0f - dragPitch) * 0.10f * step
            if (abs(dragYaw) < 0.002f) dragYaw = 0f
            if (abs(dragPitch) < 0.002f) dragPitch = 0f
        }
        val it = parts.iterator()
        while (it.hasNext()) {
            val h = it.next()
            h.vy += h.grav * step
            h.x += h.vx * step
            h.y += h.vy * step
            h.life -= (if (h.kind == K_PUFF) 0.010f else 0.013f) * step
            h.rot += h.spin * step
            if (h.life <= 0f || h.y < -fr(25f) || h.y > height + fr(25f) || h.x < -fr(25f) || h.x > width + fr(25f)) it.remove()
        }
        if (parts.size > 120) parts.subList(0, parts.size - 120).clear()
    }

    private fun drawParts(canvas: Canvas) {
        for (h in parts) {
            val a = (h.life.coerceIn(0f, 1f) * 255f).toInt()
            when (h.kind) {
                K_HEART, K_KISS -> {
                    fill.color = withA(HEART_PINK, a)
                    val pth = heartPath(h.x, h.y, h.size)
                    canvas.save(); canvas.rotate(h.rot, h.x, h.y); canvas.drawPath(pth, fill); canvas.restore()
                }
                K_STAR -> {
                    fill.color = withA(GOLD, a)
                    val pth = starPath(h.x, h.y, h.size, h.size * 0.45f)
                    canvas.save(); canvas.rotate(h.rot, h.x, h.y); canvas.drawPath(pth, fill); canvas.restore()
                }
                K_NOTE, K_ZZZ -> {
                    glyph.color = withA(if (h.kind == K_ZZZ) GRAY else ACCENT, a)
                    glyph.textSize = h.size * 2.2f
                    canvas.save(); canvas.rotate(h.rot, h.x, h.y)
                    canvas.drawText(h.text ?: "", h.x, h.y, glyph)
                    canvas.restore()
                }
                K_PUFF -> {
                    fill.color = withA(GRAY, (a * 0.5f).toInt())
                    canvas.drawCircle(h.x, h.y, h.size * 1.4f, fill)
                }
            }
        }
    }

    private fun heartPath(cx: Float, cy: Float, sz: Float): Path {
        val p = Path()
        p.moveTo(cx, cy + sz * 0.3f)
        p.cubicTo(cx - sz, cy - sz * 0.6f, cx - sz * 0.4f, cy - sz * 1.3f, cx, cy - sz * 0.55f)
        p.cubicTo(cx + sz * 0.4f, cy - sz * 1.3f, cx + sz, cy - sz * 0.6f, cx, cy + sz * 0.3f)
        p.close()
        return p
    }

    private fun starPath(cx: Float, cy: Float, r: Float, r2: Float): Path {
        val p = Path()
        for (i in 0 until 10) {
            val ang = Math.toRadians((-90 + i * 36).toDouble())
            val rad = if (i % 2 == 0) r else r2
            val x = cx + rad * Math.cos(ang).toFloat()
            val y = cy + rad * Math.sin(ang).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return p
    }

    private fun drawFloatingText(canvas: Canvas, e: Float) {
        if (mode == Mode.GIGGLE) {
            glyph.color = 0xFF976C45.toInt(); glyph.textSize = fr(9f)
            canvas.drawText("hihi!", fx(72f), fy(28f), glyph)
        }
        if (mode == Mode.FALL && e in 560f..1500f) {
            glyph.color = 0xFF976C45.toInt(); glyph.textSize = fr(9f)
            canvas.drawText("ay!", fx(74f), fy(30f), glyph)
        }
        if (mode == Mode.SULK) {
            glyph.color = 0xFF976C45.toInt(); glyph.textSize = fr(9f)
            canvas.drawText("hıh!", fx(70f), fy(24f), glyph)
        }
    }

    private fun drawBubble(canvas: Canvas, t: Long) {
        if (bubbleText.isEmpty() || t > bubbleUntil) return
        val age = (t - bubbleShown).toFloat()
        val left = (bubbleUntil - t).toFloat()
        var a = 1f
        if (age < 260f) a = age / 260f
        if (left < 380f) a = min(a, left / 380f)
        a = a.coerceIn(0f, 1f)

        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withA(INK, (a * 255f).toInt())
            textSize = fr(7.5f)
            typeface = Typeface.DEFAULT_BOLD
        }
        val maxW = (width * 0.72f).toInt().coerceAtLeast((width * 0.5f).toInt())
        val layout = StaticLayout.Builder.obtain(bubbleText, 0, bubbleText.length, tp, maxW)
            .setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        val padX = fr(7f); val padY = fr(5.5f)
        val bw = min(layout.width + padX * 2f, width - fr(16f))
        val bh = layout.height + padY * 2f
        val cx = width / 2f
        val by = oy - fr(62f)
        val rect = RectF(cx - bw / 2f, by - bh / 2f, cx + bw / 2f, by + bh / 2f)

        fill.style = Paint.Style.FILL
        fill.color = withA(BUBBLE_BG, (a * 255f).toInt())
        canvas.drawRoundRect(rect, fr(8f), fr(8f), fill)
        line.color = withA(ACCENT, (a * 255f).toInt()); line.strokeWidth = fr(1.4f)
        canvas.drawRoundRect(rect, fr(8f), fr(8f), line)
        val tail = Path()
        tail.moveTo(cx - fr(4f), rect.bottom - fr(1f))
        tail.lineTo(cx + fr(4f), rect.bottom - fr(1f))
        tail.lineTo(cx, rect.bottom + fr(7f))
        tail.close()
        fill.color = withA(BUBBLE_BG, (a * 255f).toInt())
        canvas.drawPath(tail, fill)

        canvas.save()
        canvas.translate(rect.left + (bw - layout.width) / 2f, rect.top + padY)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun withA(color: Int, a: Int): Int =
        Color.argb(a.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
}
