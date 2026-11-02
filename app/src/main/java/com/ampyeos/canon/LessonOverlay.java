package com.ampyeos.canon;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * Animated photography lesson annotations. Coordinates refer to the visible FIT_CENTER image.
 * Does not simulate touching Android controls: all "movements" are illustrated on the image.
 */
public final class LessonOverlay extends View {
    public static final int INTRO=0, LIGHT=1, COMPOSITION=2, COLOR=3, COMPARE=4;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private int step=INTRO;
    private float progress=0f;
    private RectF crop=new RectF(.08f,.08f,.92f,.92f);
    private int imageWidth=1,imageHeight=1;
    private ValueAnimator animation;
    private float L,T,R,B;

    public LessonOverlay(Context c){super(c);setClickable(false);}
    public void configure(int w,int h,RectF proposal) {
        imageWidth=Math.max(1,w);imageHeight=Math.max(1,h);
        if(proposal!=null)crop.set(proposal);
        invalidate();
    }
    public void showStep(int next) {
        step=Math.max(INTRO,Math.min(COMPARE,next));
        if(animation!=null)animation.cancel();
        animation=ValueAnimator.ofFloat(0f,1f);
        animation.setDuration(2600);
        animation.setRepeatCount(ValueAnimator.INFINITE);
        animation.setRepeatMode(ValueAnimator.REVERSE);
        animation.setInterpolator(new AccelerateDecelerateInterpolator());
        animation.addUpdateListener(a->{progress=(Float)a.getAnimatedValue();invalidate();});
        animation.start();
    }
    public void pause(){if(animation!=null && animation.isRunning())animation.pause();}
    public void play(){if(animation!=null && animation.isPaused())animation.resume();}
    private void bounds() {
        float vw=getWidth(),vh=getHeight();
        float s=Math.min(vw/imageWidth,vh/imageHeight);
        float w=imageWidth*s,h=imageHeight*s;
        L=(vw-w)/2;T=(vh-h)/2;R=L+w;B=T+h;
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        bounds();
        float w=R-L,h=B-T;
        if(w<=0||h<=0)return;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3.5f);
        if(step==INTRO){
            p.setColor(Color.argb(210,45,233,208));
            for(int i=1;i<=2;i++){
                c.drawLine(L+w*i/3,T,L+w*i/3,B,p);
                c.drawLine(L,T+h*i/3,R,T+h*i/3,p);
            }
            pointer(c,L+w*(.3f+.35f*progress),T+h*(.48f-.17f*progress));
        }else if(step==LIGHT){
            p.setColor(0xffffb45b);p.setStrokeWidth(5);
            float yy=T+h*.12f;
            c.drawRect(L+w*.1f,T+h*.08f,L+w*.9f,yy+h*.055f,p);
            p.setColor(Color.argb(160,255,225,90));p.setStyle(Paint.Style.FILL);
            c.drawCircle(L+w*(.70f+.13f*progress),T+h*.3f,15+20*progress,p);
            pointer(c,L+w*(.72f+.12f*progress),T+h*.3f);
        }else if(step==COMPOSITION){
            // Interpolate from full frame to suggested normalized crop.
            float left=lerp(0,crop.left,progress),top=lerp(0,crop.top,progress);
            float right=lerp(1,crop.right,progress),bottom=lerp(1,crop.bottom,progress);
            float x1=L+w*left,y1=T+h*top,x2=L+w*right,y2=T+h*bottom;
            p.setColor(Color.argb(170,5,8,12));p.setStyle(Paint.Style.FILL);
            c.drawRect(L,T,R,y1,p);c.drawRect(L,y2,R,B,p);
            c.drawRect(L,y1,x1,y2,p);c.drawRect(x2,y1,R,y2,p);
            p.setColor(0xff43eadb);p.setStrokeWidth(4);p.setStyle(Paint.Style.STROKE);
            c.drawRect(x1,y1,x2,y2,p);
            for(int i=1;i<3;i++){
                c.drawLine(x1+(x2-x1)*i/3,y1,x1+(x2-x1)*i/3,y2,p);
                c.drawLine(x1,y1+(y2-y1)*i/3,x2,y1+(y2-y1)*i/3,p);
            }
            pointer(c,x2-22,y2-22);
        }else if(step==COLOR){
            p.setStyle(Paint.Style.STROKE);p.setColor(0xffffbf74);p.setStrokeWidth(5);
            c.drawRoundRect(L+w*.15f,T+h*.74f,L+w*.85f,T+h*.84f,18,18,p);
            pointer(c,L+w*(.2f+.60f*progress),T+h*.78f);
        }else if(step==COMPARE){
            float x=L+w*(.1f+.8f*progress);
            p.setColor(0xff43eadb);p.setStrokeWidth(5);p.setStyle(Paint.Style.STROKE);
            c.drawLine(x,T,x,B,p);
            pointer(c,x,T+h*.5f);
        }
    }
    private static float lerp(float a,float b,float t){return a+(b-a)*t;}
    private void pointer(Canvas c,float x,float y){
        p.setColor(Color.WHITE);p.setStyle(Paint.Style.FILL);
        c.drawCircle(x,y,9,p);
        p.setColor(Color.argb(185,255,255,255));p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);
        c.drawCircle(x,y,18+10*progress,p);
    }
    @Override protected void onDetachedFromWindow(){
        if(animation!=null)animation.cancel();
        super.onDetachedFromWindow();
    }
}
