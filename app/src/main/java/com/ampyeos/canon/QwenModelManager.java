package com.ampyeos.canon;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.provider.OpenableColumns;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Downloads official Qwen3-VL 2B GGUF weights to PRIVATE app storage.
 * No user photograph is sent or even read by this class.
 *
 * Two complete pinned SHA256s are checked before either file is activated.
 * Partial transfers can resume after interruption, including on 4G/5G.
 * The download is user-initiated; there is never a surprise multi-GB transfer.
 */
public final class QwenModelManager {
    public static final String MODEL = "Qwen3VL-2B-Instruct-Q4_K_M.gguf";
    public static final String PROJECTOR = "mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf";
    private static final String BASE =
        "https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct-GGUF/resolve/main/";
    private static final long MODEL_SIZE = 1107409952L;
    private static final long PROJECTOR_SIZE = 445053216L;
    private static final String MODEL_SHA =
        "089d75c52f4b7ffc56ba998ffc50aae89fcafc755f9e7208aacca281dca6c2ae";
    private static final String PROJECTOR_SHA =
        "f9a68fabba69c3b81e153367b2c7521030b0fa8bb0de400c9599c8e6725f9c82";
    public static final long TOTAL_BYTES = MODEL_SIZE + PROJECTOR_SIZE;
    private static final long MIN_FREE_MARGIN = 350L * 1024 * 1024;
    private final Context app;
    private final File folder;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService work = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean(false);
    private volatile boolean cancelled = false;
    private volatile boolean closed = false;

    public interface Listener {
        void onProgress(String message, long bytes, long total);
        void onComplete(boolean success, String message);
    }

