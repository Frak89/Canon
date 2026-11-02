package com.ampyeos.canon;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

public final class GuideOverlay extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    public GuideOverlay(Context c){super(c);setClickable(false);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);
        float w=getWidth(),h=getHeight();
        p.setColor(Color.argb(125,40,235,220));p.setStrokeWidth(1.5f);
        for(int i=1;i<3;i++){
            c.drawLine(w*i/3,0,w*i/3,h,p);
            c.drawLine(0,h*i/3,w,h*i/3,p);
        }
        p.setStrokeWidth(2.5f);
        for(int y=1;y<3;y++)for(int x=1;x<3;x++){
            float xx=w*x/3,yy=h*y/3;
            c.drawCircle(xx,yy,5,p);
        }
    }
}
