package com.eagle.gallery.pro.views;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatImageView;

public class DrawImageView extends AppCompatImageView {

    public DrawImageView(Context context) {
        super(context);
    }

    public DrawImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DrawImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            super.onDraw(canvas);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDrawForeground(Canvas canvas) {
        try {
            super.onDrawForeground(canvas);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
