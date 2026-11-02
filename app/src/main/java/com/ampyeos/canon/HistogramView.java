package com.ampyeos.canon;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Small 32-bin luminance histogram, updated from sampled preview frames. */
public final class HistogramView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int[] bins=new int[32];

    public HistogramView(Context context){super(context);}
    public void setHistogram(int[] source) {
        if(source==null||source.length!=32) return;
        bins=source.clone();
        invalidate();
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        int w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        paint.setColor(Color.rgb(22,32,43));
        c.drawRoundRect(0,0,w,h,10,10,paint);
        int max=1;
        for(int n:bins)max=Math.max(max,n);
        float bar=w/32f;
        for(int i=0;i<bins.length;i++) {
            float height=(h-10)*bins[i]/(float)max;
            paint.setColor(i>=30?Color.rgb(246,178,111):
                i<=2?Color.rgb(126,164,231):Color.rgb(65,215,206));
            c.drawRect(i*bar+1,h-height-3,(i+1)*bar-1,h-3,paint);
        }
    }
}
