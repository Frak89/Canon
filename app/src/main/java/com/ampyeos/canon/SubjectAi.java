package com.ampyeos.canon;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.RectF;
import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import java.util.List;
import java.util.Locale;

/** Real on-device ML Kit object + face recognition. Model artifacts bundled; no cloud calls. */
public final class SubjectAi {
    public interface Callback { void onResult(String result); }
    private final ObjectDetector objects;
    private final ObjectDetector stillObjects;
    private final FaceDetector faces;

    public SubjectAi() {
        objects=ObjectDetection.getClient(new ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
            .enableMultipleObjects().enableClassification().build());
        stillObjects=ObjectDetection.getClient(new ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects().enableClassification().build());
        faces=FaceDetection.getClient(new FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .enableTracking().build());
    }

    public void analyze(ImageProxy proxy,Callback callback) {
        if(proxy.getImage()==null) {
            proxy.close();callback.onResult("AI: frame non disponibile");return;
        }
        try {
            InputImage input=InputImage.fromMediaImage(proxy.getImage(),proxy.getImageInfo().getRotationDegrees());
            process(input,()->proxy.close(),callback);
        }catch(Exception e){proxy.close();callback.onResult("AI: "+e.getMessage());}
    }

    public void analyze(Bitmap bitmap,Callback callback) {
        try {process(InputImage.fromBitmap(bitmap,0),()->{},callback);}
        catch(Exception e){callback.onResult("AI: "+e.getMessage());}
    }

    private void process(InputImage input,Runnable finish,Callback callback) {
        final Task<List<DetectedObject>> ot;
        final Task<List<Face>> ft;
        try {
            ot=objects.process(input);
            ft=faces.process(input);
        }catch(Exception e){finish.run();callback.onResult("AI: "+e.getMessage());return;}
        Tasks.whenAllComplete(ot,ft).addOnCompleteListener(task->{
            String message;
            try {
                List<Face> faceList=ft.isSuccessful()?ft.getResult():null;
                List<DetectedObject> objList=ot.isSuccessful()?ot.getResult():null;
                if(faceList!=null && !faceList.isEmpty()) {
                    Rect r=faceList.get(0).getBoundingBox();
                    String kind=faceList.size()==1?"un volto":faceList.size()+" volti (primo evidenziato)";
                    message=describe(kind,r,input.getWidth(),input.getHeight()).shortText;
                }else if(objList!=null && !objList.isEmpty()){
                    DetectedObject o=objList.get(0);
                    String label=o.getLabels().isEmpty()?
                        "un oggetto, senza categoria affidabile":
                        labelIt(o.getLabels().get(0).getText());
                    message=describe(label,o.getBoundingBox(),
                        input.getWidth(),input.getHeight()).shortText;
                }else message="AI: nessun volto o oggetto generico riconosciuto; "+
                    "non posso identificare con certezza il soggetto della scena.";
            }catch(Exception e){message="AI: elaborazione incompleta";}
            finally{finish.run();}
            callback.onResult(message);
        });
    }


