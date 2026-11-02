package com.ampyeos.canon;

import android.content.Context;
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
import java.util.zip.GZIPInputStream;

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
    private static final long MAX_ARCHIVE_BYTES=24L*1024*1024;
    private static final long MAX_MODEL_BYTES=22L*1024*1024;
    private static final long MAX_LABELS_BYTES=250_000;
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
    public static String modelName(){return "MobileNetV2 ImageNet · ~1000 classi";}
    public boolean wifiConnected(){
        ConnectivityManager cm=(ConnectivityManager)app.getSystemService(Context.CONNECTIVITY_SERVICE);
        if(cm==null)return false;
        Network network=cm.getActiveNetwork();
        if(network==null)return false;
        NetworkCapabilities nc=cm.getNetworkCapabilities(network);
        return nc!=null&&nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            &&(nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)||
               nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }
    /**
     * auto=true: Wi-Fi required for the first download; cached recognition works offline.
     * auto=false: explicit user press permits metered mobile connectivity.
     * Only one job is accepted at a time to prevent repeated large downloads.
     */
    public void classify(Bitmap image,boolean auto,Callback callback){
        if(closed||image==null)return;
        if(auto&&!autoEnabled())return;
        if(!busy.compareAndSet(false,true))return;
        worker.execute(()->{
            try{
                if(!ready) {
                    if(!verifyExisting()){
                        if(auto&&!wifiConnected()){
                            notifyStatus(callback,"Modello aggiuntivo non installato. "+
                                "Collega il Wi-Fi per il download automatico oppure premi RICONOSCIMENTO EXTRA.");
                            return;
                        }
                        if(auto&&System.currentTimeMillis()<retryAfter){
                            notifyStatus(callback,"Download AI già ritentato di recente; "+
                                "riproverò dopo una nuova analisi.");
                            return;
                        }
                        notifyStatus(callback,"ML Kit non ha riconosciuto il soggetto: "+
                            "scarico il modello ufficiale MobileNetV2, solo dati AI (nessuna foto inviata).");
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
            extractModel(archive,tmpModel);
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
            if(expected>limit)throw new IOException("Pacchetto supera il limite previsto");
            try(InputStream in=new BufferedInputStream(con.getInputStream());
                OutputStream out=new BufferedOutputStream(new FileOutputStream(target))){
                long count=0,next=2L*1024*1024;
                byte[] buf=new byte[16384];int n;
                while((n=in.read(buf))!=-1){
                    if(closed)throw new IOException("Download interrotto");
                    count+=n;
                    if(count>limit)throw new IOException("Pacchetto più grande del previsto");
                    out.write(buf,0,n);
                    if(count>=next){
                        notifyStatus(cb,"Download "+what+": "+(count/1024/1024)+" MB");
                        next+=2L*1024*1024;
                    }
                }
            }
        }finally{con.disconnect();}
    }
    /** Minimal bounded tar.gz extractor. Ignores all archive paths except the exact TFLite filename. */
    private static void extractModel(File archive,File output)throws IOException{
        try(InputStream in=new GZIPInputStream(new BufferedInputStream(new FileInputStream(archive)))){
            byte[] header=new byte[512];long scanned=0;
            while(true){
                int got=readBlock(in,header);
                if(got==0)break;
                if(got!=512)throw new IOException("Archivio tar troncato");
                scanned+=512;
                boolean zero=true;for(byte c:header)if(c!=0){zero=false;break;}
                if(zero)break;
                String name=tarName(header);
                long size=tarSize(header);
                if(size<0||size>MAX_MODEL_BYTES||scanned>80L*1024*1024)
                    throw new IOException("Voce archivio fuori limiti");
                boolean modelEntry=name.equals(MODEL_ENTRY)||name.endsWith("/"+MODEL_ENTRY);
                if(modelEntry){
                    if(size<2_000_000)throw new IOException("Modello troppo piccolo");
                    try(OutputStream out=new BufferedOutputStream(new FileOutputStream(output))){
                        copyExactly(in,out,size);
                    }
                }else skipExactly(in,size);
                long padding=(512-size%512)%512;
                skipExactly(in,padding);
                scanned+=size+padding;
                if(modelEntry)return;
            }
        }
        throw new IOException("File TFLite non presente nell'archivio ufficiale");
    }
    private static int readBlock(InputStream in,byte[] block)throws IOException {
        int offset=0;
        while(offset<block.length){
            int n=in.read(block,offset,block.length-offset);
            if(n<0)return offset;
            offset+=n;
        }
        return offset;
    }
    private static String tarName(byte[] hdr){
        int len=0;while(len<100&&hdr[len]!=0)len++;
        return new String(hdr,0,len,StandardCharsets.US_ASCII);
    }
    private static long tarSize(byte[] hdr)throws IOException{
        String size=new String(hdr,124,12,StandardCharsets.US_ASCII).replace("\u0000","").trim();
        try{return Long.parseLong(size,8);}catch(Exception e){
            throw new IOException("Dimensione voce tar non valida");
        }
    }
    private static void copyExactly(InputStream in,OutputStream out,long length)throws IOException {
        byte[] buf=new byte[16384];long left=length;
        while(left>0){
            int n=in.read(buf,0,(int)Math.min(buf.length,left));
            if(n<0)throw new IOException("Archivio troncato");
            out.write(buf,0,n);left-=n;
        }
    }
    private static void skipExactly(InputStream in,long length)throws IOException{
        byte[] buf=new byte[16384];long left=length;
        while(left>0){
            int n=in.read(buf,0,(int)Math.min(buf.length,left));
            if(n<0)throw new IOException("Archivio troncato");
            left-=n;
        }
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
