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

    public static Review analyze(Bitmap bitmap,String serverUrl) throws Exception {
        if(bitmap==null)throw new IOException("Nessuna fotografia selezionata");
        URL endpoint=validateServer(serverUrl);
        int maximum=Math.max(bitmap.getWidth(),bitmap.getHeight());
        float ratio=Math.min(1f,1024f/maximum);
        Bitmap scaled=ratio<1f?Bitmap.createScaledBitmap(bitmap,
            Math.max(1,Math.round(bitmap.getWidth()*ratio)),
            Math.max(1,Math.round(bitmap.getHeight()*ratio)),true):bitmap;
        byte[] jpeg;
        try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
            if(!scaled.compress(Bitmap.CompressFormat.JPEG,82,out))
                throw new IOException("Impossibile convertire l'anteprima in JPEG");
            jpeg=out.toByteArray();
        }finally{if(scaled!=bitmap)scaled.recycle();}
        if(jpeg.length>4*1024*1024)throw new IOException("Anteprima troppo grande (>4 MB)");

        JSONObject req=new JSONObject();
        req.put("model","local-model");
        req.put("temperature",0.1);
        req.put("max_tokens",1150);
        JSONArray messages=new JSONArray();
        JSONObject system=new JSONObject();system.put("role","system");
        system.put("content",INSTRUCTION);messages.put(system);
        JSONObject user=new JSONObject();user.put("role","user");
        JSONArray parts=new JSONArray();
        parts.put(new JSONObject().put("type","text").put("text",
            "Valuta questa foto con spirito critico. Se soggetto/intenzione sono incerti, "+
            "non assegnare un voto fotografico. Descrivi dettagli verificabili."));
        parts.put(new JSONObject().put("type","image_url").put("image_url",
            new JSONObject().put("url","data:image/jpeg;base64,"+
                Base64.encodeToString(jpeg,Base64.NO_WRAP))));
        user.put("content",parts);messages.put(user);
        req.put("messages",messages);

        HttpURLConnection connection=(HttpURLConnection)endpoint.openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(120000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type","application/json; charset=utf-8");
        connection.setRequestProperty("Accept","application/json");
        connection.setDoOutput(true);
        try{
            byte[] body=req.toString().getBytes(StandardCharsets.UTF_8);
            if(body.length>6*1024*1024)throw new IOException("Richiesta troppo grande");
            try(OutputStream out=connection.getOutputStream()){out.write(body);}
            int code=connection.getResponseCode();
            if(code!=200)throw new IOException("Server visivo HTTP "+code+
                ". Controlla che llama-server supporti un modello vision e mmproj.");
            ByteArrayOutputStream answer=new ByteArrayOutputStream();
            try(InputStream in=connection.getInputStream()){
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))>=0){
                    if(answer.size()+n>512*1024)
                        throw new IOException("Risposta del modello troppo grande");
                    answer.write(buffer,0,n);
                }
            }
            JSONObject response=new JSONObject(answer.toString(StandardCharsets.UTF_8));
            JSONArray choices=response.getJSONArray("choices");
            if(choices.length()==0)throw new IOException("Risposta AI senza scelte");
            JSONObject message=choices.getJSONObject(0).getJSONObject("message");
            String raw=message.optString("content","").trim();
            int start=raw.indexOf('{'),end=raw.lastIndexOf('}');
            if(start<0||end<start)
                throw new IOException("Il modello non ha restituito una valutazione JSON valida");
            return new Review(new JSONObject(raw.substring(start,end+1)));
        }finally{connection.disconnect();}
    }

    /**
     * Allow only real LAN/loopback IPs; no public cloud, DNS tricks, redirects
     * or userinfo. Sending is additionally gated by user confirmation in UI.
     */
    public static URL validateServer(String raw) throws Exception {
        if(raw==null||raw.trim().isEmpty())
            throw new IOException("Inserisci l'indirizzo del server Qwen-VL locale");
        URL input=new URL(raw.trim());
        String proto=input.getProtocol().toLowerCase(Locale.ROOT);
        if(!proto.equals("http")&&!proto.equals("https"))
            throw new IOException("Protocollo server non supportato");
        if(input.getUserInfo()!=null||input.getRef()!=null||input.getQuery()!=null)
            throw new IOException("Indirizzo del server non valido");
        String host=input.getHost();
        boolean loop=host.equalsIgnoreCase("localhost")||host.equals("127.0.0.1")||
            host.equals("[::1]")||host.equals("::1");
        if(!loop){
            // Reject DNS hostnames entirely; avoid DNS rebinding and public
            // service exposure. Only literal dotted IPv4 private subnets.
            String[] octets=host.split("\\.");
            if(octets.length!=4)throw new IOException("Indica un IP locale, es. 192.168.1.10");
            int[] ip=new int[4];
            for(int i=0;i<4;i++){
                if(octets[i].isEmpty()||octets[i].length()>3)
                    throw new IOException("IPv4 non valido");
                ip[i]=Integer.parseInt(octets[i]);
                if(ip[i]<0||ip[i]>255)throw new IOException("IPv4 non valido");
            }
            if(!(ip[0]==10 || (ip[0]==172&&ip[1]>=16&&ip[1]<=31) ||
                (ip[0]==192&&ip[1]==168)))
                throw new IOException("Per la privacy è consentito soltanto un server locale");
        }
        int port=input.getPort();
        if(port<1||port>65535)throw new IOException("Indica la porta del server, es. :8080");
        String path=input.getPath();
        if(!path.isEmpty()&&!path.equals("/"))
            throw new IOException("Indica soltanto IP e porta, senza percorsi");
        return new URL(proto+"://"+host+":"+port+"/v1/chat/completions");
    }
}
