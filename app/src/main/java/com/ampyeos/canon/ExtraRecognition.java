package com.ampyeos.canon;

import android.content.Context;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.DataType;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional on-device ImageNet fallback. It downloads ONLY the official model and
 * labels over HTTPS when needed; it NEVER uploads the photograph. A known SHA-256
 * is pinned for each file, so an altered archive cannot be installed silently.
 *
 * This classifier is NOT object localization or open-vocabulary recognition:
 * it labels the dominant 224x224 image among ~1000 ImageNet classes.
 */
public final class ExtraRecognition {
    // Google's TensorFlow-hosted MobileNetV2 float32 224 package.
    private static final String MODEL_ARCHIVE =
        "https://storage.googleapis.com/download.tensorflow.org/models/tflite_11_05_08/mobilenet_v2_1.0_224.tgz";
    private static final String LABELS_URL =
        "https://storage.googleapis.com/download.tensorflow.org/data/ImageNetLabels.txt";
    private static final String MODEL_ENTRY="mobilenet_v2_1.0_224.tflite";
    // SHA-256 of the EXTRACTED model bytes, not the archive.
    private static final String MODEL_SHA256 =
        "9f3bc29e38e90842a852bfed957dbf5e36f2d97a91dd17736b1e5c0aca8d3303";
    private static final String LABELS_SHA256 =
        "536feacc519de3d418de26b2effb4d75694a8c4c0063e36499a46fa8061e2da9";
    // The old 24 MiB cap rejected the official archive on real devices.
    // Retain generous anti-abuse bounds for disk writes and archive extraction.
    private static final long MAX_ARCHIVE_BYTES=512L*1024*1024;
    private static final long MAX_MODEL_BYTES=256L*1024*1024;
    private static final long MAX_LABELS_BYTES=2L*1024*1024;
    private static final long RETRY_DELAY_MS=30L*60*1000;
    private static final int INPUT_SIZE=224;
    private final Context app;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private final File directory,model,labels;
    private volatile boolean ready=false;
    private volatile boolean closed=false;
    private volatile long retryAfter=0;

