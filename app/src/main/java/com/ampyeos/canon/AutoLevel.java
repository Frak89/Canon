package com.ampyeos.canon;

import android.graphics.Bitmap;
import android.graphics.RectF;
import java.util.Locale;

/**
 * Conservative single-frame roll estimator. Works only on image pixels.
 *
 * Searches for near-horizontal or near-vertical structural edges and votes
 * their departure from the expected axes. Discards unconvincing structure;
 * diagonal bridges and perspective lines should NOT be forcibly leveled.
 *
 * A high-confidence result is a measured *candidate*, not ground truth.
 */
public final class AutoLevel {
    public static final class Result {
        public final float tiltDegrees,confidence;
        public final int supportingRegions;
        public final boolean applied;
        public final String explanation;
        Result(float angle,float certainty,int regions,boolean apply,String explanation){
            tiltDegrees=angle;confidence=certainty;supportingRegions=regions;
            applied=apply;this.explanation=explanation;
        }
        public String summary(){
            if(!applied)return "Auto Raddrizza: "+explanation;
            return String.format(Locale.ITALIAN,
                "Auto Raddrizza: compensazione %.1f° (confidenza %.0f%%, %d zone). "+
                "Operazione reversibile; controlla che le linee scelte rappresentino "+
                "un vero riferimento orizzontale/verticale.",
                tiltDegrees,confidence*100,supportingRegions);
        }
    }
    private static final int W=256,H=192;
    private static final int BINS=61;
    private static final float STEP=.25f,MAX=7.5f;
    private AutoLevel(){}

    public static Result estimate(Bitmap bitmap){
        if(bitmap==null||bitmap.getWidth()<64||bitmap.getHeight()<64)
            return new Result(0,0,0,false,"immagine troppo piccola");
        Bitmap sample=Bitmap.createScaledBitmap(bitmap,W,H,true);
        int[] pixels=new int[W*H], gray=new int[W*H];
        sample.getPixels(pixels,0,W,0,0,W,H);
        if(sample!=bitmap)sample.recycle();
        for(int i=0;i<gray.length;i++){
            int p=pixels[i];
            gray[i]=((p>>16&255)*77+(p>>8&255)*150+(p&255)*29)>>8;
        }

        double[][] hist=new double[2][BINS];
        double[] axes=new double[2];
        double[][] regions=new double[2][9];
        double[][][] binRegions=new double[2][BINS][9];
        for(int y=3;y<H-3;y+=2)for(int x=3;x<W-3;x+=2){
            // Sobel derivatives reduce sparse noise compared with 2-neighbor gradients.
            int idx=y*W+x;
            int gx=(gray[idx-W+1]-gray[idx-W-1])+
                   2*(gray[idx+1]-gray[idx-1])+
                   (gray[idx+W+1]-gray[idx+W-1]);
            int gy=(gray[idx+W-1]-gray[idx-W-1])+
                   2*(gray[idx+W]-gray[idx-W])+
                   (gray[idx+W+1]-gray[idx-W+1]);
            double magnitude=Math.hypot(gx,gy);
            if(magnitude<100)continue;
            // Line angle: tangent perpendicular to (gx,gy). Reduce modulo 180.
            double degrees=Math.toDegrees(Math.atan2(-gx,gy));
            while(degrees>90)degrees-=180;
            while(degrees<=-90)degrees+=180;
            int axis;
            double error;
            if(Math.abs(degrees)<=MAX) {
                axis=0;error=degrees;
            }else if(Math.abs(Math.abs(degrees)-90)<=MAX){
                axis=1;
                error=degrees>=0?degrees-90:degrees+90;
            }else continue;
            int bin=Math.max(0,Math.min(BINS-1,
                Math.round((float)((error+MAX)/STEP))));
            int region=(Math.min(2,y*3/H))*3+Math.min(2,x*3/W);
            double weight=Math.min(1024,magnitude);
            hist[axis][bin]+=weight;
            axes[axis]+=weight;
            regions[axis][region]+=weight;
            binRegions[axis][bin][region]+=weight;
        }

        double bestEvidence=0;
        float bestAngle=0,bestConfidence=0;
        int bestRegions=0,bestAxis=0;
        for(int axis=0;axis<2;axis++){
            if(axes[axis]<12000)continue;
            for(int bin=3;bin<BINS-3;bin++){
                double peak=hist[axis][bin-2]+hist[axis][bin-1]+
                    hist[axis][bin]+hist[axis][bin+1]+hist[axis][bin+2];
                if(peak<=0)continue;
                int supporting=0;
                for(int r=0;r<9;r++){
                    double local=0;
                    for(int k=bin-2;k<=bin+2;k++)local+=binRegions[axis][k][r];
                    if(local>2000 && local>=.012*peak)supporting++;
                }
                double concentration=peak/axes[axis];
                // A single rail/bridge/edge is not enough to establish roll.
                if(supporting<3||concentration<.28)continue;
                double evidence=peak*Math.min(1.,supporting/5.0)*concentration;
                if(evidence>bestEvidence){
                    bestEvidence=evidence;
                    bestAxis=axis;
                    bestRegions=supporting;
                    double sum=0,angle=0;
                    for(int k=bin-2;k<=bin+2;k++){
                        double value=hist[axis][k];
                        sum+=value;angle+=(-MAX+STEP*k)*value;
                    }
                    bestAngle=sum>0?(float)(angle/sum):0;
                    bestConfidence=(float)Math.min(.96,
                        Math.max(0,(concentration-.22)/.48)*
                        Math.min(1,supporting/5.));
                }
            }
        }
        if(bestEvidence==0)
            return new Result(0,0,0,false,
                "non sono presenti abbastanza linee orizzontali o verticali affidabili");
        // Ensure the other orthogonal family, when present, agrees.
        int other=1-bestAxis;
        if(axes[other]>16000){
            double cross=0,wide=0;
            for(int bin=0;bin<BINS;bin++){
                double angle=-MAX+bin*STEP;
                wide+=hist[other][bin];
                if(Math.abs(angle-bestAngle)<=1.5)cross+=hist[other][bin];
            }
            if(wide>0 && cross/wide<.14)bestConfidence*=.62f;
        }
        boolean apply=bestConfidence>=.62f &&
            Math.abs(bestAngle)>=.75f && Math.abs(bestAngle)<=5.0f;
        String why=apply?
            "sono presenti linee quasi ortogonali coerenti in più parti dell'immagine":
            "l'inclinazione stimata non è abbastanza affidabile per modificare la fotografia";
        return new Result(bestAngle,bestConfidence,bestRegions,apply,why);
    }

    /** Use the existing non-destructive small-angle geometry transform. */
    public static Bitmap corrected(Bitmap image,Result result){
        return result.applied?
            AutoOptimizer.straighten(image,result.tiltDegrees,Math.max(.65f,result.confidence)):
            image;
    }
}
