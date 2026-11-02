package com.ampyeos.canon;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Touch-controlled crop rectangle, normalized to the bitmap displayed with FIT_CENTER. */
public final class CropView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF frame=new RectF(.08f,.08f,.92f,.92f);
    private float prevX,prevY;
    private boolean cropping=true;
    private int drag=0;
    private int width=1,height=1;
    private float aspect=0;
    private float left,top,right,bottom;

    public CropView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
    public void setBitmapSize(int w,int h){width=Math.max(1,w);height=Math.max(1,h);invalidate();}
    public void setCropEnabled(boolean enabled){cropping=enabled;invalidate();}
    public void setAspect(float ratio){
        aspect=ratio;
        frame=new RectF(.08f,.08f,.92f,.92f);
        if(ratio>0) fitRatio();
        invalidate();
    }
    public RectF normalized(){return new RectF(frame);}
    private void bounds(){
        float viewW=getWidth(),viewH=getHeight();
        if(viewW==0||viewH==0)return;
        float scale=Math.min(viewW/width,viewH/height);
        float w=width*scale,h=height*scale;
        left=(viewW-w)/2f;top=(viewH-h)/2f;right=left+w;bottom=top+h;
    }
    private void fitRatio(){
        float target=aspect*height/(float)width;
        float w=frame.width(),h=frame.height();
        if(w/h>target)w=h*target;else h=w/target;
        float cx=frame.centerX(),cy=frame.centerY();
        frame.set(cx-w/2,cy-h/2,cx+w/2,cy+h/2);
        clamp();
    }
    private void clamp(){
        float min=.06f;
        if(frame.width()<min)frame.right=frame.left+min;
        if(frame.height()<min)frame.bottom=frame.top+min;
        if(frame.left<0)frame.offset(-frame.left,0);
        if(frame.top<0)frame.offset(0,-frame.top);
        if(frame.right>1)frame.offset(1-frame.right,0);
        if(frame.bottom>1)frame.offset(0,1-frame.bottom);
    }
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        if(!cropping)return;
        bounds();
        float bw=right-left,bh=bottom-top;
        float l=left+bw*frame.left,t=top+bh*frame.top;
        float r=left+bw*frame.right,b=top+bh*frame.bottom;
        p.setStyle(Paint.Style.FILL);p.setColor(0x88000000);
        c.drawRect(left,top,right,t,p);c.drawRect(left,b,right,bottom,p);
        c.drawRect(left,t,l,b,p);c.drawRect(r,t,right,b,p);
        p.setColor(Color.WHITE);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);
        c.drawRect(l,t,r,b,p);
        p.setColor(0xff43eadb);p.setStrokeWidth(1.4f);
        for(int i=1;i<3;i++){
            c.drawLine(l+(r-l)*i/3,t,l+(r-l)*i/3,b,p);
            c.drawLine(l,t+(b-t)*i/3,r,t+(b-t)*i/3,p);
        }
        p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);
        c.drawCircle(l,t,10,p);c.drawCircle(r,t,10,p);
        c.drawCircle(l,b,10,p);c.drawCircle(r,b,10,p);
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(!cropping)return false;
        bounds();
        if(right<=left||bottom<=top)return false;
        float x=(e.getX()-left)/(right-left),y=(e.getY()-top)/(bottom-top);
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                prevX=x;prevY=y;
                float distance=.09f;
                if(Math.abs(x-frame.left)<distance&&Math.abs(y-frame.top)<distance)drag=1;
                else if(Math.abs(x-frame.right)<distance&&Math.abs(y-frame.top)<distance)drag=2;
                else if(Math.abs(x-frame.left)<distance&&Math.abs(y-frame.bottom)<distance)drag=3;
                else if(Math.abs(x-frame.right)<distance&&Math.abs(y-frame.bottom)<distance)drag=4;
                else if(frame.contains(x,y))drag=5;
                else drag=0;
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx=x-prevX,dy=y-prevY;
                if(drag==5)frame.offset(dx,dy);
                else if(drag>0) {
                    if(drag==1||drag==3)frame.left=Math.max(0,Math.min(frame.right-.06f,x));
                    else frame.right=Math.min(1,Math.max(frame.left+.06f,x));
                    if(drag==1||drag==2)frame.top=Math.max(0,Math.min(frame.bottom-.06f,y));
                    else frame.bottom=Math.min(1,Math.max(frame.top+.06f,y));
                    if(aspect>0)fitRatio();
                }
                clamp();prevX=x;prevY=y;invalidate();return true;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:drag=0;return true;
        }
        return true;
    }
}
