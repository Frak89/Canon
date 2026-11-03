package com.ampyeos.canon;

import android.graphics.Bitmap;

/**
 * JNI bridge into llama.cpp + libmtmd, in the AmpyEOS app process.
 * NO socket, HTTP API, Termux, external service, or photo upload.
 *
 * Models are persisted in public Download/AmpyEOS/Qwen3-VL and opened through
 * scoped-storage descriptors, retained until the synchronous native call ends.
 */
public final class NativeQwenVision {
    private static final boolean LOADED;
    private static final String LOAD_ERROR;
    static {
        boolean ok=false;
        String error="";
        try {
            System.loadLibrary("ampyeos-vision");
            ok=true;
        }catch(Throwable e){
            error=e.getMessage();
        }
        LOADED=ok;
        LOAD_ERROR=error;
    }
    private NativeQwenVision(){}
    public static boolean available(){return LOADED;}
    public static String reason(){return LOADED?"Motore nativo disponibile":LOAD_ERROR;}

    /** Progress stages come from the native engine itself, not an estimated percentage. */
    public interface ProgressListener {
        void onProgress(String stage,int generatedTokens,int tokenLimit);
    }
    private static native String process(String modelPath,String projectorPath,
        int[] pixels,int width,int height,String prompt,int maximumTokens,
        int maxImageTokens,int budgetSeconds,ProgressListener progress);
    public static native void cancel();

    public static String analyze(Bitmap image,QwenModelManager models,String prompt)
            throws Exception {
        return analyze(image,models,prompt,null);
    }
    public static String analyze(Bitmap image,QwenModelManager models,String prompt,
                                 ProgressListener progress) throws Exception {
        return analyze(image,models,prompt,progress,320,480,120);
    }
    /** Adjustable fidelity: quick=448px/480 tokens, deep=640px/1100 tokens. */
    public static String analyze(Bitmap image,QwenModelManager models,String prompt,
                                 ProgressListener progress,int maxEdge,int tokens)
            throws Exception {
        return analyze(image,models,prompt,progress,maxEdge,tokens,120);
    }
    /**
     * A CPU budget prevents single-image analysis from occupying the
     * Pixel indefinitely when native vision throughput is too low.
     */
    public static String analyze(Bitmap image,QwenModelManager models,String prompt,
                                 ProgressListener progress,int maxEdge,int tokens,
                                 int budgetSeconds)throws Exception {
        if(maxEdge<196||maxEdge>768||tokens<100||tokens>1200 ||
            budgetSeconds<20||budgetSeconds>300)
            throw new IllegalArgumentException("Profilo Qwen non valido");
        if(!LOADED)throw new Exception("Motore nativo llama.cpp non caricato: "+LOAD_ERROR);
        if(!models.isReady())throw new Exception("Scarica prima entrambi i file Qwen3-VL");
        // Smaller preview limits CPU and multimodal prefill cost on Tensor G2.
        Bitmap small=image;
        int longest=Math.max(image.getWidth(),image.getHeight());
        if(longest>maxEdge){
            float scale=maxEdge/(float)longest;
            small=Bitmap.createScaledBitmap(image,Math.max(1,Math.round(image.getWidth()*scale)),
                Math.max(1,Math.round(image.getHeight()*scale)),true);
        }
        try {
            int w=small.getWidth(),h=small.getHeight();
            int[] pixels=new int[w*h];
            small.getPixels(pixels,0,w,0,0,w,h);
            // Scoped-storage descriptors stay open throughout native loading,
            // allowing llama.cpp to mmap the actual GGUF in public Download.
            // No second 1.55GB private copy is required.
            try(QwenPersistentStore.OpenModels open=models.openForInference()){
                // Restrict visual tokens *inside* the image preprocessor, too.
                int imageTokens=Math.min(192,Math.max(32,
                    (int)Math.ceil((double)w*h/(28.0*28.0))));
                return process(open.languagePath(),open.projectorPath(),
                    pixels,w,h,prompt,tokens,imageTokens,budgetSeconds,progress);
            }
        }finally{
            if(small!=image)small.recycle();
        }
    }
}
