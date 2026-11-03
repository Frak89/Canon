package com.ampyeos.canon;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import org.json.JSONObject;

/** A single local Qwen-VL comparison, without a second model load. */
public final class VisionComparison {
    public static final class Result {
        public final String changes, strengths, weaknesses, judgment, nextSteps, uncertainty;
        public final String decision;
        Result(JSONObject j) {
            changes=j.optString("cambiamenti","Non descritti");
            strengths=j.optString("vantaggi","Non dimostrati");
            weaknesses=j.optString("svantaggi","Non descritti");
            judgment=j.optString("motivazione","Non determinata");
            nextSteps=j.optString("consigli","Nessun intervento specifico");
            uncertainty=j.optString("incertezze","La preferenza artistica rimane soggettiva.");
            String raw=j.optString("preferenza","non_determinabile");
            decision=raw.equals("originale")?"ORIGINALE":
                raw.equals("proposta")?"PROPOSTA":"NON DETERMINABILE";
        }
        public String asText(){
            return "CONFRONTO FOTOGRAFICO QWEN3-VL · SOLO PIXEL\n"+
                "PREFERENZA MOTIVATA: "+decision+"\n"+
                "DIFFERENZE VISIBILI: "+changes+"\n"+
                "MIGLIORAMENTI: "+strengths+"\n"+
                "PEGGIORAMENTI: "+weaknesses+"\n"+
                "PERCHÉ: "+judgment+"\n"+
                "COME MIGLIORARE ULTERIORMENTE: "+nextSteps+"\n"+
                "INCERTEZZE: "+uncertainty+
                "\n\nNessun voto numerico inventato: una scelta estetica non è un dato oggettivo.";
        }
    }
    private VisionComparison(){}
    public static String prompt(){
        return "Agisci da docente di fotografia. Guarda la foto composita: "+
            "SINISTRA=ORIGINALE, DESTRA=MODIFICATA. Sono due versioni DELLO STESSO SCATTO. "+
            "Confronta soltanto dettagli REALMENTE visibili: ritaglio, soggetto, spazio "+
            "negativo, contrasto, luci, ombre, colore, geometria e distrazioni. "+
            "Non inventare un tema o una composizione che non puoi vedere. "+
            "Spiega anche perché un ritaglio o filtro può PEGGIORARE la fotografia. "+
            "Non attribuire voti numerici. Se le due foto sono equivalenti o "+
            "non è possibile sapere qual è artisticamente migliore, rispondi "+
            "non_determinabile. Restituisci SOLO JSON italiano con le chiavi: "+
            "preferenza (originale, proposta o non_determinabile), cambiamenti, "+
            "vantaggi, svantaggi, motivazione, consigli, incertezze. "+
            "Descrizioni precise, oneste, senza testo fuori dal JSON.";
    }
    public static Result parse(String raw)throws Exception {
        if(raw==null)throw new Exception("Il modello non ha restituito una risposta");
        int start=raw.indexOf('{'),end=raw.lastIndexOf('}');
        if(start<0||end<=start)throw new Exception("Qwen non ha prodotto JSON per il confronto");
        JSONObject json=new JSONObject(raw.substring(start,end+1));
        if(json.optString("cambiamenti","").length()<12 ||
            json.optString("motivazione","").length()<25)
            throw new Exception("Confronto troppo generico: nessun verdetto");
        return new Result(json);
    }
    public static Bitmap arrange(Bitmap original,Bitmap edited){
        if(original==null||edited==null)
            throw new IllegalArgumentException("Servono originale e proposta");
        // No automatic 720p or 1440px shrinking: each side of the comparison
        // retains the working bitmap's detail. The original file stays intact.
        int panelW=Math.max(original.getWidth(),edited.getWidth());
        int panelH=Math.max(original.getHeight(),edited.getHeight());
        final int footer=64;
        long width=(long)panelW*2;
        long height=(long)panelH+footer;
        if(width>8192||height>8192||width*height>32_000_000L)
            throw new IllegalArgumentException(
                "Confronto troppo grande per memoria Android: "+
                panelW+"x"+panelH+" per lato. Nessuna foto viene ridotta di nascosto.");
        int w=(int)width,h=(int)height;
        Bitmap result=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(result);c.drawColor(Color.rgb(10,15,22));
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        place(c,p,original,0,0,panelW,panelH);
        place(c,p,edited,panelW,0,w,panelH);
        p.setColor(0xffb9ffe9);
        p.setTextSize(28);p.setFakeBoldText(true);
        c.drawText("ORIGINALE",16,h-18,p);
        c.drawText("PROPOSTA",panelW+16,h-18,p);
        p.setColor(0xff86beb8);p.setStrokeWidth(3);
        c.drawLine(panelW,0,panelW,h,p);
        return result;
    }
    private static void place(Canvas canvas,Paint paint,Bitmap source,
                              float l,float t,float r,float b){
        if(source==null)return;
        float maxW=r-l,maxH=b-t;
        float scale=Math.min(1f,Math.min(maxW/source.getWidth(),maxH/source.getHeight()));
        float w=source.getWidth()*scale,h=source.getHeight()*scale;
        float left=l+(maxW-w)/2,top=t+(maxH-h)/2;
        paint.setColor(Color.WHITE);
        canvas.drawBitmap(source,null,new RectF(left,top,left+w,top+h),paint);
    }
}
