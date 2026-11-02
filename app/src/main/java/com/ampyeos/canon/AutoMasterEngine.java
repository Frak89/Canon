package com.ampyeos.canon;

import android.graphics.Bitmap;
import android.graphics.RectF;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Local, explainable candidate renderer and selector.
 *
 * IMPORTANT: This is an optimization of heuristic image metrics, not a learned
 * aesthetic judgment, image generation model, or assurance of a 10/10 photo.
 * All proposals are derived strictly from the supplied photograph.
 */
public final class AutoMasterEngine {
    public static final class Plan {
        public final String name;
        public final ColorLooks.Look look;
        public final float exposure, contrast, saturation;
        public final boolean crop;
        public final RectF rect;
        public Plan(String name,ColorLooks.Look look,float exposure,float contrast,
                    float saturation,boolean crop,RectF rect){
            this.name=name;this.look=look;this.exposure=exposure;
            this.contrast=contrast;this.saturation=saturation;this.crop=crop;
            this.rect=rect==null?new RectF(0,0,1,1):new RectF(rect);
        }
        public String changes() {
            StringBuilder s=new StringBuilder("Look "+look.label);
            if(crop)s.append("; ritaglio mirato ai dettagli salienti");
            if(Math.abs(exposure)>=2)s.append(exposure>0?"; schiarimento moderato":"; riduzione luminosità");
            if(contrast>1.05f)s.append("; contrasto rinforzato");
            else if(contrast<.95f)s.append("; contrasto ammorbidito");
            if(saturation>1.05f)s.append("; colori intensificati");
            else if(saturation<.95f)s.append("; saturazione contenuta");
            return s.toString();
        }
    }
    public static final class Candidate {
        public final Plan plan;
        public final Bitmap preview;
        public final PhotoAnalyzer.Result metrics;
        public final double quality;
        public final int vote;
        public final String rationale;
        Candidate(Plan p,Bitmap bitmap,PhotoAnalyzer.Result m,double quality,int vote,String why){
            plan=p;preview=bitmap;metrics=m;this.quality=quality;
            this.vote=vote;rationale=why;
        }
    }
    public static final class Session {
        public final PhotoReview originalReview;
        public final List<Candidate> ranked;
        public final Candidate best;
        public final String verdict, nextShot;
        Session(PhotoReview r,List<Candidate> c,Candidate b,String verdict,String next){
            originalReview=r;ranked=Collections.unmodifiableList(c);best=b;
            this.verdict=verdict;nextShot=next;
        }
    }
    private AutoMasterEngine(){}

    /** Resized analysis preview, max 1400px: no cloud, no permanent bitmap write. */
    public static Session analyze(Bitmap original) {
        if(original==null)throw new IllegalArgumentException("Nessuna fotografia");
        Bitmap analysis=resize(original,1400);
        PhotoReview baseline=PhotoReview.inspect(analysis);
        RectF proposed=baseline.suggestedCrop;
        float e=baseline.metrics.brightness<70?12:baseline.metrics.brightness>190?-10:0;
        ColorLooks.Look advised=baseline.look.look;
        ArrayList<Plan> plans=new ArrayList<>();
        plans.add(new Plan("Originale",ColorLooks.Look.NATURALE,0,1,1,false,null));
        plans.add(new Plan("Esposizione bilanciata",ColorLooks.Look.NATURALE,e,1.03f,1,false,null));
        plans.add(new Plan("Composizione guidata",advised,e,1,1,true,proposed));
        plans.add(new Plan("Bianco e nero",ColorLooks.Look.BW,e,1.02f,1,false,null));
        plans.add(new Plan("B/N alto contrasto",ColorLooks.Look.BW_HARD,0,.90f,1,true,proposed));
        plans.add(new Plan("Cinematografico",ColorLooks.Look.CINEMA,e,.97f,1,false,null));
        plans.add(new Plan("Pellicola vintage",ColorLooks.Look.FILM,e,1,1,false,null));
        plans.add(new Plan("Ritratto morbido",ColorLooks.Look.SOFT,e,1,1,false,null));
        plans.add(new Plan("Colori intensi",ColorLooks.Look.VIVID,e,1,1,false,null));

        ArrayList<Candidate> candidates=new ArrayList<>();
        for(Plan p:plans) {
            Bitmap bitmap;
            if(p.name.equals("Originale"))bitmap=analysis;
            else bitmap=render(analysis,p);
            PhotoAnalyzer.Result stats=PhotoAnalyzer.fromBitmap(bitmap);
            double quality=quality(stats,p,baseline.metrics);
            int vote=Math.max(1,Math.min(10,(int)Math.round(quality/10)));
            String why=explain(baseline.metrics,stats,p);
            candidates.add(new Candidate(p,bitmap,stats,quality,vote,why));
        }
        candidates.sort((a,b)->Double.compare(b.quality,a.quality));
        Candidate winning=candidates.get(0);
        Candidate originalCandidate=null;
        for(Candidate c:candidates)if(c.plan.name.equals("Originale")){originalCandidate=c;break;}
        // Require a measurable gain; don't pretend an edit is better when the metric is flat.
        if(originalCandidate!=null && winning.quality<originalCandidate.quality+1.3)
            winning=originalCandidate;
        String result="Originale "+(originalCandidate==null?baseline.score10:originalCandidate.vote)
            +"/10; proposta "+winning.vote+"/10. "+winning.rationale
            +" Valutazioni esclusivamente tecniche ed euristiche: nessuna modifica può inventare dettagli perduti.";
        return new Session(baseline,candidates,winning,result,baseline.how);
    }

