/*
    Copyright (C) 2018, 2019 Ilya Zhuravlev

    This file is part of OpenMW-Android.

    OpenMW-Android is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    OpenMW-Android is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with OpenMW-Android.  If not, see <https://www.gnu.org/licenses/>.
*/

package ui.controls

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View

/**
 * Shared touch tracker for both on-screen sticks.
 *
 * Android can reorder pointer indices whenever another finger is added/removed.
 * Using event.x/event.y therefore occasionally makes a stick follow the wrong
 * finger or stop updating.  Keep a stable pointerId instead and explicitly
 * recover/handoff when Android changes the active pointer set.
 */
open class Joystick : View {

    private companion object {
        const val INVALID_POINTER_ID = -1
    }

    // Initial touch position
    protected var initialX = 0f
    protected var initialY = 0f

    // Current touch position
    protected var currentX = -1f
    protected var currentY = -1f

    // Whether the finger is down
    protected var down = false

    // left or right stick
    protected var stickId = 0

    // Pointer IDs are stable for the lifetime of a touch; pointer indices are not.
    private var activePointerId = INVALID_POINTER_ID

    // width of a stroke to draw stick circles
    private var strokeWidth = 0

    private var showVisuals = true

    private val paint = Paint()

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(context, attrs, defStyle) {
        init()
    }

    private fun init() {
        strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, context.resources.displayMetrics).toInt()
    }

    fun setStick(id: Int) {
        stickId = id
    }

    fun setShowVisuals(show: Boolean) {
        showVisuals = show
    }

    public override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!showVisuals) {
            return
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth.toFloat()
        paint.color = Color.GRAY

        if (down) {
            // Draw initial touch
            canvas.drawCircle(initialX, initialY, width / 10f, paint)

            // Draw current stick position
            canvas.drawCircle(currentX, currentY, width / 5f, paint)
        } else {
            // Draw the outline
            canvas.drawCircle(width / 2f, height / 2f,
                width / 2f - strokeWidth, paint)
        }
    }

    public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
    }

    /** Called whenever this joystick starts tracking a (possibly replacement) finger. */
    protected open fun onTrackedPointerDown(x: Float, y: Float) {
        initialX = x
        initialY = y
        currentX = x
        currentY = y
        down = true
    }

    /** Called for movement of the stable active pointer only. */
    protected open fun onTrackedPointerMove(x: Float, y: Float) {
        currentX = x
        currentY = y
    }

    /** Hook for subclasses such as the relative-mouse right stick. */
    protected open fun onTrackedPointerUp(cancelled: Boolean) {}

    private fun beginTracking(event: MotionEvent, pointerIndex: Int) {
        if (pointerIndex < 0 || pointerIndex >= event.pointerCount)
            return

        activePointerId = event.getPointerId(pointerIndex)
        onTrackedPointerDown(event.getX(pointerIndex), event.getY(pointerIndex))

        // A joystick owns this gesture until its tracked finger really goes away.
        // Prevent transient parent interception from generating avoidable CANCELs.
        parent?.requestDisallowInterceptTouchEvent(true)
    }

    private fun finishTracking(cancelled: Boolean) {
        if (activePointerId == INVALID_POINTER_ID && !down)
            return

        onTrackedPointerUp(cancelled)
        activePointerId = INVALID_POINTER_ID
        down = false
        currentX = -1f
        currentY = -1f
        // Do not clear disallow-intercept here: the other joystick may still be
        // tracking another finger in the same multi-touch stream. Android resets
        // this flag when the overall gesture ends.
    }

    /**
     * Select another pointer when the tracked one disappears while the gesture
     * still contains fingers.  Re-anchor at the new finger to avoid a movement
     * or camera jump during handoff.
     */
    private fun handoffToAnotherPointer(event: MotionEvent, excludedIndex: Int): Boolean {
        if (event.pointerCount <= 1)
            return false

        var bestIndex = -1
        var bestDistanceSq = Float.MAX_VALUE

        // Prefer the pointer physically closest to where the old finger was.
        // This is more robust than blindly using index 0 after Android reorders indices.
        for (i in 0 until event.pointerCount) {
            if (i == excludedIndex)
                continue

            val dx = if (currentX >= 0f) event.getX(i) - currentX else 0f
            val dy = if (currentY >= 0f) event.getY(i) - currentY else 0f
            val distanceSq = dx * dx + dy * dy
            if (bestIndex == -1 || distanceSq < bestDistanceSq) {
                bestIndex = i
                bestDistanceSq = distanceSq
            }
        }

        if (bestIndex == -1)
            return false

        beginTracking(event, bestIndex)
        return true
    }

    /** Recover if an OEM/parent delivered MOVE after silently changing pointer IDs. */
    private fun recoverMissingPointer(event: MotionEvent): Int {
        if (event.pointerCount == 0)
            return -1

        var bestIndex = 0
        var bestDistanceSq = Float.MAX_VALUE
        for (i in 0 until event.pointerCount) {
            val dx = if (currentX >= 0f) event.getX(i) - currentX else 0f
            val dy = if (currentY >= 0f) event.getY(i) - currentY else 0f
            val distanceSq = dx * dx + dy * dy
            if (distanceSq < bestDistanceSq) {
                bestDistanceSq = distanceSq
                bestIndex = i
            }
        }

        beginTracking(event, bestIndex)
        return bestIndex
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                beginTracking(event, event.actionIndex)
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Normally each half-screen joystick receives its own split gesture.
                // If the active pointer was lost, immediately adopt the new one.
                if (activePointerId == INVALID_POINTER_ID)
                    beginTracking(event, event.actionIndex)
            }

            MotionEvent.ACTION_MOVE -> {
                var pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0)
                    pointerIndex = recoverMissingPointer(event)

                if (pointerIndex >= 0)
                    onTrackedPointerMove(event.getX(pointerIndex), event.getY(pointerIndex))
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val liftedIndex = event.actionIndex
                val liftedId = event.getPointerId(liftedIndex)
                if (liftedId == activePointerId) {
                    if (!handoffToAnotherPointer(event, liftedIndex))
                        finishTracking(false)
                }
            }

            MotionEvent.ACTION_UP -> {
                finishTracking(false)
            }

            MotionEvent.ACTION_CANCEL -> {
                // Always release axes/mouse tracking on interruption.  The old
                // implementation ignored CANCEL and could leave a dead/stuck stick.
                finishTracking(true)
            }
        }

        updateStick()
        invalidate()
        return true
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus && down) {
            finishTracking(true)
            updateStick()
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        if (down) {
            finishTracking(true)
            updateStick()
        }
        super.onDetachedFromWindow()
    }

    protected open fun updateStick() {}
}
