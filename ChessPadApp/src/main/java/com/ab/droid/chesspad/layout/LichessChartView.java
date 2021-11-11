package com.ab.droid.chesspad.layout;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ab.droid.chesspad.ChessPad;
import com.ab.droid.chesspad.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SuppressLint("ViewConstructor")
public class LichessChartView extends RelativeLayout {
	private static final int
		LABEL_FONT_SIZE_RATIO = 50,
		LABEL_WIDTH_RATIO = 8,
        TOP_PANE_FONT_SIZE_RATIO = 40,
		TOP_PANE_HEIGHT_RATIO = 8,
        BOTTOM_PANE_FONT_SIZE_RATIO = TOP_PANE_FONT_SIZE_RATIO,
		BOTTOM_PANE_HEIGHT_RATIO = 4,
		BOTTOM_ROW_SIZE = 8,
        INIT_MIN_Y = 1490,
        INIT_MAX_Y = 1510,
		dummy_int = 0;

	private final ChessPad context;
	private ChartSettings chartSettings = new ChartSettings();	// todo:
	private final Paint mPaint = new Paint();

	private List<Double> values;
	private List<Double> labelValues;
	private double minY, maxY;
	private int gridLeft, gridTop, gridRight, gridBottom;
	private LinearLayout leftLabelLayout;
	private LinearLayout topLabelLayout;
	private final LinearLayout[] bottomLabelLayouts = new LinearLayout[2];

	public LichessChartView(ChessPad context) {
		this(context, null);
	}

	public LichessChartView(ChessPad context, ChartSettings chartSettings) {
		super(context, null, 0);
		this.context = context;
		if (chartSettings != null) {
			this.chartSettings = chartSettings;
		}
		setBackgroundColor(this.chartSettings.backgroundColor);
	}

	private void setLayouts() {
		this.removeAllViews();
		int k = getChildCount();

		Context context = getContext();

		leftLabelLayout = new LinearLayout(context);
		leftLabelLayout.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
		leftLabelLayout.setOrientation(LinearLayout.VERTICAL);

		topLabelLayout = new LinearLayout(context);
		topLabelLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
		topLabelLayout.setOrientation(LinearLayout.HORIZONTAL);

		LayoutParams bottomLabelParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
		bottomLabelParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
		for (int i = 0; i < bottomLabelLayouts.length; ++i) {
			bottomLabelLayouts[i] = new LinearLayout(context);
			bottomLabelLayouts[i].setLayoutParams(bottomLabelParams);
			bottomLabelLayouts[i].setOrientation(LinearLayout.HORIZONTAL);
			addView(bottomLabelLayouts[i]);
		}

		addView(leftLabelLayout);
		addView(topLabelLayout);
	}

    public void setValues(List<Double> values) {
	    boolean newValues = this.values == null || this.values.size() != values.size();

	    if (values.size() <= 1) {
            minY = INIT_MIN_Y;
            maxY = INIT_MAX_Y;
        } else {
            minY = Double.MAX_VALUE;
            maxY = Double.MIN_VALUE;

            for (int i = 0; i < values.size(); ++i) {
                double value = values.get(i);
                if (!newValues) {
                    if (value != this.values.get(i)) {
                        newValues = true;
                    }
                }
                if (minY > value) {
                    minY = value;
                }
                if (maxY < value) {
                    maxY = value;
                }
            }
        }

        this.values = values;
		double yStep = (maxY - minY) / (chartSettings.gridLinesVertical + 1);
		double yFactor;
		double newFactor = 1;
		do {
			yFactor = newFactor;
			newFactor *= 10;
		} while (Math.floor(yStep / newFactor) > 0);

		minY = Math.floor(minY / yFactor) * yFactor;
		maxY = Math.ceil(maxY / yFactor) * yFactor;
		yStep = Math.ceil((maxY - minY) / (chartSettings.gridLinesVertical + 1) / yFactor) * yFactor;
		maxY = minY + yStep * (chartSettings.gridLinesVertical + 1);

        if (newValues) {
            labelValues = new ArrayList<>();
            double value = minY;
            for (int i = 0; i <= chartSettings.gridLinesVertical; ++i) {
                labelValues.add(value);
                value += yStep;
            }
            labelValues.add(value);
            setLayouts();
        }
		this.invalidate();
	}

