package com.ampyeos.canon;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import androidx.camera.core.ImageProxy;
import java.nio.ByteBuffer;

/** Fully local color statistics and editable photographic looks; no external inference. */
public final class ColorLooks {
    public enum Look {
        NATURALE("Naturale"), BW("Bianco e nero"), BW_HARD("B/N contrasto alto"),
        FILM("Pellicola vintage"), CINEMA("Cinematografico"), SOFT("Ritratto morbido"),
        VIVID("Colori intensi"), MATTE("Matte");
        public final String label;
        Look(String s){label=s;}
        public static String[] labels(){Look[] a=values();String[] b=new String[a.length];for(int i=0;i<a.length;i++)b[i]=a[i].label;return b;}
    }
    public static class Recommendation {
        public final Look look;
        public final String reason;
        Recommendation(Look l,String s){look=l;reason=s;}
        public String label(){return look.label+" · "+reason;}
    }
    private ColorLooks(){}

    public static Recommendation recommend(Bitmap bitmap) {
        Bitmap b=Bitmap.createScaledBitmap(bitmap,64,48,true);
        int[] pixels=new int[64*48];b.getPixels(pixels,0,64,0,0,64,48);
        if(b!=bitmap)b.recycle();
        double sat=0,avg=0,var=0,skin=0;int dark=0,bright=0;
        for(int p:pixels){
            int r=Color.red(p),g=Color.green(p),blue=Color.blue(p);
            int mx=Math.max(r,Math.max(g,blue)),mn=Math.min(r,Math.min(g,blue));
            sat+=(mx-mn)/255.0;
            double l=(.2126*r+.7152*g+.0722*blue);
            avg+=l;var+=l*l;
            if(l<40)dark++;if(l>215)bright++;
            if(r>g&&g>blue && r>85 && g>40 && (r-g)>10 && (g-blue)>3)skin++;
        }
        int n=pixels.length;avg/=n;double contrast=Math.sqrt(Math.max(0,var/n-avg*avg));
        sat/=n;skin/=n;
        if(sat<0.14&&contrast>43)
            return new Recommendation(Look.BW_HARD,"forme e ombre già definite: valorizza il contrasto");
        if(sat<0.20&&contrast<44)
            return new Recommendation(Look.BW,"colori deboli: concentra l'attenzione su luce e geometrie");
        if(skin>0.21&&contrast<68)
            return new Recommendation(Look.SOFT,"tonalità calde: prova una resa delicata della pelle");
        if(avg<82&&bright>n*.025)
            return new Recommendation(Look.CINEMA,"scena scura con luci puntiformi: atmosfera cinematografica");
        if(sat>.38)
            return new Recommendation(Look.FILM,"colori già forti: toni più morbidi e vintage");
        if(contrast<38)
            return new Recommendation(Look.VIVID,"poca separazione cromatica: un look intenso può dare presenza");
        return new Recommendation(Look.NATURALE,"colore bilanciato: conserva una resa fedele");
    }
    /** YUV420 sampled statistics for preview; U/V are sampled respecting row/pixel strides. */
    public static Recommendation recommend(ImageProxy frame) {
        try{
            ImageProxy.PlaneProxy[] planes=frame.getPlanes();
            if(planes.length<3)return new Recommendation(Look.NATURALE,"analisi colore non disponibile");
            ByteBuffer y=planes[0].getBuffer(),u=planes[1].getBuffer(),v=planes[2].getBuffer();
            int w=frame.getWidth(),h=frame.getHeight();
            double saturation=0,luma=0,variation=0;int count=0;
            for(int sy=2;sy<h;sy+=Math.max(12,h/24))
                for(int sx=2;sx<w;sx+=Math.max(12,w/32)){
                    int yi=sy*planes[0].getRowStride()+sx*planes[0].getPixelStride();
                    int ux=sx/2,uy=sy/2;
                    int ui=uy*planes[1].getRowStride()+ux*planes[1].getPixelStride();
                    int vi=uy*planes[2].getRowStride()+ux*planes[2].getPixelStride();
                    if(yi>=y.limit()||ui>=u.limit()||vi>=v.limit())continue;
                    int Y=y.get(yi)&255,U=(u.get(ui)&255)-128,V=(v.get(vi)&255)-128;
                    luma+=Y;variation+=Y*Y;
                    saturation+=Math.sqrt(U*U+V*V)/180.0;
                    count++;
                }
            if(count<10)return new Recommendation(Look.NATURALE,"attendo dati colore");
            double s=saturation/count,mean=luma/count,sd=Math.sqrt(Math.max(0,variation/count-mean*mean));
            if(s<.19&&sd>44)return new Recommendation(Look.BW_HARD,"pochi colori e buona struttura tonale");
            if(s<.17)return new Recommendation(Look.BW,"palette poco satura: prova una lettura monocromatica");
            if(mean<88&&sd>38)return new Recommendation(Look.CINEMA,"luci e ombre adatte a toni cinematografici");
            if(s>.45)return new Recommendation(Look.FILM,"colori intensi: prova una resa analogica morbida");
            if(sd<37)return new Recommendation(Look.VIVID,"contrasto contenuto: valuta colori più intensi");
            return new Recommendation(Look.NATURALE,"preserva i colori e la gamma tonale originali");
        }catch(Exception e){return new Recommendation(Look.NATURALE,"valutazione colore non disponibile");}
    }
    private static ColorMatrix matrix(Look look){
        ColorMatrix m=new ColorMatrix();
        float sat=1,contrast=1,brightness=0;
        switch(look){
            case BW: sat=0;contrast=1.12f;break;
            case BW_HARD:sat=0;contrast=1.55f;brightness=-6;break;
            case FILM:sat=.78f;contrast=.91f;brightness=9;break;
            case CINEMA:sat=.80f;contrast=1.18f;brightness=-3;break;
            case SOFT:sat=.85f;contrast=.89f;brightness=8;break;
            case VIVID:sat=1.37f;contrast=1.09f;break;
            case MATTE:sat=.80f;contrast=.79f;brightness=23;break;
            default:break;
        }
        m.setSaturation(sat);
        ColorMatrix change=new ColorMatrix(new float[]{
            contrast,0,0,0,128*(1-contrast)+brightness,
            0,contrast,0,0,128*(1-contrast)+brightness,
            0,0,contrast,0,128*(1-contrast)+brightness,
            0,0,0,1,0
        });
        m.postConcat(change);
        if(look==Look.FILM||look==Look.SOFT||look==Look.CINEMA){
            ColorMatrix tint=new ColorMatrix(new float[]{
                1.04f,0,0,0,look==Look.CINEMA?3:5,
                0,1.00f,0,0,look==Look.CINEMA?3:1,
                0,0,look==Look.CINEMA?1.09f:.94f,0,look==Look.CINEMA?5:-2,
                0,0,0,1,0});
            m.postConcat(tint);
        }
        return m;
    }
    public static Bitmap render(Bitmap source,Look look,float brightness,float contrast,float saturation){
        Bitmap out=Bitmap.createBitmap(source.getWidth(),source.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(out);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        ColorMatrix m=matrix(look);
        ColorMatrix sat=new ColorMatrix();sat.setSaturation(Math.max(0,Math.min(2.5f,saturation)));
        m.postConcat(sat);
        float k=Math.max(.35f,Math.min(2.5f,contrast));
        m.postConcat(new ColorMatrix(new float[]{
            k,0,0,0,128*(1-k)+brightness,
            0,k,0,0,128*(1-k)+brightness,
            0,0,k,0,128*(1-k)+brightness,
            0,0,0,1,0
        }));
        paint.setColorFilter(new ColorMatrixColorFilter(m));
        c.drawBitmap(source,0,0,paint);
        return out;
    }
}
