package com.ampyeos.canon;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Canon CCAPI transport, with discovery of the camera's advertised endpoint versions.
 * No firmware writes, SD format or permanent camera changes. Mutating calls are
 * limited to user-requested shutter, movie and live-view start/stop.
 */
public final class CanonCcapi {
    private volatile String origin;
    private final Map<String,String> endpointPaths=new HashMap<>();
    private final Map<String,String> endpointMethods=new HashMap<>();
    private volatile boolean liveStarted;
    private volatile boolean recording;
    public volatile String info="Canon EOS";
    private volatile long lastLatency=-1;
    private volatile long lastContact=0;

    public boolean connected(){return origin!=null;}
    public long latency(){return lastLatency;}
    public long lastContact(){return lastContact;}
    public boolean recording(){return recording;}
    public String endpoint(){return origin==null?"":origin;}
    public String capabilities() {
        synchronized(endpointPaths){return "API pubblicate: "+endpointPaths.size()+" · "+String.join(", ",endpointPaths.keySet());}
    }

    private static String hostUrl(String host) throws IOException {
        String text=host.trim();
        if(!text.startsWith("http://")&&!text.startsWith("https://")) text="http://"+text;
        URL url=new URL(text);
        if(!url.getProtocol().equals("http")&&!url.getProtocol().equals("https"))throw new IOException("Richiesto HTTP(S)");
        if(url.getHost().isEmpty() || !url.getPath().isEmpty() && !url.getPath().equals("/"))
            throw new IOException("Indirizzo deve essere IP:porta (senza percorsi)");
        if(url.getUserInfo()!=null||url.getQuery()!=null||url.getRef()!=null)
            throw new IOException("Indirizzo non valido");
        return url.getProtocol()+"://"+url.getAuthority();
    }