	@Override
    public void invalidate() {
	    super.invalidate();
        this.requestLayout();
    }

	@SuppressLint("RtlHardcoded")
	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		if (leftLabelLayout == null) {
		    setLayouts();
        }
		int mLeftLabelWidth = getWidth() / LABEL_WIDTH_RATIO;
		int mTopLabelHeight = getHeight() / TOP_PANE_HEIGHT_RATIO;
		int mBottomLabelHeight = getHeight() / BOTTOM_PANE_HEIGHT_RATIO;

		gridLeft = mLeftLabelWidth + chartSettings.gridLineWidth - 1;
		gridRight = getWidth() - chartSettings.gridLineWidth;
		gridTop = mTopLabelHeight + chartSettings.gridLineWidth - 1;
		gridBottom = getHeight() - mBottomLabelHeight - chartSettings.gridLineWidth;

		// Set layout sizes
		LayoutParams leftParams = (LayoutParams) leftLabelLayout.getLayoutParams();
		leftParams.width = mLeftLabelWidth;
		leftParams.height = gridBottom - gridTop;
		leftLabelLayout.setLayoutParams(leftParams);

		LayoutParams topParams = (LayoutParams) topLabelLayout.getLayoutParams();
		topParams.width = gridRight - gridLeft;
		topLabelLayout.setLayoutParams(topParams);
		topLabelLayout.setBackgroundColor(Color.TRANSPARENT);
		topLabelLayout.setGravity(Gravity.CENTER);

		int height = mBottomLabelHeight / bottomLabelLayouts.length;
		for (int i = 0; i < bottomLabelLayouts.length; ++i) {
			LayoutParams bottomParams = (LayoutParams) bottomLabelLayouts[i].getLayoutParams();
			bottomParams.width = gridRight - gridLeft;
			bottomParams.height = height;
			bottomLabelLayouts[i].setLayoutParams(bottomParams);
			bottomLabelLayouts[i].setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
			bottomLabelLayouts[i].layout(gridLeft, gridBottom + i * height, gridRight, gridBottom + (i + 1) * height);
		}

