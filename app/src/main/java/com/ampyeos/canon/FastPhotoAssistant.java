package com.ampyeos.canon;

import android.graphics.Bitmap;
import java.util.Locale;

/**
 * The responsive offline classroom tier. No GGUF, no image upload, no fake
 * aesthetic votes and no dependency on the slow CPU-only vision encoder.
 *
 * Technical measurements are grounded in the pixels; subject observations
 * are quoted as ML Kit hypotheses, not invented as Qwen detections.
 */
public final class FastPhotoAssistant {
    public static final class Result {
        public final String explanation;
        public final String speech;
        Result(String text,String voice){explanation=text;speech=voice;}
    }
    private FastPhotoAssistant() {}
    public static Result inspect(Bitmap bitmap,String mlKit,
                                 LearnedQuality.Result learned){
        if(bitmap==null)throw new IllegalArgumentException("Apri una fotografia");
        int longer=Math.max(bitmap.getWidth(),bitmap.getHeight());
        float scale=Math.min(1f,640f/Math.max(1,longer));
        Bitmap sample=scale<1f?Bitmap.createScaledBitmap(bitmap,
            Math.max(1,Math.round(bitmap.getWidth()*scale)),
            Math.max(1,Math.round(bitmap.getHeight()*scale)),true):bitmap;
        PhotoReview review;
        VisualInspector.Report visual;
        try{
            review=PhotoReview.inspect(sample);
            visual=VisualInspector.analyze(sample);
        }finally{
            if(sample!=bitmap)sample.recycle();
        }
        String subject=mlKit==null||mlKit.trim().isEmpty()?
            "Non ancora identificato dal rilevatore locale":
            mlKit.trim();
        // The local object classifier may return an unreliable generic label.
        // Do not convert such a label into a specific object identity.
        StringBuilder s=new StringBuilder();
        s.append("ANALISI RAPIDA LOCALE · FOTO MAI INVIATA ONLINE\n\n");
        s.append("RICONOSCIMENTO SOGGETTO (ML Kit)\n").append(subject).append('\n');
        s.append("Questo è un risultato del rilevatore, non un'interpretazione "+
            "visiva approfondita. Se la categoria è generica, il soggetto "+
            "rimane non identificato.\n\n");
        if(learned!=null) {
            s.append(String.format(Locale.ITALIAN,
                "MUSIQ · QUALITÀ PERCETTIVA STIMATA %.1f/100\n",learned.score));
            s.append("Modello neurale ONNX; NON misura significato o originalità.\n\n");
        }else{
            s.append("MUSIQ · punteggio neurale non ancora calcolato; "+
                "usa VALUTA QUALITÀ MUSIQ quando il modello è disponibile.\n\n");
        }
        s.append("OSSERVAZIONI TECNICHE MISURATE\n")
         .append(review.why).append("\n\n");
        s.append("COMPOSIZIONE GEOMETRICA, NON ARTISTICA\n")
         .append(visual.summary).append(" ")
         .append(visual.explanation).append("\n\n");
        s.append("INTERVENTI DA PROVARE\n")
         .append(review.how).append(" ")
         .append(visual.improvements).append("\n\n");
        s.append("VOTO ARTISTICO: NON VALUTABILE SENZA COMPRENSIONE DELLA SCENA.\n");
        s.append("Questa modalità non pretende di essere Qwen: offre dati e "+
            "indicazioni verificabili rapidamente sul Pixel. "+
            "Per un'interpretazione semantica puoi scegliere Qwen approfondito, "+
            "ma non è necessario tenerlo in esecuzione per utilizzare l'editor.");
        String spoken="Analisi rapida locale completata. "+
            "Soggetto rilevato: "+subject+". "+
            (learned!=null?
                String.format(Locale.ITALIAN,"Qualità percettiva MUSIQ %.0f su 100. ",learned.score):
                "Qualità MUSIQ non ancora calcolata. ")+
            "Aspetti tecnici: "+review.why+
            ". Possibili modifiche: "+review.how+
            ". Non assegno un voto artistico dai soli pixel.";
        return new Result(s.toString(),spoken);
    }
}
