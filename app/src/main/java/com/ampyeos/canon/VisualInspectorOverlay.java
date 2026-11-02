package com.ampyeos.canon;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Layered visual annotations aligned to FIT_CENTER image bounds. */
public final class VisualInspectorOverlay extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private VisualInspector.Report report;
    private boolean heatmap=true,thirds=true,horizon=true,crop=true,subject=true;
    private RectF subjectRect;
    private String subjectName="";
    private int imageW=1,imageH=1;
    public VisualInspectorOverlay(Context context){super(context);setClickable(false);}
    public void configure(VisualInspector.Report report,int w,int h){
        this.report=report;imageW=Math.max(1,w);imageH=Math.max(1,h);invalidate();
    }
    public void setSubject(RectF normalized,String name){
        subjectRect=normalized==null?null:new RectF(normalized);
        subjectName=name==null?"":name;invalidate();
    }
    public void setHeatmap(boolean b){heatmap=b;invalidate();}
    public void setThirds(boolean b){thirds=b;invalidate();}
    public void setHorizon(boolean b){horizon=b;invalidate();}
    public void setCrop(boolean b){crop=b;invalidate();}
    public void setSubjectVisible(boolean b){subject=b;invalidate();}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        VisualInspector.Report r=report;
        if(r==null)return;
        float w=getWidth(),h=getHeight();
        float scale=Math.min(w/imageW,h/imageH);
        float iw=imageW*scale,ih=imageH*scale;
        float x0=(w-iw)/2,y0=(h-ih)/2;
        if(heatmap) {
            paint.setStyle(Paint.Style.FILL);
            for(int y=0;y<VisualInspector.ROWS;y++)
                for(int x=0;x<VisualInspector.COLS;x++){
                    float v=r.attention[y*VisualInspector.COLS+x];
                    if(v<.20f)continue;
                    paint.setColor(Color.argb(Math.min(130,(int)(v*125)),
                        255,(int)(215-160*v),40));
                    canvas.drawRect(x0+iw*x/VisualInspector.COLS,
                        y0+ih*y/VisualInspector.ROWS,
                        x0+iw*(x+1)/VisualInspector.COLS,
                        y0+ih*(y+1)/VisualInspector.ROWS,paint);
                }
        }
        paint.setStyle(Paint.Style.STROKE);
        if(thirds){
            paint.setColor(Color.argb(185,67,235,222));paint.setStrokeWidth(1.6f);
            for(int i=1;i<=2;i++){
                canvas.drawLine(x0+iw*i/3,y0,x0+iw*i/3,y0+ih,paint);
                canvas.drawLine(x0,y0+ih*i/3,x0+iw,y0+ih*i/3,paint);
            }
        }
        if(crop){
            paint.setColor(Color.argb(215,255,215,110));paint.setStrokeWidth(3.1f);
            RectF rect=r.crop;
            canvas.drawRect(x0+iw*rect.left,y0+ih*rect.top,
                x0+iw*rect.right,y0+ih*rect.bottom,paint);
        }
        if(horizon&&r.candidateHorizontalLine){
            paint.setColor(0xfff18ae3);paint.setStrokeWidth(3.5f);
            float angle=(float)Math.tan(Math.toRadians(r.lineAngleDegrees));
            float yc=y0+ih*.5f;
            canvas.drawLine(x0,yc-angle*iw/2,x0+iw,yc+angle*iw/2,paint);
            paint.setStyle(Paint.Style.FILL);paint.setTextSize(28);
            canvas.drawText(String.format(java.util.Locale.ITALIAN,"LINEA CANDIDATA %.1f°",r.lineAngleDegrees),
                x0+10,Math.min(y0+ih-14,yc-18),paint);
            paint.setStyle(Paint.Style.STROKE);
        }
        paint.setColor(Color.WHITE);paint.setStrokeWidth(2.5f);
        canvas.drawCircle(x0+iw*r.focusX,y0+ih*r.focusY,12,paint);
        canvas.drawCircle(x0+iw*r.focusX,y0+ih*r.focusY,24,paint);
        if(subject && subjectRect!=null){
            paint.setColor(0xff61ffad);paint.setStrokeWidth(3);
            canvas.drawRoundRect(x0+subjectRect.left*iw,y0+subjectRect.top*ih,
                x0+subjectRect.right*iw,y0+subjectRect.bottom*ih,8,8,paint);
            paint.setStyle(Paint.Style.FILL);paint.setTextSize(31);
            canvas.drawText(subjectName,x0+subjectRect.left*iw+7,
                Math.max(y0+28,y0+subjectRect.top*ih-11),paint);
        }
    }
}
