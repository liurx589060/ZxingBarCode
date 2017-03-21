/*
 * Copyright (C) 2008 ZXing authors
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

package com.example.zxingbar.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.example.zxingbar.R;
import com.example.zxingbar.camera.CameraManager;
import com.google.zxing.ResultPoint;

import java.util.Collection;
import java.util.HashSet;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder
 * rectangle and partial transparency outside it, as well as the laser scanner
 * animation and result points.
 * 
 */
public final class ViewfinderView extends View {
	private static final String TAG = "log";
	/**
	 * 刷新界面的时间
	 */
	private static final long ANIMATION_DELAY = 20L;
	private static final int OPAQUE = 0xFF;

	/**
	 * 四个绿色边角对应的长度
	 */
	private int ScreenRate;
	
	/**
	 * 四个绿色边角对应的宽度
	 */
	private static final int CORNER_WIDTH = 12;
	/**
	 * 扫描框中的中间线的宽度
	 */
	private static final int MIDDLE_LINE_WIDTH = 6;
	
	/**
	 * 扫描框中的中间线的与扫描框左右的间隙
	 */
	private static final int MIDDLE_LINE_PADDING = 5;
	
	/**
	 * 中间那条线每次刷新移动的距离
	 */
	private static final int SPEEN_DISTANCE = 5;
	
	/**
	 * 手机的屏幕密度
	 */
	private static float density;
	/**
	 * 字体大小
	 */
	private static final int TEXT_SIZE = 12;
	/**
	 * 字体距离扫描框下面的距离
	 */
	private static final int TEXT_PADDING_TOP = 30;
	
	/**
	 * 画笔对象的引用
	 */
	private Paint paint;
	
	/**
	 * 中间滑动线的最顶端位置
	 */
	private int slideTop;
	
	/**
	 * 中间滑动线的最底端位置
	 */
	private int slideBottom;
	
	/**
	 * 将扫描的二维码拍下来，这里没有这个功能，暂时不考虑
	 */
	private Bitmap mScanRod;//扫描上下的杆
	private boolean isDefault = true;//是否使用默认，绘制背景

	private Bitmap resultBitmap;
	private final int maskColor;
	private final int resultColor;
	
	private final int resultPointColor;
	private Collection<ResultPoint> possibleResultPoints;
	private Collection<ResultPoint> lastPossibleResultPoints;

	boolean isFirst;

	/**
	 * 设置上下扫描杆
	 * @param bitmap
	 */
	public void setmScanRod(Bitmap bitmap) {
		this.mScanRod = bitmap;
	}

	/**
	 * 是否使用默认，绘制背景
	 * @param isDefault
	 */
	public void setDefault(boolean isDefault) {
		this.isDefault = isDefault;
		this.invalidate();//刷新本View
	}

	public ViewfinderView(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		density = context.getResources().getDisplayMetrics().density;
		//将像素转换成dp
		ScreenRate = (int)(18 * density);
 
		paint = new Paint();
		Resources resources = getResources();
		maskColor = resources.getColor(R.color.viewfinder_mask);
		resultColor = resources.getColor(R.color.result_view);

		resultPointColor = resources.getColor(R.color.possible_result_points);
		possibleResultPoints = new HashSet<ResultPoint>(5);
	}

	@Override
	public void onDraw(Canvas canvas) {
		//中间的扫描框，你要修改扫描框的大小，去CameraManager里面修改
		Rect frame = CameraManager.get().getFramingRect();
		if (frame == null) {
			return;
		}
		
		//初始化中间线滑动的最上边和最下边
		if(!isFirst){
			isFirst = true;
			slideTop = frame.top;
			slideBottom = frame.bottom;
		}

		//获取屏幕的宽和高
		int width = canvas.getWidth();
		int height = canvas.getHeight();

		int rootWidth = this.getRootView().getWidth();
		int rootHeight = this.getRootView().getHeight();

		//画扫描框边上的角，总共8个部分
		if(isDefault) {
			paint.setColor(Color.parseColor("#2EC7CA"));
			canvas.drawRect(0, 0, ScreenRate,
					CORNER_WIDTH, paint);
			canvas.drawRect(0, 0, CORNER_WIDTH, ScreenRate, paint);
			canvas.drawRect(width - ScreenRate, 0, width,
					CORNER_WIDTH, paint);
			canvas.drawRect(width - CORNER_WIDTH, 0, width,
					ScreenRate, paint);
			canvas.drawRect(0, height - CORNER_WIDTH,
					ScreenRate, height, paint);
			canvas.drawRect(0, height - ScreenRate,
					CORNER_WIDTH, height, paint);
			canvas.drawRect(width - ScreenRate, height - CORNER_WIDTH,
					width, height, paint);
			canvas.drawRect(width - CORNER_WIDTH, height - ScreenRate,
					width, height, paint);
		}


		//绘制中间的线,每次刷新界面，中间的线往下移动SPEEN_DISTANCE
		slideTop += SPEEN_DISTANCE;
		if(slideTop >= height){
			slideTop = 0;
		}
		if(this.mScanRod != null) {
//				Bitmap bitmap = BitmapFactory.decodeResource(getResources(), R.drawable.code_scan_middle_line);
			//canvas.drawRect(frame.left + MIDDLE_LINE_PADDING, slideTop - MIDDLE_LINE_WIDTH/2,
			//			frame.right - MIDDLE_LINE_PADDING,slideTop + MIDDLE_LINE_WIDTH/2, paint);
			int bitHeight = mScanRod.getHeight();
			Rect rect = new Rect(MIDDLE_LINE_PADDING, slideTop,
					width - MIDDLE_LINE_PADDING,bitHeight + slideTop);
			canvas.drawBitmap(mScanRod, null, rect, null);
		}

		Collection<ResultPoint> currentPossible = possibleResultPoints;
		Collection<ResultPoint> currentLast = lastPossibleResultPoints;
		if (currentPossible.isEmpty()) {
			lastPossibleResultPoints = null;
		} else {
			possibleResultPoints = new HashSet<ResultPoint>(5);
			lastPossibleResultPoints = currentPossible;
			paint.setAlpha(OPAQUE);
			paint.setColor(resultPointColor);
			for (ResultPoint point : currentPossible) {
				canvas.drawCircle(frame.left + point.getX(), frame.top
						+ point.getY(), 6.0f, paint);
			}
		}
		if (currentLast != null) {
			paint.setAlpha(OPAQUE / 2);
			paint.setColor(resultPointColor);
			for (ResultPoint point : currentLast) {
				canvas.drawCircle(frame.left + point.getX(), frame.top
						+ point.getY(), 3.0f, paint);
			}
		}


		//只刷新扫描框的内容，其他地方不刷新
		postInvalidateDelayed(ANIMATION_DELAY, frame.left, frame.top,
				frame.right, frame.bottom);

	}

	public void drawViewfinder() {
		resultBitmap = null;
		invalidate();
	}

	/**
	 * Draw a bitmap with the result points highlighted instead of the live
	 * scanning display.
	 * 
	 * @param barcode
	 *            An image of the decoded barcode.
	 */
	public void drawResultBitmap(Bitmap barcode) {
		resultBitmap = barcode;
		invalidate();
	}

	public void addPossibleResultPoint(ResultPoint point) {
		possibleResultPoints.add(point);
	}

}
