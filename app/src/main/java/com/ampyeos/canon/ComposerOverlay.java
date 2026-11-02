package com.ampyeos.canon;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Animated live framing hints driven by coarse edge saliency. No physical camera movement. */
public final class ComposerOverlay extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box=new RectF(.09f,.10f,.91f,.90f);
    private float centerX=.5f,centerY=.5f;
    private ValueAnimator motion;
    private boolean enabled=true;
    public ComposerOverlay(Context context){super(context);setClickable(false);}
    public void setActive(boolean active){enabled=active;setVisibility(active?VISIBLE:GONE);}
    public void setTarget(float normalizedX,float normalizedY) {
        if(!enabled)return;
        normalizedX=Math.max(.25f,Math.min(.75f,normalizedX));
        normalizedY=Math.max(.25f,Math.min(.75f,normalizedY));
        final float startX=centerX,startY=centerY,endX=normalizedX,endY=normalizedY;
        if(motion!=null)motion.cancel();
        motion=ValueAnimator.ofFloat(0f,1f);
        motion.setDuration(550);
        motion.setInterpolator(new DecelerateInterpolator());
        motion.addUpdateListener(a->{
            float progress=(Float)a.getAnimatedValue();
            centerX=startX+(endX-startX)*progress;
            centerY=startY+(endY-startY)*progress;
            invalidate();
        });
        motion.start();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        if(!enabled)return;
        float w=getWidth(),h=getHeight();
        if(w<=0||h<=0)return;
        float bw=w*.76f,bh=h*.72f;
        float x=Math.max(0,Math.min(w-bw,w*centerX-bw/2));
        float y=Math.max(0,Math.min(h-bh,h*centerY-bh/2));
        paint.setColor(Color.argb(215,255,195,100));paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.7f);
        canvas.drawRoundRect(x,y,x+bw,y+bh,9,9,paint);
        for(int i=1;i<=2;i++) {
            paint.setColor(Color.argb(130,255,214,133));paint.setStrokeWidth(1.5f);
            canvas.drawLine(x+bw*i/3,y,x+bw*i/3,y+bh,paint);
            canvas.drawLine(x,y+bh*i/3,x+bw,y+bh*i/3,paint);
        }
        paint.setColor(0xfff5dc7d);paint.setStrokeWidth(2.5f);
        canvas.drawCircle(w*centerX,h*centerY,11,paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(12*getResources().getDisplayMetrics().scaledDensity);
        paint.setColor(Color.WHITE);
        canvas.drawText("CROP PROPOSTO · NON APPLICATO AL RAW",x+10,y+24,paint);
    }
    @Override protected void onDetachedFromWindow(){
        if(motion!=null)motion.cancel();
        super.onDetachedFromWindow();
    }
}
