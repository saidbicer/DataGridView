package com.said.datagridview

import android.animation.*
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.util.Log
import android.util.SparseArray
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.widget.TableRow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class DragLinearLayout constructor(context: Context?, attrs: AttributeSet? = null) :
    TableRow(context, attrs) {
    private val mNominalDistanceScaled: Float
    private var mSwapListener: OnViewSwapListener? = null
    private var mLayoutTransition: LayoutTransition? = null

    /**
     * Mapping from child index to drag-related info container.
     * Presence of mapping implies the child can be dragged, and is considered for swaps with the
     * currently dragged item.
     */
    private val mDraggableChildren: SparseArray<DraggableChild> = SparseArray()

    private class DraggableChild {
        /**
         * If non-null, a reference to an on-going mPosition animation.
         */
        var mValueAnimator: ValueAnimator? = null
        fun endExistingAnimation() {
            mValueAnimator?.end()
        }

        fun cancelExistingAnimation() {
            mValueAnimator?.cancel()
        }
    }

    /**
     * Holds state information about the currently dragged item.
     *
     *
     * Rough lifecycle:
     *  * #startDetectingOnPossibleDrag - #mDetecting == true
     *  *      if drag is recognised, #onDragStart - #mDragging == true
     *  *      if drag ends, #onDragStop - #mDragging == false, #settling == true
     *  * if gesture ends without drag, or settling finishes, #stopDetecting - #mDetecting == false
     */
    private inner class DragItem {
        var mView: View? = null
        var mStartVisibility = 0
        var mBitmapDrawable: BitmapDrawable? = null
        var mPosition = 0
        var mStartTop = 0
        var mHeight = 0
        var mTotalDragOffset = 0
        var mTargetTopOffset = 0
        var mStartLeft = 0
        var mWidth = 0
        var mTargetLeftOffset = 0
        var mSettleAnimation: ValueAnimator? = null
        var mDetecting = false
        var mDragging = false

        fun startDetectingOnPossibleDrag(view: View, position: Int) {
            mView = view
            mStartVisibility = view.visibility
            mBitmapDrawable = getDragDrawable(view)
            mPosition = position
            mStartTop = view.top
            mHeight = view.height
            mStartLeft = view.left
            mWidth = view.width
            mTotalDragOffset = 0
            mTargetTopOffset = 0
            mTargetLeftOffset = 0
            mSettleAnimation = null
            mDetecting = true
        }

        fun onDragStart() {
            mView!!.visibility = INVISIBLE
            mDragging = true
        }

        fun setTotalOffset(offset: Int) {
            mTotalDragOffset = offset
            updateTargetLocation()
        }

        fun updateTargetLocation() {
            if (orientation == VERTICAL) {
                updateTargetTop()
            } else {
                updateTargetLeft()
            }
        }

        private fun updateTargetLeft() {
            mTargetLeftOffset = mStartLeft - mView!!.left + mTotalDragOffset
        }

        private fun updateTargetTop() {
            mTargetTopOffset = mStartTop - mView!!.top + mTotalDragOffset
        }

        fun onDragStop() {
            mDragging = false
        }

        fun settling(): Boolean {
            return null != mSettleAnimation
        }

        fun stopDetecting() {
            mDetecting = false
            if (null != mView) mView!!.visibility = mStartVisibility
            mView = null
            mStartVisibility = -1
            mBitmapDrawable = null
            mPosition = -1
            mStartTop = -1
            mHeight = -1
            mStartLeft = -1
            mWidth = -1
            mTotalDragOffset = 0
            mTargetTopOffset = 0
            mTargetLeftOffset = 0
            if (null != mSettleAnimation) mSettleAnimation!!.end()
            mSettleAnimation = null
        }

        init {
            stopDetecting()
        }
    }

    private val mDragItem: DragItem
    private val mSlop: Int
    private var mDownY = -1
    private var mDownX = -1
    private var mActivePointerId = INVALID_POINTER_ID

    /**
     * Makes the child a candidate for mDragging. Must be an existing child of this layout.
     */
    fun setViewDraggable(child: View, dragHandle: View) {
        if (this === child.parent) {
            dragHandle.setOnTouchListener(DragHandleOnTouchListener(child))
            mDraggableChildren.put(indexOfChild(child), DraggableChild())
        } else {
            Log.e(
                TAG,
                "$child is not a child, cannot make draggable."
            )
        }
    }

    override fun removeAllViews() {
        val count = childCount
        for (i in 0 until count) {
            getChildAt(i).setOnLongClickListener(null)
            getChildAt(i).setOnTouchListener(null)
        }
        super.removeAllViews()
        mDraggableChildren.clear()
    }

    fun setOnViewSwapListener(swapListener: OnViewSwapListener?) {
        mSwapListener = swapListener
    }

    /**
     * A linear relationship b/w distance and duration, bounded.
     */
    private fun getTranslateAnimationDuration(distance: Float): Long {
        return min(
            MAX_SWITCH_DURATION,
            max(
                MIN_SWITCH_DURATION,
                (NOMINAL_SWITCH_DURATION * abs(distance) / mNominalDistanceScaled).toLong()
            )
        )
    }

    fun startDetectingDrag(child: View) {
        if (mDragItem.mDetecting) return  // existing drag in process, only one at a time is allowed
        val position = indexOfChild(child)

        // complete any existing animations, both for the newly selected child and the previous dragged one
        mDraggableChildren[position].endExistingAnimation()
        mDragItem.startDetectingOnPossibleDrag(child, position)
    }

    private fun startDrag() {
        // remove layout transition, it conflicts with drag animation
        // we will restore it after drag animation end, see onDragStop()
        mLayoutTransition = layoutTransition
        if (mLayoutTransition != null) {
            layoutTransition = null
        }
        mDragItem.onDragStart()
        requestDisallowInterceptTouchEvent(true)
    }

    private fun onDragStop() {
        if (orientation == VERTICAL) {
            mDragItem.mSettleAnimation = ValueAnimator.ofFloat(
                mDragItem.mTotalDragOffset.toFloat(), (
                        mDragItem.mTotalDragOffset - mDragItem.mTargetTopOffset).toFloat()
            )
                .setDuration(getTranslateAnimationDuration(mDragItem.mTargetTopOffset.toFloat()))
        } else {
            mDragItem.mSettleAnimation = ValueAnimator.ofFloat(
                mDragItem.mTotalDragOffset.toFloat(), (
                        mDragItem.mTotalDragOffset - mDragItem.mTargetLeftOffset).toFloat()
            )
                .setDuration(getTranslateAnimationDuration(mDragItem.mTargetLeftOffset.toFloat()))
        }
        mDragItem.mSettleAnimation?.addUpdateListener(AnimatorUpdateListener { animation ->
            if (!mDragItem.mDetecting) return@AnimatorUpdateListener  // already stopped
            mDragItem.setTotalOffset((animation.animatedValue as Float).toInt())
            invalidate()
        })
        mDragItem.mSettleAnimation?.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                mDragItem.onDragStop()
            }

            override fun onAnimationEnd(animation: Animator) {
                if (!mDragItem.mDetecting) {
                    return  // already stopped
                }
                mDragItem.mSettleAnimation = null
                mDragItem.stopDetecting()

                // restore layout transition
                if (mLayoutTransition != null && layoutTransition == null) {
                    layoutTransition = mLayoutTransition
                }
                if (mSwapListener != null) {
                    mSwapListener!!.onSwapFinish()
                }
            }
        })
        mDragItem.mSettleAnimation?.start()
    }

    /**
     * Updates the dragged item with the given total offset from its starting mPosition.
     * Evaluates and executes draggable mView swaps.
     */
    private fun onDrag(offset: Int) {
        if (orientation == VERTICAL) {
            mDragItem.setTotalOffset(offset)
            invalidate()
            val currentTop = mDragItem.mStartTop + mDragItem.mTotalDragOffset
            val belowPosition = nextDraggablePosition(mDragItem.mPosition)
            val abovePosition = previousDraggablePosition(mDragItem.mPosition)
            val belowView = getChildAt(belowPosition)
            val aboveView = getChildAt(abovePosition)
            val isBelow = belowView != null &&
                    currentTop + mDragItem.mHeight > belowView.top + belowView.height / 2
            val isAbove = aboveView != null &&
                    currentTop < aboveView.top + aboveView.height / 2
            if (isBelow || isAbove) {
                val switchView = if (isBelow) belowView else aboveView

                // swap elements
                val originalPosition = mDragItem.mPosition
                val switchPosition = if (isBelow) belowPosition else abovePosition
                mDraggableChildren[switchPosition].cancelExistingAnimation()
                val switchViewStartY = switchView!!.y
                if (null != mSwapListener) {
                    mSwapListener!!.onSwap(
                        mDragItem.mView,
                        mDragItem.mPosition,
                        switchView,
                        switchPosition
                    )
                }
                if (isBelow) {
                    removeViewAt(originalPosition)
                    removeViewAt(switchPosition - 1)
                    addView(belowView, originalPosition)
                    addView(mDragItem.mView, switchPosition)
                } else {
                    removeViewAt(switchPosition)
                    removeViewAt(originalPosition - 1)
                    addView(mDragItem.mView, switchPosition)
                    addView(aboveView, originalPosition)
                }
                mDragItem.mPosition = switchPosition
                val switchViewObserver = switchView.viewTreeObserver
                switchViewObserver.addOnPreDrawListener(object :
                    ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        switchViewObserver.removeOnPreDrawListener(this)
                        val switchAnimator = ObjectAnimator.ofFloat(
                            switchView, "y",
                            switchViewStartY, switchView.top.toFloat()
                        )
                            .setDuration(getTranslateAnimationDuration(switchView.top - switchViewStartY))
                        switchAnimator.addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationStart(animation: Animator) {
                                mDraggableChildren[originalPosition].mValueAnimator = switchAnimator
                            }

                            override fun onAnimationEnd(animation: Animator) {
                                mDraggableChildren[originalPosition].mValueAnimator = null
                            }
                        })
                        switchAnimator.start()
                        return true
                    }
                })
                val observer = mDragItem.mView!!.viewTreeObserver
                observer.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        observer.removeOnPreDrawListener(this)
                        mDragItem.updateTargetLocation()

                        // TODO test if still necessary..
                        // because mDragItem#mView#getTop() is only up-to-date NOW
                        // (and not right after the #addView() swaps above)
                        // we may need to update an ongoing settle animation
                        if (mDragItem.settling()) {
                            Log.d(TAG, "Updating settle animation")
                            mDragItem.mSettleAnimation!!.removeAllListeners()
                            mDragItem.mSettleAnimation!!.cancel()
                            onDragStop()
                        }
                        return true
                    }
                })
            }
        } else {
            mDragItem.setTotalOffset(offset)
            invalidate()
            val currentLeft = mDragItem.mStartLeft + mDragItem.mTotalDragOffset
            val nextPosition = nextDraggablePosition(mDragItem.mPosition)
            val prePosition = previousDraggablePosition(mDragItem.mPosition)
            val nextView = getChildAt(nextPosition)
            val preView = getChildAt(prePosition)
            val isToNext = nextView != null &&
                    currentLeft + mDragItem.mWidth > nextView.left + nextView.width / 2
            val isToPre = preView != null &&
                    currentLeft < preView.left + preView.width / 2
            if (isToNext || isToPre) {
                val switchView = if (isToNext) nextView else preView
                // swap elements
                val originalPosition = mDragItem.mPosition
                val switchPosition = if (isToNext) nextPosition else prePosition
                mDraggableChildren[switchPosition].cancelExistingAnimation()
                val switchViewStartX = switchView!!.x
                if (null != mSwapListener) {
                    mSwapListener!!.onSwap(
                        mDragItem.mView,
                        mDragItem.mPosition,
                        switchView,
                        switchPosition
                    )
                }
                if (isToNext) {
                    removeViewAt(originalPosition)
                    removeViewAt(switchPosition - 1)
                    addView(nextView, originalPosition)
                    addView(mDragItem.mView, switchPosition)
                } else {
                    removeViewAt(switchPosition)
                    removeViewAt(originalPosition - 1)
                    addView(mDragItem.mView, switchPosition)
                    addView(preView, originalPosition)
                }
                mDragItem.mPosition = switchPosition
                val switchViewObserver = switchView.viewTreeObserver
                switchViewObserver.addOnPreDrawListener(object :
                    ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        switchViewObserver.removeOnPreDrawListener(this)
                        val switchAnimator = ObjectAnimator.ofFloat(
                            switchView, "x",
                            switchViewStartX, switchView.left.toFloat()
                        )
                            .setDuration(getTranslateAnimationDuration(switchView.left - switchViewStartX))
                        switchAnimator.addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationStart(animation: Animator) {
                                mDraggableChildren[originalPosition].mValueAnimator = switchAnimator
                            }

                            override fun onAnimationEnd(animation: Animator) {
                                mDraggableChildren[originalPosition].mValueAnimator = null
                            }
                        })
                        switchAnimator.start()
                        return true
                    }
                })
                val observer = mDragItem.mView!!.viewTreeObserver
                observer.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        observer.removeOnPreDrawListener(this)
                        mDragItem.updateTargetLocation()
                        if (mDragItem.settling()) {
                            mDragItem.mSettleAnimation!!.removeAllListeners()
                            mDragItem.mSettleAnimation!!.cancel()
                            onDragStop()
                        }
                        return true
                    }
                })
            }
        }
    }

    private fun previousDraggablePosition(position: Int): Int {
        val startIndex = mDraggableChildren.indexOfKey(position)
        return if (startIndex < 1 || startIndex > mDraggableChildren.size()) -1 else mDraggableChildren.keyAt(
            startIndex - 1
        )
    }

    private fun nextDraggablePosition(position: Int): Int {
        val startIndex = mDraggableChildren.indexOfKey(position)
        return if (startIndex < -1 || startIndex > mDraggableChildren.size() - 2) -1 else mDraggableChildren.keyAt(
            startIndex + 1
        )
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (mDragItem.mDetecting && (mDragItem.mDragging || mDragItem.settling())) {
            canvas.save()
            if (orientation == VERTICAL) {
                canvas.translate(0f, mDragItem.mTotalDragOffset.toFloat())
            } else {
                canvas.translate(mDragItem.mTotalDragOffset.toFloat(), 0f)
            }
            mDragItem.mBitmapDrawable!!.draw(canvas)
            canvas.restore()
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                if (mDragItem.mDetecting) return false // an existing item is (likely) settling
                mDownY = event.getY(0).toInt()
                mDownX = event.getX(0).toInt()
                mActivePointerId = event.getPointerId(0)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!mDragItem.mDetecting) return false
                if (INVALID_POINTER_ID == mActivePointerId) return false
                val pointerIndex = event.findPointerIndex(mActivePointerId)
                val y = event.getY(pointerIndex)
                val x = event.getX(pointerIndex)
                val dy = y - mDownY
                val dx = x - mDownX
                if (orientation == VERTICAL) {
                    if (Math.abs(dy) > mSlop) {
                        startDrag()
                        return true
                    }
                } else {
                    if (Math.abs(dx) > mSlop) {
                        startDrag()
                        return true
                    }
                }
                return false
            }
            MotionEvent.ACTION_POINTER_UP -> {
                run {
                    val pointerIndex = event.actionIndex
                    val pointerId = event.getPointerId(pointerIndex)
                    if (pointerId != mActivePointerId) return false // if active pointer, fall through and cancel!
                }
                run {
                    parent.requestDisallowInterceptTouchEvent(false)
                    onTouchEnd()
                    if (mDragItem.mDetecting) mDragItem.stopDetecting()
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                parent.requestDisallowInterceptTouchEvent(false)
                onTouchEnd()
                if (mDragItem.mDetecting) mDragItem.stopDetecting()
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!mDragItem.mDetecting || mDragItem.settling()) return false
                startDrag()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!mDragItem.mDragging) return false
                if (INVALID_POINTER_ID == mActivePointerId) return false
                val pointerIndex = event.findPointerIndex(mActivePointerId)
                val lastEventY = event.getY(pointerIndex).toInt()
                val lastEventX = event.getX(pointerIndex).toInt()
                if (orientation == VERTICAL) {
                    val deltaY = lastEventY - mDownY
                    onDrag(deltaY)
                } else {
                    val deltaX = lastEventX - mDownX
                    onDrag(deltaX)
                }
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                run {
                    val pointerIndex = event.actionIndex
                    val pointerId = event.getPointerId(pointerIndex)
                    if (pointerId != mActivePointerId) return false // if active pointer, fall through and cancel!
                }
                run {
                    onTouchEnd()
                    if (mDragItem.mDragging) {
                        onDragStop()
                    } else if (mDragItem.mDetecting) {
                        mDragItem.stopDetecting()
                    }
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                onTouchEnd()
                if (mDragItem.mDragging) {
                    onDragStop()
                } else if (mDragItem.mDetecting) {
                    mDragItem.stopDetecting()
                }
                return true
            }
        }
        return false
    }

    private fun onTouchEnd() {
        mDownY = -1
        mDownX = -1
        mActivePointerId = INVALID_POINTER_ID
    }

    private inner class DragHandleOnTouchListener(private val view: View) : OnTouchListener {
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (MotionEvent.ACTION_DOWN == event.actionMasked) {
                startDetectingDrag(view)
            }
            return false
        }
    }

    private fun getDragDrawable(view: View): BitmapDrawable {
        val top = view.top
        val left = view.left
        val bitmap = getBitmapFromView(view)
        val drawable = BitmapDrawable(resources, bitmap)
        drawable.bounds = Rect(left, top, left + view.width, top + view.height)
        return drawable
    }

    companion object {
        private val TAG = DragLinearLayout::class.java.simpleName
        private const val NOMINAL_SWITCH_DURATION: Long = 150
        private const val MIN_SWITCH_DURATION = NOMINAL_SWITCH_DURATION
        private const val MAX_SWITCH_DURATION = NOMINAL_SWITCH_DURATION * 2
        private const val NOMINAL_DISTANCE = 20f
        private const val INVALID_POINTER_ID = -1

        /**
         * @return a bitmap showing a screenshot of the mView passed in.
         */
        private fun getBitmapFromView(view: View): Bitmap {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            return bitmap
        }
    }

    init {
        mDragItem = DragItem()
        val vc = ViewConfiguration.get(context)
        mSlop = vc.scaledTouchSlop
        val resources = resources
        mNominalDistanceScaled =
            (NOMINAL_DISTANCE * resources.displayMetrics.density + 0.5f).toInt().toFloat()
    }


    /**
     * to listen for draggable mView swaps.
     */
    interface OnViewSwapListener {
        /**
         * Invoked right before the two items are swapped due to a drag event.
         * After the swap, the firstView will be in the secondPosition, and vice versa.
         */
        fun onSwap(draggedView: View?, initPosition: Int, swappedView: View?, swappedPosition: Int)

        /**
         * Invoked when swap action finish
         */
        fun onSwapFinish()
    }
}