		leftLabelLayout.layout(0, gridTop, gridLeft, gridBottom);
		topLabelLayout.layout(0, 0, gridRight, gridTop);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		drawGrid(canvas);
		drawLeftLabels(canvas);
		drawTopLabels(canvas);
		drawBottomLabels(canvas);
		drawSeries(canvas);
	}

	private void drawGrid(Canvas canvas) {
		mPaint.setColor(chartSettings.gridLineColor);
		mPaint.setStrokeWidth(chartSettings.gridLineWidth);

		float stepX = (gridRight - gridLeft) / (float) (chartSettings.gridLinesHorizontal + 1);
		float stepY = (gridBottom - gridTop) / (float) (chartSettings.gridLinesVertical + 1);

		float left = gridLeft;
		float right = gridRight;
		float top = gridTop;
		float bottom = gridBottom;

		for (int i = 0; i < chartSettings.gridLinesHorizontal + 2; i++) {
			canvas.drawLine(left + (stepX * i), top, left + (stepX * i), bottom, mPaint);
		}

		for (int i = 0; i < chartSettings.gridLinesVertical + 2; i++) {
			canvas.drawLine(left, top + (stepY * i), right, top + (stepY * i), mPaint);
		}
	}

	@SuppressLint("RtlHardcoded")
	private void drawLeftLabels(Canvas canvas) {
		final int labelCount = labelValues.size();
		for (int i = 0; i < labelCount; i++) {
			View view = leftLabelLayout.getChildAt(i);
			if (view == null) {
				LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0);
				if (i == 0 || i == labelCount - 1) {
					params.weight = 0.7f;
				}
				else {
					params.weight = 1;
				}

				int position = (labelCount - 1) - i;
				TextView labelTextView = new TextView(context);
				int gravity;
				if (position == 0) {
					gravity = Gravity.BOTTOM | Gravity.RIGHT;
				} else if (position == labelCount - 1) {
					gravity = Gravity.TOP | Gravity.RIGHT;
				} else {
					gravity = Gravity.CENTER | Gravity.RIGHT;
				}

				labelTextView.setGravity(gravity);
				float size = (float)context.getMainLayout().getHeight() / LABEL_FONT_SIZE_RATIO;
				labelTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
				labelTextView.setPadding(0, 0, 8, 0);
				labelTextView.setText(String.format(Locale.US, "%.0f", labelValues.get(position)));

				labelTextView.setLayoutParams(params);
				leftLabelLayout.addView(labelTextView);
			}
		}
	}

	private void drawTopLabels(Canvas canvas) {
		View view = topLabelLayout.getChildAt(0);
		if (view == null) {
			TextView topTextView = new TextView(context);
			topTextView.setGravity(Gravity.CENTER);
			float size = (float) context.getMainLayout().getHeight() / TOP_PANE_FONT_SIZE_RATIO;
			topTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
			topTextView.setPadding(0, 0, 0, 0);
			topTextView.setText(String.format(Locale.US, "Your puzzle rating: %.0f", values.get(values.size() - 1)));
			topLabelLayout.addView(topTextView);
		}
	}

	private void drawBottomLabels(Canvas canvas) {
		final int count = values.size() - 1;
		for (int i = 0; i < count; i++) {
			int j = i / BOTTOM_ROW_SIZE;
			int k = i % BOTTOM_ROW_SIZE;
			View view = bottomLabelLayouts[j].getChildAt(k);
			if (view == null) {
				TextView textView = new TextView(context);
				textView.setGravity(Gravity.CENTER);
				float size = (float) context.getMainLayout().getHeight() / BOTTOM_PANE_FONT_SIZE_RATIO;
				textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
				textView.setPadding(2, 2, 2, 2);
				double val = values.get(i + 1) - values.get(i);
				int bgResource = R.drawable.bordered_red;
				String sign = "";
				if (val > 0) {
					sign = "+";
					bgResource = R.drawable.bordered_green;
				}
				textView.setBackgroundResource(bgResource);
				textView.setText(String.format(Locale.US,"%s%.0f", sign, val));
				bottomLabelLayouts[j].addView(textView);
			}
		}
	}

	private void drawSeries(Canvas canvas) {
		mPaint.setColor(chartSettings.lineColor);
		mPaint.setStrokeWidth(chartSettings.lineWidth);

		float stepX = (float) (gridRight - gridLeft) / (values.size() - 1);
		float scaleY = (float) ((gridBottom - gridTop) / (maxY - minY));

		float lastX = gridLeft;
		float lastY = (float) (gridBottom - scaleY * (values.get(0) - minY));
		for (int i = 1; i < values.size(); ++i) {
			double value = values.get(i);
			float x = lastX + stepX;
			float y = (float) (gridBottom - scaleY * (value - minY));
			canvas.drawLine(lastX, lastY, x, y, mPaint);
			lastX = x;
			lastY = y;
		}
	}

	public static class ChartSettings {
		// todo: parameterize
		public final int backgroundColor = Color.CYAN;
		public final int lineColor = 0xFF0099CC;
		public final int lineWidth = 2;

		public final int gridLineColor = Color.LTGRAY;
		public final int gridLineWidth = 1;
		public final int gridLinesHorizontal = 4;
		public final int gridLinesVertical = 3;
	}
}