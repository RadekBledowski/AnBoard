/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pl.rkbdi.anboard.inputmethod.keyboard;

import android.animation.AnimatorInflater;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.util.WeakHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import pl.rkbdi.anboard.inputmethod.accessibility.AccessibilityUtils;
import pl.rkbdi.anboard.inputmethod.accessibility.MainKeyboardAccessibilityDelegate;
import pl.rkbdi.anboard.inputmethod.annotations.ExternallyReferenced;
import pl.rkbdi.anboard.inputmethod.keyboard.internal.DrawingPreviewPlacerView;
import pl.rkbdi.anboard.inputmethod.keyboard.internal.DrawingProxy;
import pl.rkbdi.anboard.inputmethod.keyboard.internal.GestureFloatingTextDrawingPreview;
import pl.rkbdi.anboard.inputmethod.keyboard.internal.GestureTrailsDrawingPreview;
import pl.rkbdi.anboard.inputmethod.keyboard.internal.KeyDrawParams;
import pl.rkbdi.anboard.inputmethod.keyboard.internal.KeyPreviewChoreographer;
import pl.rkbdi.anboard.inputmethod.keyboard.internal.KeyPreviewDrawParams;
import pl.rkbdi.anboard.inputmethod.keyboard.internal.KeyPreviewView;
import pl.rkbdi.anboard.inputmethod.keyboard.internal.MoreKeySpec;
import pl.rkbdi.anboard.inputmethod.keyboard.internal.NonDistinctMultitouchHelper;
import pl.rkbdi.anboard.inputmethod.keyboard.internal.SlidingKeyInputDrawingPreview;
import pl.rkbdi.anboard.inputmethod.keyboard.internal.TimerHandler;
import pl.rkbdi.anboard.inputmethod.latin.R;
import pl.rkbdi.anboard.inputmethod.latin.RichInputMethodSubtype;
import pl.rkbdi.anboard.inputmethod.latin.SuggestedWords;
import pl.rkbdi.anboard.inputmethod.latin.common.Constants;
import pl.rkbdi.anboard.inputmethod.latin.common.CoordinateUtils;
import pl.rkbdi.anboard.inputmethod.latin.settings.DebugSettings;
import pl.rkbdi.anboard.inputmethod.latin.utils.LanguageOnSpacebarUtils;
import pl.rkbdi.anboard.inputmethod.latin.utils.TypefaceUtils;

/**
 * A view that is responsible for detecting key presses and touch movements.
 *
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarTextRatio
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarTextColor
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarTextShadowRadius
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarTextShadowColor
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarFinalAlpha
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarFadeoutAnimator
 * @attr ref R.styleable#MainKeyboardView_altCodeKeyWhileTypingFadeoutAnimator
 * @attr ref R.styleable#MainKeyboardView_altCodeKeyWhileTypingFadeinAnimator
 * @attr ref R.styleable#MainKeyboardView_keyHysteresisDistance
 * @attr ref R.styleable#MainKeyboardView_touchNoiseThresholdTime
 * @attr ref R.styleable#MainKeyboardView_touchNoiseThresholdDistance
 * @attr ref R.styleable#MainKeyboardView_keySelectionByDraggingFinger
 * @attr ref R.styleable#MainKeyboardView_keyRepeatStartTimeout
 * @attr ref R.styleable#MainKeyboardView_keyRepeatInterval
 * @attr ref R.styleable#MainKeyboardView_longPressKeyTimeout
 * @attr ref R.styleable#MainKeyboardView_longPressShiftKeyTimeout
 * @attr ref R.styleable#MainKeyboardView_ignoreAltCodeKeyTimeout
 * @attr ref R.styleable#MainKeyboardView_keyPreviewLayout
 * @attr ref R.styleable#MainKeyboardView_keyPreviewOffset
 * @attr ref R.styleable#MainKeyboardView_keyPreviewHeight
 * @attr ref R.styleable#MainKeyboardView_keyPreviewLingerTimeout
 * @attr ref R.styleable#MainKeyboardView_keyPreviewShowUpAnimator
 * @attr ref R.styleable#MainKeyboardView_keyPreviewDismissAnimator
 * @attr ref R.styleable#MainKeyboardView_moreKeysKeyboardLayout
 * @attr ref R.styleable#MainKeyboardView_moreKeysKeyboardForActionLayout
 * @attr ref R.styleable#MainKeyboardView_backgroundDimAlpha
 * @attr ref R.styleable#MainKeyboardView_showMoreKeysKeyboardAtTouchPoint
 * @attr ref R.styleable#MainKeyboardView_gestureFloatingPreviewTextLingerTimeout
 * @attr ref R.styleable#MainKeyboardView_gestureStaticTimeThresholdAfterFastTyping
 * @attr ref R.styleable#MainKeyboardView_gestureDetectFastMoveSpeedThreshold
 * @attr ref R.styleable#MainKeyboardView_gestureDynamicThresholdDecayDuration
 * @attr ref R.styleable#MainKeyboardView_gestureDynamicTimeThresholdFrom
 * @attr ref R.styleable#MainKeyboardView_gestureDynamicTimeThresholdTo
 * @attr ref R.styleable#MainKeyboardView_gestureDynamicDistanceThresholdFrom
 * @attr ref R.styleable#MainKeyboardView_gestureDynamicDistanceThresholdTo
 * @attr ref R.styleable#MainKeyboardView_gestureSamplingMinimumDistance
 * @attr ref R.styleable#MainKeyboardView_gestureRecognitionMinimumTime
 * @attr ref R.styleable#MainKeyboardView_gestureRecognitionSpeedThreshold
 * @attr ref R.styleable#MainKeyboardView_suppressKeyPreviewAfterBatchInputDuration
 */