    private static String labelIt(String english) {
        if(english==null||english.isEmpty())
            return "un oggetto, senza categoria affidabile";
        switch(english.toLowerCase(Locale.ROOT)) {
            case "food": return "un oggetto della categoria cibo";
            case "plant": return "un oggetto della categoria pianta";
            case "place": return "una scena o luogo (categoria generica)";
            case "fashion good": return "un oggetto della categoria abbigliamento/accessori";
            case "home good": return "un oggetto della categoria oggetti per la casa";
            default: return "un oggetto, categoria "+english;
        }
    }
    private static final class Description {
        final String shortText, explanation;
        Description(String shortText,String explanation){
            this.shortText=shortText;this.explanation=explanation;
        }
    }
    private static Description describe(String kind,Rect box,int width,int height) {
        if(box==null)
            return new Description("Soggetto non localizzato",
                "Non riesco a individuare una regione attendibile da evidenziare.");
        float w=Math.max(1,width),h=Math.max(1,height);
        float x=Math.max(0,Math.min(1,box.exactCenterX()/w));
        float y=Math.max(0,Math.min(1,box.exactCenterY()/h));
        float area=Math.max(0,Math.min(1,box.width()/w*box.height()/h));
        String horizontal=x<.38f?"nella parte sinistra":x>.62f?
            "nella parte destra":"vicino al centro orizzontale";
        String vertical=y<.35f?"in alto":y>.65f?"in basso":"a metà altezza";
        double minDistance=10;
        for(float tx:new float[]{1f/3,2f/3})for(float ty:new float[]{1f/3,2f/3})
            minDistance=Math.min(minDistance,Math.hypot(x-tx,y-ty));
        String compose=minDistance<.16?
            "La regione rilevata si trova vicino a un'intersezione dei terzi. "+
            "Questa disposizione può funzionare, ma non è una regola obbligatoria.":
            "Il soggetto non è vicino alle intersezioni dei terzi. "+
            (x>.4f&&x<.6f?"Una composizione centrale può valorizzare la simmetria.":
                "Prova un'inquadratura alternativa, lasciando spazio nella direzione dello sguardo o del movimento.");
        boolean edge=box.left<w*.04f||box.top<h*.04f||
            box.right>w*.96f||box.bottom>h*.96f;
        String border=edge?
            "Una parte della regione è molto vicina al bordo: verifica che il taglio sia intenzionale. ":
            "";
        String percent=area<.10?"occupa una porzione relativamente piccola":
            area>.45?"occupa una parte considerevole":"ha una dimensione intermedia";
        String summary="AI · Rilevato "+kind+", "+horizontal+", "+vertical+".";
        String details="Il rilevatore locale ha individuato "+kind+", "+horizontal+", "+vertical+
            "; la regione "+percent+" dell'immagine. "+border+compose+
            " La categoria rilevata è generica: non posso descrivere dettagli "+
            "specifici, identità o significato della scena se il modello non li ha riconosciuti.";
        return new Description(summary,details);
    }

    /** Report from actual on-device ML Kit inference, not edge saliency. */
    public static final class Observation {
        public final RectF normalizedBox;
        public final String description;
        public final String explanation;
        Observation(RectF b,String description,String explanation){
            normalizedBox=b;this.description=description;this.explanation=explanation;
        }
    }
    public interface ObservationCallback {void onResult(Observation observation);}
    public void observe(Bitmap bitmap,ObservationCallback callback){
        try {
            InputImage input=InputImage.fromBitmap(bitmap,0);
            Task<List<DetectedObject>> objectsTask=stillObjects.process(input);
            Task<List<Face>> facesTask=faces.process(input);
            Tasks.whenAllComplete(objectsTask,facesTask).addOnCompleteListener(t->{
                String name="Nessun volto o oggetto generico riconosciuto";
                Rect found=null;
                try{
                    List<Face> facesFound=facesTask.isSuccessful()?facesTask.getResult():null;
                    List<DetectedObject> objectsFound=objectsTask.isSuccessful()?objectsTask.getResult():null;
                    if(facesFound!=null&&!facesFound.isEmpty()){
                        found=facesFound.get(0).getBoundingBox();
                        name=facesFound.size()==1?"un volto":facesFound.size()+" volti (primo evidenziato)";
                    }else if(objectsFound!=null&&!objectsFound.isEmpty()){
                        DetectedObject o=objectsFound.get(0);
                        found=o.getBoundingBox();
                        name=o.getLabels().isEmpty()?"un oggetto, categoria sconosciuta":
                            labelIt(o.getLabels().get(0).getText());
                    }
                }catch(Exception ignored){}
                RectF normalized=null;
                if(found!=null){
                    normalized=new RectF(
                        Math.max(0,found.left/(float)bitmap.getWidth()),
                        Math.max(0,found.top/(float)bitmap.getHeight()),
                        Math.min(1,found.right/(float)bitmap.getWidth()),
                        Math.min(1,found.bottom/(float)bitmap.getHeight()));
                }
                if(found!=null) {
                    Description info=describe(name,found,bitmap.getWidth(),bitmap.getHeight());
                    callback.onResult(new Observation(normalized,info.shortText,info.explanation));
                }else {
                    callback.onResult(new Observation(null,name,
                        "Non ho rilevato con sufficiente certezza un volto o un oggetto "+
                        "delle categorie supportate. Posso comunque misurare luce, "+
                        "ombre, colore e contorni, ma non inventerò un soggetto."));
                }
            });
        }catch(Exception e){callback.onResult(new Observation(null,
            "Riconoscimento soggetti non disponibile",
            "Errore del rilevatore locale: "+e.getMessage()));}
    }
    public void close(){objects.close();stillObjects.close();faces.close();}
}
