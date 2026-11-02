package com.ampyeos.canon;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Full-frame original/proposal comparison with touch-draggable vertical reveal. */
public final class BeforeAfterView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private Bitmap before,after;
    private float split=.5f;
    public BeforeAfterView(Context c){super(c);setClickable(true);}
    public void setImages(Bitmap original,Bitmap candidate){before=original;after=candidate;invalidate();}
    public void setSplit(float x){split=Math.max(.05f,Math.min(.95f,x));invalidate();}
    public float split(){return split;}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        c.drawColor(Color.BLACK);
        if(before==null||after==null)return;
        float divider=w*split;
        p.setColor(Color.WHITE);
        c.save();
        c.clipRect(0,0,divider,h);
        drawFit(c,before,w,h);
        c.restore();
        c.save();
        c.clipRect(divider,0,w,h);
        drawFit(c,after,w,h);
        c.restore();
        p.setColor(0xff43eadb);p.setStrokeWidth(4);
        c.drawLine(divider,0,divider,h,p);
        p.setStyle(Paint.Style.FILL);
        c.drawCircle(divider,h/2,18,p);
        p.setColor(Color.BLACK);p.setTextSize(13*getResources().getDisplayMetrics().scaledDensity);
        c.drawText("‹  ›",divider-11,h/2+5,p);
        p.setColor(Color.WHITE);
        c.drawText("ORIGINALE",14,27,p);
        c.drawText("PROPOSTA",Math.max(divider+10,w-105),27,p);
    }
    private void drawFit(Canvas c,Bitmap bmp,float w,float h){
        float scale=Math.min(w/bmp.getWidth(),h/bmp.getHeight());
        float bw=bmp.getWidth()*scale,bh=bmp.getHeight()*scale;
        float l=(w-bw)/2f,t=(h-bh)/2f;
        p.setColor(Color.WHITE);
        c.drawBitmap(bmp,null,new RectF(l,t,l+bw,t+bh),p);
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN ||
           event.getActionMasked()==MotionEvent.ACTION_MOVE){
            setSplit(event.getX()/Math.max(1,getWidth()));
            return true;
        }
        return event.getActionMasked()==MotionEvent.ACTION_UP;
    }
}