public final class MainKeyboardView extends KeyboardView implements DrawingProxy,
        MoreKeysPanel.Controller {
    private static final String TAG = MainKeyboardView.class.getSimpleName();
    private static final float LANGUAGE_ON_SPACEBAR_TEXT_SHADOW_RADIUS_DISABLED = -1.0f;
    // The minimum x-scale to fit the language name on spacebar.
    private static final float MINIMUM_XSCALE_OF_LANGUAGE_NAME = 0.8f;
    private final static float CORNER_RADIUS = 15.0f;
    // Stuff to draw language name on spacebar.
    private final int mLanguageOnSpacebarFinalAlpha;
    private final float mLanguageOnSpacebarTextRatio;
    private final int mLanguageOnSpacebarTextColor;
    private final float mLanguageOnSpacebarTextShadowRadius;
    private final int mLanguageOnSpacebarTextShadowColor;
    // Stuff to draw altCodeWhileTyping keys.
    private final ObjectAnimator mAltCodeKeyWhileTypingFadeoutAnimator;
    private final ObjectAnimator mAltCodeKeyWhileTypingFadeinAnimator;
    // Drawing preview placer view
    private final DrawingPreviewPlacerView mDrawingPreviewPlacerView;
    private final int[] mOriginCoords = CoordinateUtils.newInstance();
    private final GestureFloatingTextDrawingPreview mGestureFloatingTextDrawingPreview;
    private final GestureTrailsDrawingPreview mGestureTrailsDrawingPreview;
    private final SlidingKeyInputDrawingPreview mSlidingKeyInputDrawingPreview;
    // Key preview
    private final KeyPreviewDrawParams mKeyPreviewDrawParams;
    private final KeyPreviewChoreographer mKeyPreviewChoreographer;
    // More keys keyboard
    private final Paint mBackgroundDimAlphaPaint = new Paint();
    private final View mMoreKeysKeyboardContainer;
    private final View mMoreKeysKeyboardForActionContainer;
    private final WeakHashMap<Key, Keyboard> mMoreKeysKeyboardCache = new WeakHashMap<>();
    private final boolean mConfigShowMoreKeysKeyboardAtTouchedPoint;
    private final KeyDetector mKeyDetector;
    private final NonDistinctMultitouchHelper mNonDistinctMultitouchHelper;
    private final TimerHandler mTimerHandler;
    private final int mLanguageOnSpacebarHorizontalMargin;
    /**
     * Listener for {@link KeyboardActionListener}.
     */
    private KeyboardActionListener mKeyboardActionListener;
    /* Space key and its icon and background. */
    private Key mSpaceKey;
    private ObjectAnimator mLanguageOnSpacebarFadeoutAnimator;
    private int mLanguageOnSpacebarFormatType;
    private boolean mHasMultipleEnabledIMEsOrSubtypes;
    private int mLanguageOnSpacebarAnimAlpha = Constants.Color.ALPHA_OPAQUE;
    private float mLanguageOnSpacebarTextSize;
    private int mAltCodeKeyWhileTypingAnimAlpha = Constants.Color.ALPHA_OPAQUE;
    // More keys panel (used by both more keys keyboard and more suggestions view)
    // TODO: Consider extending to support multiple more keys panels
    private MoreKeysPanel mMoreKeysPanel;
    // Gesture floating preview text
    // TODO: Make this parameter customizable by user via settings.
    private int mGestureFloatingPreviewTextLingerTimeout;
    private MainKeyboardAccessibilityDelegate mAccessibilityDelegate;
    private Paint paint, maskPaint;
    private float cornerRadius;
    private Bitmap maskBitmap;

    public MainKeyboardView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.mainKeyboardViewStyle);
        initRoundedCorners(context);
    }

    // Implements {@link DrawingProxy#startWhileTypingAnimation(int)}.
    public MainKeyboardView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        initRoundedCorners(context);

        final DrawingPreviewPlacerView drawingPreviewPlacerView =
                new DrawingPreviewPlacerView(context, attrs);

        final TypedArray mainKeyboardViewAttr = context.obtainStyledAttributes(
                attrs, R.styleable.MainKeyboardView, defStyle, R.style.MainKeyboardView);
        final int ignoreAltCodeKeyTimeout = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_ignoreAltCodeKeyTimeout, 0);
        final int gestureRecognitionUpdateTime = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_gestureRecognitionUpdateTime, 0);
        mTimerHandler = new TimerHandler(
                this, ignoreAltCodeKeyTimeout, gestureRecognitionUpdateTime);

        final float keyHysteresisDistance = mainKeyboardViewAttr.getDimension(
                R.styleable.MainKeyboardView_keyHysteresisDistance, 0.0f);
        final float keyHysteresisDistanceForSlidingModifier = mainKeyboardViewAttr.getDimension(
                R.styleable.MainKeyboardView_keyHysteresisDistanceForSlidingModifier, 0.0f);
        mKeyDetector = new KeyDetector(
                keyHysteresisDistance, keyHysteresisDistanceForSlidingModifier);

        PointerTracker.init(mainKeyboardViewAttr, mTimerHandler, this /* DrawingProxy */);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean forceNonDistinctMultitouch = prefs.getBoolean(
                DebugSettings.PREF_FORCE_NON_DISTINCT_MULTITOUCH, false);
        final boolean hasDistinctMultitouch = context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT)
                && !forceNonDistinctMultitouch;
        mNonDistinctMultitouchHelper = hasDistinctMultitouch ? null
                : new NonDistinctMultitouchHelper();

        final int backgroundDimAlpha = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_backgroundDimAlpha, 0);
        mBackgroundDimAlphaPaint.setColor(Color.BLACK);
        mBackgroundDimAlphaPaint.setAlpha(backgroundDimAlpha);
        mLanguageOnSpacebarTextRatio = mainKeyboardViewAttr.getFraction(
                R.styleable.MainKeyboardView_languageOnSpacebarTextRatio, 1, 1, 1.0f);
        mLanguageOnSpacebarTextColor = mainKeyboardViewAttr.getColor(
                R.styleable.MainKeyboardView_languageOnSpacebarTextColor, 0);
        mLanguageOnSpacebarTextShadowRadius = mainKeyboardViewAttr.getFloat(
                R.styleable.MainKeyboardView_languageOnSpacebarTextShadowRadius,
                LANGUAGE_ON_SPACEBAR_TEXT_SHADOW_RADIUS_DISABLED);
        mLanguageOnSpacebarTextShadowColor = mainKeyboardViewAttr.getColor(
                R.styleable.MainKeyboardView_languageOnSpacebarTextShadowColor, 0);
        mLanguageOnSpacebarFinalAlpha = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_languageOnSpacebarFinalAlpha,
                Constants.Color.ALPHA_OPAQUE);
        final int languageOnSpacebarFadeoutAnimatorResId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_languageOnSpacebarFadeoutAnimator, 0);
        final int altCodeKeyWhileTypingFadeoutAnimatorResId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_altCodeKeyWhileTypingFadeoutAnimator, 0);
        final int altCodeKeyWhileTypingFadeinAnimatorResId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_altCodeKeyWhileTypingFadeinAnimator, 0);

        mKeyPreviewDrawParams = new KeyPreviewDrawParams(mainKeyboardViewAttr);
        mKeyPreviewChoreographer = new KeyPreviewChoreographer(mKeyPreviewDrawParams);

        final int moreKeysKeyboardLayoutId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_moreKeysKeyboardLayout, 0);
        final int moreKeysKeyboardForActionLayoutId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_moreKeysKeyboardForActionLayout,
                moreKeysKeyboardLayoutId);
        mConfigShowMoreKeysKeyboardAtTouchedPoint = mainKeyboardViewAttr.getBoolean(
                R.styleable.MainKeyboardView_showMoreKeysKeyboardAtTouchedPoint, false);

        mGestureFloatingPreviewTextLingerTimeout = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_gestureFloatingPreviewTextLingerTimeout, 0);

        mGestureFloatingTextDrawingPreview = new GestureFloatingTextDrawingPreview(
                mainKeyboardViewAttr);
        mGestureFloatingTextDrawingPreview.setDrawingView(drawingPreviewPlacerView);

        mGestureTrailsDrawingPreview = new GestureTrailsDrawingPreview(mainKeyboardViewAttr);
        mGestureTrailsDrawingPreview.setDrawingView(drawingPreviewPlacerView);

        mSlidingKeyInputDrawingPreview = new SlidingKeyInputDrawingPreview(mainKeyboardViewAttr);
        mSlidingKeyInputDrawingPreview.setDrawingView(drawingPreviewPlacerView);
        mainKeyboardViewAttr.recycle();

        mDrawingPreviewPlacerView = drawingPreviewPlacerView;

        final LayoutInflater inflater = LayoutInflater.from(getContext());
        mMoreKeysKeyboardContainer = inflater.inflate(moreKeysKeyboardLayoutId, null);
        mMoreKeysKeyboardForActionContainer = inflater.inflate(
                moreKeysKeyboardForActionLayoutId, null);
        mLanguageOnSpacebarFadeoutAnimator = loadObjectAnimator(
                languageOnSpacebarFadeoutAnimatorResId, this);
        mAltCodeKeyWhileTypingFadeoutAnimator = loadObjectAnimator(
                altCodeKeyWhileTypingFadeoutAnimatorResId, this);
        mAltCodeKeyWhileTypingFadeinAnimator = loadObjectAnimator(
                altCodeKeyWhileTypingFadeinAnimatorResId, this);

        mKeyboardActionListener = KeyboardActionListener.EMPTY_LISTENER;

        mLanguageOnSpacebarHorizontalMargin = (int) getResources().getDimension(
                R.dimen.config_language_on_spacebar_horizontal_margin);
    }

    private static void cancelAndStartAnimators(final ObjectAnimator animatorToCancel,
                                                final ObjectAnimator animatorToStart) {
        if (animatorToCancel == null || animatorToStart == null) {
            // TODO: Stop using null as a no-operation animator.
            return;
        }
        float startFraction = 0.0f;
        if (animatorToCancel.isStarted()) {
            animatorToCancel.cancel();
            startFraction = 1.0f - animatorToCancel.getAnimatedFraction();
        }
        final long startTime = (long) (animatorToStart.getDuration() * startFraction);
        animatorToStart.start();
        animatorToStart.setCurrentPlayTime(startTime);
    }

    static public Path RoundedRect(float left, float top, float right, float bottom, float rx, float ry, boolean conformToOriginalPost) {
        Path path = new Path();
        if (rx < 0) rx = 0;
        if (ry < 0) ry = 0;
        float width = right - left;
        float height = bottom - top;
        if (rx > width / 2) rx = width / 2;
        if (ry > height / 2) ry = height / 2;
        float widthMinusCorners = (width - (2 * rx));
        float heightMinusCorners = (height - (2 * ry));

        path.moveTo(right, top + ry);
        path.rQuadTo(0, -ry, -rx, -ry);//top-right corner
        path.rLineTo(-widthMinusCorners, 0);
        path.rQuadTo(-rx, 0, -rx, ry); //top-left corner
        path.rLineTo(0, heightMinusCorners);

        if (conformToOriginalPost) {
            path.rLineTo(0, ry);
            path.rLineTo(width, 0);
            path.rLineTo(0, -ry);
        } else {
            path.rQuadTo(0, ry, rx, ry);//bottom-left corner
            path.rLineTo(widthMinusCorners, 0);
            path.rQuadTo(rx, 0, rx, -ry); //bottom-right corner
        }

        path.rLineTo(0, -heightMinusCorners);

        path.close();//Given close, last lineto can be removed.

        return path;
    }

    @Override
    public void setHardwareAcceleratedDrawingEnabled(final boolean enabled) {
        super.setHardwareAcceleratedDrawingEnabled(enabled);
        mDrawingPreviewPlacerView.setHardwareAcceleratedDrawingEnabled(enabled);
    }

    private ObjectAnimator loadObjectAnimator(final int resId, final Object target) {
        if (resId == 0) {
            // TODO: Stop returning null.
            return null;
        }
        final ObjectAnimator animator = (ObjectAnimator) AnimatorInflater.loadAnimator(
                getContext(), resId);
        if (animator != null) {
            animator.setTarget(target);
        }
        return animator;
    }

    /**
     * Called when a while-typing-animation should be started.
     *
     * @param fadeInOrOut {@link DrawingProxy#FADE_IN} starts while-typing-fade-in animation.
     *                    {@link DrawingProxy#FADE_OUT} starts while-typing-fade-out animation.
     */
    @Override
    public void startWhileTypingAnimation(final int fadeInOrOut) {
        switch (fadeInOrOut) {
            case DrawingProxy.FADE_IN:
                cancelAndStartAnimators(
                        mAltCodeKeyWhileTypingFadeoutAnimator, mAltCodeKeyWhileTypingFadeinAnimator);
                break;
            case DrawingProxy.FADE_OUT:
                cancelAndStartAnimators(
                        mAltCodeKeyWhileTypingFadeinAnimator, mAltCodeKeyWhileTypingFadeoutAnimator);
                break;
        }
    }

    @ExternallyReferenced
    public int getLanguageOnSpacebarAnimAlpha() {
        return mLanguageOnSpacebarAnimAlpha;
    }

    @ExternallyReferenced
    public void setLanguageOnSpacebarAnimAlpha(final int alpha) {
        mLanguageOnSpacebarAnimAlpha = alpha;
        invalidateKey(mSpaceKey);
    }

    @ExternallyReferenced
    public int getAltCodeKeyWhileTypingAnimAlpha() {
        return mAltCodeKeyWhileTypingAnimAlpha;
    }

    @ExternallyReferenced
    public void setAltCodeKeyWhileTypingAnimAlpha(final int alpha) {
        if (mAltCodeKeyWhileTypingAnimAlpha == alpha) {
            return;
        }
        // Update the visual of alt-code-key-while-typing.
        mAltCodeKeyWhileTypingAnimAlpha = alpha;
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            return;
        }
        for (final Key key : keyboard.mAltCodeKeysWhileTyping) {
            invalidateKey(key);
        }
    }

    public void setKeyboardActionListener(final KeyboardActionListener listener) {
        mKeyboardActionListener = listener;
        PointerTracker.setKeyboardActionListener(listener);
    }

    // TODO: We should reconsider which coordinate system should be used to represent keyboard
    // event.
    public int getKeyX(final int x) {
        return Constants.isValidCoordinate(x) ? mKeyDetector.getTouchX(x) : x;
    }

    // TODO: We should reconsider which coordinate system should be used to represent keyboard
    // event.
    public int getKeyY(final int y) {
        return Constants.isValidCoordinate(y) ? mKeyDetector.getTouchY(y) : y;
    }

    /**
     * Attaches a keyboard to this view. The keyboard can be switched at any time and the
     * view will re-layout itself to accommodate the keyboard.
     *
     * @param keyboard the keyboard to display in this view
     * @see Keyboard
     * @see #getKeyboard()
     */
    @Override
    public void setKeyboard(final Keyboard keyboard) {
        // Remove any pending messages, except dismissing preview and key repeat.
        mTimerHandler.cancelLongPressTimers();
        super.setKeyboard(keyboard);
        mKeyDetector.setKeyboard(
                keyboard, -getPaddingLeft(), -getPaddingTop() + getVerticalCorrection());
        PointerTracker.setKeyDetector(mKeyDetector);
        mMoreKeysKeyboardCache.clear();

        mSpaceKey = keyboard.getKey(Constants.CODE_SPACE);
        final int keyHeight = keyboard.mMostCommonKeyHeight - keyboard.mVerticalGap;
        mLanguageOnSpacebarTextSize = keyHeight * mLanguageOnSpacebarTextRatio;

        if (AccessibilityUtils.Companion.getInstance().isAccessibilityEnabled()) {
            if (mAccessibilityDelegate == null) {
                mAccessibilityDelegate = new MainKeyboardAccessibilityDelegate(this, mKeyDetector);
            }
            mAccessibilityDelegate.setKeyboard(keyboard);
        } else {
            mAccessibilityDelegate = null;
        }
    }

    /**
     * Enables or disables the key preview popup. This is a popup that shows a magnified
     * version of the depressed key. By default the preview is enabled.
     *
     * @param previewEnabled whether or not to enable the key feedback preview
     * @param delay          the delay after which the preview is dismissed
     */
    public void setKeyPreviewPopupEnabled(final boolean previewEnabled, final int delay) {
        mKeyPreviewDrawParams.setPopupEnabled(previewEnabled, delay);
    }

    /**
     * Enables or disables the key preview popup animations and set animations' parameters.
     *
     * @param hasCustomAnimationParams false to use the default key preview popup animations
     *                                 specified by keyPreviewShowUpAnimator and keyPreviewDismissAnimator attributes.
     *                                 true to override the default animations with the specified parameters.
     * @param showUpStartXScale        from this x-scale the show up animation will start.
     * @param showUpStartYScale        from this y-scale the show up animation will start.
     * @param showUpDuration           the duration of the show up animation in milliseconds.
     * @param dismissEndXScale         to this x-scale the dismiss animation will end.
     * @param dismissEndYScale         to this y-scale the dismiss animation will end.
     * @param dismissDuration          the duration of the dismiss animation in milliseconds.
     */
    public void setKeyPreviewAnimationParams(final boolean hasCustomAnimationParams,
                                             final float showUpStartXScale, final float showUpStartYScale, final int showUpDuration,
                                             final float dismissEndXScale, final float dismissEndYScale, final int dismissDuration) {
        mKeyPreviewDrawParams.setAnimationParams(hasCustomAnimationParams,
                showUpStartXScale, showUpStartYScale, showUpDuration,
                dismissEndXScale, dismissEndYScale, dismissDuration);
    }

    private void locatePreviewPlacerView() {
        getLocationInWindow(mOriginCoords);
        mDrawingPreviewPlacerView.setKeyboardViewGeometry(mOriginCoords, getWidth(), getHeight());
    }

    private void installPreviewPlacerView() {
        final View rootView = getRootView();
        if (rootView == null) {
            Log.w(TAG, "Cannot find root view");
            return;
        }
        final ViewGroup windowContentView = rootView.findViewById(android.R.id.content);
        // Note: It'd be very weird if we get null by android.R.id.content.
        if (windowContentView == null) {
            Log.w(TAG, "Cannot find android.R.id.content view to add DrawingPreviewPlacerView");
            return;
        }
        windowContentView.addView(mDrawingPreviewPlacerView);
    }

    // Implements {@link DrawingProxy#onKeyPressed(Key,boolean)}.
    @Override
    public void onKeyPressed(@Nonnull final Key key, final boolean withPreview) {
        key.onPressed();
        invalidateKey(key);
        if (withPreview && !key.noKeyPreview()) {
            showKeyPreview(key);
        }
    }

    private void showKeyPreview(@Nonnull final Key key) {
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            return;
        }
        final KeyPreviewDrawParams previewParams = mKeyPreviewDrawParams;
        if (!previewParams.isPopupEnabled()) {
            previewParams.setVisibleOffset(-keyboard.mVerticalGap);
            return;
        }

        locatePreviewPlacerView();
        getLocationInWindow(mOriginCoords);
        mKeyPreviewChoreographer.placeAndShowKeyPreview(key, keyboard.mIconsSet, getKeyDrawParams(),
                getWidth(), mOriginCoords, mDrawingPreviewPlacerView, isHardwareAccelerated());
    }

    private void dismissKeyPreviewWithoutDelay(@Nonnull final Key key) {
        mKeyPreviewChoreographer.dismissKeyPreview(key, false /* withAnimation */);
        invalidateKey(key);
    }

    // Implements {@link DrawingProxy#onKeyReleased(Key,boolean)}.
    @Override
    public void onKeyReleased(@Nonnull final Key key, final boolean withAnimation) {
        key.onReleased();
        invalidateKey(key);
        if (!key.noKeyPreview()) {
            if (withAnimation) {
                dismissKeyPreview(key);
            } else {
                dismissKeyPreviewWithoutDelay(key);
            }
        }
    }

    private void dismissKeyPreview(@Nonnull final Key key) {
        if (isHardwareAccelerated()) {
            mKeyPreviewChoreographer.dismissKeyPreview(key, true /* withAnimation */);
            return;
        }
        // TODO: Implement preference option to control key preview method and duration.
        mTimerHandler.postDismissKeyPreview(key, mKeyPreviewDrawParams.getLingerTimeout());
    }

    public void setSlidingKeyInputPreviewEnabled(final boolean enabled) {
        mSlidingKeyInputDrawingPreview.setPreviewEnabled(enabled);
    }

    @Override
    public void showSlidingKeyInputPreview(@Nullable final PointerTracker tracker) {
        locatePreviewPlacerView();
        if (tracker != null) {
            mSlidingKeyInputDrawingPreview.setPreviewPosition(tracker);
        } else {
            mSlidingKeyInputDrawingPreview.dismissSlidingKeyInputPreview();
        }
    }

    private void setGesturePreviewMode(final boolean isGestureTrailEnabled,
                                       final boolean isGestureFloatingPreviewTextEnabled) {
        mGestureFloatingTextDrawingPreview.setPreviewEnabled(isGestureFloatingPreviewTextEnabled);
        mGestureTrailsDrawingPreview.setPreviewEnabled(isGestureTrailEnabled);
    }

    public void showGestureFloatingPreviewText(@Nonnull final SuggestedWords suggestedWords,
                                               final boolean dismissDelayed) {
        locatePreviewPlacerView();
        final GestureFloatingTextDrawingPreview gestureFloatingTextDrawingPreview =
                mGestureFloatingTextDrawingPreview;
        gestureFloatingTextDrawingPreview.setSuggetedWords(suggestedWords);
        if (dismissDelayed) {
            mTimerHandler.postDismissGestureFloatingPreviewText(
                    mGestureFloatingPreviewTextLingerTimeout);
        }
    }

    // Implements {@link DrawingProxy#dismissGestureFloatingPreviewTextWithoutDelay()}.
    @Override
    public void dismissGestureFloatingPreviewTextWithoutDelay() {
        mGestureFloatingTextDrawingPreview.dismissGestureFloatingPreviewText();
    }

    @Override
    public void showGestureTrail(@Nonnull final PointerTracker tracker,
                                 final boolean showsFloatingPreviewText) {
        locatePreviewPlacerView();
        if (showsFloatingPreviewText) {
            mGestureFloatingTextDrawingPreview.setPreviewPosition(tracker);
        }
        mGestureTrailsDrawingPreview.setPreviewPosition(tracker);
    }

    // Note that this method is called from a non-UI thread.
    @SuppressWarnings("static-method")
    public void setMainDictionaryAvailability(final boolean mainDictionaryAvailable) {
        PointerTracker.setMainDictionaryAvailability(mainDictionaryAvailable);
    }

    public void setGestureHandlingEnabledByUser(final boolean isGestureHandlingEnabledByUser,
                                                final boolean isGestureTrailEnabled,
                                                final boolean isGestureFloatingPreviewTextEnabled) {
        PointerTracker.setGestureHandlingEnabledByUser(isGestureHandlingEnabledByUser);
        setGesturePreviewMode(isGestureHandlingEnabledByUser && isGestureTrailEnabled,
                isGestureHandlingEnabledByUser && isGestureFloatingPreviewTextEnabled);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        installPreviewPlacerView();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mDrawingPreviewPlacerView.removeAllViews();
    }

    // Implements {@link DrawingProxy@showMoreKeysKeyboard(Key,PointerTracker)}.
    @Override
    @Nullable
    public MoreKeysPanel showMoreKeysKeyboard(@Nonnull final Key key,
                                              @Nonnull final PointerTracker tracker) {
        final MoreKeySpec[] moreKeys = key.getMoreKeys();
        if (moreKeys == null) {
            return null;
        }
        Keyboard moreKeysKeyboard = mMoreKeysKeyboardCache.get(key);
        if (moreKeysKeyboard == null) {
            // {@link KeyPreviewDrawParams#mPreviewVisibleWidth} should have been set at
            // {@link KeyPreviewChoreographer#placeKeyPreview(Key,TextView,KeyboardIconsSet,KeyDrawParams,int,int[]},
            // though there may be some chances that the value is zero. <code>width == 0</code>
            // will cause zero-division error at
            // {@link MoreKeysKeyboardParams#setParameters(int,int,int,int,int,int,boolean,int)}.
            final boolean isSingleMoreKeyWithPreview = mKeyPreviewDrawParams.isPopupEnabled()
                    && !key.noKeyPreview() && moreKeys.length == 1
                    && mKeyPreviewDrawParams.getVisibleWidth() > 0;
            final MoreKeysKeyboard.Builder builder = new MoreKeysKeyboard.Builder(
                    getContext(), key, getKeyboard(), isSingleMoreKeyWithPreview,
                    mKeyPreviewDrawParams.getVisibleWidth(),
                    mKeyPreviewDrawParams.getVisibleHeight(), newLabelPaint(key));
            moreKeysKeyboard = builder.build();
            mMoreKeysKeyboardCache.put(key, moreKeysKeyboard);
        }

        final View container = key.isActionKey() ? mMoreKeysKeyboardForActionContainer
                : mMoreKeysKeyboardContainer;
        final MoreKeysKeyboardView moreKeysKeyboardView =
                container.findViewById(R.id.more_keys_keyboard_view);
        moreKeysKeyboardView.setKeyboard(moreKeysKeyboard);
        container.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        final int[] lastCoords = CoordinateUtils.newInstance();
        tracker.getLastCoordinates(lastCoords);
        final boolean keyPreviewEnabled = mKeyPreviewDrawParams.isPopupEnabled()
                && !key.noKeyPreview();
        // The more keys keyboard is usually horizontally aligned with the center of the parent key.
        // If showMoreKeysKeyboardAtTouchedPoint is true and the key preview is disabled, the more
        // keys keyboard is placed at the touch point of the parent key.
        final int pointX = (mConfigShowMoreKeysKeyboardAtTouchedPoint && !keyPreviewEnabled)
                ? CoordinateUtils.x(lastCoords)
                : key.getX() + key.getWidth() / 2;
        // The more keys keyboard is usually vertically aligned with the top edge of the parent key
        // (plus vertical gap). If the key preview is enabled, the more keys keyboard is vertically
        // aligned with the bottom edge of the visible part of the key preview.
        // {@code mPreviewVisibleOffset} has been set appropriately in
        // {@link KeyboardView#showKeyPreview(PointerTracker)}.
        final int pointY = key.getY() + mKeyPreviewDrawParams.getVisibleOffset();
        moreKeysKeyboardView.showMoreKeysPanel(this, this, pointX, pointY, mKeyboardActionListener);
        return moreKeysKeyboardView;
    }

    public boolean isInDraggingFinger() {
        if (isShowingMoreKeysPanel()) {
            return true;
        }
        return PointerTracker.isAnyInDraggingFinger();
    }

    @Override
    public void onShowMoreKeysPanel(final MoreKeysPanel panel) {
        locatePreviewPlacerView();
        // Dismiss another {@link MoreKeysPanel} that may be being showed.
        onDismissMoreKeysPanel();
        // Dismiss all key previews that may be being showed.
        PointerTracker.setReleasedKeyGraphicsToAllKeys();
        // Dismiss sliding key input preview that may be being showed.
        mSlidingKeyInputDrawingPreview.dismissSlidingKeyInputPreview();
        panel.showInParent(mDrawingPreviewPlacerView);
        mMoreKeysPanel = panel;
    }

    public boolean isShowingMoreKeysPanel() {
        return mMoreKeysPanel != null && mMoreKeysPanel.isShowingInParent();
    }

    @Override
    public void onCancelMoreKeysPanel() {
        PointerTracker.dismissAllMoreKeysPanels();
    }

    @Override
    public void onDismissMoreKeysPanel() {
        if (isShowingMoreKeysPanel()) {
            mMoreKeysPanel.removeFromParent();
            mMoreKeysPanel = null;
        }
    }

    public void startDoubleTapShiftKeyTimer() {
        mTimerHandler.startDoubleTapShiftKeyTimer();
    }

    public void cancelDoubleTapShiftKeyTimer() {
        mTimerHandler.cancelDoubleTapShiftKeyTimer();
    }

    public boolean isInDoubleTapShiftKeyTimeout() {
        return mTimerHandler.isInDoubleTapShiftKeyTimeout();
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        if (getKeyboard() == null) {
            return false;
        }
        if (mNonDistinctMultitouchHelper != null) {
            if (event.getPointerCount() > 1 && mTimerHandler.isInKeyRepeat()) {
                // Key repeating timer will be canceled if 2 or more keys are in action.
                mTimerHandler.cancelKeyRepeatTimers();
            }
            // Non distinct multitouch screen support
            mNonDistinctMultitouchHelper.processMotionEvent(event, mKeyDetector);
            return true;
        }
        return processMotionEvent(event);
    }

    public boolean processMotionEvent(final MotionEvent event) {
        final int index = event.getActionIndex();
        final int id = event.getPointerId(index);
        final PointerTracker tracker = PointerTracker.getPointerTracker(id);
        // When a more keys panel is showing, we should ignore other fingers' single touch events
        // other than the finger that is showing the more keys panel.
        if (isShowingMoreKeysPanel() && !tracker.isShowingMoreKeysPanel()
                && PointerTracker.getActivePointerTrackerCount() == 1) {
            return true;
        }
        tracker.processMotionEvent(event, mKeyDetector);
        return true;
    }

    public void cancelAllOngoingEvents() {
        mTimerHandler.cancelAllMessages();
        PointerTracker.setReleasedKeyGraphicsToAllKeys();
        mGestureFloatingTextDrawingPreview.dismissGestureFloatingPreviewText();
        mSlidingKeyInputDrawingPreview.dismissSlidingKeyInputPreview();
        PointerTracker.dismissAllMoreKeysPanels();
        PointerTracker.cancelAllPointerTrackers();
    }

    public void closing() {
        cancelAllOngoingEvents();
        mMoreKeysKeyboardCache.clear();
    }

    public void onHideWindow() {
        onDismissMoreKeysPanel();
        final MainKeyboardAccessibilityDelegate accessibilityDelegate = mAccessibilityDelegate;
        if (accessibilityDelegate != null
                && AccessibilityUtils.Companion.getInstance().isAccessibilityEnabled()) {
            accessibilityDelegate.onHideWindow();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onHoverEvent(final MotionEvent event) {
        final MainKeyboardAccessibilityDelegate accessibilityDelegate = mAccessibilityDelegate;
        if (accessibilityDelegate != null
                && AccessibilityUtils.Companion.getInstance().isTouchExplorationEnabled()) {
            return accessibilityDelegate.onHoverEvent(event);
        }
        return super.onHoverEvent(event);
    }

    public void updateShortcutKey(final boolean available) {
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            return;
        }
        final Key shortcutKey = keyboard.getKey(Constants.CODE_SHORTCUT);
        if (shortcutKey == null) {
            return;
        }
        shortcutKey.setEnabled(available);
        invalidateKey(shortcutKey);
    }

    public void startDisplayLanguageOnSpacebar(final boolean subtypeChanged,
                                               final int languageOnSpacebarFormatType,
                                               final boolean hasMultipleEnabledIMEsOrSubtypes) {
        if (subtypeChanged) {
            KeyPreviewView.clearTextCache();
        }
        mLanguageOnSpacebarFormatType = languageOnSpacebarFormatType;
        mHasMultipleEnabledIMEsOrSubtypes = hasMultipleEnabledIMEsOrSubtypes;
        final ObjectAnimator animator = mLanguageOnSpacebarFadeoutAnimator;
        if (animator == null) {
            mLanguageOnSpacebarFormatType = LanguageOnSpacebarUtils.FORMAT_TYPE_NONE;
        } else {
            if (subtypeChanged
                    && languageOnSpacebarFormatType != LanguageOnSpacebarUtils.FORMAT_TYPE_NONE) {
                setLanguageOnSpacebarAnimAlpha(Constants.Color.ALPHA_OPAQUE);
                if (animator.isStarted()) {
                    animator.cancel();
                }
                animator.start();
            } else {
                if (!animator.isStarted()) {
                    mLanguageOnSpacebarAnimAlpha = mLanguageOnSpacebarFinalAlpha;
                }
            }
        }
        invalidateKey(mSpaceKey);
    }

    @Override
    protected void onDrawKeyTopVisuals(final Key key, final Canvas canvas, final Paint paint,
                                       final KeyDrawParams params) {
        if (key.altCodeWhileTyping() && key.isEnabled()) {
            params.mAnimAlpha = mAltCodeKeyWhileTypingAnimAlpha;
        }
        super.onDrawKeyTopVisuals(key, canvas, paint, params);
        final int code = key.getCode();
        if (code == Constants.CODE_SPACE) {
            // If input language are explicitly selected.
            if (mLanguageOnSpacebarFormatType != LanguageOnSpacebarUtils.FORMAT_TYPE_NONE) {
                drawLanguageOnSpacebar(key, canvas, paint);
            }
            // Whether space key needs to show the "..." popup hint for special purposes
            if (key.isLongPressEnabled() && mHasMultipleEnabledIMEsOrSubtypes) {
                drawKeyPopupHint(key, canvas, paint, params);
            }
        } else if (code == Constants.CODE_LANGUAGE_SWITCH) {
            drawKeyPopupHint(key, canvas, paint, params);
        }
    }

    private boolean fitsTextIntoWidth(final int width, final String text, final Paint paint) {
        final int maxTextWidth = width - mLanguageOnSpacebarHorizontalMargin * 2;
        paint.setTextScaleX(1.0f);
        final float textWidth = TypefaceUtils.getStringWidth(text, paint);
        if (textWidth < width) {
            return true;
        }

        final float scaleX = maxTextWidth / textWidth;
        if (scaleX < MINIMUM_XSCALE_OF_LANGUAGE_NAME) {
            return false;
        }

        paint.setTextScaleX(scaleX);
        return TypefaceUtils.getStringWidth(text, paint) < maxTextWidth;
    }

    // Layout language name on spacebar.
    private String layoutLanguageOnSpacebar(final Paint paint,
                                            final RichInputMethodSubtype subtype, final int width) {
        // Choose appropriate language name to fit into the width.
        if (mLanguageOnSpacebarFormatType == LanguageOnSpacebarUtils.FORMAT_TYPE_FULL_LOCALE) {
            final String fullText = subtype.getFullDisplayName();
            if (fitsTextIntoWidth(width, fullText, paint)) {
                return fullText;
            }
        }

        final String middleText = subtype.getMiddleDisplayName();
        if (fitsTextIntoWidth(width, middleText, paint)) {
            return middleText;
        }

        return "";
    }

    private void drawLanguageOnSpacebar(final Key key, final Canvas canvas, final Paint paint) {
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            return;
        }
        final int width = key.getWidth();
        final int height = key.getHeight();
        paint.setTextAlign(Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(mLanguageOnSpacebarTextSize);
        final String language = layoutLanguageOnSpacebar(paint, keyboard.mId.mSubtype, width);
        // Draw language text with shadow
        final float descent = paint.descent();
        final float textHeight = -paint.ascent() + descent;
        final float baseline = height / 2 + textHeight / 2;
        if (mLanguageOnSpacebarTextShadowRadius > 0.0f) {
            paint.setShadowLayer(mLanguageOnSpacebarTextShadowRadius, 0, 0,
                    mLanguageOnSpacebarTextShadowColor);
        } else {
            paint.clearShadowLayer();
        }
        paint.setColor(mLanguageOnSpacebarTextColor);
        paint.setAlpha(mLanguageOnSpacebarAnimAlpha);
        canvas.drawText(language, width / 2, baseline - descent, paint);
        paint.clearShadowLayer();
        paint.setTextScaleX(1.0f);
    }

    @Override
    public void deallocateMemory() {
        super.deallocateMemory();
        mDrawingPreviewPlacerView.deallocateMemory();
    }

    private void initRoundedCorners(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        cornerRadius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, CORNER_RADIUS, metrics);

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        setWillNotDraw(false);
    }

    @Override
    public void draw(Canvas canvas) {
        Bitmap offscreenBitmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas offscreenCanvas = new Canvas(offscreenBitmap);

        super.draw(offscreenCanvas);

        if (maskBitmap == null) {
            maskBitmap = createMask(canvas.getWidth(), canvas.getHeight());
        }

        offscreenCanvas.drawBitmap(maskBitmap, 0f, 0f, maskPaint);
        canvas.drawBitmap(offscreenBitmap, 0f, 0f, paint);
    }

    private Bitmap createMask(int width, int height) {
        Bitmap mask = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
        Canvas canvas = new Canvas(mask);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);

        canvas.drawRect(0, 0, width, height, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        Path path = RoundedRect(0, 0, width, height, cornerRadius, cornerRadius,
                true);
        canvas.drawPath(path, paint);

        return mask;
    }
}
