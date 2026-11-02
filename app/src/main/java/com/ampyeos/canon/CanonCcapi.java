package com.ampyeos.canon;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Bounded CCAPI client. Never sends firmware operations, deletes media or modifies settings. */
public final class CanonCcapi {
    private String base, version;
    private boolean started=false;
    public String info="";
    public boolean connected(){return base!=null;}
    public String endpoint(){return base==null?"":base+"/ccapi/"+version;}

    public void connect(String host) throws Exception {
        String text=host.trim().replaceAll("/+$","");
        if(!text.startsWith("http://") && !text.startsWith("https://")) text="http://"+text;
        URL u=new URL(text);
        if(!u.getProtocol().equals("http") && !u.getProtocol().equals("https")) throw new IOException("Solo HTTP(S)");
        if(u.getHost().isEmpty() || (u.getPath()!=null && !u.getPath().isEmpty())) throw new IOException("Inserisci IP:porta, senza percorsi");
        base=text;version=null;
        IOException last=null;
        for(String v:new String[]{"ver100","ver110","ver120","ver130","ver140"}) {
            try {
                byte[] raw=read("GET",base+"/ccapi/"+v+"/deviceinformation",null,128*1024);
                JSONObject j=new JSONObject(new String(raw,"UTF-8"));
                version=v;info=j.optString("productname",j.optString("model","Canon EOS"));
                return;
            }catch(Exception e){last=new IOException(v+": "+e.getMessage(),e);}
        }
        base=null;
        throw new IOException("Nessun endpoint CCAPI deviceinformation: "+(last==null?"risposta sconosciuta":last.getMessage()));
    }

    public void startLive() throws Exception {
        if(!connected()) throw new IOException("Canon non collegata");
        String url=endpoint()+"/shooting/liveview";
        try{
            read("POST",url,"{\"cameradisplay\":\"off\",\"liveviewsize\":\"small\"}",64*1024);
        }catch(IOException first){
            // A device may reject an explicit size; do not fallback on auth/transport errors.
            if(!first.getMessage().contains("HTTP 400")) throw first;
            read("POST",url,"{\"cameradisplay\":\"off\"}",64*1024);
        }
        started=true;
    }
    public Bitmap frame() throws Exception {
        if(!started) throw new IOException("Live View non avviato");
        byte[] b=read("GET",endpoint()+"/shooting/liveview/flip?kind=small",null,4*1024*1024);
        Bitmap bmp=BitmapFactory.decodeByteArray(b,0,b.length);
        if(bmp==null) throw new IOException("Frame Live View non JPEG (multipart/RTP non ancora implementati)");
        return bmp;
    }
    public void shoot() throws Exception {
        if(!connected()) throw new IOException("Canon non collegata");
        read("POST",endpoint()+"/shooting/control/shutterbutton","{\"af\":true}",64*1024);
    }
    public void stop() {
        if(started && connected()) {
            try { read("POST",endpoint()+"/shooting/liveview","{\"cameradisplay\":\"on\",\"liveviewsize\":\"off\"}",64*1024); }
            catch(Exception ignored) {
                try { read("DELETE",endpoint()+"/shooting/liveview",null,64*1024); } catch(Exception ignored2) {}
            }
        }
        started=false;
    }

    static byte[] read(String method,String url,String json,int max) throws IOException {
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
        c.setConnectTimeout(2500);c.setReadTimeout(3500);
        c.setInstanceFollowRedirects(false);
        c.setRequestMethod(method);
        c.setRequestProperty("Accept","application/json,image/jpeg");
        try {
            if(json!=null) {
                c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
                try(OutputStream out=c.getOutputStream()){out.write(json.getBytes("UTF-8"));}
            }
            int code=c.getResponseCode();
            if(code<200||code>=300)throw new IOException("HTTP "+code+" su "+new URL(url).getPath());
            try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buf=new byte[8192];int n,total=0;
                while((n=in.read(buf))!=-1){
                    total+=n;
                    if(total>max)throw new IOException("Risposta supera limite "+max+" byte");
                    out.write(buf,0,n);
                }
                return out.toByteArray();
            }
        }finally{c.disconnect();}
    }
}