    public void connect(String host)throws Exception{
        String target=hostUrl(host);
        byte[] bytes=exchange("GET",target+"/ccapi",null,256*1024);
        JSONObject root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        Map<String,String> paths=new HashMap<>(),methods=new HashMap<>();
        for(Iterator<String> it=root.keys();it.hasNext();) {
            String ver=it.next();JSONArray a=root.optJSONArray(ver);
            if(a==null)continue;
            for(int i=0;i<a.length();i++) {
                JSONObject item=a.optJSONObject(i);
                if(item==null)continue;
                String path=item.optString("path","");
                if(!path.startsWith("/ccapi/"))continue;
                int slash=path.indexOf('/',7);
                if(slash<0)continue;
                String suffix=path.substring(slash);
                // Prefer higher version if the same suffix is advertised more than once.
                if(!paths.containsKey(suffix)||path.compareTo(paths.get(suffix))>0) {
                    paths.put(suffix,path);
                    methods.put(suffix,item.toString().toLowerCase());
                }
            }
        }
        String dev=paths.get("/deviceinformation");
        if(dev==null)throw new IOException("La fotocamera non pubblica /deviceinformation nella tabella CCAPI");
        byte[] infoRaw=exchange("GET",target+dev,null,128*1024);
        JSONObject di=new JSONObject(new String(infoRaw,StandardCharsets.UTF_8));
        synchronized(endpointPaths) {
            endpointPaths.clear();endpointPaths.putAll(paths);
            endpointMethods.clear();endpointMethods.putAll(methods);
        }
        info=di.optString("productname",di.optString("model","Canon EOS"));
        origin=target;
        liveStarted=false;recording=false;
    }
    public boolean has(String path) {
        synchronized(endpointPaths){return endpointPaths.containsKey(path);}
    }
    private String uri(String suffix)throws IOException {
        String path;
        synchronized(endpointPaths){path=endpointPaths.get(suffix);}
        if(!connected())throw new IOException("Canon non connessa");
        if(path==null)throw new IOException("Funzione CCAPI non pubblicata: "+suffix);
        return origin+path;
    }
    public JSONObject status(String suffix) throws Exception {
        return new JSONObject(new String(exchange("GET",uri(suffix),null,256*1024),StandardCharsets.UTF_8));
    }
    public String diagnostics() {
        StringBuilder s=new StringBuilder(info+" · "+origin+"\n");
        try{s.append("Batteria: ").append(status("/devicestatus/battery")).append("\n");}
        catch(Exception e){s.append("Batteria: non disponibile (").append(e.getMessage()).append(")\n");}
        try{s.append("Obiettivo: ").append(status("/devicestatus/lens")).append("\n");}
        catch(Exception e){s.append("Obiettivo: non disponibile\n");}
        try{s.append("Parametri: ").append(status("/shooting/settings")).append("\n");}
        catch(Exception e){s.append("Parametri: non disponibili (").append(e.getMessage()).append(")\n");}
        s.append("Ultima risposta: ").append(lastLatency).append(" ms\n");
        s.append(capabilities());
        return s.toString();
    }
    public void startLive()throws Exception {
        if(liveStarted)return;
        String url=uri("/shooting/liveview");
        try {
            exchange("POST",url,"{\"cameradisplay\":\"off\",\"liveviewsize\":\"small\"}",64*1024);
        }catch(IOException first) {
            if(!first.getMessage().contains("HTTP 400"))throw first;
            exchange("POST",url,"{\"cameradisplay\":\"off\"}",64*1024);
        }
        liveStarted=true;
    }
    public Bitmap frame()throws Exception{
        if(!liveStarted)throw new IOException("Live View non avviato");
        String suffix=has("/shooting/liveview/flipdetail")?"/shooting/liveview/flipdetail":"/shooting/liveview/flip";
        byte[] b=exchange("GET",uri(suffix)+"?kind=image",null,5*1024*1024);
        BitmapFactory.Options o=new BitmapFactory.Options();
        o.inPreferredConfig=Bitmap.Config.RGB_565;
        o.inSampleSize=1;
        Bitmap result=BitmapFactory.decodeByteArray(b,0,b.length,o);
        if(result==null)throw new IOException("Anteprima non JPEG; stream multipart/RTP non disponibile in questa versione");
        return result;
    }
    public void shoot()throws Exception{
        exchange("POST",uri("/shooting/control/shutterbutton"),"{\"af\":true}",96*1024);
    }
    public void toggleRecord()throws Exception{
        if(!has("/shooting/control/recbutton"))throw new IOException("REC non pubblicato dalla Canon");
        // Camera CCAPI accepts the command model's status token on supported bodies.
        // Explicit user action only. Never auto-start a recording.
        exchange("POST",uri("/shooting/control/recbutton"),
            recording?"{\"action\":\"stop\"}":"{\"action\":\"start\"}",96*1024);
        recording=!recording;
    }
    public void stopLive(){
        if(liveStarted&&connected()) {
            try{exchange("POST",uri("/shooting/liveview"),"{\"cameradisplay\":\"on\",\"liveviewsize\":\"off\"}",96*1024);}
            catch(Exception e){
                try{exchange("DELETE",uri("/shooting/liveview"),null,96*1024);}
                catch(Exception ignored){}
            }
        }
        liveStarted=false;
    }
    public void disconnect(){stopLive();origin=null;}

    private byte[] exchange(String method,String address,String json,int limit)throws IOException{
        long start=SystemClock.elapsedRealtime();
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(4000);c.setReadTimeout(4500);
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod(method);
        c.setRequestProperty("Accept","application/json,image/jpeg,*/*");
        try {
            if(json!=null){
                c.setRequestProperty("Content-Type","application/json");
                c.setDoOutput(true);
                try(OutputStream out=c.getOutputStream()){out.write(json.getBytes(StandardCharsets.UTF_8));}
            }
            int status=c.getResponseCode();
            if(status<200||status>=300)
                throw new IOException("HTTP "+status+" su "+new URL(address).getPath());
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                if(in==null)return new byte[0];
                byte[] buffer=new byte[8192];int n;int total=0;
                while((n=in.read(buffer))!=-1){
                    total+=n;
                    if(total>limit)throw new IOException("Dati oltre limite di sicurezza: "+limit+" byte");
                    out.write(buffer,0,n);
                }
                lastLatency=SystemClock.elapsedRealtime()-start;
                lastContact=System.currentTimeMillis();
                return out.toByteArray();
            }
        }finally{c.disconnect();}
    }
}
