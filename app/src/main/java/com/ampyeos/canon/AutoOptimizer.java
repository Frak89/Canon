package com.ampyeos.canon;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

/** Pixel-side non-destructive geometric and tone utilities. No RAW reconstruction. */
public final class AutoOptimizer {
    public enum ExportPreset {
        ORIGINAL("Formato originale",0,0),
        WEB("Web 2048 lato lungo",2048,0),
        SQUARE("Social 1:1 · 1080×1080",1080,1080),
        PORTRAIT("Social 4:5 · 1080×1350",1080,1350),
        STORY("Storia 9:16 · 1080×1920",1080,1920),
        WIDE("Cinema 16:9 · 1920×1080",1920,1080);
        public final String label;
        public final int w,h;
        ExportPreset(String label,int w,int h){this.label=label;this.w=w;this.h=h;}
        public static String[] labels(){
            ExportPreset[] all=values();String[] labels=new String[all.length];
            for(int i=0;i<all.length;i++)labels[i]=all[i].label;
            return labels;
        }
    }
    private AutoOptimizer(){}
    /** Rotate without black corners; a small additional crop is unavoidable. */
    public static Bitmap straighten(Bitmap src,float detectedAngle, float confidence) {
        if(confidence<.65f || Math.abs(detectedAngle)<.75f || Math.abs(detectedAngle)>6.1f)
            return src;
        return rotateAndCrop(src,detectedAngle);
    }

    /**
     * User-adjustable roll compensation. Positive tilt means an image line
     * slopes downward to the right and needs counter-rotation.
     * Returns a distinct Bitmap only when an adjustment is requested.
     */
    public static Bitmap rotateAndCrop(Bitmap src,float tiltDegrees){
        if(src==null||Math.abs(tiltDegrees)<.09f)return src;
        if(!Float.isFinite(tiltDegrees)||Math.abs(tiltDegrees)>15f)
            throw new IllegalArgumentException("Correzione consentita solo tra -15° e +15°");
        float a=-tiltDegrees;
        int w=src.getWidth(),h=src.getHeight();
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(out);
        double sine=Math.abs(Math.sin(Math.toRadians(a)));
        double factor=1+sine*Math.max(w/(double)h,h/(double)w)+.018;
        canvas.translate(w/2f,h/2f);
        canvas.scale((float)factor,(float)factor);
        canvas.rotate(a);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(src,-w/2f,-h/2f,p);
        return out;
    }

    /** Mild local tone compression by luminance, never a replacement for RAW recovery. */
    public static Bitmap tone(Bitmap input,PhotoAnalyzer.Result r) {
        Bitmap out=Bitmap.createBitmap(input.getWidth(),input.getHeight(),Bitmap.Config.ARGB_8888);
        int w=input.getWidth(),h=input.getHeight();
        int[] row=new int[w];
        float lift=(r.shadows>23||r.brightness<75)?Math.min(19,3+r.shadows*.22f):0;
        float compress=(r.highlights>6||r.brightness>194)?Math.min(17,2+r.highlights*.24f):0;
        for(int y=0;y<h;y++){
            input.getPixels(row,0,w,0,y,w,1);
            for(int x=0;x<w;x++){
                int pix=row[x];
                int red=Color.red(pix),green=Color.green(pix),blue=Color.blue(pix);
                float lum=(.2126f*red+.7152f*green+.0722f*blue)/255f;
                // Smooth weights and no hard threshold contours.
                float dark=(1-lum)*(1-lum);
                float bright=lum*lum;
                float delta=lift*dark-compress*bright;
                row[x]=Color.argb(Color.alpha(pix),clamp(red+delta),
                    clamp(green+delta),clamp(blue+delta));
            }
            out.setPixels(row,0,w,0,y,w,1);
        }
        return out;
    }
    private static int clamp(float v){return Math.max(0,Math.min(255,Math.round(v)));}

    public static Bitmap resize(Bitmap src,ExportPreset preset, boolean fill,float focalX,float focalY) {
        if(preset==ExportPreset.ORIGINAL)return src;
        int tw=preset.w,th=preset.h;
        if(th==0) {
            // Long-edge limit never upscales, independent of orientation.
            int m=Math.max(src.getWidth(),src.getHeight());
            if(m<=tw)return src;
            float factor=tw/(float)m;
            return Bitmap.createScaledBitmap(src,
                Math.max(1,Math.round(src.getWidth()*factor)),
                Math.max(1,Math.round(src.getHeight()*factor)),true);
        }
        Bitmap output=Bitmap.createBitmap(tw,th,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(output);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        Rect source=new Rect(0,0,src.getWidth(),src.getHeight());
        RectF target=new RectF(0,0,tw,th);
        if(fill) {
            float aspect=tw/(float)th;
            float sourceAspect=src.getWidth()/(float)src.getHeight();
            if(sourceAspect>aspect){
                int slice=Math.max(1,Math.round(src.getHeight()*aspect));
                int center=Math.round(src.getWidth()*Math.max(0,Math.min(1,focalX)));
                int l=Math.max(0,Math.min(src.getWidth()-slice,center-slice/2));
                source.set(l,0,l+slice,src.getHeight());
            }else{
                int slice=Math.max(1,Math.round(src.getWidth()/aspect));
                int center=Math.round(src.getHeight()*Math.max(0,Math.min(1,focalY)));
                int t=Math.max(0,Math.min(src.getHeight()-slice,center-slice/2));
                source.set(0,t,src.getWidth(),t+slice);
            }
        } else {
            // FIT pads rather than removing any pixels.
            c.drawColor(Color.BLACK);
            float scale=Math.min(tw/(float)src.getWidth(),th/(float)src.getHeight());
            float w=src.getWidth()*scale,h=src.getHeight()*scale;
            target.set((tw-w)/2,(th-h)/2,(tw+w)/2,(th+h)/2);
        }
        c.drawBitmap(src,source,target,p);
        return output;
    }

    public static String describeResize(Bitmap src,ExportPreset preset,boolean fill) {
        if(preset==ExportPreset.ORIGINAL)return "Nessun ridimensionamento.";
        return "Esportazione "+preset.label+(fill?" con riempimento e crop guidato":
            " con immagine intera e bande se necessarie")+
            ". La risoluzione non aggiunge dettagli non catturati dal sensore.";
    }
}