    /** Rerender on the underlying bitmap for export; do not upscale missing source detail. */
    public static Bitmap render(Bitmap bitmap,Plan p) {
        Bitmap adjusted=ColorLooks.render(bitmap,p.look,p.exposure,p.contrast,p.saturation);
        if(!p.crop)return adjusted;
        Bitmap cropped=PhotoReview.crop(adjusted,p.rect);
        if(cropped!=adjusted)adjusted.recycle();
        return cropped;
    }
    private static Bitmap resize(Bitmap source,int limit){
        int w=source.getWidth(),h=source.getHeight(),m=Math.max(w,h);
        if(m<=limit)return source;
        float ratio=limit/(float)m;
        return Bitmap.createScaledBitmap(source,Math.max(1,Math.round(w*ratio)),
            Math.max(1,Math.round(h*ratio)),true);
    }
    private static double quality(PhotoAnalyzer.Result r,Plan p,PhotoAnalyzer.Result original) {
        double s=r.score;
        // Avoid gaming the score by suppressing all color or aggressively clipping.
        s-=Math.max(0,r.highlights-original.highlights)*.65;
        s-=Math.max(0,r.shadows-original.shadows)*.35;
        if(r.brightness>195 || r.brightness<55)s-=5;
        if(r.detail<10)s-=6;
        if(p.crop)s-=2.5; // lost coverage costs something
        if(p.look==ColorLooks.Look.BW_HARD)s-=4; // aggressive stylization isn't inherently 'better'
        if(p.look!=ColorLooks.Look.NATURALE)s-=.75;
        if(Math.abs(p.exposure)>10)s-=1;
        return Math.max(1,Math.min(99,s));
    }
    private static String explain(PhotoAnalyzer.Result src,PhotoAnalyzer.Result dst,Plan p){
        ArrayList<String> reasons=new ArrayList<>();
        if(dst.highlights<src.highlights)reasons.add("meno pixel vicini al bianco");
        if(dst.shadows<src.shadows)reasons.add("meno ombre quasi nere");
        if(dst.contrast>src.contrast+8)reasons.add("maggiore separazione tonale");
        if(dst.detail>src.detail+6)reasons.add("microcontrasto percepito più forte");
        if(p.crop)reasons.add("meno elementi periferici, ma anche meno contesto");
        if(reasons.isEmpty())reasons.add("una resa estetica diversa, senza beneficio tecnico netto dimostrato");
        return p.name+": "+p.changes()+". Effetti stimati: "+String.join(", ",reasons)
            +". La composizione migliore dipende dal soggetto e dall'intenzione fotografica.";
    }
}
