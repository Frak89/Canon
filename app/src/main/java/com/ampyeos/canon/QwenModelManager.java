package com.ampyeos.canon;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.provider.OpenableColumns;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Qwen3-VL 2B: DOWNLOAD ONLY ONCE.
 *
 * Canonical storage: public Download/AmpyEOS/Qwen3-VL via Android MediaStore.
 * Files in MediaStore survive app updates AND uninstalls, unlike getFilesDir().
 * If Android restricts an older Downloads item after reinstall, the user can
 * select it via ACTION_OPEN_DOCUMENT; the URI grant is persisted locally.
 *
 * Uses ParcelFileDescriptor + /proc/self/fd/N to feed GGUF directly to llama.cpp.
 * No duplicate 1.55GB internal cache is needed for new downloads.
 * No photographs or EXIF are sent anywhere.
 */
public final class QwenModelManager {
    public static final String MODEL="Qwen3VL-2B-Instruct-Q4_K_M.gguf";
    public static final String PROJECTOR="mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf";
    private static final String BASE=
        "https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct-GGUF/resolve/main/";
    private static final long MODEL_SIZE=1107409952L;
    private static final long PROJECTOR_SIZE=445053216L;
    private static final String MODEL_SHA=
        "089d75c52f4b7ffc56ba998ffc50aae89fcafc755f9e7208aacca281dca6c2ae";
    private static final String PROJECTOR_SHA=
        "f9a68fabba69c3b81e153367b2c7521030b0fa8bb0de400c9599c8e6725f9c82";
    public static final long TOTAL_BYTES=MODEL_SIZE+PROJECTOR_SIZE;
    private static final long MARGIN=350L*1024*1024;

    private final Context app;
    private final QwenPersistentStore shared;
    private final SharedPreferences prefs;
    private final File legacyFolder;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService queue=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private volatile Uri modelUri,projectorUri;
    private volatile boolean modelLegacy=false,projectorLegacy=false;
    private volatile boolean cancelled=false,closed=false,scanned=false;
    private volatile String lastMessage="Verifica dei modelli in Download non ancora avviata";

    public interface Listener {
        void onProgress(String message,long bytes,long total);
        void onComplete(boolean success,String message);
    }
    public QwenModelManager(Context context) {
        app=context.getApplicationContext();
        shared=new QwenPersistentStore(app);
        prefs=app.getSharedPreferences("ampyeos_qwen_downloads",Context.MODE_PRIVATE);
        legacyFolder=new File(app.getFilesDir(),"ampyeos_qwen_vision");
    }
    /** Historical locations; never use these as the canonical model store. */
    public File modelFile(){return new File(legacyFolder,MODEL);}
    public File projectorFile(){return new File(legacyFolder,PROJECTOR);}
    public boolean isReady(){
        return (modelUri!=null||modelLegacy)&&(projectorUri!=null||projectorLegacy);
    }
    public boolean isWorking(){return busy.get();}
    public String state(){
        if(isReady()){
            if(!modelLegacy&&!projectorLegacy)
                return "Qwen3-VL pronto · modello persistente in Download/AmpyEOS/Qwen3-VL";
            return "Qwen3-VL presente nella vecchia cache; copia in Download in preparazione";
        }
        if(isWorking())return lastMessage;
        if(!scanned)return "Verifico Qwen3-VL in Download/AmpyEOS/Qwen3-VL…";
        return "Qwen3-VL incompleto · usa file salvati in Download; "+
            "non serve riscaricare quelli già presenti";
    }
    public void cancel(){cancelled=true;}
    public void close(){closed=true;cancelled=true;queue.shutdownNow();}

