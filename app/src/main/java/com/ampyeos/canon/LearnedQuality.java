package com.ampyeos.canon;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.OpenableColumns;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Locale;

/**
 * Learned, no-reference image QUALITY using an actual pretrained MUSIQ model
 * under ONNX Runtime. This score predicts perceived technical quality; it
 * cannot prove artistic meaning or scene composition. This is intentionally
 * kept separate from the Qwen-VL photographic review.
 *
 * Model: 86Cao/IQA-ONNX-Models (MUSIQ single-scale export, 224x224).
 * Model and external data must be explicitly imported through SAF.
 */
public final class LearnedQuality {
    public static final String MODEL="musiq_model.onnx";
    public static final String DATA="musiq_model.onnx.data";
    private static final int SIZE=224;
    private static final long MAX_FILE=256L*1024*1024;
    public static final class Result {
        public final float score;
        Result(float score){this.score=score;}
        public String describe() {
            return String.format(Locale.ITALIAN,
                "MUSIQ · Qualità percettiva stimata %.1f/100. "+
                "Punteggio appreso da fotografie reali, non derivato dall'istogramma. "+
                "NON misura l'intenzione, l'originalità o il significato della fotografia.",score);
        }
    }
    private LearnedQuality(){}
    private static File storage(Context ctx){
        return new File(ctx.getFilesDir(),"ampyeos_musiq");
    }
    public static boolean hasFiles(Context ctx){
        File base=storage(ctx);
        return new File(base,MODEL).isFile()&&new File(base,DATA).isFile();
    }
    public static String status(Context ctx){
        File base=storage(ctx);
        return (new File(base,MODEL).isFile()?"ONNX presente":"ONNX mancante")+
            " · "+(new File(base,DATA).isFile()?"dati presenti":"dati mancanti");
    }
    /** Copy exactly the filename indicated in official model documentation. */
    public static String importFile(Context ctx,Uri document)throws Exception{
        String name=null;
        try(Cursor c=ctx.getContentResolver().query(document,
            new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(c!=null&&c.moveToFirst())name=c.getString(0);
        }
        if(!MODEL.equals(name)&&!DATA.equals(name))
            throw new IllegalArgumentException("Scegli solo "+MODEL+" oppure "+DATA+
                " da 86Cao/IQA-ONNX-Models.");
        File base=storage(ctx);
        if(!base.exists()&&!base.mkdirs())throw new IllegalStateException("Cartella IQA non disponibile");
        File tmp=new File(base,name+".partial");
        long bytes=0;
        try(InputStream in=ctx.getContentResolver().openInputStream(document);
            OutputStream out=new BufferedOutputStream(new FileOutputStream(tmp),32*1024)){
            if(in==null)throw new IllegalStateException("Il documento selezionato non è leggibile");
            byte[] buffer=new byte[64*1024];int n;
            while((n=in.read(buffer))!=-1){
                bytes+=n;
                if(bytes>MAX_FILE)throw new IllegalArgumentException("File oltre 256 MiB: importazione annullata");
                out.write(buffer,0,n);
            }
        }catch(Exception e){tmp.delete();throw e;}
        if(bytes<4096){tmp.delete();throw new IllegalArgumentException("File incompleto");}
        File dst=new File(base,name);
        if(dst.exists()&&!dst.delete()){tmp.delete();throw new IllegalStateException("Modello già presente ma non sostituibile");}
        if(!tmp.renameTo(dst))throw new IllegalStateException("Salvataggio modello IQA fallito");
        return "Importato "+name+" ("+(bytes/1024/1024)+" MiB). "+status(ctx);
    }

    /**
     * Used on a worker thread. Loaded only on demand to avoid keeping the
     * learned network's memory resident while live camera processing runs.
     */
    public static Result assess(Context ctx,Bitmap original)throws Exception{
        if(!hasFiles(ctx))
            throw new IllegalStateException("MUSIQ non completo. Importa "+
                MODEL+" e "+DATA+" dalla cartella Download.");
        final File base=storage(ctx);
        OrtEnvironment environment=OrtEnvironment.getEnvironment();
        OrtSession.SessionOptions options=new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(2);
        try(OrtSession session=environment.createSession(new File(base,MODEL).getAbsolutePath(),options)){
            if(session.getInputNames().size()!=1)
                throw new IllegalStateException("Input MUSIQ inaspettati");
            String key=session.getInputNames().iterator().next();
            float[][][][] image=preprocess(original);
            try(OnnxTensor input=OnnxTensor.createTensor(environment,image);
                OrtSession.Result results=session.run(Collections.singletonMap(key,input))){
                if(results.size()<1)throw new IllegalStateException("MUSIQ non ha prodotto punteggi");
                Object out=results.get(0).getValue();
                Float candidate=scalar(out);
                if(candidate==null||Float.isNaN(candidate)||Float.isInfinite(candidate)||
                    candidate<0||candidate>100)
                    throw new IllegalStateException("MUSIQ ha prodotto un risultato non valido");
                return new Result(candidate);
            }
        }finally{options.close();}
    }
    private static Float scalar(Object value){
        if(value instanceof Number)return ((Number)value).floatValue();
        if(value instanceof float[]) {
            float[] a=(float[])value;return a.length>0?a[0]:null;
        }
        if(value instanceof float[][]){
            float[][] a=(float[][])value;
            return a.length>0&&a[0].length>0?a[0][0]:null;
        }
        if(value instanceof double[]) {
            double[] a=(double[])value;return a.length>0?(float)a[0]:null;
        }
        if(value instanceof double[][]){
            double[][] a=(double[][])value;
            return a.length>0&&a[0].length>0?(float)a[0][0]:null;
        }
        return null;
    }
    /** PIL-style RGB resize to 224 and Inception normalization (x /127.5 -1). */
    private static float[][][][] preprocess(Bitmap bitmap){
        Bitmap sample=Bitmap.createScaledBitmap(bitmap,SIZE,SIZE,true);
        int[] pixels=new int[SIZE*SIZE];
        sample.getPixels(pixels,0,SIZE,0,0,SIZE,SIZE);
        if(sample!=bitmap)sample.recycle();
        float[][][][] nchw=new float[1][3][SIZE][SIZE];
        for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){
            int p=pixels[y*SIZE+x];
            nchw[0][0][y][x]=((p>>16&255)/127.5f)-1;
            nchw[0][1][y][x]=((p>>8&255)/127.5f)-1;
            nchw[0][2][y][x]=((p&255)/127.5f)-1;
        }
        return nchw;
    }
}
