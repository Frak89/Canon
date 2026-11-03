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
 * Explicit, opt-in visual review with Qwen3-VL (llama.cpp /v1/chat/completions).
 * This class DOES NOT upload photos automatically. Only loopback and RFC1918
 * LAN addresses are permitted, and the Editor shows a confirmation before send.
 *
 * The model's opinions are attributed as uncertain, not factual measurements.
 */
public final class VisionCritic {
    public static final class Review {
        public final String subject,composition,meaning,problems,improvements,limits;
        public final Integer photographicVote,technicalVote,compositionVote;
        public final boolean subjectClear,intentClear;
        public final String evidence;
        Review(JSONObject obj) {
            subject=plain(obj.optString("soggetto","Soggetto non determinato"));
            composition=plain(obj.optString("composizione","Composizione non descritta"));
            meaning=plain(obj.optString("interpretazione","Intenzione non determinata"));
            problems=plain(obj.optString("problemi","Nessun problema specifico verificato"));
            improvements=plain(obj.optString("miglioramenti","Non è possibile raccomandare correzioni certe"));
            limits=plain(obj.optString("incertezze","Giudizio interpretativo del modello"));
            subjectClear=obj.optBoolean("soggetto_chiaro",false);
            intentClear=obj.optBoolean("intenzione_comprensibile",false);
            evidence=plain(obj.optString("prove_visive",""));
            technicalVote=bounded(obj.opt("voto_tecnico"));
            compositionVote=bounded(obj.opt("voto_composizione"));
            Integer preliminary=bounded(obj.opt("voto_fotografico"));
            // No unconditional 9/10 from an aesthetic guess. Evidence and a
            // subject/intent are mandatory for the overall scale to be shown.
            photographicVote=subjectClear&&intentClear&&evidence.length()>=55
                ?preliminary:null;
        }
        private static Integer bounded(Object data) {
            if(!(data instanceof Number))return null;
            double x=((Number)data).doubleValue();
            if(!Double.isFinite(x)||x<0||x>10)return null;
            return (int)Math.round(x);
        }
        private static String plain(String value) {
            return value==null?"":value.trim().replaceAll("[\\x00-\\x08\\x0b\\x0c\\x0e-\\x1f]"," ");
        }
        public String rating() {
            return photographicVote==null?"NON VALUTABILE":
                photographicVote+"/10 · ipotesi critica Qwen-VL, non verdetto oggettivo";
        }
        public String asText() {
            return "VALUTAZIONE FOTOGRAFICA: "+rating()+
                "\nSOGGETTO: "+subject+
                "\nINTENZIONE: "+meaning+
                "\nCOMPOSIZIONE: "+composition+
                "\nPROVE OSSERVABILI: "+evidence+
                "\nQUALITÀ TECNICA: "+label(technicalVote)+
                "\nCOMPOSIZIONE: "+label(compositionVote)+
                "\nPROBLEMI: "+problems+
                "\nCOME MIGLIORARE: "+improvements+
                "\nINCERTEZZE: "+limits+
                "\n\nIl voto complessivo compare soltanto con soggetto e intenzione descritti "+
                "e prove visive sufficienti. La fotografia può essere astratta o intenzionalmente minimale.";
        }
        private static String label(Integer value){
            return value==null?"non valutabile":value+"/10 (stima modello)";
        }
    }
    private VisionCritic(){}

    private static final String INSTRUCTION=String.join("\n",
        "Sei un critico fotografico e insegnante rigoroso. Analizza DAVVERO i pixel dell'immagine.",
        "Distingui il valore artistico da nitidezza, esposizione e contrasto.",
        "Non premiare scene casuali, foto prive di intenzione evidente, soggetti insignificanti",
        "o documentazione puramente funzionale solo perché tecnicamente corretti.",
        "Non imporre la regola dei terzi: simmetria, astrattismo, minimalismo e foto concettuali",
        "possono essere intenzionali e validi. Se non capisci l'intento, esprimilo come incertezza.",
        "Cita elementi visibili specifici, senza inventare oggetti, persone o contesto.",
        "Evita il 9 o 10 salvo elementi eccezionali concreti e verificabili.",
        "Se la scena non è interpretabile come fotografia d'autore, usa null per voto_fotografico.",
        "Non confondere un buon filtro con un'immagine migliore.",
        "Rispondi SOLO con JSON valido, senza markdown. Proprietà obbligatorie:",
        "soggetto (string), soggetto_chiaro (boolean), interpretazione (string),",
        "intenzione_comprensibile (boolean), composizione (string), prove_visive (string),",
        "problemi (string), miglioramenti (string), incertezze (string),",
        "voto_tecnico (number 0..10 oppure null), voto_composizione (number 0..10 oppure null),",
        "voto_fotografico (number 0..10 oppure null).",
        "Tutte le spiegazioni in italiano. Non formulare diagnosi mediche o identità di persone.");

    @Deprecated
    public static Review analyze(Bitmap bitmap,String serverUrl) throws Exception {
        // No network photo transport, including LAN endpoints.
        throw new IOException("La modalità server fotografico è disabilitata. "+
            "AmpyEOS esegue Qwen3-VL soltanto nel processo Android nativo.");
    }
}