    public interface Callback {
        void status(String info);
        void result(Recognition result);
    }
    public static final class Recognition {
        public final String label;
        public final float confidence;
        public final String alternatives;
        public final String explanation;
        public final boolean recognized;
        public Recognition(String label,float confidence,String alternatives,String explanation,boolean found){
            this.label=label;this.confidence=confidence;this.alternatives=alternatives;
            this.explanation=explanation;recognized=found;
        }
        public String describe(){
            return recognized?
                String.format(Locale.ITALIAN,"Riconoscimento AI avanzato: %s (confidenza stimata %.0f%%). %s",
                    label,confidence*100f,explanation):
                "AI avanzata: "+explanation;
        }
    }
    public ExtraRecognition(Context context){
        app=context.getApplicationContext();
        directory=new File(app.getFilesDir(),"ampyeos_models");
        model=new File(directory,MODEL_ENTRY);
        labels=new File(directory,"ImageNetLabels.txt");
    }
    public boolean isWorking(){return busy.get();}
    public boolean isCached(){return model.isFile()&&labels.isFile();}
    public boolean autoEnabled(){
        return app.getSharedPreferences("extra_ai",Context.MODE_PRIVATE)
            .getBoolean("auto_download",true);
    }
    public void setAutoEnabled(boolean on){
        app.getSharedPreferences("extra_ai",Context.MODE_PRIVATE)
            .edit().putBoolean("auto_download",on).apply();
    }
    /** User-requested cellular support is enabled by default, independently of auto download. */
    public boolean allowMobileAuto(){
        return app.getSharedPreferences("extra_ai",Context.MODE_PRIVATE)
            .getBoolean("allow_mobile_auto",true);
    }
    public void setAllowMobileAuto(boolean allowed){
        app.getSharedPreferences("extra_ai",Context.MODE_PRIVATE)
            .edit().putBoolean("allow_mobile_auto",allowed).apply();
    }
    public static String modelName(){return "MobileNetV2 ImageNet · ~1000 classi";}
    private NetworkCapabilities activeCapabilities(){
        ConnectivityManager cm=(ConnectivityManager)app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm==null)return null;
        Network network=cm.getActiveNetwork();
        if(network==null)return null;
        return cm.getNetworkCapabilities(network);
    }
    public boolean wifiConnected(){
        NetworkCapabilities nc=activeCapabilities();
        return nc!=null&&nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            &&(nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||
                nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }
    public boolean mobileConnected(){
        NetworkCapabilities nc=activeCapabilities();
        return nc!=null&&nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            &&nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
    }
    /**
     * Automatic download: Wi-Fi/Ethernet or cellular if permitted by the user's
     * switch. Roaming cellular is excluded from automatic downloads to avoid
     * surprise charges; manual user-triggered download still works.
     */
    public boolean autoNetworkAllowed(){
        NetworkCapabilities nc=activeCapabilities();
        if(nc==null||!nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            return false;
        if(nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||
           nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))return true;
        return allowMobileAuto()&&nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            &&nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
    }
    /**
     * auto=true: Wi-Fi/Ethernet or user-enabled non-roaming mobile data.
     * auto=false: manual action can use mobile data, including roaming.
     * Cached recognition works without any internet connection.
     * Only one job is accepted at a time to prevent repeated downloads.
     */
    public void classify(Bitmap image,boolean auto,Callback callback){
        if(closed||image==null)return;
        if(auto&&!autoEnabled())return;
        if(!busy.compareAndSet(false,true))return;
        worker.execute(()->{
            try{
                if(!ready) {
                    if(!verifyExisting()){
                        if(auto&&!autoNetworkAllowed()){
                            notifyStatus(callback,"Modello aggiuntivo non installato. "+
                                (mobileConnected()&&!allowMobileAuto()?
                                    "Abilita DATI MOBILI AI per scaricarlo automaticamente su 4G/5G. ":
                                    "Connettiti al Wi-Fi o a una rete mobile non in roaming. ")+
                                "Puoi sempre usare RICONOSCIMENTO EXTRA per il download manuale.");
                            return;
                        }
                        if(auto&&System.currentTimeMillis()<retryAfter){
                            notifyStatus(callback,"Download AI già ritentato di recente; "+
                                "riproverò dopo una nuova analisi.");
                            return;
                        }
                        notifyStatus(callback,"ML Kit non ha riconosciuto il soggetto: "+
                            "scarico MobileNetV2 via "+(wifiConnected()?"Wi-Fi":"rete mobile 4G/5G")+
                            ". Verranno trasferiti solo file del modello, non fotografie.");
                        ensureInstalled(callback);
                    }
                    ready=true;
                }
                if(closed)return;
                Recognition result=classifyLocal(image);
                main.post(()->{if(!closed)callback.result(result);});
            }catch(Exception e){
                retryAfter=System.currentTimeMillis()+RETRY_DELAY_MS;
                notifyStatus(callback,"Modello AI non disponibile: "+e.getMessage());
            }finally{busy.set(false);}
        });
    }

    /**
     * ACTION_OPEN_DOCUMENT supplies this Uri with temporary read permission.
     * Import only the official pinned TFLite model/tgz and ImageNet label text.
     * Files may reside in Downloads or another folder selected by the user.
     */
    public void importFromDevice(Uri selected,Callback callback){
        importFromDevice(selected,null,callback);
    }
    public void importFromDevice(Uri selected,Bitmap imageToClassify,Callback callback){
        if(closed||selected==null)return;
        if(!busy.compareAndSet(false,true)){
            notifyStatus(callback,"Importazione non avviata: è già attiva un'operazione AI.");
            return;
        }
        worker.execute(()->{
            File incoming=new File(directory,"selected.partial");
            File extracted=new File(directory,"imported-model.partial");
            try{
                if(!directory.exists()&&!directory.mkdirs())
                    throw new IOException("Impossibile creare la cartella privata dei modelli");
                String name=displayName(selected).toLowerCase(Locale.ROOT);
                if(!name.endsWith(".tgz")&&!name.endsWith(".tar.gz")&&
                   !name.endsWith(".tflite")&&!name.endsWith(".txt"))
                    throw new IOException("Formato non supportato. Seleziona .tgz, .tflite o ImageNetLabels.txt");
                long max=name.endsWith(".txt")?MAX_LABELS_BYTES:
                    name.endsWith(".tflite")?MAX_MODEL_BYTES:MAX_ARCHIVE_BYTES;
                notifyStatus(callback,"Lettura file dalla cartella Download: "+name);
                try(InputStream in=app.getContentResolver().openInputStream(selected);
                    OutputStream out=new BufferedOutputStream(new FileOutputStream(incoming))){
                    if(in==null)throw new IOException("Il selettore Android non consente di leggere il file");
                    copyWithBound(in,out,max);
                }
                if(name.endsWith(".txt")){
                    if(!verifyHash(incoming,LABELS_SHA256))
                        throw new IOException("Le etichette non corrispondono al file ufficiale ImageNetLabels.txt");
                    replaceFile(incoming,labels);
                    ready=false;
                    notifyStatus(callback,"Etichette ImageNet verificate e importate dalla cartella Download.");
                }else{
                    if(name.endsWith(".tgz")||name.endsWith(".tar.gz"))
                        extractModel(incoming,extracted);
                    else {
                        if(!incoming.renameTo(extracted))
                            throw new IOException("Impossibile preparare il modello selezionato");
                    }
                    if(!verifyHash(extracted,MODEL_SHA256))
                        throw new IOException("SHA-256 diverso dal modello ufficiale. "+
                            "Seleziona il file MobileNetV2 previsto, non un modello incompatibile.");
                    replaceFile(extracted,model);
                    ready=false;
                    notifyStatus(callback,"Modello TFLite verificato e installato da Download.");
                    if(!labels.isFile()||!verifyHash(labels,LABELS_SHA256)){
                        notifyStatus(callback,"Scarico le etichette ufficiali (file piccolo). "+
                            "In assenza di rete puoi importare ImageNetLabels.txt da Download.");
                        File labelStage=new File(directory,"labels-import.partial");
                        try {
                            download(LABELS_URL,labelStage,MAX_LABELS_BYTES,callback,"etichette");
                            if(!verifyHash(labelStage,LABELS_SHA256))
                                throw new IOException("Hash etichette non valido");
                            replaceFile(labelStage,labels);
                            notifyStatus(callback,"Modello ed etichette verificati. Riconoscimento AI pronto offline.");
                        }finally{labelStage.delete();}
                    }else{
                        notifyStatus(callback,"Modello ed etichette verificati. Riconoscimento AI pronto offline.");
                    }
                }
                if(model.isFile() && labels.isFile() && verifyExisting()) {
                    ready=true;
                    if(imageToClassify!=null&&!closed){
                        notifyStatus(callback,"Modello importato: analizzo subito la fotografia.");
                        Recognition result=classifyLocal(imageToClassify);
                        main.post(()->{if(!closed)callback.result(result);});
                    }
                }
            }catch(Exception e){
                notifyStatus(callback,"Importazione AI: "+e.getMessage());
            }finally{
                incoming.delete();extracted.delete();
                busy.set(false);
            }
        });
    }
    private String displayName(Uri uri){
        try(Cursor cursor=app.getContentResolver().query(uri,
            new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()){
                String name=cursor.getString(0);
                if(name!=null&&!name.isEmpty())return name;
            }
        }catch(Exception ignored){}
        String path=uri.getLastPathSegment();
        return path==null?"modello.tgz":path;
    }
    private static void replaceFile(File source,File destination)throws IOException{
        if(destination.exists()&&!destination.delete())
            throw new IOException("Impossibile sostituire il modello precedente");
        if(!source.renameTo(destination))
            throw new IOException("Impossibile installare il file verificato");
    }
    private static void copyWithBound(InputStream in,OutputStream out,long limit)throws IOException{
        byte[] buf=new byte[32*1024];long bytes=0;int n;
        while((n=in.read(buf))!=-1){
            bytes+=n;
            if(bytes>limit)throw new IOException("Il file supera il limite anti-abuso di "+
                (limit/1024/1024)+" MiB");
            out.write(buf,0,n);
        }
        if(bytes==0)throw new IOException("Il file selezionato è vuoto");
    }

    public void close(){closed=true;worker.shutdownNow();}

    private void notifyStatus(Callback cb,String msg){
        main.post(()->{if(!closed)cb.status(msg);});
    }
    private boolean verifyExisting(){
        try{
            return model.isFile()&&labels.isFile()&&verifyHash(model,MODEL_SHA256)
                &&verifyHash(labels,LABELS_SHA256);
        }catch(Exception ignored){return false;}
    }
    private void ensureInstalled(Callback cb)throws Exception{
        if(!directory.exists()&&!directory.mkdirs())
            throw new IOException("Impossibile creare archivio locale AI");
        File archive=new File(directory,"download.tgz.partial");
        File tmpModel=new File(directory,"model.partial");
        File tmpLabels=new File(directory,"labels.partial");
        try{
            download(MODEL_ARCHIVE,archive,MAX_ARCHIVE_BYTES,cb,"modello");
            notifyStatus(cb,"Download completo. Estraggo solo il modello TFLite; "+
                "gli altri file di addestramento dell'archivio vengono ignorati.");
            extractModel(archive,tmpModel);
            notifyStatus(cb,"Modello estratto: verifico integrità SHA-256.");
            if(!verifyHash(tmpModel,MODEL_SHA256))
                throw new IOException("SHA-256 del modello errato: download rifiutato");
            download(LABELS_URL,tmpLabels,MAX_LABELS_BYTES,cb,"etichette");
            if(!verifyHash(tmpLabels,LABELS_SHA256))
                throw new IOException("SHA-256 delle etichette errato: download rifiutato");
            if(!tmpModel.renameTo(model))
                throw new IOException("Impossibile salvare il modello verificato");
            if(!tmpLabels.renameTo(labels)){
                model.delete();
                throw new IOException("Impossibile salvare le etichette verificate");
            }
            notifyStatus(cb,"Modello AI verificato e installato. "+
                "Le prossime analisi funzioneranno anche offline.");
        }finally{
            archive.delete();tmpModel.delete();tmpLabels.delete();
        }
    }
    private void download(String url,File target,long limit,Callback cb,String what)throws IOException{
        URL targetUrl=new URL(url);
        if(!targetUrl.getProtocol().equals("https")||
            !targetUrl.getHost().equals("storage.googleapis.com"))
            throw new IOException("Sorgente non consentita");
        HttpURLConnection con=(HttpURLConnection)targetUrl.openConnection();
        con.setConnectTimeout(15_000);
        con.setReadTimeout(30_000);
        con.setInstanceFollowRedirects(false);
        con.setRequestProperty("Accept","application/octet-stream,*/*");
        try{
            int status=con.getResponseCode();
            if(status!=200)throw new IOException("Download "+what+": HTTP "+status);
            long expected=con.getContentLengthLong();
            if(expected>limit)throw new IOException("Dimensione dichiarata "+(expected/1024/1024)+" MiB: supera il limite di sicurezza "+(limit/1024/1024)+" MiB");
            try(InputStream in=new BufferedInputStream(con.getInputStream());
                OutputStream out=new BufferedOutputStream(new FileOutputStream(target))){
                long count=0,next=2L*1024*1024;
                byte[] buf=new byte[16384];int n;
                while((n=in.read(buf))!=-1){
                    if(closed)throw new IOException("Download interrotto");
                    count+=n;
                    if(count>limit)throw new IOException("Download oltre il limite di sicurezza "+(limit/1024/1024)+" MiB");
                    out.write(buf,0,n);
                    if(count>=next){
                        notifyStatus(cb,"Download "+what+": "+(count/1024/1024)+" MB");
                        next+=2L*1024*1024;
                    }
                }
            }
        }finally{con.disconnect();}
    }
    /** Streaming extractor also exercised against the actual TensorFlow archive in CI. */
    private static void extractModel(File archive, File output) throws IOException {
        ModelArchive.extract(archive,output,MODEL_ENTRY,MAX_MODEL_BYTES);
    }

    private static boolean verifyHash(File file,String expected)throws Exception{
        MessageDigest dig=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))){
            byte[] buf=new byte[32768];int n;
            while((n=in.read(buf))!=-1)dig.update(buf,0,n);
        }
        StringBuilder hash=new StringBuilder();
        for(byte b:dig.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));
        return expected.equals(hash.toString());
    }

    private Recognition classifyLocal(Bitmap source)throws Exception{
        ArrayList<String> list=new ArrayList<>();
        try(BufferedInputStream in=new BufferedInputStream(new FileInputStream(labels))){
            byte[] raw=in.readAllBytes();
            String content=new String(raw,StandardCharsets.UTF_8);
            for(String line:content.split("\\R"))if(!line.trim().isEmpty())list.add(line.trim());
        }
        if(list.size()!=1001)
            throw new IOException("Formato etichette non compatibile: "+list.size());
        try(FileInputStream in=new FileInputStream(model);
            FileChannel channel=in.getChannel()){
            Interpreter.Options options=new Interpreter.Options().setNumThreads(2);
            try(Interpreter interpreter=new Interpreter(
                channel.map(FileChannel.MapMode.READ_ONLY,0,channel.size()),options)){
                int[] shape=interpreter.getInputTensor(0).shape();
                int[] outputShape=interpreter.getOutputTensor(0).shape();
                if(shape.length!=4||shape[0]!=1||shape[1]!=INPUT_SIZE||shape[2]!=INPUT_SIZE||
                    shape[3]!=3||interpreter.getInputTensor(0).dataType()!=DataType.FLOAT32 ||
                    outputShape.length!=2||outputShape[0]!=1||outputShape[1]!=1001 ||
                    interpreter.getOutputTensor(0).dataType()!=DataType.FLOAT32)
                    throw new IOException("Architettura del modello non supportata");

                Bitmap sized=Bitmap.createScaledBitmap(source,INPUT_SIZE,INPUT_SIZE,true);
                int[] pixels=new int[INPUT_SIZE*INPUT_SIZE];
                sized.getPixels(pixels,0,INPUT_SIZE,0,0,INPUT_SIZE,INPUT_SIZE);
                if(sized!=source)sized.recycle();
                ByteBuffer rgb=ByteBuffer.allocateDirect(INPUT_SIZE*INPUT_SIZE*3*4)
                    .order(ByteOrder.nativeOrder());
                for(int pixel:pixels){
                    rgb.putFloat(((pixel>>16&255)-127.5f)/127.5f);
                    rgb.putFloat(((pixel>>8&255)-127.5f)/127.5f);
                    rgb.putFloat(((pixel&255)-127.5f)/127.5f);
                }
                rgb.rewind();
                float[][] output=new float[1][1001];
                interpreter.run(rgb,output);
                int first=0,second=0,third=0;
                for(int i=1;i<output[0].length;i++){
                    if(output[0][i]>output[0][first]){third=second;second=first;first=i;}
                    else if(i!=first&&output[0][i]>output[0][second]){third=second;second=i;}
                    else if(i!=first&&i!=second&&output[0][i]>output[0][third])third=i;
                }
                float confidence=output[0][first],runnerUp=output[0][second];
                boolean confident=first>0 && confidence>=.18f &&
                    confidence-runnerUp>=.045f;
                String optionsText=label(list,first)+
                    String.format(Locale.ITALIAN," %.0f%%",confidence*100)+"; "+
                    label(list,second)+
                    String.format(Locale.ITALIAN," %.0f%%",runnerUp*100)+"; "+
                    label(list,third)+
                    String.format(Locale.ITALIAN," %.0f%%",output[0][third]*100);
                if(!confident) {
                    return new Recognition("non identificato",confidence,optionsText,
                        "Ho analizzato la foto con MobileNetV2, ma le probabilità "+
                        "sono troppo simili o basse per identificare il soggetto con affidabilità. "+
                        "Possibili etichette: "+optionsText+". Non invento una risposta.",
                        false);
                }
                String name=label(list,first);
                return new Recognition(name,confidence,optionsText,
                    "La rete ha associato la foto alla classe ImageNet \""+name+
                    "\". È un'ipotesi statistica sul contenuto prevalente, non un'identificazione certa. "+
                    "Altre possibilità: "+optionsText+". Il modello lavora localmente sul Pixel.",
                    true);
            }
        }
    }
    private static String label(ArrayList<String> list,int index){
        return index>=0&&index<list.size()?list.get(index):"sconosciuto";
    }
}
