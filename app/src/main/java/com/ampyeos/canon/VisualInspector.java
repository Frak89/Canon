package com.ampyeos.canon;

import android.graphics.Bitmap;
import android.graphics.RectF;
import java.util.Locale;

/**
 * Explains measurements of JPEG pixels, not human perception or composition quality.
 * Face/object descriptions are independently supplied by ML Kit.
 */
public final class VisualInspector {
    public static final int COLS=8, ROWS=6;
    public static final class Report {
        public final PhotoAnalyzer.Result technical;
        public final float[] attention; // normalized gradient energy in 8x6 tiles
        public final float focusX,focusY;
        public final int balanceScore, thirdsScore, compositionScore, tonalScore;
        public final float lineAngleDegrees,lineConfidence;
        public final boolean candidateHorizontalLine;
        public final RectF crop;
        public final String summary, explanation, improvements;
        Report(PhotoAnalyzer.Result technical,float[] attention,float focusX,float focusY,
               int balanceScore,int thirdsScore,int compositionScore,int tonalScore,
               float angle,float confidence,boolean horizontal,RectF crop,
               String summary,String explanation,String improvements){
            this.technical=technical;this.attention=attention;this.focusX=focusX;this.focusY=focusY;
            this.balanceScore=balanceScore;this.thirdsScore=thirdsScore;
            this.compositionScore=compositionScore;this.tonalScore=tonalScore;
            this.lineAngleDegrees=angle;this.lineConfidence=confidence;
            candidateHorizontalLine=horizontal;this.crop=crop;
            this.summary=summary;this.explanation=explanation;this.improvements=improvements;
        }
        public String fullReport() {
            String line=candidateHorizontalLine?
                String.format(Locale.ITALIAN,"Possibile linea quasi orizzontale: %.1f° (confidenza %.0f%%). Non necessariamente l'orizzonte.",
                    lineAngleDegrees,100*lineConfidence):
                "Nessuna linea quasi orizzontale affidabile rilevata.";
            return "ESPOSIZIONE / TONI\n"+technical.summary()+
                "\nIndice diagnostico luce/contorni: "+tonalScore+"/100"+
                "\n\nCOMPOSIZIONE MISURABILE\nDistribuzione di dettaglio: "+balanceScore+
                "/100 · Vicinanza ai terzi: "+thirdsScore+
                "/100 · Indicatore geometrico NON artistico: "+compositionScore+"/100"+
                "\nCentro di dettaglio: "+Math.round(100*focusX)+"% larghezza, "+
                Math.round(100*focusY)+"% altezza.\n"+line+
                "\n\nCHE COSA MOSTRO\n"+summary+"\n"+explanation+
                "\n\nCOME MIGLIORARE\n"+improvements+
                "\n\nATTENZIONE: una mappa di dettagli non equivale a una mappa di sguardi, "+
                "né riconosce automaticamente un soggetto. Le fotografie possono "+
                "essere espressive anche senza rispettare la regola dei terzi.";
        }
    }
    private VisualInspector(){}
    private static int gray(int p){return ((p>>16&255)*77+(p>>8&255)*150+(p&255)*29)>>8;}
    public static Report analyze(Bitmap bitmap){
        final int width=160,height=120;
        Bitmap small=Bitmap.createScaledBitmap(bitmap,width,height,true);
        int[] raw=new int[width*height],g=new int[width*height];
        small.getPixels(raw,0,width,0,0,width,height);
        if(small!=bitmap)small.recycle();
        for(int i=0;i<g.length;i++)g[i]=gray(raw[i]);

        float[] cells=new float[COLS*ROWS];
        double l=0,r=0,top=0,bottom=0,cx=0,cy=0,sum=0,max=0;
        for(int y=2;y<height-2;y++)for(int x=2;x<width-2;x++){
            int i=y*width+x;
            int gx=Math.abs(g[i+1]-g[i-1]),gy=Math.abs(g[i+width]-g[i-width]);
            int weight=gx+gy;
            if(weight<17)continue;
            int tx=Math.min(COLS-1,x*COLS/width),ty=Math.min(ROWS-1,y*ROWS/height);
            cells[ty*COLS+tx]+=weight;
            sum+=weight;cx+=x*weight;cy+=y*weight;
            if(x<width/2)l+=weight;else r+=weight;
            if(y<height/2)top+=weight;else bottom+=weight;
        }
        for(float c:cells)max=Math.max(max,c);
        if(max>0)for(int i=0;i<cells.length;i++)cells[i]/=max;
        float fx=sum>0?(float)(cx/sum/(width-1)):.5f;
        float fy=sum>0?(float)(cy/sum/(height-1)):.5f;

        // 100 is balanced detail distribution; intentional visual imbalance can still be artistic.
        int balance=(int)Math.round(100*(1-Math.abs(l-r)/Math.max(1,l+r)));
        double d=Double.MAX_VALUE;
        for(double xx:new double[]{1d/3,2d/3})
            for(double yy:new double[]{1d/3,2d/3})
                d=Math.min(d,Math.hypot(fx-xx,fy-yy));
        int thirds=(int)Math.round(100*Math.max(0,1-d/.40));
        int composed=(int)Math.round(.60*balance+.40*thirds);

        // Candidate horizontal structure: weighted vertical gradient integrated
        // along lines with angles -8..+8deg. Not a geometric perspective solver.
        double strongest=0,totalBaseline=0;
        int baselineSamples=0;
        for(int y=20;y<height-20;y+=4)for(int x=3;x<width-3;x+=3){
            totalBaseline+=Math.abs(g[(y+1)*width+x]-g[(y-1)*width+x]);
            baselineSamples++;
        }
        double mean=totalBaseline/Math.max(1,baselineSamples);
        float bestAngle=0;
        for(int ai=-16;ai<=16;ai++){
            float angle=ai*.5f;
            double slope=Math.tan(Math.toRadians(angle)),candidateBest=0;
            for(int y0=height/4;y0<height*3/4;y0+=3){
                double energy=0;int n=0;
                for(int x=4;x<width-4;x+=2) {
                    int y=(int)Math.round(y0+slope*(x-width/2));
                    if(y<2||y>=height-2)continue;
                    int i=y*width+x;
                    energy+=Math.abs(g[i+width]-g[i-width]);
                    n++;
                }
                if(n>0)candidateBest=Math.max(candidateBest,energy/n);
            }
            if(candidateBest>strongest){strongest=candidateBest;bestAngle=angle;}
        }
        float confidence=(float)Math.min(1,Math.max(0,(strongest/(mean+1)-1.25)/2.5));
        boolean line=confidence>=.50f && strongest>12;
        PhotoAnalyzer.Result technical=PhotoAnalyzer.fromBitmap(bitmap);
        int tonal=(int)Math.max(0,Math.min(100,technical.score));
        String detail=balance<55?
            "Le zone ricche di contorni sono concentrate prevalentemente su un lato.":
            "I dettagli visivi risultano distribuiti tra i due lati.";
        String guide=thirds>76?
            "Il centro dei dettagli cade vicino a un punto dei terzi.":
            "Il centro dei dettagli è distante dai punti dei terzi; valuta se cercavi simmetria.";
        String summary=detail+" "+guide;
        String explanation="La heatmap evidenzia variazioni locali di luminosità: "+
            "non indica dove una persona guarderà con certezza. "+
            (technical.highlights>8?"Le zone molto chiare possono perdere dettaglio. ":"")+
            (technical.shadows>30?"Molte aree sono quasi nere. ":"")+
            (line?"È presente una possibile linea strutturale inclinata. ":"");
        StringBuilder improve=new StringBuilder();
        if(technical.highlights>8)improve.append("Proteggi le zone chiare in fase di ripresa; ");
        if(technical.shadows>30)improve.append("valuta luce di riempimento o esposizione mirata; ");
        if(balance<55)improve.append("prova un ritaglio meno sbilanciato, ma senza eliminare contesto utile; ");
        if(thirds<65)improve.append("confronta una composizione sui terzi con una simmetrica; ");
        if(line&&Math.abs(bestAngle)>1.2)improve.append("verifica visivamente la possibile linea prima di raddrizzare; ");
        if(improve.length()==0)improve.append("prova a cambiare altezza di ripresa, luce e momento dello scatto.");
        RectF crop=PhotoReview.autoCrop(bitmap);
        return new Report(technical,cells,fx,fy,balance,thirds,composed,tonal,
            bestAngle,confidence,line,crop,summary,explanation,improve.toString());
    }
}
