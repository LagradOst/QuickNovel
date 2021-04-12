package com.lagradost.quicknovel;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

public class MyFlowLayout extends ViewGroup {

    public MyFlowLayout(Context context) {
        super(context);
    }

    public MyFlowLayout(Context context, AttributeSet attrs) {
        this(context,attrs,0);
    }

    public MyFlowLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int realWidth = MeasureSpec.getSize(widthMeasureSpec);

        int currentHeight = 0;
        int currentWidth = 0;

        int currentChildHookPointx = 0;
        int currentChildHookPointy = 0;

        int childCount = this.getChildCount();

        for(int i = 0; i < childCount; i++) {
            View child = getChildAt(i);
            this.measureChild(child, widthMeasureSpec, heightMeasureSpec);
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();

            //check if child can be placed in the current row, else go to next line
            if(currentChildHookPointx + childWidth > realWidth) {
                //new line
                currentWidth = Math.max(currentWidth, currentChildHookPointx);

                //reset for new line
                currentChildHookPointx = 0;

                currentChildHookPointy += childHeight;
            }

            int nextChildHookPointx;
            int nextChildHookPointy;

            nextChildHookPointx = currentChildHookPointx + childWidth;
            nextChildHookPointy = currentChildHookPointy;

            currentHeight = Math.max(currentHeight, currentChildHookPointy + childHeight);

            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            lp.x = currentChildHookPointx;
            lp.y = currentChildHookPointy;

            currentChildHookPointx = nextChildHookPointx;
            currentChildHookPointy = nextChildHookPointy;
        }

        currentWidth = Math.max(currentChildHookPointx, currentWidth);

        setMeasuredDimension(resolveSize(currentWidth, widthMeasureSpec),
                resolveSize(currentHeight, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean b, int left, int top, int right, int bottom) {
        //call layout on children
        int childCount = this.getChildCount();
        for(int i = 0; i < childCount; i++) {
            View child = this.getChildAt(i);
            LayoutParams lp = (LayoutParams) child.getLayoutParams();
            child.layout(lp.x, lp.y, lp.x + child.getMeasuredWidth(), lp.y + child.getMeasuredHeight());
        }

    }

    @Override
    public LayoutParams generateLayoutParams(AttributeSet attrs) {
        return new MyFlowLayout.LayoutParams(getContext(), attrs);
    }

    @Override
    protected LayoutParams generateDefaultLayoutParams() {
        return new MyFlowLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
    }

    @Override
    protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
        return new MyFlowLayout.LayoutParams(p);
    }

    @Override
    protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
        return p instanceof MyFlowLayout.LayoutParams;
    }

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {

        int spacing = -1;
        int x = 0;
        int y = 0;

        LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
            TypedArray t = c.obtainStyledAttributes(attrs, R.styleable.FlowLayout_Layout);
            spacing = 0;//t.getDimensionPixelSize(R.styleable.FlowLayout_Layout_layout_space, 0);
            t.recycle();
        }

        LayoutParams(int width, int height) {
            super(width, height);
            spacing = 0;
        }

        public LayoutParams(MarginLayoutParams source) {
            super(source);
        }

        LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }
}