package com.ampyeos.canon;

import android.graphics.Bitmap;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * An ACTUAL vision-language assessment through a locally running llama.cpp
 * multimodal server. Never uploads the image to a remote host. Only 127.0.0.1
 * loopback is accepted (the server must run on the same Android device).
 *
 * This is NOT a bundled inference runtime. Requires user-installed Qwen3-VL
 * multimodal model + matching mmproj and a running llama-server (e.g. Termux).
 */
public final class LocalVisionReviewer {
    public static final int DEFAULT_PORT=8080;
    public static final class Result {
        public final boolean meaningful,scoreAvailable;
        public final int score;
        public final String subject,scene,composition,light,color,strengths,
            problems,changes,reshoot,why,uncertainty,model;
        Result(boolean meaningful,int score,String subject,String scene,String composition,
               String light,String color,String strengths,String problems,
               String changes,String reshoot,String why,String uncertainty,String model) {
            this.meaningful=meaningful;
            this.scoreAvailable=meaningful && score>=1 && score<=10;
            this.score=this.scoreAvailable?score:0;
            this.subject=subject;this.scene=scene;this.composition=composition;
            this.light=light;this.color=color;this.strengths=strengths;
            this.problems=problems;this.changes=changes;this.reshoot=reshoot;
            this.why=why;this.uncertainty=uncertainty;this.model=model;
        }
        public String scoreLabel(){return scoreAvailable?score+"/10 · giudizio IA visiva":"Non valutabile · soggetto/scena incerti";}
        public String narrative() {
            return "QWEN-VL / VALUTAZIONE SEMANTICA\n"+
                "SCENA: "+scene+"\nSOGGETTO: "+subject+
                "\nCOMPOSIZIONE: "+composition+
                "\nLUCE: "+light+"\nCOLORE: "+color+
                "\nPUNTI FORTI: "+strengths+
                "\nPROBLEMI: "+problems+
                "\nINTERVENTI PROPOSTI: "+changes+
                "\nCOME RIFARE LO SCATTO: "+reshoot+
                "\n\nVERDETTO: "+scoreLabel()+". "+why+
                "\nDUBBI / LIMITI: "+uncertainty+
                "\n\nQuesto è il giudizio interpretativo di "+model+
                ", non una valutazione artistica oggettiva.";
        }
    }

    private static final String PROMPT =
        "Sei un docente di fotografia molto rigoroso. Guarda ATTENTAMENTE la foto allegata, "+
        "non solo luminosita e nitidezza. Non inventare soggetti, sentimenti o intenzioni. "+
        "Descrivi precisamente cio che e visibile: materiali, oggetti, persone, geometrie, "+
        "posizioni. Se vedi una foto casuale senza un soggetto o una composizione significativa, "+
        "dillo e NON darle 8/10 o 9/10 solo perche la luce e corretta. "+
        "Una foto minimalista o astratta puo comunque avere significato: valuta solo quanto vedi. "+
        "La qualita tecnica non equivale a valore artistico. "+
        "Restituisci ESCLUSIVAMENTE un oggetto JSON in italiano, senza markdown. "+
        "Chiavi ESATTE: meaningful (boolean: la scena ha elementi sufficienti per un giudizio?), "+
        "subject (string: che cosa riconosci, o 'incerto'), "+
        "scene (string: descrizione visibile precisa), "+
        "composition (string: distribuzione, linee guida, soggetto, spazio negativo, equilibrio), "+
        "light (string), color (string), strengths (string), problems (string), "+
        "changes (string: modifiche realmente possibili), "+
        "reshoot (string: istruzioni concrete su ripresa, prospettiva, luce), "+
        "why (string: perche assegni quel voto o perche non puoi valutarla), "+
        "uncertainty (string: cosa NON puoi verificare visivamente), "+
        "score (numero intero tra 0 e 10: 0 se meaningful=false; 9-10 SOLO con "+
        "fortissima e specifica motivazione di soggetto, narrativa, luce, composizione; "+
        "non premiare immagini senza messaggio). "+
        "Sii critico, specifico, evita frasi generiche. Dai esempi di elementi DAVVERO visibili.";

    private LocalVisionReviewer(){}

    /** Only an on-phone loopback server. Never send photos off-device. */
    @Deprecated
    public static Result inspect(Bitmap original,int port) throws Exception {
        // Privacy lock: on-device JNI is now the only approved vision path.
        throw new IOException("Analisi HTTP disabilitata. Usa Qwen3-VL nativo sul Pixel.");
    }

    /** Prompt is consumed directly by the JNI engine, without any HTTP request. */
    public static String offlinePrompt() {
        return PROMPT;
    }

