package com.rama.tui_zero.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import com.rama.tui_zero.managers.FontManager;
import com.rama.tui_zero.managers.ZoomManager;
import com.rama.tui_zero.objects.Themes;
import com.rama.tui_zero.objects.Visualizer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws the lines of a Visualizer on a labelled grid. The window is fitted around the complete lines and stays put
 * while they animate, and both axes share one scale (a unit is as wide as it is tall). Pan and zoom would change
 * cx, cy and scale.
 *
 * The animation needs no timer: while something is still moving, onDraw asks for the next frame, and a view that
 * is hidden or scrolled away is not drawn, so it stops by itself and carries on when it is visible again.
 */
public class WdPlot extends View {
    private static final double EMPTY_HALF_WIDTH = 10;
    private static final double MARGIN = 1.2;
    private static final int MAX_DOTS = 2000;

    private final Paint gridPaint = new Paint();
    private final Paint axisPaint = new Paint();
    private final Paint curvePaint = new Paint();
    private final Paint dotPaint = new Paint();
    private final Paint labelPaint = new Paint();
    private final Path path = new Path();
    private final float density;

    private int background = 0xFF11111B;
    private Themes.Palette palette;
    private List<Visualizer.Series> series = new ArrayList<Visualizer.Series>();
    private Visualizer.Animation animation;
    private long startedAt;
    // Window of the complete lines, worked out once per scene.
    private boolean hasBounds;
    private double cx, cy, spanX, spanY;

    public WdPlot(Context context) {
        this(context, null);
    }

    public WdPlot(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = context.getResources().getDisplayMetrics().density;
        // Pixel look: no smoothing on the lines, like the icons.
        gridPaint.setStrokeWidth(Math.max(1f, density));
        axisPaint.setStrokeWidth(Math.max(1f, density * 2));
        curvePaint.setStyle(Paint.Style.STROKE);
        curvePaint.setStrokeWidth(density * 2);
        curvePaint.setStrokeJoin(Paint.Join.ROUND);
        labelPaint.setAntiAlias(true);
        Typeface font = FontManager.getJersey25(context);
        if (font != null) labelPaint.setTypeface(font);
        applyLabelSize();
    }

    /** Shows a built Visualizer and starts its animation from the beginning. Null clears the canvas. */
    public void setScene(Visualizer.Result result) {
        series = result == null ? new ArrayList<Visualizer.Series>() : result.series;
        animation = result == null ? null : result.animation;
        startedAt = SystemClock.uptimeMillis();
        fitWindow();
        invalidate();
    }

    public void setPalette(Themes.Palette palette) {
        this.palette = palette;
        background = palette.crust;
        gridPaint.setColor(palette.surface_0);
        axisPaint.setColor(palette.border);
        labelPaint.setColor(palette.disabled);
        applyLabelSize(); // called after every zoom change too, so the labels follow the app zoom
        invalidate();
    }

