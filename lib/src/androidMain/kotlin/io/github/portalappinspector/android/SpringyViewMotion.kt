package io.github.portalappinspector.android

import android.view.View
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

class SpringyViewMotion(private val view: View) {
    var onUpdate: (() -> Unit)? = null

    private var animX: SpringAnimation? = null
    private var animY: SpringAnimation? = null
    private var animTranslationY: SpringAnimation? = null
    private var animAlpha: SpringAnimation? = null
    private var animScaleX: SpringAnimation? = null
    private var animScaleY: SpringAnimation? = null

    fun springPositionTo(
        targetX: Float,
        targetY: Float,
        velocityX: Float = 0f,
        velocityY: Float = 0f,
        onEnd: (() -> Unit)? = null,
    ) {
        val endTracker = MultiAnimationEndTracker(2, onEnd)
        animX = animateProperty(DynamicAnimation.X, targetX, velocityX, 620f, 0.72f) { endTracker.onAnimationEnded() }
        animY = animateProperty(DynamicAnimation.Y, targetY, velocityY, 620f, 0.72f) { endTracker.onAnimationEnded() }
    }

    fun springTranslationYTo(
        target: Float,
        velocity: Float = 0f,
        onEnd: (() -> Unit)? = null,
    ) {
        animTranslationY = animateProperty(DynamicAnimation.TRANSLATION_Y, target, velocity, 520f, 0.77f, onEnd)
    }

    fun springAlphaTo(
        target: Float,
        velocity: Float = 0f,
        onEnd: (() -> Unit)? = null,
    ) {
        animAlpha = animateProperty(DynamicAnimation.ALPHA, target.coerceIn(0f, 1f), velocity, 680f, 0.69f, onEnd)
    }

    fun springScaleTo(target: Float) {
        animScaleX = animateProperty(DynamicAnimation.SCALE_X, target, 0f, 760f, 0.58f)
        animScaleY = animateProperty(DynamicAnimation.SCALE_Y, target, 0f, 760f, 0.58f)
    }

    fun cancel() {
        animX?.cancel()
        animY?.cancel()
        animTranslationY?.cancel()
        animAlpha?.cancel()
        animScaleX?.cancel()
        animScaleY?.cancel()
    }

    private fun animateProperty(
        property: FloatPropertyCompat<View>,
        target: Float,
        startVelocity: Float,
        stiffness: Float,
        dampingRatio: Float,
        onEnd: (() -> Unit)? = null,
    ): SpringAnimation {
        val anim = SpringAnimation(view, property).apply {
            spring = SpringForce(target).apply {
                this.stiffness = stiffness
                this.dampingRatio = dampingRatio
            }
            if (startVelocity != 0f) {
                setStartVelocity(startVelocity)
            }
            addUpdateListener { _, _, _ -> onUpdate?.invoke() }
            if (onEnd != null) {
                addEndListener { _, canceled, _, _ -> if (!canceled) onEnd() }
            }
        }
        anim.start()
        return anim
    }

    private class MultiAnimationEndTracker(
        private val totalCount: Int,
        private val onEnd: (() -> Unit)?,
    ) {
        private var endedCount = 0

        fun onAnimationEnded() {
            endedCount++
            if (endedCount >= totalCount) {
                onEnd?.invoke()
            }
        }
    }
}
