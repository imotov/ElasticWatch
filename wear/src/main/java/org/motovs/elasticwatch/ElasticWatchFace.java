/*
 * Copyright (C) 2014 The Android Open Source Project
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

package org.motovs.elasticwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RotateDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.Gravity;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class ElasticWatchFace extends CanvasWatchFaceService {
    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.MILLISECONDS.toMillis(100);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<ElasticWatchFace.Engine> mWeakReference;

        public EngineHandler(ElasticWatchFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            ElasticWatchFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;

        Paint mHandOutlinePaint;
        Paint mHandFillPaint;

        Paint mSecondHandOutlinePaint;
        Paint mSecondHandFillPaint;

        int mHandFillColor;
        int mHandStrokeColor;
        int mHandFillAmbientColor;
        int mHandStrokeAmbientColor;
        int mSecondHandFillColor;
        int mSecondHandStrokeColor;

        RotateDrawable mLogoColor;
        Drawable mLogoWhite;
        int[] mBackgroundColors;

        // Bounds-related sizes
        Rect mBounds;
        float mCenterX;
        float mCenterY;
        float mSecLength;
        float mMinLength;
        float mHrLength;


        boolean mAmbient;
        Calendar mCalendar;
        Time mTime;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getTimeZone(intent.getStringExtra("time-zone")));
                mCalendar.setTimeInMillis(System.currentTimeMillis());

                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        int mTapCount;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            Resources.Theme theme = ElasticWatchFace.this.getTheme();

            setWatchFaceStyle(new WatchFaceStyle.Builder(ElasticWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setViewProtectionMode(WatchFaceStyle.PROTECT_STATUS_BAR)
                    .setStatusBarGravity(Gravity.TOP)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = ElasticWatchFace.this.getResources();

            mBackgroundColors = resources.getIntArray(R.array.backgrounds);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(mBackgroundColors[0]);

            mLogoColor = new RotateDrawable();
            mLogoColor.setDrawable(resources.getDrawable(R.drawable.elastic_color, theme));
            mLogoColor.setToDegrees(30);
            mLogoWhite = resources.getDrawable(R.drawable.elastic_white, theme);

            mHandFillColor = resources.getColor(R.color.hand_fill);
            mHandStrokeColor = resources.getColor(R.color.hand_outline);
            mHandFillAmbientColor = resources.getColor(R.color.hand_fill_ambient);
            mHandStrokeAmbientColor = resources.getColor(R.color.hand_outline_ambient);
            mSecondHandFillColor = resources.getColor(R.color.second_hand_fill);
            mSecondHandStrokeColor = resources.getColor(R.color.second_hand_outline);

            mHandOutlinePaint = new Paint();
            mHandOutlinePaint.setAntiAlias(true);
            mHandOutlinePaint.setColor(mHandStrokeColor);
//            mHandOutlinePaint.setShadowLayer(5.0f, 1.0f, 1.0f, Color.GRAY);
            mHandOutlinePaint.setStrokeCap(Paint.Cap.ROUND);

            mHandFillPaint = new Paint();
            mHandFillPaint.setAntiAlias(true);
            mHandFillPaint.setColor(mHandFillColor);
            mHandFillPaint.setStrokeCap(Paint.Cap.ROUND);

            mSecondHandOutlinePaint = new Paint(mHandOutlinePaint);
            mSecondHandOutlinePaint.setColor(mSecondHandFillColor);

            mSecondHandFillPaint = new Paint(mHandFillPaint);
            mSecondHandFillPaint.setColor(mSecondHandFillColor);

            mTime = new Time();
            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();

                mCalendar.setTimeZone(TimeZone.getDefault());
                mCalendar.setTimeInMillis(System.currentTimeMillis());
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            ElasticWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            ElasticWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHandOutlinePaint.setAntiAlias(!inAmbientMode);
                    mHandFillPaint.setAntiAlias(!inAmbientMode);
                    mSecondHandOutlinePaint.setAntiAlias(!inAmbientMode);
                    mSecondHandFillPaint.setAntiAlias(!inAmbientMode);
                }

                mHandOutlinePaint.setColor(mAmbient ? mHandStrokeAmbientColor : mHandStrokeColor);
                mHandFillPaint.setColor(mAmbient ? mHandFillAmbientColor : mHandFillColor);

                invalidate();
            }


            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            Resources resources = ElasticWatchFace.this.getResources();
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    mTapCount++;
                    mBackgroundPaint.setColor(mBackgroundColors[mTapCount % mBackgroundColors.length]);
                    invalidate();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            if (!bounds.equals(mBounds)) {
                // Need to update all bounds-specific sizes
                mBounds = bounds;

                // Find the center. Ignore the window insets so that, on round watches with a
                // "chin", the watch face is centered on the entire screen, not just the usable
                // portion.
                mCenterX = bounds.width() / 2f;
                mCenterY = bounds.height() / 2f;
                mSecLength = mCenterX * 0.95f;
                mMinLength = mCenterX * 0.9f;
                mHrLength = mCenterX * 0.6f;

                mHandOutlinePaint.setStrokeWidth(mCenterX * 0.07f);
                mHandFillPaint.setStrokeWidth(mCenterX * 0.04f);
                mSecondHandOutlinePaint.setStrokeWidth(mCenterX * 0.02f);
                mSecondHandFillPaint.setStrokeWidth(mCenterX * 0.01f);

                int shift = (int) (bounds.width() * 0.05);
                mLogoWhite.setBounds(shift, shift, bounds.width() - shift, bounds.height() - shift);
                mLogoColor.setBounds(shift, shift, bounds.width() - shift, bounds.height() - shift);

            }

            // Draw the background.
            if (mAmbient) {
                canvas.drawColor(Color.BLACK);
                if (!mLowBitAmbient) {
                    mLogoWhite.draw(canvas);
                }
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
                mLogoColor.draw(canvas);
            }

            mTime.setToNow();

            mCalendar.setTimeInMillis(System.currentTimeMillis());
            float second = mCalendar.get(Calendar.SECOND);
            float millis = mCalendar.get(Calendar.MILLISECOND);
            float secRot = (second + (millis / 1000f)) / 30f * (float) Math.PI;

            int minutes = mCalendar.get(Calendar.MINUTE);
            float minRot = (minutes + (second / 60f)) / 30f * (float) Math.PI;

            int hours = mCalendar.get(Calendar.HOUR);
            float hrRot = ((hours + (minutes / 60f)) / 6f) * (float) Math.PI;
            
            float minX = (float) Math.sin(minRot) * mMinLength;
            float minY = (float) -Math.cos(minRot) * mMinLength;
            float hrX = (float) Math.sin(hrRot) * mHrLength;
            float hrY = (float) -Math.cos(hrRot) * mHrLength;

            canvas.drawLine(mCenterX, mCenterY, mCenterX + minX, mCenterY + minY, mHandOutlinePaint);
            canvas.drawLine(mCenterX, mCenterY, mCenterX + minX, mCenterY + minY, mHandFillPaint);
            canvas.drawLine(mCenterX, mCenterY, mCenterX + hrX, mCenterY + hrY, mHandOutlinePaint);
            canvas.drawLine(mCenterX, mCenterY, mCenterX + hrX, mCenterY + hrY, mHandFillPaint);

            if (!mAmbient) {
                float secX = (float) Math.sin(secRot) * mSecLength;
                float secY = (float) -Math.cos(secRot) * mSecLength;
                canvas.drawLine(mCenterX, mCenterY, mCenterX + secX, mCenterY + secY, mSecondHandOutlinePaint);
                canvas.drawLine(mCenterX, mCenterY, mCenterX + secX, mCenterY + secY, mSecondHandFillPaint);
            }

            canvas.drawCircle(mCenterX, mCenterY, mCenterX * 0.065f, mHandOutlinePaint);
            canvas.drawCircle(mCenterX, mCenterY, mCenterX * 0.06f, mHandFillPaint);

        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}