    /**
     * Short output budget for Pixel 7 Pro CPU. Still requires actual visual
     * observations; never substitutes histogram metrics for semantics.
     */
    public static String fastPrompt() {
        return "Analizza SOLO ciò che VEDI nella fotografia. Non inventare. "+
            "Rispondi ESCLUSIVAMENTE con un oggetto JSON senza markdown, in italiano. "+
            "Chiavi: meaningful (boolean), subject, scene, composition, light, color, "+
            "strengths, problems, changes, reshoot, why, uncertainty (tutte stringhe), "+
            "score (intero da 0 a 10; 0 quando non puoi valutare). "+
            "Descrivi soggetto e sua posizione, 2 problemi visibili e 2 correzioni "+
            "pratiche. Massimo una frase breve per campo. "+
            "Mai assegnare 9 o 10 per sola nitidezza o luminosità. "+
            "Non penalizzare minimalismo o prospettive intenzionali. "+
            "Se non comprendi la scena, scrivi meaningful=false e score=0.";
    }

    /**
     * Parse only a response generated by a REAL vision-language model.
     * A pixel histogram or image classifier cannot invoke this method.
     * A missing/meaningless visual description never earns a photo score.
     */
    public static Result parseOffline(String raw,String modelName) throws Exception {
        if(raw==null)throw new IOException("Qwen non ha prodotto una spiegazione");
        int start=raw.indexOf('{'),end=raw.lastIndexOf('}');
        if(start<0||end<=start)
            throw new IOException("Qwen non ha restituito la valutazione JSON richiesta. "+ 
                "Riprova con una foto meno grande.");
        JSONObject json=new JSONObject(raw.substring(start,end+1));
        String subject=required(json,"subject");
        String scene=required(json,"scene");
        String composition=required(json,"composition");
        String why=required(json,"why");
        if(subject.length()<3||scene.length()<12||composition.length()<12||why.length()<15)
            throw new IOException("Analisi visuale troppo generica: non assegno voti");
        boolean meaningful=json.optBoolean("meaningful",false);
        int score=meaningful?json.optInt("score",0):0;
        // Deliberately don't award 9/10 because the scene is merely exposed.
        // The photographic judgment is strictly tied to explicit visible evidence.
        if(meaningful && score>=9 &&
            (composition.length()<65 || why.length()<110 ||
             optional(json,"strengths").length()<60)) {
            score=0;
            meaningful=false;
        }
        if(score<1||score>10){score=0;meaningful=false;}
        return new Result(meaningful,score,subject,scene,composition,
            optional(json,"light"),optional(json,"color"),optional(json,"strengths"),
            optional(json,"problems"),optional(json,"changes"),
            optional(json,"reshoot"),why,optional(json,"uncertainty"),
            modelName);
    }

    private static String required(JSONObject json,String field)throws IOException {
        String value=json.optString(field,"").trim();
        if(value.isEmpty())throw new IOException("Risposta IA incompleta: manca "+field);
        return value;
    }
    private static String optional(JSONObject json,String field) {
        String s=json.optString(field,"").trim();
        return s.isEmpty()?"Non analizzato":s;
    }
    private static JSONObject get(URL address)throws Exception {
        HttpURLConnection c=(HttpURLConnection)address.openConnection();
        c.setConnectTimeout(3500);c.setReadTimeout(6500);
        c.setInstanceFollowRedirects(false);
        try{return readJson(c,64*1024);}finally{c.disconnect();}
    }
    private static JSONObject post(URL address,JSONObject request)throws Exception {
        HttpURLConnection c=(HttpURLConnection)address.openConnection();
        c.setConnectTimeout(4500);c.setReadTimeout(180_000);
        c.setInstanceFollowRedirects(false);c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type","application/json");
        try(OutputStream out=c.getOutputStream()) {
            out.write(request.toString().getBytes(StandardCharsets.UTF_8));
        }
        try{return readJson(c,512*1024);}finally{c.disconnect();}
    }
    private static JSONObject readJson(HttpURLConnection c,int limit)throws Exception {
        int status=c.getResponseCode();
        if(status<200||status>299)
            throw new IOException("Qwen-VL server HTTP "+status+
                ". Verifica che il modello sia multimodale e mmproj sia caricato.");
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int n,bytes=0;
            while((n=in.read(buffer))!=-1){
                bytes+=n;if(bytes>limit)throw new IOException("Risposta AI oltre limite");
                out.write(buffer,0,n);
            }
            return new JSONObject(out.toString("UTF-8"));
        }
    }
}
