package com.ampyeos.canon;

import android.graphics.Bitmap;
import androidx.camera.core.ImageProxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Deterministic, offline visual-quality heuristics. Not subject recognition or neural AI. */
public final class PhotoAnalyzer {
    public static final class Result {
        public final int score;
        public final int brightness, highlights, shadows, contrast, detail;
        public final String advice;
        public final int[] histogram;
        Result(int s,int b,int h,int sh,int c,int d,String a,int[] histogram) {
            score=s; brightness=b; highlights=h; shadows=sh; contrast=c; detail=d; advice=a; this.histogram=histogram;
        }
        public String summary() {
            return "LUCE " + brightness + "/255  ·  ALTE LUCI " + highlights + "%  ·  OMBRE " + shadows
                + "%  ·  DETTAGLIO " + detail + "  ·  SCORE " + score + "/100";
        }
    }
    private PhotoAnalyzer(){}

    public static Result fromCamera(ImageProxy frame) {
        final ImageProxy.PlaneProxy plane = frame.getPlanes()[0];
        final ByteBuffer b = plane.getBuffer();
        final int row = plane.getRowStride(), pixel = plane.getPixelStride();
        final int w = frame.getWidth(), h = frame.getHeight();
        int[] g = new int[160*120];
        for(int y=0;y<120;y++) {
            int sy=Math.min(h-1,y*h/120);
            for(int x=0;x<160;x++){
                int sx=Math.min(w-1,x*w/160);
                int i=sy*row+sx*pixel;
                g[y*160+x]=(i>=0 && i<b.limit()) ? (b.get(i)&255) : 128;
            }
        }
        return calculate(g,160,120);
    }
    public static Result fromBitmap(Bitmap bitmap) {
        Bitmap small=Bitmap.createScaledBitmap(bitmap,160,120,true);
        int[] rgb=new int[160*120];
        small.getPixels(rgb,0,160,0,0,160,120);
        if(small!=bitmap) small.recycle();
        int[] g=new int[rgb.length];
        for(int i=0;i<g.length;i++) {
            int p=rgb[i];
            g[i]=((p>>16&255)*77+(p>>8&255)*150+(p&255)*29)>>8;
        }
        return calculate(g,160,120);
    }

    // Uses downsampled luminance (not raw sensor data). Thresholds are qualitative.
    static Result calculate(int[] g,int w,int h) {
        long sum=0, sq=0; int low=0,hi=0;
        int[] histogram=new int[32];
        double edgeSum=0, weightedX=0, weightedY=0, weights=0;
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            int i=y*w+x,v=g[i]; sum+=v; sq+=(long)v*v;
            histogram[Math.max(0,Math.min(31,v/8))]++;
            if(v<24) low++; if(v>240) hi++;
            if(x>0 && y>0) {
                int mag=Math.abs(v-g[i-1])+Math.abs(v-g[i-w]);
                edgeSum+=mag;
                if(mag>25) { weightedX+=x*mag;weightedY+=y*mag;weights+=mag; }
            }
        }
        int n=w*h; int avg=(int)(sum/n);
        int contrast=(int)Math.min(255,Math.sqrt(Math.max(0,sq/(double)n-(double)avg*avg))*3.0);
        int shadows=100*low/n, highlights=100*hi/n;
        int detail=(int)Math.min(100,edgeSum/(double)n*1.45);
        double cx=weights>1?weightedX/weights/(w-1):0.5;
        double cy=weights>1?weightedY/weights/(h-1):0.5;
        double third=Math.min(Math.min(Math.abs(cx-1.0/3),Math.abs(cx-2.0/3)),
                              Math.min(Math.abs(cy-1.0/3),Math.abs(cy-2.0/3)));
        int score=90;
        if(avg<62 || avg>210) score-=22;
        if(highlights>8) score-=Math.min(22,highlights);
        if(shadows>30) score-=Math.min(16,shadows/2);
        if(detail<14) score-=18;
        if(contrast<40) score-=8;
        if(third>0.15) score-=5;
        score=Math.max(5,Math.min(99,score));

        List<String> tips=new ArrayList<>();
        if(avg<62) tips.add("Scena scura: cerca più luce o valuta esposizione più lunga/ISO");
        else if(avg>210) tips.add("Scena molto chiara: riduci l'esposizione");
        if(highlights>8) tips.add("Alte luci potenzialmente bruciate: controlla l'istogramma");
        if(shadows>30) tips.add("Molte ombre chiuse: prova luce laterale o compensazione");
        if(detail<14) tips.add("Poco microcontrasto: stabilizza, controlla fuoco e tempo");
        if(contrast<40) tips.add("Contrasto basso: prova luce laterale o uno sfondo più netto");
        if(tips.isEmpty()) {
            if(third>0.15) tips.add("Prova a spostare il soggetto verso un punto dei terzi");
            else tips.add("Buon equilibrio tecnico: cerca il momento e l'espressione migliori");
        }
        return new Result(score,avg,highlights,shadows,contrast,detail,String.join("  •  ",tips),histogram);
    }
}
