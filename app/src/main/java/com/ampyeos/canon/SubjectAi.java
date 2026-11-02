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
                    message=composition("Ritratto ("+faceList.size()+" volti)",r,input.getWidth(),input.getHeight());
                }else if(objList!=null && !objList.isEmpty()){
                    DetectedObject o=objList.get(0);
                    String label=o.getLabels().isEmpty()?"Soggetto":o.getLabels().get(0).getText();
                    message=composition(label,o.getBoundingBox(),input.getWidth(),input.getHeight());
                }else message="AI: nessun soggetto distinto rilevato. Cerca un punto focale chiaro.";
            }catch(Exception e){message="AI: elaborazione incompleta";}
            finally{finish.run();}
            callback.onResult(message);
        });
    }

    private static String composition(String type,Rect box,int w,int h) {
        double x=box.exactCenterX()/Math.max(1,w);
        double y=box.exactCenterY()/Math.max(1,h);
        double deltaX=Math.min(Math.abs(x-1.0/3),Math.abs(x-2.0/3));
        double deltaY=Math.min(Math.abs(y-1.0/3),Math.abs(y-2.0/3));
        if(deltaX<0.10 && deltaY<0.10) return "AI · "+type+": soggetto vicino all'incrocio dei terzi.";
        if(x>0.42&&x<0.58&&y>0.36&&y<0.64)
            return "AI · "+type+": centrato. Prova un'inquadratura decentrata oppure valorizza la simmetria.";
        return "AI · "+type+": prova a posizionarlo su uno dei punti della griglia dei terzi.";
    }

    /** Report from actual on-device ML Kit inference, not edge saliency. */
    public static final class Observation {
        public final RectF normalizedBox;
        public final String description;
        Observation(RectF b,String s){normalizedBox=b;description=s;}
    }
    public interface ObservationCallback {void onResult(Observation observation);}
    public void observe(Bitmap bitmap,ObservationCallback callback){
        try {
            InputImage input=InputImage.fromBitmap(bitmap,0);
            Task<List<DetectedObject>> objectsTask=stillObjects.process(input);
            Task<List<Face>> facesTask=faces.process(input);
            Tasks.whenAllComplete(objectsTask,facesTask).addOnCompleteListener(t->{
                String name="Nessun volto o oggetto riconosciuto";
                Rect found=null;
                try{
                    List<Face> facesFound=facesTask.isSuccessful()?facesTask.getResult():null;
                    List<DetectedObject> objectsFound=objectsTask.isSuccessful()?objectsTask.getResult():null;
                    if(facesFound!=null&&!facesFound.isEmpty()){
                        found=facesFound.get(0).getBoundingBox();
                        name=facesFound.size()==1?"Volto rilevato":facesFound.size()+" volti rilevati";
                    }else if(objectsFound!=null&&!objectsFound.isEmpty()){
                        DetectedObject o=objectsFound.get(0);
                        found=o.getBoundingBox();
                        name=o.getLabels().isEmpty()?"Oggetto rilevato":
                            "Oggetto: "+o.getLabels().get(0).getText();
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
                callback.onResult(new Observation(normalized,name));
            });
        }catch(Exception e){callback.onResult(new Observation(null,
            "Analisi semantica non disponibile: "+e.getMessage()));}
    }
    public void close(){objects.close();stillObjects.close();faces.close();}
}