    /**
     * Runs on a worker. No network. Checks existing public files, persisted
     * SAF access, and upgrades the v0.7/0.8 private cache automatically.
     */
    public void prepare(Listener listener){
        if(closed)return;
        if(!busy.compareAndSet(false,true))return;
        queue.execute(()->{
            try{
                report(listener,"Cerco modelli Qwen già scaricati in Download…",0);
                scan(listener);
                finish(listener,true,isReady()?
                    "Qwen3-VL recuperato da Download: nessun download necessario.":
                    "Nessun modello Qwen completo trovato. "+
                    "Puoi scegliere i GGUF già presenti con IMPORTA DA DOWNLOAD.");
            }catch(Exception e){
                finish(listener,false,"Ricerca modelli: "+e.getMessage());
            }finally{busy.set(false);}
        });
    }
    public QwenPersistentStore.OpenModels openForInference()throws Exception {
        if(!isReady())throw new Exception("Qwen non verificato: apri la sezione modelli");
        ParcelFileDescriptor lang=modelUri!=null?
            shared.descriptor(modelUri):
            ParcelFileDescriptor.open(modelFile(),ParcelFileDescriptor.MODE_READ_ONLY);
        try{
            ParcelFileDescriptor vision=projectorUri!=null?
                shared.descriptor(projectorUri):
                ParcelFileDescriptor.open(projectorFile(),ParcelFileDescriptor.MODE_READ_ONLY);
            return new QwenPersistentStore.OpenModels(lang,vision);
        }catch(Exception e){
            lang.close();
            throw e;
        }
    }
    private static final class Source {
        Uri uri;
        boolean legacy;
        Source(Uri uri,boolean legacy){this.uri=uri;this.legacy=legacy;}
    }
    private void scan(Listener listener)throws Exception{
        Source lang=resolve(MODEL,MODEL_SIZE,MODEL_SHA,listener);
        Source mm=resolve(PROJECTOR,PROJECTOR_SIZE,PROJECTOR_SHA,listener);
        modelUri=lang.uri;modelLegacy=lang.legacy;
        projectorUri=mm.uri;projectorLegacy=mm.legacy;
        scanned=true;
    }
    private Source resolve(String name,long expected,String pinned,Listener listener)
            throws Exception {
        // 1. Persisted SAF document: also works after an app update.
        String saved=prefs.getString("document_"+name,null);
        if(saved!=null) {
            try{
                Uri selected=Uri.parse(saved);
                if(validate(selected,expected,pinned,listener))
                    return new Source(selected,false);
            }catch(Exception ignored){}
            prefs.edit().remove("document_"+name).apply();
        }
        // 2. Public MediaStore Downloads (survives app uninstall).
        Uri existing=shared.find(name);
        if(existing!=null&&validate(existing,expected,pinned,listener)){
            prefs.edit().putString("document_"+name,existing.toString()).apply();
            return new Source(existing,false);
        }
        // 3. Migrate models downloaded by the previous app version from
        //    private app storage. Never delete the only verified copy.
        File privateModel=new File(legacyFolder,name);
        if(privateModel.isFile()&&privateModel.length()==expected){
            report(listener,"Recupero il modello precedente e lo salvo in Download: "+name,0);
            if(!sha256(privateModel).equals(pinned))
                throw new Exception("Il vecchio GGUF è corrotto: "+name);
            try {
                Uri publicCopy=shared.publish(privateModel,name,expected);
                if(validate(publicCopy,expected,pinned,listener)){
                    prefs.edit().putString("document_"+name,publicCopy.toString()).apply();
                    // The durable file is fully verified; release old cache space.
                    privateModel.delete();
                    return new Source(publicCopy,false);
                }
            }catch(Exception e){
                report(listener,"Copia in Download non riuscita ("+
                    e.getMessage()+"). Conservo il file privato originale.",0);
            }
            return new Source(null,true);
        }
        return new Source(null,false);
    }
    private boolean validate(Uri uri,long expected,String pinned,Listener listener)
            throws Exception {
        if(uri==null)return false;
        long length=shared.length(uri);
        if(length!=expected)return false;
        report(listener,"Verifico il modello locale: "+(expected/1024/1024)+" MiB",0);
        try(InputStream in=shared.read(uri)){
            return sha256(in).equals(pinned);
        }catch(Exception e){return false;}
    }

