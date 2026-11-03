package com.ampyeos.canon;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.List;

/** Explainable technical review; 10/10 is heuristic, NOT a measure of artistic merit. */
public final class PhotoReview {
    public final Bitmap source;
    public final PhotoAnalyzer.Result metrics;
    /** Always 0 until a genuine vision-language scene review is available. */
    public final int score10;
    public String scoreLabel(){return "Non valutata · serve un modello IA visiva";}
    public final String verdict, why, how, comparison;
    public final RectF suggestedCrop;
    public final ColorLooks.Recommendation look;

    private PhotoReview(Bitmap b, PhotoAnalyzer.Result m,int score,String verdict,String why,
                        String how,String comparison,RectF crop,ColorLooks.Recommendation l){
        source=b;metrics=m;score10=score;this.verdict=verdict;this.why=why;
        this.how=how;this.comparison=comparison;suggestedCrop=crop;look=l;
    }
    public static PhotoReview inspect(Bitmap bitmap){
        PhotoAnalyzer.Result m=PhotoAnalyzer.fromBitmap(bitmap);
        ColorLooks.Recommendation look=ColorLooks.recommend(bitmap);
        RectF crop=autoCrop(bitmap);
        // An exposure/edge heuristic cannot assess whether a photograph has meaning.
        int score=0;
        List<String> issues=new ArrayList<>(),fixes=new ArrayList<>();
        if(m.highlights>8) {
            issues.add(m.highlights+"% dei pixel campionati sono molto chiari: possibili dettagli persi");
            fixes.add("abbassa l'esposizione durante lo scatto; un filtro non recupera aree già bruciate");
        }
        if(m.shadows>30) {
            issues.add(m.shadows+"% dei pixel campionati è quasi nero: le ombre possono risultare chiuse");
            fixes.add("usa luce di riempimento oppure aumenta leggermente l'esposizione");
        }
        if(m.brightness<62) {
            issues.add("luminosità media bassa: il soggetto potrebbe non essere leggibile");
            fixes.add("cerca luce sul soggetto, stabilizza e verifica tempi/ISO");
        } else if(m.brightness>210) {
            issues.add("luminosità media molto alta");
            fixes.add("proteggi le alte luci usando esposizione più bassa");
        }
        if(m.detail<14) {
            issues.add("microcontrasto basso: può essere mosso, sfocato oppure una scena volutamente morbida");
            fixes.add("controlla il fuoco, il tempo di scatto e la stabilità");
        }
        if(m.contrast<40) {
            issues.add("separazione tonale debole tra elementi");
            fixes.add("prova luce laterale, uno sfondo più semplice o un look dal contrasto moderato");
        }
        if(issues.isEmpty()){
            issues.add("non sono emersi problemi tecnici evidenti nei campioni analizzati");
            fixes.add("sperimenta con punto di vista, momento e posizione del soggetto");
        }
        String reasons=String.join(". ",issues)+".";
        String actions="Prova a "+String.join("; ",fixes)+".";
        String contrast="Ritaglio consigliato: riduce i bordi meno informativi preservando la zona di dettaglio maggiore. "
            +"Look suggerito: "+look.look.label+" ("+look.reason+"). "
            +"Questa proposta non cambia la scena realmente ripresa, l'illuminazione né la messa a fuoco.";
        String verdict="Giudizio fotografico non disponibile: la sola analisi "+
            "di luce e contorni non riconosce il contenuto della fotografia. "+
            "Per una valutazione semantica avvia QWEN-VL.";
        return new PhotoReview(bitmap,m,score,verdict,reasons,actions,contrast,crop,look);
    }
    /** Gradient-energy saliency, computed on a subsampled image; no semantic subject guarantee. */
    public static RectF autoCrop(Bitmap bitmap) {
        final int w=96,h=72;
        Bitmap sample=Bitmap.createScaledBitmap(bitmap,w,h,true);
        int[] px=new int[w*h];sample.getPixels(px,0,w,0,0,w,h);
        if(sample!=bitmap)sample.recycle();
        double xsum=0,ysum=0,weight=0;
        for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){
            int c=gray(px[y*w+x]),dx=Math.abs(c-gray(px[y*w+x+1]))+
                Math.abs(c-gray(px[y*w+x-1]));
            int dy=Math.abs(c-gray(px[(y+1)*w+x]))+Math.abs(c-gray(px[(y-1)*w+x]));
            double v=dx+dy;
            if(v>22){weight+=v;xsum+=x*v;ysum+=y*v;}
        }
        // Mild crop only; protect from severe loss of context.
        double cx=weight>0?xsum/weight/w:.5;
        double cy=weight>0?ysum/weight/h:.5;
        float width=.82f,height=.82f;
        float x=(float)Math.max(width/2,Math.min(1-width/2,cx));
        float y=(float)Math.max(height/2,Math.min(1-height/2,cy));
        return new RectF(x-width/2,y-height/2,x+width/2,y+height/2);
    }
    private static int gray(int p){return ((p>>16&255)*77+(p>>8&255)*150+(p&255)*29)>>8;}
    public static Bitmap crop(Bitmap src,RectF norm) {
        int w=src.getWidth(),h=src.getHeight();
        int left=Math.max(0,Math.min(w-1,Math.round(norm.left*w)));
        int top=Math.max(0,Math.min(h-1,Math.round(norm.top*h)));
        int right=Math.max(left+1,Math.min(w,Math.round(norm.right*w)));
        int bottom=Math.max(top+1,Math.min(h,Math.round(norm.bottom*h)));
        return Bitmap.createBitmap(src,left,top,right-left,bottom-top);
    }
    public static String compare(PhotoReview initial,PhotoReview edited) {
        int delta=edited.metrics.score-initial.metrics.score;
        return "Indice tecnico (luce/dettaglio) originale "+initial.metrics.score+
            "/100, elaborazione "+edited.metrics.score+"/100 ("+
            (delta>=0?"+":"")+delta+"). "+
            "Questo NON misura soggetto, intenzione, coerenza visiva o bellezza. "+
            "Nessuna elaborazione è automaticamente migliore artisticamente: "+
            "per valutarla serve un'analisi IA visiva di entrambe le immagini.";
    }
}
