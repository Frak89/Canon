package com.ampyeos.canon;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RenderEffect;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.view.ViewGroup;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import java.util.Locale;
import java.util.Date;
import java.text.SimpleDateFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** AmpyEOS Rev0.1: on-device photo coach + experimental Canon CCAPI live view. */
public final class MainActivity extends ComponentActivity implements SensorEventListener {
    private static final int CYAN=Color.rgb(75,240,228), BG=Color.rgb(9,15,25);
    private final ExecutorService analysisExecutor=Executors.newSingleThreadExecutor();
    private final ExecutorService networkExecutor=Executors.newSingleThreadExecutor();
    private final ExecutorService commandExecutor=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final AtomicBoolean canonLoop=new AtomicBoolean(false);
    private final AtomicBoolean aiBusy=new AtomicBoolean(false);
    private SubjectAi subjectAi;
    private ExtraRecognition extraRecognition;
    private Button extraToggle;
    private TextView extraStatus;
    private long lastExtraRequest=0;
    private BleDiscovery bluetooth=new BleDiscovery();
    private ComposerOverlay composer;
    private GuideOverlay guide;
    private HistogramView histogram;
    private ImageCapture capture;
    private SensorManager sensors;
    private Sensor accelerometer;
    private TextToSpeech speech;
    private boolean autoCompose=true,autoLook=true,voice=false;
    private long lastVoice=0;
    private ColorLooks.Look liveLook=ColorLooks.Look.NATURALE;
    private Spinner profiles;
    private Button pixelShutter;
    private TextView lookAdvice,lesson,diagnostics;
    private String currentSubjectSpoken="Non ho ancora riconosciuto un soggetto.";
    private int shots=0;
    private int frameCount=0;
    private PreviewView pixelView;
    private ImageView canonView;
    private TextView state, metrics, advice, aiText, score, modeLabel;
    private Button shutter;
    private Button recordButton;
    private Button composerToggle,lookToggle,voiceToggle;
    private ProcessCameraProvider cameraProvider;
    private CanonCcapi canon;
    private String currentMode="PIXEL";
    private long lastUi=0;
    private volatile boolean closing=false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        subjectAi=new SubjectAi();
        extraRecognition=new ExtraRecognition(this);
        sensors=(SensorManager)getSystemService(SENSOR_SERVICE);
        accelerometer=sensors==null?null:sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        voice=getPreferences(MODE_PRIVATE).getBoolean("voice",false);
        autoCompose=getPreferences(MODE_PRIVATE).getBoolean("auto_compose",true);
        autoLook=getPreferences(MODE_PRIVATE).getBoolean("auto_look",true);
        speech=new TextToSpeech(this,status->{if(status==TextToSpeech.SUCCESS && speech!=null)speech.setLanguage(Locale.ITALIAN);});
        createUi();
        startPixel();
    }
    private TextView text(String value,int size,int color) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(dp(8),dp(7),dp(8),dp(7));return t;
    }
    private int dp(int d){return (int)(d*getResources().getDisplayMetrics().density+.5f);}
    private Button button(String label,LinearLayout row,View.OnClickListener run) {
        Button b=AmpyButtons.action(this,label);
        b.setTextSize(11);b.setOnClickListener(run);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(145),dp(52));
        lp.setMargins(dp(3),dp(2),dp(3),dp(2));row.addView(b,lp);return b;
    }
    private Button toggleButton(String label,boolean checked,LinearLayout row,View.OnClickListener run) {
        Button b=AmpyButtons.toggle(this,label,checked);
        b.setTextSize(11);b.setOnClickListener(run);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(159),dp(52));
        lp.setMargins(dp(3),dp(2),dp(3),dp(2));row.addView(b,lp);return b;
    }
    private void createUi() {
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(BG);setContentView(layout);
        modeLabel=text("AMPY▲EOS · VIRTUAL PHOTOGRAPHY STUDIO",18,CYAN);
        modeLabel.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        layout.addView(modeLabel);

        android.widget.HorizontalScrollView navScroll=new android.widget.HorizontalScrollView(this);
        navScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout nav=new LinearLayout(this);
        button("PIXEL LIVE",nav,v->startPixel());
        button("CANON WIFI",nav,v->showCanonConnect());
        button("CERCA WIFI",nav,v->discoverCanon());
        button("BLE SCAN",nav,v->scanBle());
        button("AMPYLINK",nav,v->openAmpyLink());
        navScroll.addView(nav);layout.addView(navScroll);

        FrameLayout view=new FrameLayout(this);
        layout.addView(view,new LinearLayout.LayoutParams(-1,0,1));
        pixelView=new PreviewView(this);
        pixelView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        pixelView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        view.addView(pixelView,new FrameLayout.LayoutParams(-1,-1));
        canonView=new ImageView(this);canonView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        canonView.setBackgroundColor(Color.BLACK);canonView.setVisibility(View.GONE);
        view.addView(canonView,new FrameLayout.LayoutParams(-1,-1));
        guide=new GuideOverlay(this);
        view.addView(guide,new FrameLayout.LayoutParams(-1,-1));
        composer=new ComposerOverlay(this);
        composer.setActive(autoCompose);
        view.addView(composer,new FrameLayout.LayoutParams(-1,-1));

        ScrollView panelScroll=new ScrollView(this);panelScroll.setFillViewport(false);
        layout.addView(panelScroll,new LinearLayout.LayoutParams(-1,dp(326)));
        LinearLayout panel=new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panelScroll.addView(panel);
        LinearLayout profileRow=new LinearLayout(this);
        profileRow.setGravity(Gravity.CENTER_VERTICAL);
        profileRow.addView(text("STILE",12,CYAN));
        profiles=new Spinner(this);
        profiles.setAdapter(new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item,PhotoCoach.Profile.labels()));
        profiles.setSelection(Math.min(PhotoCoach.Profile.values().length-1,
            getPreferences(MODE_PRIVATE).getInt("profile",0)));
        profiles.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,View v,int position,long id){
                getPreferences(MODE_PRIVATE).edit().putInt("profile",position).apply();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
        });
        profileRow.addView(profiles,new LinearLayout.LayoutParams(0,dp(45),1));
        panel.addView(profileRow);
        score=text("Analisi in avvio…",16,CYAN);
        metrics=text("Attendi fotogrammi",12,0xffc7d4df);
        advice=text("Analizzo luce, dettaglio e inquadratura offline.",13,Color.WHITE);
        aiText=text("AI locale: ricerca volti e oggetti…",12,0xff88e4d2);
        lookAdvice=text("LOOK: analisi colore in avvio…",13,0xffffc98b);
        lesson=text("MAESTRO: prepara il soggetto, cerca luce e osserva la griglia.",12,Color.WHITE);
        panel.addView(score);panel.addView(metrics);panel.addView(advice);
        panel.addView(aiText);panel.addView(lookAdvice);panel.addView(lesson);
        histogram=new HistogramView(this);
        panel.addView(histogram,new LinearLayout.LayoutParams(-1,dp(53)));
        android.widget.HorizontalScrollView controls=new android.widget.HorizontalScrollView(this);
        LinearLayout actions=new LinearLayout(this);
        shutter=button("SCATTA CANON",actions,v->sendShutter());
        shutter.setEnabled(false);
        pixelShutter=button("SCATTA PIXEL",actions,v->shootPixel());
        recordButton=button("REC CANON",actions,v->toggleRecord());
        recordButton.setEnabled(false);
        button("EDIT / AULA",actions,v->openEditor());
        button("CLASSIFICA FOTO",actions,v->rankPhotos());
        button("RICONOSCI EXTRA",actions,v->tryExtraLive(false,null));
        extraToggle=toggleButton("DOWNLOAD EXTRA",
            extraRecognition.autoEnabled(),actions,v->{
                extraRecognition.setAutoEnabled(!extraRecognition.autoEnabled());
                AmpyButtons.setToggle(extraToggle,extraRecognition.autoEnabled());
                log(extraRecognition.autoEnabled()?
                    "Download AI Wi-Fi attivato":"Download automatico AI disattivato");
            });
        composerToggle=toggleButton("AUTO COMPOSER",autoCompose,actions,v->toggleComposer());
        lookToggle=toggleButton("AUTO LOOK",autoLook,actions,v->toggleAutoLook());
        voiceToggle=toggleButton("VOCE MAESTRO",voice,actions,v->toggleVoice());
        button("DIAGNOSTICA",actions,v->showDiagnostics());
        controls.addView(actions);panel.addView(controls);
        diagnostics=text("CANON: non collegata",11,0xffabc7d4);
        state=text("PIXEL · nessuna immagine inviata al cloud",11,0xff9badbd);
        panel.addView(diagnostics);panel.addView(state);
        extraStatus=text("AI EXTRA · "+ExtraRecognition.modelName()+
            " · "+(extraRecognition.isCached()?"cache presente":"modello scaricabile su Wi-Fi"),11,0xff9de2cf);
        panel.addView(extraStatus);
        panel.addView(text("COLORI · MENTA CHIARO = ON · SCURO = OFF o NON DISPONIBILE · BLU = AZIONE",
            10,0xffb8d5e3));
    }

    private void update(PhotoAnalyzer.Result result) {
        long now=android.os.SystemClock.uptimeMillis();
        if(now-lastUi<420)return;
        lastUi=now;
        score.setText("QUALITÀ TECNICA STIMATA "+result.score+"/100  ·  "+Math.max(1,Math.round(result.score/10f))+"/10");
        metrics.setText(result.summary());
        advice.setText(result.advice);
        histogram.setHistogram(result.histogram);
        PhotoCoach.Profile profile=PhotoCoach.Profile.values()[Math.max(0,Math.min(
            profiles.getSelectedItemPosition(),PhotoCoach.Profile.values().length-1))];
        String teacher=PhotoCoach.suggest(result,profile);
        String lecture=currentSubjectSpoken+" "+teacher;
        lesson.setText("MAESTRO · "+lecture);
        if(autoCompose && ++frameCount%4==0)composer.setTarget(result.focusX,result.focusY);
        if(voice && now-lastVoice>11000 && speech!=null) {
            lastVoice=now;speech.speak(lecture,TextToSpeech.QUEUE_FLUSH,null,"ampyeos-coach");
        }
    }
    private void log(String message) {ui.post(()-> {if(!closing)state.setText(message);});}

    private void startPixel() {
        canonLoop.set(false);
        shutter.setEnabled(false);
        recordButton.setEnabled(false);
        pixelShutter.setEnabled(true);
        currentMode="PIXEL";
        currentSubjectSpoken="Non ho ancora riconosciuto un soggetto.";
        canonView.setVisibility(View.GONE);pixelView.setVisibility(View.VISIBLE);
        modeLabel.setText("AmpyEOS / PIXEL LIVE");
        log("PIXEL LIVE · elaborazione offline");
        networkExecutor.execute(()->{if(canon!=null){canon.disconnect();canon=null;}});
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA},101);return;
        }
        openCamera();
    }
    private void openCamera() {
        ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{
            if(closing||!"PIXEL".equals(currentMode))return;
            try {
                cameraProvider=future.get();cameraProvider.unbindAll();
                Preview preview=new Preview.Builder().build();
                preview.setSurfaceProvider(pixelView.getSurfaceProvider());
                capture=new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
                ImageAnalysis analyzer=new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
                analyzer.setAnalyzer(analysisExecutor,frame->{
                    boolean handedOff=false;
                    try {
                        PhotoAnalyzer.Result r=PhotoAnalyzer.fromCamera(frame);
                        ColorLooks.Recommendation look=null;
                        if(++frameCount%12==0)look=ColorLooks.recommend(frame);
                        final ColorLooks.Recommendation recommendation=look;
                        ui.post(()->{
                            if(!closing && "PIXEL".equals(currentMode)) {
                                update(r);
                                if(recommendation!=null)applyRecommendation(recommendation);
                            }
                        });
                        if(subjectAi!=null && (++frameCount%5==0) && aiBusy.compareAndSet(false,true)) {
                            handedOff=true;
                            subjectAi.analyze(frame,message->{
                                aiBusy.set(false);
                                ui.post(()->{if(!closing && "PIXEL".equals(currentMode)){
                                    aiText.setText(message);currentSubjectSpoken=message;
                                    if(!message.contains("volto")&&!message.contains("volti"))
                                        tryExtraLive(true,null);
                                }});
                            });
                        }
                    }catch(Exception e){log("Analisi frame: "+e.getMessage());}
                    finally{if(!handedOff)frame.close();}
                });
                cameraProvider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,preview,capture,analyzer);
            }catch(Exception e){log("CameraX: "+e.getMessage());}
        },getMainExecutor());
    }

    private void showCanonConnect() {
        EditText field=new EditText(this);field.setSingleLine(true);
        field.setText(getPreferences(MODE_PRIVATE).getString("canon_host","192.168.1.2:8080"));
        field.setHint("IP:porta (es. 192.168.1.2:8080)");
        new AlertDialog.Builder(this).setTitle("Collega Canon · CCAPI")
            .setMessage("Attiva CCAPI sulla Canon e collega Pixel e fotocamera alla stessa rete.")
            .setView(field)
            .setNegativeButton("Annulla",null)
            .setPositiveButton("CONNETTI",(d,w)->connectCanon(field.getText().toString())).show();
    }

    private void connectCanon(String address) {
        if(address.trim().isEmpty()){log("Indirizzo Canon vuoto");return;}
        getPreferences(MODE_PRIVATE).edit().putString("canon_host",address.trim()).apply();
        currentMode="CANON";currentSubjectSpoken="Attendo i fotogrammi Canon.";
        canonLoop.set(false);shutter.setEnabled(false);
        recordButton.setEnabled(false);pixelShutter.setEnabled(false);
        if(cameraProvider!=null)cameraProvider.unbindAll();
        pixelView.setVisibility(View.GONE);canonView.setVisibility(View.VISIBLE);
        modeLabel.setText("AmpyEOS / CANON LIVE");
        log("Connessione CCAPI in corso a "+address+"…");
        networkExecutor.execute(()-> {
            if(canon!=null)canon.disconnect();
            CanonCcapi client=new CanonCcapi();canon=client;
            try{
                client.connect(address);
                log("Collegata: "+client.info+" · "+client.endpoint());
                ui.post(()->{if(!closing)diagnostics.setText("CANON · "+client.info+" · "+client.latency()+" ms");});
                canonLoop.set(true);
                ui.post(()->{if(!closing && "CANON".equals(currentMode)){
                    shutter.setEnabled(client.has("/shooting/control/shutterbutton"));
                    recordButton.setEnabled(client.has("/shooting/control/recbutton"));
                }});
                try { client.startLive(); }
                catch(Exception e) {log("CCAPI collegato, ma Live View non avviabile: "+e.getMessage());return;}
                int misses=0;
                while(canonLoop.get()&&!closing && "CANON".equals(currentMode)){
                    try{
                        Bitmap frame=client.frame();misses=0;
                        PhotoAnalyzer.Result m=PhotoAnalyzer.fromBitmap(frame);
                        ColorLooks.Recommendation look=(++frameCount%9==0)?ColorLooks.recommend(frame):null;
                        if(subjectAi!=null && (++frameCount%5==0) && aiBusy.compareAndSet(false,true)) {
                            subjectAi.analyze(frame,message->{
                                aiBusy.set(false);
                                ui.post(()->{if(!closing && "CANON".equals(currentMode)){
                                    aiText.setText(message);currentSubjectSpoken=message;
                                    if(!message.contains("volto")&&!message.contains("volti"))
                                        tryExtraLive(true,frame);
                                }});
                            });
                        }
                        ui.post(()->{
                            if(!closing&&"CANON".equals(currentMode)){
                                canonView.setImageBitmap(frame);update(m);
                                if(look!=null)applyRecommendation(look);
                            }
                            else frame.recycle();
                        });
                        Thread.sleep(200);
                    }catch(Exception e){
                        misses++;if(misses==1)log("Live View: "+e.getMessage());
                        if(misses>=3){log("Live View non disponibile: controlla compatibilità flip/JPEG, CCAPI e pairing.");break;}
                        Thread.sleep(600);
                    }
                }
            }catch(Exception e){log("Connessione Canon fallita: "+e.getMessage());}
            finally{canonLoop.set(false);client.disconnect();}
        });
    }

    private void sendShutter() {
        if(!"CANON".equals(currentMode)||canon==null)return;
        shutter.setEnabled(false);
        // Separate thread: do not queue behind a continuous Live View polling loop.
        new Thread(()->{
            try {canon.shoot();log("Comando SCATTA inviato");}
            catch(Exception e){log("SCATTA non riuscito: "+e.getMessage());}
            finally{ui.post(()->{if(!closing && "CANON".equals(currentMode))shutter.setEnabled(true);});}
        },"ampyeos-shutter").start();
    }

    private void discoverCanon() {
        log("Ricerca SSDP sulla rete Wi-Fi…");
        networkExecutor.execute(()->{
            try{
                String ips=CanonDiscovery.discover(this);
                if(ips.isEmpty()){log("Nessuna Canon rilevata via SSDP. Usa CANON LIVE e IP manuale.");return;}
                log("Possibili Canon: "+ips+" · verifica via CCAPI");
                String ip=ips.split(", ")[0];
                ui.post(()->{if(!closing)new AlertDialog.Builder(this).setTitle("Candidato Canon SSDP")
                    .setMessage(ip+" — provare CCAPI sulla porta 8080?")
                    .setPositiveButton("Connetti",(d,w)->connectCanon(ip+":8080"))
                    .setNegativeButton("Annulla",null).show();});
            }catch(Exception e){log("Discovery: "+e.getMessage());}
        });
    }


    private void tryExtraLive(boolean automatic,Bitmap remoteFrame){
        if(extraRecognition==null||extraRecognition.isWorking())return;
        if(automatic&&!extraRecognition.autoEnabled())return;
        long now=android.os.SystemClock.elapsedRealtime();
        // Do not run inference every video frame or start many downloads.
        if(automatic&&now-lastExtraRequest<22000)return;
        Bitmap bitmap=remoteFrame;
        if(bitmap==null)bitmap=pixelView==null?null:pixelView.getBitmap();
        if(bitmap==null) {
            if(!automatic)log("Nessun fotogramma disponibile per il riconoscimento extra");
            return;
        }
        lastExtraRequest=now;
        final String mode=currentMode;
        final Bitmap sample;
        // Limit holding Pixel/Canon full-resolution frames for background inference.
        int max=Math.max(bitmap.getWidth(),bitmap.getHeight());
        if(max>512) {
            float factor=512f/max;
            sample=Bitmap.createScaledBitmap(bitmap,
                Math.max(1,Math.round(bitmap.getWidth()*factor)),
                Math.max(1,Math.round(bitmap.getHeight()*factor)),true);
        } else {
            sample=Bitmap.createBitmap(bitmap);
        }
        extraRecognition.classify(sample,automatic,new ExtraRecognition.Callback(){
            @Override public void status(String msg) {
                if(closing||!mode.equals(currentMode))return;
                extraStatus.setText("AI EXTRA · "+msg);
            }
            @Override public void result(ExtraRecognition.Recognition r) {
                if(closing||!mode.equals(currentMode))return;
                extraStatus.setText(r.describe());
                if(r.recognized) {
                    currentSubjectSpoken=r.describe();
                    aiText.setText("AI EXTRA · "+r.label+
                        String.format(java.util.Locale.ITALIAN," · %.0f%%",r.confidence*100));
                }
                // The fallback provides a scene classification, not a bounding box.
            }
        });
    }

    private void applyRecommendation(ColorLooks.Recommendation recommendation) {
        lookAdvice.setText("LOOK CONSIGLIATO · "+recommendation.label());
        if(autoLook && recommendation.look!=liveLook) {
            liveLook=recommendation.look;
            // Android 12+: compatible CameraX TextureView supports per-view render effects.
            if(Build.VERSION.SDK_INT>=31) {
                pixelView.setRenderEffect(RenderEffect.createColorFilterEffect(
                    ColorLooks.filter(liveLook)));
            }
            canonView.setColorFilter(ColorLooks.filter(liveLook));
        }
    }

    private void toggleComposer(){
        autoCompose=!autoCompose;
        composer.setActive(autoCompose);
        AmpyButtons.setToggle(composerToggle,autoCompose);
        getPreferences(MODE_PRIVATE).edit().putBoolean("auto_compose",autoCompose).apply();
        log("Automated Composer "+(autoCompose?"ATTIVO: guida visiva animata":"DISATTIVATO"));
    }
    private void toggleAutoLook(){
        autoLook=!autoLook;
        AmpyButtons.setToggle(lookToggle,autoLook);
        getPreferences(MODE_PRIVATE).edit().putBoolean("auto_look",autoLook).apply();
        if(!autoLook) {
            liveLook=ColorLooks.Look.NATURALE;
            if(Build.VERSION.SDK_INT>=31)pixelView.setRenderEffect(null);
            canonView.clearColorFilter();
        }
        log("Look dinamico "+(autoLook?"attivo":"disattivo"));
    }
    private void toggleVoice(){
        voice=!voice;
        AmpyButtons.setToggle(voiceToggle,voice);
        getPreferences(MODE_PRIVATE).edit().putBoolean("voice",voice).apply();
        if(!voice&&speech!=null)speech.stop();
        log("Maestro vocale "+(voice?"attivo":"disattivo"));
    }
    private void openEditor(){startActivity(new Intent(this,EditorActivity.class));}
    private void rankPhotos(){
        Intent i=new Intent(this,EditorActivity.class);
        i.putExtra("pick_multiple",true);
        startActivity(i);
    }
    private void openAmpyLink(){
        try {
            Intent i=getPackageManager().getLaunchIntentForPackage("com.ampylink.app");
            if(i==null){log("AmpyLink non installata: collegamento CCAPI interno disponibile.");return;}
            startActivity(i);
        }catch(Exception e){log("AmpyLink: "+e.getMessage());}
    }
    private void shootPixel(){
        if(!"PIXEL".equals(currentMode)||capture==null){
            log("Pixel Camera non ancora pronta");return;
        }
        ContentValues values=new ContentValues();
        String name="AmpyEOS_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".jpg";
        values.put(MediaStore.MediaColumns.DISPLAY_NAME,name);
        values.put(MediaStore.MediaColumns.MIME_TYPE,"image/jpeg");
        if(Build.VERSION.SDK_INT>=29)
            values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/AmpyEOS");
        ImageCapture.OutputFileOptions options=new ImageCapture.OutputFileOptions.Builder(
            getContentResolver(),MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values).build();
        pixelShutter.setEnabled(false);
        capture.takePicture(options,getMainExecutor(),new ImageCapture.OnImageSavedCallback(){
            @Override public void onImageSaved(ImageCapture.OutputFileResults r){
                shots++;
                pixelShutter.setEnabled(true);
                log("Foto "+shots+" salvata: "+name);
                Uri uri=r.getSavedUri();
                if(uri!=null)new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Scatto acquisito")
                    .setMessage("Vuoi vedere la spiegazione, migliorare colore e ritaglio nella Virtual Classroom?")
                    .setPositiveButton("AULA",(dialog,which)->{
                        Intent i=new Intent(MainActivity.this,EditorActivity.class);
                        i.putExtra("image_uri",uri.toString());startActivity(i);
                    })
                    .setNegativeButton("CONTINUA",null).show();
            }
            @Override public void onError(ImageCaptureException e){
                pixelShutter.setEnabled(true);log("Errore scatto Pixel: "+e.getMessage());
            }
        });
    }
    private void toggleRecord(){
        CanonCcapi client=canon;
        if(client==null||!client.has("/shooting/control/recbutton")) {
            log("Registrazione video non esposta dalla fotocamera via CCAPI");return;
        }
        recordButton.setEnabled(false);
        commandExecutor.execute(()->{
            try {
                client.toggleRecord();log(client.recording()?"Registrazione avviata":"Registrazione arrestata");
                ui.post(()->recordButton.setText(client.recording()?"STOP VIDEO":"REC CANON"));
            } catch(Exception e){log("REC non disponibile: "+e.getMessage());}
            finally{ui.post(()->{if(!closing)recordButton.setEnabled(true);});}
        });
    }
    private void showDiagnostics(){
        CanonCcapi client=canon;
        if(client==null||!client.connected()){
            new AlertDialog.Builder(this).setTitle("DIAGNOSTICA AMPYEOS")
                .setMessage("Pixel 7 Pro · ML Kit locale · CameraX · Composer animato · ColorLab.\nCanon non collegata. Attiva CCAPI sulla EOS, poi CANON WIFI.")
                .setPositiveButton("OK",null).show();
            return;
        }
        log("Diagnostica CCAPI in corso…");
        commandExecutor.execute(()->{
            String details=client.diagnostics();
            ui.post(()->{if(!closing)new AlertDialog.Builder(this)
                .setTitle("Canon CCAPI · diagnostica")
                .setMessage(details).setPositiveButton("OK",null).show();});
        });
    }
    private void scanBle(){
        if(Build.VERSION.SDK_INT>=31 &&
            (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED ||
             checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)){
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},102);
            return;
        }
        log("Ricerca BLE per 12 s…");
        bluetooth.start(this,new BleDiscovery.Listener(){
            @Override public void found(String name,String mac,int rssi) {
                log("BLE: "+name+" · "+mac+" · "+rssi+" dBm (solo rilevamento)");
            }
            @Override public void error(String reason){log(reason);}
        });
        ui.postDelayed(()->bluetooth.stop(),12000);
    }
    @Override public void onSensorChanged(SensorEvent event){
        if(event.sensor.getType()!=Sensor.TYPE_ACCELEROMETER)return;
        // Level is a guide based on the Pixel, not the Canon's own orientation.
        float x=event.values[0],y=event.values[1];
        guide.setTilt((float)Math.toDegrees(Math.atan2(x,y)));
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
    @Override protected void onResume(){
        super.onResume();
        if(sensors!=null&&accelerometer!=null)
            sensors.registerListener(this,accelerometer,SensorManager.SENSOR_DELAY_UI);
    }
    @Override protected void onPause(){
        if(sensors!=null)sensors.unregisterListener(this);
        super.onPause();
    }
    @Override public void onRequestPermissionsResult(int request,String[] perms,int[] grants){
        super.onRequestPermissionsResult(request,perms,grants);
        if(request==101 && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED)openCamera();
        else if(request==101)log("Per Pixel Live serve il permesso fotocamera.");
        if(request==102 && grants.length==2 &&
           grants[0]==PackageManager.PERMISSION_GRANTED && grants[1]==PackageManager.PERMISSION_GRANTED)scanBle();
    }
    @Override protected void onDestroy() {
        closing=true;canonLoop.set(false);
        if(cameraProvider!=null)cameraProvider.unbindAll();
        if(canon!=null){networkExecutor.execute(()->canon.disconnect());}
        analysisExecutor.shutdownNow();networkExecutor.shutdown();commandExecutor.shutdown();
        bluetooth.stop();if(speech!=null){speech.stop();speech.shutdown();}
        if(subjectAi!=null && !aiBusy.get())subjectAi.close();
        if(extraRecognition!=null)extraRecognition.close();
        super.onDestroy();
    }
}
