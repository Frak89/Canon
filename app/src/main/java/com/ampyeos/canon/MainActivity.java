package com.ampyeos.canon;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
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
public final class MainActivity extends ComponentActivity {
    private static final int CYAN=Color.rgb(75,240,228), BG=Color.rgb(9,15,25);
    private final ExecutorService analysisExecutor=Executors.newSingleThreadExecutor();
    private final ExecutorService networkExecutor=Executors.newSingleThreadExecutor();
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final AtomicBoolean canonLoop=new AtomicBoolean(false);
    private final AtomicBoolean aiBusy=new AtomicBoolean(false);
    private SubjectAi subjectAi;
    private int frameCount=0;
    private PreviewView pixelView;
    private ImageView canonView;
    private TextView state, metrics, advice, aiText, score, modeLabel;
    private Button shutter;
    private ProcessCameraProvider cameraProvider;
    private CanonCcapi canon;
    private String currentMode="PIXEL";
    private long lastUi=0;
    private volatile boolean closing=false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        createUi();
        subjectAi=new SubjectAi();
        startPixel();
    }
    private TextView text(String value,int size,int color) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setPadding(dp(8),dp(7),dp(8),dp(7));return t;
    }
    private int dp(int d){return (int)(d*getResources().getDisplayMetrics().density+.5f);}
    private Button button(String label,LinearLayout row,View.OnClickListener run) {
        Button b=new Button(this);b.setText(label);b.setTextSize(11);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setOnClickListener(run);
        row.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));return b;
    }
    private void createUi() {
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(BG);setContentView(layout);
        modeLabel=text("AmpyEOS  /  CAMERA ASSISTANT",19,CYAN);
        modeLabel.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
        layout.addView(modeLabel);
        LinearLayout nav=new LinearLayout(this);
        button("PIXEL LIVE",nav,v->startPixel());
        button("CANON LIVE",nav,v->showCanonConnect());
        button("SCOPRI",nav,v->discoverCanon());
        layout.addView(nav);
        FrameLayout view=new FrameLayout(this);
        layout.addView(view,new LinearLayout.LayoutParams(-1,0,1));
        pixelView=new PreviewView(this);
        pixelView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        view.addView(pixelView,new FrameLayout.LayoutParams(-1,-1));
        canonView=new ImageView(this);canonView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        canonView.setBackgroundColor(Color.BLACK);canonView.setVisibility(View.GONE);
        view.addView(canonView,new FrameLayout.LayoutParams(-1,-1));
        view.addView(new GuideOverlay(this),new FrameLayout.LayoutParams(-1,-1));
        score=text("Analisi in avvio…",17,CYAN);
        score.setGravity(Gravity.CENTER_VERTICAL);
        metrics=text("Attendi i fotogrammi",12,0xffc7d4df);
        advice=text("Analizzo luce, dettaglio e inquadratura in locale.",14,Color.WHITE);
        aiText=text("AI locale · ricerca volti e oggetti…",13,0xff88e4d2);
        state=text("PIXEL · nessuna immagine inviata a servizi esterni",11,0xff9badbd);
        layout.addView(score);layout.addView(metrics);layout.addView(advice);layout.addView(aiText);
        shutter=new Button(this);shutter.setText("SCATTA CANON");shutter.setEnabled(false);
        shutter.setOnClickListener(v->sendShutter());
        layout.addView(shutter,new LinearLayout.LayoutParams(-1,dp(50)));
        layout.addView(state);
    }

    private void update(PhotoAnalyzer.Result result) {
        long now=android.os.SystemClock.uptimeMillis();
        if(now-lastUi<350)return;
        lastUi=now;
        score.setText("QUALITÀ TECNICA STIMATA  "+result.score+"/100");
        metrics.setText(result.summary());
        advice.setText(result.advice);
    }
    private void log(String message) {ui.post(()-> {if(!closing)state.setText(message);});}

    private void startPixel() {
        canonLoop.set(false);
        shutter.setEnabled(false);
        currentMode="PIXEL";
        canonView.setVisibility(View.GONE);pixelView.setVisibility(View.VISIBLE);
        modeLabel.setText("AmpyEOS / PIXEL LIVE");
        log("PIXEL LIVE · elaborazione offline");
        networkExecutor.execute(()->{if(canon!=null){canon.stop();canon=null;}});
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
                ImageAnalysis analyzer=new ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
                analyzer.setAnalyzer(analysisExecutor,frame->{
                    boolean handedOff=false;
                    try {
                        PhotoAnalyzer.Result r=PhotoAnalyzer.fromCamera(frame);
                        ui.post(()->{if(!closing && "PIXEL".equals(currentMode))update(r);});
                        if(subjectAi!=null && (++frameCount%5==0) && aiBusy.compareAndSet(false,true)) {
                            handedOff=true;
                            subjectAi.analyze(frame,message->{
                                aiBusy.set(false);
                                ui.post(()->{if(!closing && "PIXEL".equals(currentMode))aiText.setText(message);});
                            });
                        }
                    }catch(Exception e){log("Analisi frame: "+e.getMessage());}
                    finally{if(!handedOff)frame.close();}
                });
                cameraProvider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,preview,analyzer);
            }catch(Exception e){log("CameraX: "+e.getMessage());}
        },getMainExecutor());
    }

    @Override public void onRequestPermissionsResult(int request,String[] perms,int[] grants){
        super.onRequestPermissionsResult(request,perms,grants);
        if(request==101 && grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED)openCamera();
        else if(request==101)log("Per Pixel Live serve il permesso fotocamera.");
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
        currentMode="CANON";canonLoop.set(false);shutter.setEnabled(false);
        if(cameraProvider!=null)cameraProvider.unbindAll();
        pixelView.setVisibility(View.GONE);canonView.setVisibility(View.VISIBLE);
        modeLabel.setText("AmpyEOS / CANON LIVE");
        log("Connessione CCAPI in corso a "+address+"…");
        networkExecutor.execute(()-> {
            if(canon!=null)canon.stop();
            CanonCcapi client=new CanonCcapi();canon=client;
            try{
                client.connect(address);
                log("Collegata: "+client.info+" · "+client.endpoint());
                canonLoop.set(true);
                ui.post(()->{if(!closing && "CANON".equals(currentMode))shutter.setEnabled(true);});
                try { client.startLive(); }
                catch(Exception e) {log("CCAPI collegato, ma Live View non avviabile: "+e.getMessage());return;}
                int misses=0;
                while(canonLoop.get()&&!closing && "CANON".equals(currentMode)){
                    try{
                        Bitmap frame=client.frame();misses=0;
                        PhotoAnalyzer.Result m=PhotoAnalyzer.fromBitmap(frame);
                        if(subjectAi!=null && (++frameCount%5==0) && aiBusy.compareAndSet(false,true)) {
                            subjectAi.analyze(frame,message->{
                                aiBusy.set(false);
                                ui.post(()->{if(!closing && "CANON".equals(currentMode))aiText.setText(message);});
                            });
                        }
                        ui.post(()->{
                            if(!closing&&"CANON".equals(currentMode)){canonView.setImageBitmap(frame);update(m);}
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
            finally{canonLoop.set(false);client.stop();}
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
    @Override protected void onDestroy() {
        closing=true;canonLoop.set(false);
        if(cameraProvider!=null)cameraProvider.unbindAll();
        if(canon!=null){networkExecutor.execute(()->canon.stop());}
        analysisExecutor.shutdownNow();networkExecutor.shutdown();
        if(subjectAi!=null && !aiBusy.get())subjectAi.close();
        super.onDestroy();
    }
}