    public QwenModelManager(Context context){
        app = context.getApplicationContext();
        folder = new File(app.getFilesDir(), "ampyeos_qwen_vision");
    }
    public File modelFile(){return new File(folder, MODEL);}
    public File projectorFile(){return new File(folder, PROJECTOR);}
    public boolean isReady(){
        return modelFile().isFile()&&projectorFile().isFile()
            &&modelFile().length()==MODEL_SIZE
            &&projectorFile().length()==PROJECTOR_SIZE;
    }
    public boolean isWorking(){return busy.get();}
    public String state(){
        if(isReady())return "Qwen3-VL 2B + mmproj installati (verificati durante download)";
        return "Qwen3-VL 2B non installato · circa 1,55 GB da scaricare su Wi-Fi/4G/5G";
    }
    public void cancel(){cancelled=true;}
    public void close(){closed=true;cancelled=true;work.shutdownNow();}
    public void download(Listener listener){
        if(closed)return;
        if(!busy.compareAndSet(false,true)){
            report(listener,"Download già in esecuzione",0);
            return;
        }
        cancelled=false;
        work.execute(()->{
            try{
                if(!folder.exists()&&!folder.mkdirs())throw new Exception("Cartella privata non disponibile");
                long partial = (isComplete(modelFile(), MODEL_SIZE)?MODEL_SIZE:
                    Math.min(MODEL_SIZE,Math.max(0,new File(folder,MODEL+".part").length())))
                    + (isComplete(projectorFile(),PROJECTOR_SIZE)?PROJECTOR_SIZE:
                    Math.min(PROJECTOR_SIZE,Math.max(0,new File(folder,PROJECTOR+".part").length())));
                StatFs fs=new StatFs(folder.getAbsolutePath());
                long free=fs.getAvailableBytes();
                long remaining=TOTAL_BYTES-partial;
                if(free<remaining+MIN_FREE_MARGIN)
                    throw new Exception("Spazio insufficiente: liberi "+
                        Math.max(0,(remaining+MIN_FREE_MARGIN-free)/1024/1024)+" MiB");
                // Sequential: no RAM pressure from two simultaneous downloads.
                transfer(MODEL,MODEL_SIZE,MODEL_SHA,listener);
                transfer(PROJECTOR,PROJECTOR_SIZE,PROJECTOR_SHA,listener);
                if(!isReady())throw new Exception("Installazione incompleta");
                finish(listener,true,"Qwen3-VL 2B e proiettore verificati. Inferenza 100% sul Pixel.");
            }catch(Exception e){
                finish(listener,false,
                    (cancelled?"Download sospeso. Puoi riprenderlo. ":"Download interrotto: ")+
                    e.getMessage());
            }finally{busy.set(false);}
        });
    }
    private void transfer(String filename,long expected,String pinned,Listener listener)throws Exception{
        File completed=new File(folder,filename);
        if(isComplete(completed,expected)){
            // Never trust same-size unverified file on the device.
            report(listener,"Controllo SHA-256 di "+filename,0);
            if(sha256(completed).equals(pinned)){
                report(listener,"Già pronto: "+filename,0);
                return;
            }
            if(!completed.delete())throw new Exception("File esistente non sostituibile");
        }
        File partial=new File(folder,filename+".part");
        long offset=Math.max(0,partial.length());
        if(offset>expected){
            if(!partial.delete())throw new Exception("File parziale oltre dimensione");
            offset=0;
        }
        URL address=new URL(BASE+filename);
        HttpURLConnection connection=openPinnedRedirect(address,offset);
        try{
            int status=connection.getResponseCode();
            if(offset>0&&status==200){
                // HTTP server ignored Range: restart without corrupting the file.
                offset=0;
            }else if(offset>0&&status==206){
                String range=connection.getHeaderField("Content-Range");
                if(range==null||!range.startsWith("bytes "+offset+"-"))
                    throw new Exception("Resume server non coerente, riprova");
            }else if(status!=200)
                throw new Exception("Download "+filename+": HTTP "+status);
            long length=connection.getContentLengthLong();
            if(length>0 && offset+length>expected)
                throw new Exception("Risposta server supera la dimensione ufficiale");
            try(InputStream in=new BufferedInputStream(connection.getInputStream(),64*1024);
                RandomAccessFile out=new RandomAccessFile(partial,"rw")){
                if(offset==0)out.setLength(0);
                out.seek(offset);
                byte[] buffer=new byte[128*1024];
                long size=offset;
                long lastShown=0;
                int n;
                while((n=in.read(buffer))!=-1){
                    if(cancelled||closed||Thread.currentThread().isInterrupted())
                        throw new Exception("Sospeso dall'utente");
                    size+=n;
                    if(size>expected)throw new Exception("Pacchetto più grande del file ufficiale");
                    out.write(buffer,0,n);
                    if(size-lastShown>=4*1024*1024){
                        report(listener,filename+" · "+(size/1024/1024)+
                            "/"+(expected/1024/1024)+" MiB",size);
                        lastShown=size;
                    }
                }
                if(size!=expected)
                    throw new Exception("Download incompleto: "+size+" / "+expected+" byte");
            }
            report(listener,"Verifica SHA-256 "+filename+" (può richiedere un po')",expected);
            if(!sha256(partial).equals(pinned)){
                partial.delete();
                throw new Exception("Integrità SHA-256 non valida: file eliminato");
            }
            // GGUF signature check in addition to pinned full-file SHA-256.
            try(FileInputStream in=new FileInputStream(partial)){
                byte[] magic=new byte[4];
                if(in.read(magic)!=4||magic[0]!='G'||magic[1]!='G'||
                    magic[2]!='U'||magic[3]!='F')
                    throw new Exception("Intestazione GGUF non valida");
            }
            if(completed.exists()&&!completed.delete())
                throw new Exception("Non posso sostituire il modello precedente");
            if(!partial.renameTo(completed))
                throw new Exception("Impossibile attivare il GGUF verificato");
            report(listener,"Verificato: "+filename,expected);
        }finally{connection.disconnect();}
    }
    private static boolean isComplete(File file,long expected){
        return file.isFile()&&file.length()==expected;
    }
    private static String sha256(File input)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new BufferedInputStream(new FileInputStream(input),128*1024)){
            byte[] buffer=new byte[128*1024];
            int n;
            while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);
        }
        StringBuilder out=new StringBuilder(64);
        for(byte b:digest.digest())out.append(String.format(Locale.ROOT,"%02x",b&0xff));
        return out.toString();
    }
    /** Follows signed Hugging Face CDN redirects without any photo or auth data. */
    private static HttpURLConnection openPinnedRedirect(URL start,long offset)throws Exception{
        URL url=start;
        for(int n=0;n<8;n++){
            if(!url.getProtocol().equalsIgnoreCase("https"))
                throw new Exception("Download modello bloccato: connessione non HTTPS");
            HttpURLConnection c=(HttpURLConnection)url.openConnection();
            c.setConnectTimeout(20_000);c.setReadTimeout(45_000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","AmpyEOS-LocalVision/0.7");
            if(offset>0)c.setRequestProperty("Range","bytes="+offset+"-");
            int code=c.getResponseCode();
            if(code==301||code==302||code==303||code==307||code==308){
                String target=c.getHeaderField("Location");
                c.disconnect();
                if(target==null)throw new Exception("Redirect download privo di indirizzo");
                url=new URL(url,target);
            }else return c;
        }
        throw new Exception("Troppi redirect nel download del modello");
    }
    private void report(Listener listener,String message,long bytes){
        main.post(()->{if(!closed)listener.onProgress(message,bytes,TOTAL_BYTES);});
    }
    private void finish(Listener listener,boolean success,String message){
        main.post(()->{if(!closed)listener.onComplete(success,message);});
    }
}