    private void fitWindow() {
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Visualizer.Series s : series) {
            for (int i = 0; i < s.xs.length; i++) {
                if (!isFinite(s.xs[i]) || !isFinite(s.ys[i])) continue;
                minX = Math.min(minX, s.xs[i]);
                maxX = Math.max(maxX, s.xs[i]);
                minY = Math.min(minY, s.ys[i]);
                maxY = Math.max(maxY, s.ys[i]);
            }
        }
        hasBounds = minX <= maxX;
        if (hasBounds) {
            cx = (minX + maxX) / 2;
            cy = (minY + maxY) / 2;
            spanX = maxX - minX;
            spanY = maxY - minY;
        }
    }

    private void applyLabelSize() {
        labelPaint.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity * ZoomManager.getPercent(getContext()) / 100f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;
        canvas.drawColor(background);

        double centerX = 0, centerY = 0, scale = w / (2 * EMPTY_HALF_WIDTH);
        if (hasBounds) {
            centerX = cx;
            centerY = cy;
            if (spanX == 0 && spanY == 0) {
                scale = Math.min(w, h) / 4.0; // a single point: show two units around it
            } else {
                double sx = spanX > 0 ? w / (spanX * MARGIN) : Double.MAX_VALUE;
                double sy = spanY > 0 ? h / (spanY * MARGIN) : Double.MAX_VALUE;
                scale = Math.min(sx, sy);
            }
        }

        double halfW = w / 2.0 / scale, halfH = h / 2.0 / scale;
        double step = niceStep(Math.max(halfW, halfH) * 2 / 8);
        drawGrid(canvas, w, h, scale, centerX, centerY, halfW, halfH, step);

        long elapsed = SystemClock.uptimeMillis() - startedAt;
        int[] ranges = animation == null ? null : animation.ranges(elapsed);
        for (Visualizer.Series s : series) {
            int color = palette != null ? Themes.lineColor(palette, s.color, s.formulaIndex) : 0xFFCBA6F7;
            drawSeries(canvas, s, ranges, color, w, h, scale, centerX, centerY);
        }
        if (animation != null && animation.isRunning(elapsed)) postInvalidateOnAnimation();
    }

    private static boolean isFinite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
    }

    private void drawGrid(Canvas canvas, int w, int h, double scale, double cx, double cy, double halfW, double halfH, double step) {
        float originX = (float) (w / 2.0 - cx * scale);
        float originY = (float) (h / 2.0 + cy * scale);
        float pad = 4 * density;
        float textH = labelPaint.getTextSize();

        for (long i = (long) Math.ceil((cx - halfW) / step); i * step <= cx + halfW; i++) {
            if (i == 0) continue;
            float px = (float) (w / 2.0 + (i * step - cx) * scale);
            canvas.drawLine(px, 0, px, h, gridPaint);
            labelPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(format(i * step), px, Math.max(textH, Math.min(h - 2, originY + textH + pad)), labelPaint);
        }
        for (long i = (long) Math.ceil((cy - halfH) / step); i * step <= cy + halfH; i++) {
            if (i == 0) continue;
            float py = (float) (h / 2.0 - (i * step - cy) * scale);
            canvas.drawLine(0, py, w, py, gridPaint);
            labelPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(format(i * step), Math.max(2, Math.min(w - textH * 3, originX + pad)), py - pad, labelPaint);
        }
        if (originX >= 0 && originX <= w) canvas.drawLine(originX, 0, originX, h, axisPaint);
        if (originY >= 0 && originY <= h) canvas.drawLine(0, originY, w, originY, axisPaint);
    }

    /** Draws the given stretches of points (pairs from, to excluded; null: all) as lines, the newest point marked. */
    private void drawSeries(Canvas canvas, Visualizer.Series s, int[] ranges, int color, int w, int h, double scale, double cx, double cy) {
        curvePaint.setColor(color);
        dotPaint.setColor(color);
        path.reset();
        if (ranges == null) ranges = new int[]{0, s.xs.length};
        int shown = 0;
        for (int r = 0; r < ranges.length; r += 2) shown += Math.max(0, Math.min(ranges[r + 1], s.xs.length) - ranges[r]);
        boolean dots = shown <= MAX_DOTS;
        float half = 2 * density;
        float headX = 0, headY = 0;
        boolean hasHead = false;
        for (int r = 0; r < ranges.length; r += 2) {
            boolean penDown = false; // every stretch starts a new line
            int end = Math.min(ranges[r + 1], s.xs.length);
            for (int i = ranges[r]; i < end; i++) {
                if (!isFinite(s.xs[i]) || !isFinite(s.ys[i])) {
                    penDown = false; // a gap: the formula had no value here
                    continue;
                }
                float px = (float) (w / 2.0 + (s.xs[i] - cx) * scale);
                float py = (float) (h / 2.0 - (s.ys[i] - cy) * scale);
                if (penDown) path.lineTo(px, py);
                else path.moveTo(px, py);
                penDown = true;
                if (dots) canvas.drawRect(px - half, py - half, px + half, py + half, dotPaint);
                headX = px;
                headY = py;
                hasHead = true;
            }
        }
        canvas.drawPath(path, curvePaint);
        if (hasHead && shown > 1) {
            float big = half * 2.2f;
            canvas.drawRect(headX - big, headY - big, headX + big, headY + big, dotPaint);
        }
    }

    /** 1, 2 or 5 times a power of ten, at least as big as the wanted step. */
    private static double niceStep(double wanted) {
        double pow = Math.pow(10, Math.floor(Math.log10(wanted)));
        double[] multipliers = {1, 2, 5, 10};
        for (double m : multipliers) {
            if (m * pow >= wanted) return m * pow;
        }
        return 10 * pow;
    }

    private static String format(double value) {
        return new BigDecimal(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