    /** Use the file picked by Android directly, without creating another 1GB copy. */
    public void importFile(Uri document,Listener listener){
        if(closed||document==null)return;
        if(!busy.compareAndSet(false,true)){
            report(listener,"Operazione Qwen già in corso",0);return;
        }
        queue.execute(()->{
            try{
                String filename=null;
                try(Cursor c=app.getContentResolver().query(document,
                    new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
                    if(c!=null&&c.moveToFirst())filename=c.getString(0);
                }
                if(filename==null)throw new Exception("Nome file non disponibile");
                long length;String digest;
                if(filename.equals(MODEL)){length=MODEL_SIZE;digest=MODEL_SHA;}
                else if(filename.equals(PROJECTOR)){length=PROJECTOR_SIZE;digest=PROJECTOR_SHA;}
                else throw new Exception("Seleziona il modello Qwen3-VL o il relativo mmproj GGUF");
                if(!validate(document,length,digest,listener))
                    throw new Exception("GGUF non completo o SHA-256 errato: "+filename);
                try{
                    app.getContentResolver().takePersistableUriPermission(document,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }catch(SecurityException ignored){
                    // Some MediaStore URIs are always accessible to their owner.
                }
                prefs.edit().putString("document_"+filename,document.toString()).apply();
                if(filename.equals(MODEL)){modelUri=document;modelLegacy=false;}
                else {projectorUri=document;projectorLegacy=false;}
                scanned=true;
                finish(listener,true,"File riconosciuto senza riscaricare: "+filename+
                    ". "+(isReady()?"Qwen offline pronto.":"Scegli anche l'altro GGUF da Download."));
            }catch(Exception e){
                finish(listener,false,"Importazione Qwen: "+e.getMessage());
            }finally{busy.set(false);}
        });
    }

    public void download(Listener listener){
        if(closed)return;
        if(!busy.compareAndSet(false,true)){
            report(listener,"Operazione Qwen già in corso",0);return;
        }
        cancelled=false;
        queue.execute(()->{
            try {
                scan(listener); // Recover any existing file BEFORE making HTTP requests.
                if(isReady()){
                    finish(listener,true,"Modelli già salvati in Download. "+
                        "Nessun dato scaricato da Internet.");
                    return;
                }
                long remaining=(modelUri!=null||modelLegacy?0:MODEL_SIZE)+
                    (projectorUri!=null||projectorLegacy?0:PROJECTOR_SIZE);
                long free=new StatFs(Environment.getExternalStorageDirectory().getPath())
                    .getAvailableBytes();
                if(free<remaining+MARGIN)
                    throw new Exception("Libera almeno "+
                        ((remaining+MARGIN-free+1048575)/1048576)+
                        " MiB per salvare i modelli in Download");
                if(modelUri==null&&!modelLegacy) {
                    Uri uri=transfer(MODEL,MODEL_SIZE,MODEL_SHA,listener);
                    modelUri=uri;
                    prefs.edit().putString("document_"+MODEL,uri.toString()).apply();
                }
                if(projectorUri==null&&!projectorLegacy) {
                    Uri uri=transfer(PROJECTOR,PROJECTOR_SIZE,PROJECTOR_SHA,listener);
                    projectorUri=uri;
                    prefs.edit().putString("document_"+PROJECTOR,uri.toString()).apply();
                }
                finish(listener,isReady(),isReady()?
                    "Qwen installato in Download/AmpyEOS/Qwen3-VL. "+
                    "Resterà sul telefono anche dopo la disinstallazione.":
                    "Modello incompleto: importa il file mancante.");
            }catch(Exception e){
                finish(listener,false,(cancelled?"Download sospeso: ":"Download Qwen: ")+
                    e.getMessage());
            }finally{busy.set(false);}
        });
    }

    /**
     * Resume a public .part MediaStore row through a seekable descriptor.
     * Rename it to the final .gguf only after full SHA-256 verification.
     */
    private Uri transfer(String filename,long expected,String pinned,Listener listener)
            throws Exception {
        Uri partial=shared.find(filename+".part");
        if(partial==null)partial=shared.create(filename+".part");
        long offset=shared.length(partial);
        if(offset<0||offset>expected) {
            shared.discardOwnPartial(partial);
            partial=shared.create(filename+".part");
            offset=0;
        }
        report(listener,"Download in Download/AmpyEOS/Qwen3-VL: "+filename,offset);
        if(offset<expected) {
            HttpURLConnection connection=openPinnedRedirect(new URL(BASE+filename),offset);
            try{
                int status=connection.getResponseCode();
                if(offset>0&&status==200){
                    offset=0; // Server ignored Range. Start again without corruption.
                }else if(offset>0&&status==206){
                    String range=connection.getHeaderField("Content-Range");
                    if(range==null||!range.startsWith("bytes "+offset+"-"))
                        throw new Exception("Ripresa download non coerente col server");
                }else if(status!=200&&status!=206)
                    throw new Exception("Download "+filename+": HTTP "+status);
                long contentLength=connection.getContentLengthLong();
                if(contentLength>0&&offset+contentLength>expected)
                    throw new Exception("Dimensioni del modello non corrispondenti alla fonte");
                try(ParcelFileDescriptor fd=shared.writable(partial);
                    FileOutputStream out=new FileOutputStream(fd.getFileDescriptor());
                    InputStream in=new BufferedInputStream(connection.getInputStream(),128*1024)){
                    FileChannel channel=out.getChannel();
                    if(offset==0)channel.truncate(0);
                    channel.position(offset);
                    byte[] buffer=new byte[128*1024];int n;
                    long current=offset,lastShown=offset;
                    while((n=in.read(buffer))!=-1){
                        if(cancelled||closed||Thread.currentThread().isInterrupted())
                            throw new Exception("Sospeso; i byte già scaricati restano in Download");
                        current+=n;
                        if(current>expected)throw new Exception("File più grande del previsto");
                        out.write(buffer,0,n);
                        if(current-lastShown>=8L*1024*1024){
                            report(listener,filename+": "+(current/1024/1024)+
                                " / "+(expected/1024/1024)+" MiB",current);
                            lastShown=current;
                        }
                    }
                    out.flush();
                    if(current!=expected)
                        throw new Exception("Download incompleto; riprenderà dai byte salvati");
                }
            }finally{connection.disconnect();}
        }
        if(shared.length(partial)!=expected)
            throw new Exception("GGUF parziale: non installo dati incompleti");
        report(listener,"Verifica SHA-256 di "+filename+" (in locale)",expected);
        try(InputStream in=shared.read(partial)){
            if(!sha256(in).equals(pinned)){
                shared.discardOwnPartial(partial);
                throw new Exception("Hash SHA-256 errato; file rifiutato");
            }
        }
        try(InputStream in=shared.read(partial)){
            byte[] magic=new byte[4];
            if(in.read(magic)!=4||magic[0]!='G'||magic[1]!='G'||
                magic[2]!='U'||magic[3]!='F'){
                shared.discardOwnPartial(partial);
                throw new Exception("Il file non contiene un modello GGUF valido");
            }
        }
        shared.finish(partial,filename);
        report(listener,"Verificato e salvato definitivamente in Download: "+filename,expected);
        return partial;
    }
    private static String sha256(File file)throws Exception{
        try(InputStream in=new BufferedInputStream(new FileInputStream(file),128*1024)){
            return sha256(in);
        }
    }
    private static String sha256(InputStream in)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        byte[] bytes=new byte[128*1024];int n;
        while((n=in.read(bytes))!=-1)digest.update(bytes,0,n);
        StringBuilder hex=new StringBuilder(64);
        for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));
        return hex.toString();
    }
    private static HttpURLConnection openPinnedRedirect(URL from,long offset)
            throws Exception {
        URL address=from;
        for(int attempt=0;attempt<8;attempt++){
            if(!"https".equalsIgnoreCase(address.getProtocol()))
                throw new Exception("È richiesta una connessione HTTPS al modello");
            HttpURLConnection con=(HttpURLConnection)address.openConnection();
            con.setConnectTimeout(20000);con.setReadTimeout(45000);
            con.setInstanceFollowRedirects(false);
            con.setRequestProperty("User-Agent","AmpyEOS-ModelDownload/0.8.1");
            if(offset>0)con.setRequestProperty("Range","bytes="+offset+"-");
            int status=con.getResponseCode();
            if(status==301||status==302||status==303||status==307||status==308){
                String dest=con.getHeaderField("Location");
                con.disconnect();
                if(dest==null)throw new Exception("Redirect CDN senza destinazione");
                address=new URL(address,dest);
            }else return con;
        }
        throw new Exception("Troppi reindirizzamenti dal sito dei modelli");
    }
    private void report(Listener l,String message,long bytes){
        lastMessage=message;
        main.post(()->{if(!closed)l.onProgress(message,bytes,TOTAL_BYTES);});
    }
    private void finish(Listener l,boolean success,String message){
        lastMessage=message;
        main.post(()->{if(!closed)l.onComplete(success,message);});
    }
}
