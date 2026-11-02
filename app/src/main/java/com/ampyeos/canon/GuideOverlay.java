package com.ampyeos.canon;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Composition overlay; tilt describes the Pixel itself, not the camera over Wi-Fi. */
public final class GuideOverlay extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean grid=true, level=true;
    private float tilt=0;
    public GuideOverlay(Context c){super(c);setClickable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    public void setGrid(boolean v){grid=v;invalidate();}
    public void setLevel(boolean v){level=v;invalidate();}
    public void setTilt(float degrees){tilt=degrees;invalidate();}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        p.setStyle(Paint.Style.STROKE);
        if(grid){
            p.setColor(Color.argb(145,65,237,224));p.setStrokeWidth(1.7f);
            for(int i=1;i<3;i++){
                c.drawLine(w*i/3,0,w*i/3,h,p);
                c.drawLine(0,h*i/3,w,h*i/3,p);
            }
            p.setStrokeWidth(2.4f);
            for(int y=1;y<3;y++)for(int x=1;x<3;x++)
                c.drawCircle(w*x/3,h*y/3,5,p);
        }
        if(level){
            boolean straight=Math.abs(tilt)<2.0f;
            p.setColor(straight?Color.rgb(80,245,155):Color.rgb(255,197,86));
            p.setStrokeWidth(3.6f);
            c.save();
            c.rotate(-tilt,w/2,h/2);
            c.drawLine(w/2-64,h/2,w/2-18,h/2,p);
            c.drawLine(w/2+18,h/2,w/2+64,h/2,p);
            c.restore();
            c.drawCircle(w/2,h/2,5,p);
        }
    }
}
