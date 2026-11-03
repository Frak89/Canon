package com.ampyeos.canon;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AmpyEOS Color Lab & Classroom.
 * Supports image ranking, non-destructive look previews, gestures, comparison,
 * fully visual 5-step lessons and export of a new JPEG copy to MediaStore.
 */
public final class EditorActivity extends Activity {
    private static final int PICK=1, PICK_GROUP=2, PICK_MODEL=3, PICK_MUSIQ=4, PICK_QWEN=5, CYAN=0xff43eadb, BG=0xff080f1a;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicInteger generation=new AtomicInteger();
    private Bitmap original, working, display;
    private Uri originUri;
    private ImageView image;
    private CropView crop;
    private LessonOverlay lesson;
    private BeforeAfterView comparison;
    private VisualInspectorOverlay visualOverlay;
    private VisualInspector.Report inspection;
    private SubjectAi stillSubject;
    private ExtraRecognition extraRecognition;
    private Button extraAiToggle,mobileAiToggle;
    private TextView modelStatus;
    private TextView learnedStatus;
    private LearnedQuality.Result learnedResult;
    private int extraRequests=0;
    private String subjectDescription="Analisi soggetto non avviata";
    private String subjectExplanation="Attendo il riconoscimento ML Kit per spiegare che cosa è stato rilevato.";
    private boolean showVisual=false;
    private AutoMasterEngine.Session masterSession;
    private AutoMasterEngine.Plan activePlan;
    private boolean compareOn=false,autoLesson=false;
    private Runnable lessonAdvance;
    private Spinner looks;
    private SeekBar brightness,contrast,saturation;
    private TextView caption,report,teacher,stepInfo;
    private boolean before=false, voice=false, playing=true;
    private Button beforeButton,comparisonButton,overlayButton,playButton,voiceButton,autoLessonButton;
    private Button applyCropButton,versionsButton;
    private final ArrayList<Button> needsPhoto=new ArrayList<>();
    private TextToSpeech speech;
    private int lessonStep=0;
    private PhotoReview originalReview;
    private LocalVisionReviewer.Result visionResult;
    private TextView visionStatus;
    private Button qwenReviewButton;
    private QwenModelManager qwenModels;
    private Button qwenDownloadButton;
    private ColorLooks.Look selectedLook=ColorLooks.Look.NATURALE;
    private volatile boolean closing=false;
    private final String[] steps={"1 · OSSERVA","2 · LUCE","3 · COMPOSIZIONE","4 · COLORE","5 · CONFRONTA"};

    private int dp(int x){return Math.round(getResources().getDisplayMetrics().density*x);}
    private TextView txt(String value,int size){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);
        t.setPadding(dp(8),dp(6),dp(8),dp(6));return t;
    }
    private Button button(String name,LinearLayout row,View.OnClickListener listener){
        Button b=AmpyButtons.action(this,name);
        b.setTextSize(12);b.setOnClickListener(listener);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(148),dp(48));
        p.setMargins(dp(3),dp(2),dp(3),dp(2));row.addView(b,p);
        return b;
    }
    private Button actionPhoto(String name,LinearLayout row,View.OnClickListener listener){
        Button b=button(name,row,listener);
        needsPhoto.add(b);
        b.setEnabled(original!=null);
        return b;
    }
    private Button toggleButton(String name,boolean on,LinearLayout row,View.OnClickListener listener){
        Button b=AmpyButtons.toggle(this,name,on);
        b.setTextSize(11);b.setOnClickListener(listener);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(167),dp(48));
        p.setMargins(dp(3),dp(2),dp(3),dp(2));row.addView(b,p);
        return b;
    }
    private void updateButtonAvailability(){
        boolean ready=original!=null;
        for(Button b:needsPhoto)b.setEnabled(ready);
        if(applyCropButton!=null)applyCropButton.setEnabled(ready&&crop!=null&&crop.isCropActive());
        if(versionsButton!=null)versionsButton.setEnabled(masterSession!=null);
        if(voiceButton!=null)AmpyButtons.setToggle(voiceButton,voice);
        if(beforeButton!=null)AmpyButtons.setToggle(beforeButton,before);
        if(comparisonButton!=null)AmpyButtons.setToggle(comparisonButton,compareOn);
        if(overlayButton!=null)AmpyButtons.setToggle(overlayButton,showVisual);
        if(playButton!=null)AmpyButtons.setToggle(playButton,playing);
        if(autoLessonButton!=null)AmpyButtons.setToggle(autoLessonButton,autoLesson);
    }
    private LinearLayout row(LinearLayout container){
        HorizontalScrollView sc=new HorizontalScrollView(this);sc.setHorizontalScrollBarEnabled(false);
        LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER_VERTICAL);sc.addView(r);
        container.addView(sc);return r;
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);setContentView(root);
        // Android 15/16 edge-to-edge: honor the real status/navigation bar insets.
        root.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets safe=insets.getInsets(android.view.WindowInsets.Type.systemBars());
            v.setPadding(0,safe.top,0,safe.bottom);
            return insets;
        });
        root.requestApplyInsets();
        TextView title=txt("AMPY▲EOS  /  COLOR LAB + CLASSROOM",18);title.setTextColor(CYAN);
        root.addView(title);
        FrameLayout area=new FrameLayout(this);area.setBackgroundColor(Color.BLACK);
        root.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        area.addView(image,new FrameLayout.LayoutParams(-1,-1));
        crop=new CropView(this);crop.setCropEnabled(false);
        area.addView(crop,new FrameLayout.LayoutParams(-1,-1));
        lesson=new LessonOverlay(this);
        lesson.setVisibility(View.GONE);
        area.addView(lesson,new FrameLayout.LayoutParams(-1,-1));
        visualOverlay=new VisualInspectorOverlay(this);
        visualOverlay.setVisibility(View.GONE);
        area.addView(visualOverlay,new FrameLayout.LayoutParams(-1,-1));
        comparison=new BeforeAfterView(this);
        comparison.setVisibility(View.GONE);
        area.addView(comparison,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroller=new ScrollView(this);
        root.addView(scroller,new LinearLayout.LayoutParams(-1,dp(370)));
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(panel);
        TextView legend=txt("MENTA CHIARO = TOGGLE ON · SCURO = OFF/NON DISPONIBILE · BLU = AZIONE",11);
        legend.setTextColor(0xffb8d5e3);panel.addView(legend);
        extraRecognition=new ExtraRecognition(this);
        LinearLayout modelActions=row(panel);
        extraAiToggle=toggleButton("DOWNLOAD AI AUTOMATICO",extraRecognition.autoEnabled(),
            modelActions,v->{
                extraRecognition.setAutoEnabled(!extraRecognition.autoEnabled());
                AmpyButtons.setToggle(extraAiToggle,extraRecognition.autoEnabled());
                modelStatus.setText(extraRecognition.autoEnabled()?
                    "AI EXTRA: download automatico attivo su Wi-Fi "+
                        (extraRecognition.allowMobileAuto()?"e rete mobile 4G/5G":"(dati mobili disattivati)"):
                    "AI EXTRA: download automatico disattivato; avvio manuale disponibile");
            });
        mobileAiToggle=toggleButton("DATI MOBILI AI",extraRecognition.allowMobileAuto(),
            modelActions,v->{
                extraRecognition.setAllowMobileAuto(!extraRecognition.allowMobileAuto());
                AmpyButtons.setToggle(mobileAiToggle,extraRecognition.allowMobileAuto());
                modelStatus.setText(extraRecognition.allowMobileAuto()?
                    "4G/5G consentito per il download automatico AI (escluso roaming). "+
                    "Il consumo del traffico dipende dal piano dati.":
                    "Download automatico AI limitato al Wi-Fi. Il download manuale resta possibile.");
            });
        modelStatus=txt("AI EXTRA · "+ExtraRecognition.modelName()+
            " · "+(extraRecognition.isCached()?"modello in cache":
                "download su Wi-Fi e "+(extraRecognition.allowMobileAuto()?"4G/5G":"dati mobili spenti")),
            12);
        modelStatus.setTextColor(0xff8debd2);panel.addView(modelStatus);
        learnedStatus=txt("MUSIQ · "+LearnedQuality.status(this)+
            " · qualità percettiva appresa (NON voto artistico)",12);
        learnedStatus.setTextColor(0xfff2c88f);panel.addView(learnedStatus);
        LinearLayout localModelRow=row(panel);
        button("IMPORTA MODELLO DA DOWNLOAD",localModelRow,v->pickModel());
        button("INFO MODELLI",localModelRow,v->new AlertDialog.Builder(this)
            .setTitle("Modelli AI compatibili")
            .setMessage("AmpyEOS usa MobileNetV2 TensorFlow Lite. Puoi scegliere "+
                "dalla cartella Download il file ufficiale .tgz o il .tflite estratto. "+
                "Per funzionare offline sono necessarie anche le etichette "+
                "ImageNetLabels.txt. I file vengono verificati con SHA-256. "+
                "Qwen .gguf non è compatibile con questo motore: "+
                "servirà un runtime Vision-Language separato.")
            .setPositiveButton("OK",null).show());
        qwenModels=new QwenModelManager(this);
        visionStatus=txt("QWEN3-VL · SOLO PIXEL · "+qwenModels.state()+
            " · Nessuna fotografia trasferita alla rete.",12);
        visionStatus.setTextColor(0xffefcc98);panel.addView(visionStatus);
        LinearLayout qwenActions=row(panel);
        qwenDownloadButton=button("SCARICA QWEN 2B (1,55 GB)",qwenActions,v->downloadQwen());
        button("IMPORTA GGUF DA DOWNLOAD",qwenActions,v->pickQwen());
        button("SOSPENDI DOWNLOAD",qwenActions,v->{
            if(qwenModels!=null)qwenModels.cancel();
        });
        qwenReviewButton=button("ANALIZZA QWEN OFFLINE",qwenActions,v->analyzeVision());
        qwenReviewButton.setEnabled(false);
        caption=txt("Apri una foto oppure confronta più scatti.",13);caption.setTextColor(CYAN);panel.addView(caption);
        report=txt("L'originale non viene mai sovrascritto.",12);panel.addView(report);
        LinearLayout tools=row(panel);
        button("APRI FOTO",tools,v->pick(false));
        button("IMPORTA MUSIQ ONNX",tools,v->pickMusiq());
        actionPhoto("VALUTA QUALITÀ MUSIQ",tools,v->analyzeMusiq());
        button("ORDINA PER DATI TECNICI",tools,v->pick(true));
        actionPhoto("AUTO LOOK TECNICO",tools,v->autoMaster());
        actionPhoto("AUTO TONO + CROP",tools,v->autoPro());
        versionsButton=actionPhoto("VERSIONI AI",tools,v->showVersions());
        versionsButton.setEnabled(false);
        actionPhoto("AUTO COMPONI",tools,v->autoCompose());
        actionPhoto("AUTO RADDRIZZA",tools,v->autoStraighten());
        actionPhoto("AUTO TONI",tools,v->autoTone());
        actionPhoto("AUTO RESIZE",tools,v->chooseResize());
        actionPhoto("ANALISI VISIVA",tools,v->showAnalysis());
        actionPhoto("RICONOSCIMENTO EXTRA",tools,v->runExtraRecognition(false));
        overlayButton=toggleButton("OVERLAY TECNICO",false,tools,v->toggleTechnicalOverlay());
        overlayButton.setEnabled(false);
        needsPhoto.add(overlayButton);
        actionPhoto("RITAGLIA",tools,v->chooseRatio());
        applyCropButton=actionPhoto("APPLICA CROP",tools,v->applyCrop());
        applyCropButton.setEnabled(false);
        actionPhoto("RUOTA 90°",tools,v->rotate());
        actionPhoto("SPECCHIO",tools,v->mirror());
        beforeButton=toggleButton("ORIGINALE",false,tools,v->toggleBefore());
        beforeButton.setEnabled(false);needsPhoto.add(beforeButton);
        comparisonButton=toggleButton("SPLIT SCORREVOLE",false,tools,v->toggleComparison());
        comparisonButton.setEnabled(false);needsPhoto.add(comparisonButton);
        actionPhoto("SPIEGA",tools,v->explain());
        actionPhoto("SALVA COPIA",tools,v->save());
        actionPhoto("RIPRISTINA",tools,v->restore());

        LinearLayout lookRow=new LinearLayout(this);lookRow.setGravity(Gravity.CENTER_VERTICAL);
        lookRow.addView(txt("LOOK",12));
        looks=new Spinner(this);
        looks.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,ColorLooks.Look.labels()));
        lookRow.addView(looks,new LinearLayout.LayoutParams(0,dp(47),1));panel.addView(lookRow);
        looks.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(android.widget.AdapterView<?> a,View v,int index,long id){
                selectedLook=ColorLooks.Look.values()[index];refresh();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> a){}
        });
        brightness=slider(panel,"ESPOSIZIONE SIMULATA",100);
        contrast=slider(panel,"CONTRASTO",100);
        saturation=slider(panel,"SATURAZIONE",100);

        teacher=txt("LEZIONE VISIVA · L'assistente annoterà la foto, mostrando passo per passo come migliorarla.",12);
        teacher.setTextColor(0xffb7fae9);panel.addView(teacher);
        LinearLayout classroom=row(panel);
        button("◀ INDIETRO",classroom,v->changeStep(-1));
        button("AVANTI ▶",classroom,v->changeStep(+1));
        playButton=toggleButton("ANIMAZIONI",playing,classroom,v->{
            playing=!playing;if(playing)lesson.play();else lesson.pause();
            updateButtonAvailability();
        });
        voice=getPreferences(MODE_PRIVATE).getBoolean("teacher_voice",true);
        voiceButton=toggleButton("VOCE MAESTRO",voice,classroom,v->{
            voice=!voice;getPreferences(MODE_PRIVATE).edit().putBoolean("teacher_voice",voice).apply();
            caption.setText(voice?"LEZIONE VOCALE ATTIVA":"LEZIONE VOCALE DISATTIVA");
            if(!voice&&speech!=null)speech.stop();
            else say(teacher.getText().toString());
            updateButtonAvailability();
        });
        autoLessonButton=toggleButton("LEZIONE AUTOMATICA",false,classroom,v->{
            if(autoLesson)stopAutoLesson();else beginAutoLesson();
            updateButtonAvailability();
        });
        stepInfo=txt(steps[0],12);panel.addView(stepInfo);
        stillSubject=new SubjectAi();
        updateButtonAvailability();
        speech=new TextToSpeech(this,code->{if(code==TextToSpeech.SUCCESS && speech!=null){
            speech.setLanguage(Locale.ITALIAN);speech.setSpeechRate(.92f);
        }});
        String incoming=getIntent().getStringExtra("image_uri");
        if(incoming!=null)load(Uri.parse(incoming));
        else if(getIntent().getData()!=null)load(getIntent().getData());
        else pick(getIntent().getBooleanExtra("pick_multiple",false));
    }
    private SeekBar slider(LinearLayout root,String text,int value){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(txt(text,11));
        SeekBar seek=new SeekBar(this);seek.setMax(200);seek.setProgress(value);
        row.addView(seek,new LinearLayout.LayoutParams(0,dp(36),1));root.addView(row);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int p,boolean user){if(user)refresh();}
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
        return seek;
    }
    private void pick(boolean multiple){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,multiple);
        startActivityForResult(i,multiple?PICK_GROUP:PICK);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null)return;
        if(request==PICK_QWEN&&data.getData()!=null){
            importQwen(data.getData());
            return;
        }
        if(request==PICK_MUSIQ&&data.getData()!=null){
            importMusiq(data.getData());
            return;
        }
        if(request==PICK_MODEL&&data.getData()!=null){
            importModel(data.getData());
            return;
        }
        if(request==PICK && data.getData()!=null)load(data.getData());
        else if(request==PICK_GROUP){
            ArrayList<Uri> selected=new ArrayList<>();
            if(data.getClipData()!=null){
                int size=Math.min(15,data.getClipData().getItemCount());
                for(int i=0;i<size;i++)selected.add(data.getClipData().getItemAt(i).getUri());
            }else if(data.getData()!=null)selected.add(data.getData());
            if(!selected.isEmpty())rank(selected);
        }
    }
    private void pickModel(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra("android.content.extra.SHOW_ADVANCED",true);
        startActivityForResult(intent,PICK_MODEL);
    }
    private void importModel(Uri selected){
        modelStatus.setText("IMPORTAZIONE: lettura del file selezionato…");
        extraRecognition.importFromDevice(selected,original,new ExtraRecognition.Callback(){
            @Override public void status(String info){
                if(closing)return;
                modelStatus.setText("IMPORTAZIONE · "+info);
            }
            @Override public void result(ExtraRecognition.Recognition result){
                if(closing)return;
                modelStatus.setText(result.describe());
                if(result.recognized) {
                    subjectDescription="Classificazione MobileNet: "+result.label;
                    subjectExplanation=result.describe();
                    if(original!=null)report.setText("RICONOSCIMENTO EXTRA\\n"+subjectExplanation);
                }
            }
        });
    }

    private void pickMusiq(){
        new AlertDialog.Builder(this)
            .setTitle("Modello MUSIQ via ONNX Runtime")
            .setMessage("Questo è un vero modello di qualità percettiva appreso da fotografie. "+
                "Scarica i file ufficiali musiq_model.onnx e musiq_model.onnx.data "+
                "dal repository 86Cao/IQA-ONNX-Models su Hugging Face, poi selezionali "+
                "separatamente dal telefono. Non può giudicare contenuto o valore artistico; "+
                "per quello serve Qwen-VL.")
            .setPositiveButton("SELEZIONA FILE",(d,w)->{
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                startActivityForResult(intent,PICK_MUSIQ);
            })
            .setNeutralButton("APRI REPOSITORY",(d,w)->{
                Intent browser=new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://huggingface.co/86Cao/IQA-ONNX-Models/tree/main"));
                startActivity(browser);
            })
            .setNegativeButton("CHIUDI",null).show();
    }
    private void importMusiq(Uri uri){
        learnedStatus.setText("MUSIQ · importazione modello ONNX in corso…");
        worker.execute(()->{
            try{
                String result=LearnedQuality.importFile(getApplicationContext(),uri);
                ui.post(()->{if(!closing)learnedStatus.setText("MUSIQ · "+result);});
            }catch(Exception e){
                ui.post(()->{if(!closing)learnedStatus.setText("MUSIQ · "+e.getMessage());});
            }
        });
    }
    private void analyzeMusiq(){
        Bitmap image=original;
        if(image==null){learnedStatus.setText("MUSIQ · apri prima una foto.");return;}
        learnedStatus.setText("MUSIQ · eseguo rete neurale ONNX reale…");
        worker.execute(()->{
            try{
                LearnedQuality.Result result=LearnedQuality.assess(
                    getApplicationContext(),image);
                ui.post(()->{
                    if(closing||original!=image)return;
                    learnedResult=result;
                    learnedStatus.setText(result.describe());
                });
            }catch(Exception e){
                ui.post(()->{
                    if(!closing&&original==image)
                        learnedStatus.setText("MUSIQ · modello assente o non compatibile: "+e.getMessage());
                });
            }
        });
    }
    private Bitmap decode(Uri uri)throws Exception{
        BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;
        try(InputStream stream=getContentResolver().openInputStream(uri)){
            if(stream==null)throw new Exception("File non accessibile");
            BitmapFactory.decodeStream(stream,null,bounds);
        }
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new Exception("Formato non decodificabile: prova JPG/PNG");
        BitmapFactory.Options opt=new BitmapFactory.Options();
        int sample=1;while(Math.max(bounds.outWidth,bounds.outHeight)/sample>2300)sample*=2;
        opt.inSampleSize=sample;opt.inPreferredConfig=Bitmap.Config.ARGB_8888;
        try(InputStream stream=getContentResolver().openInputStream(uri)){
            Bitmap bitmap=BitmapFactory.decodeStream(stream,null,opt);
            if(bitmap==null)throw new Exception("Immagine non decodificabile");
            return bitmap;
        }
    }
    private void load(Uri uri){
        caption.setText("Caricamento immagine…");originUri=uri;
        worker.execute(()->{
            try{
                Bitmap b=decode(uri);
                PhotoReview r=PhotoReview.inspect(b);
                VisualInspector.Report visual=VisualInspector.analyze(b);
                ui.post(()->{
                    if(closing)return;
                    stopAutoLesson();comparison.setVisibility(View.GONE);compareOn=false;
                    masterSession=null;activePlan=null;visionResult=null;
                    learnedResult=null;
                    learnedStatus.setText("MUSIQ · "+LearnedQuality.status(this)+
                        " · premi VALUTA QUALITÀ MUSIQ per calcolare la qualità percettiva");
                    visionStatus.setText("QWEN-VL · Foto caricata; in attesa di analisi semantica.");
                    original=b;working=b;originalReview=r;inspection=visual;
                    qwenReviewButton.setEnabled(true);
                    image.setImageBitmap(b); // Show the actual decoded source immediately.
                    if(r.metrics.brightness<14 && r.metrics.detail<8)
                        modelStatus.setText("AVVISO IMMAGINE: i pixel del file risultano quasi neri. "+
                            "Verifica che il JPEG/PNG selezionato mostri davvero una fotografia.");
                    subjectDescription="Ricerca di volti e oggetti sul Pixel…";
                    subjectExplanation="Il modello sta analizzando questa fotografia.";
                    extraRequests++;
                    visualOverlay.setSubject(null,"");
                    visualOverlay.configure(visual,b.getWidth(),b.getHeight());
                    visualOverlay.setVisibility(View.GONE);showVisual=false;
                    if(stillSubject!=null)stillSubject.observe(b,observation->ui.post(()->{
                        if(closing||original!=b)return;
                        subjectDescription=observation.description;
                        subjectExplanation=observation.explanation;
                        visualOverlay.setSubject(observation.normalizedBox,observation.description);
                        report.setText("SOGGETTO RILEVATO · "+subjectDescription+
                            "\nSPIEGAZIONE · "+subjectExplanation+
                            "\nDATI TECNICI · "+visual.summary+
                            "\nPERCHÉ · "+r.why+"\nCOME · "+r.how);
                        if(voice && autoLesson && lessonStep==0) {
                            say(subjectExplanation);
                            teacher.setText("MAESTRO · "+subjectExplanation);
                        }
                        String details=observation.description.toLowerCase(java.util.Locale.ROOT);
                        if(observation.normalizedBox==null ||
                            (!details.contains("volto")&&!details.contains("volti"))) {
                            runExtraRecognition(true);
                        }
                    }));
                    selectedLook=r.look.look;
                    looks.setSelection(selectedLook.ordinal());
                    brightness.setProgress(100);contrast.setProgress(100);saturation.setProgress(100);
                    before=false;crop.setBitmapSize(b.getWidth(),b.getHeight());
                    crop.setCropEnabled(false);crop.setAspect(0);
                    lesson.configure(b.getWidth(),b.getHeight(),r.suggestedCrop);
                    caption.setText("VALUTAZIONE FOTOGRAFICA: "+r.scoreLabel()+
                        " · indice tecnico luce/dettaglio "+r.metrics.score+"/100");
                    report.setText("DATI VISIVI: "+visual.summary+
                        "\nPERCHÉ: "+r.why+"\nCOME: "+r.how);
                    refresh();showStep(0);updateButtonAvailability();
                });
            }catch(Exception e){ui.post(()->caption.setText("Caricamento fallito: "+e.getMessage()));}
        });
    }
    private void rank(List<Uri> uris) {
        caption.setText("Confronto locale di "+uris.size()+" scatti…");
        worker.execute(()->{
            ArrayList<Candidate> candidates=new ArrayList<>();
            for(Uri u:uris){
                try{
                    Bitmap b=decode(u);PhotoReview review=PhotoReview.inspect(b);
                    candidates.add(new Candidate(u,0,review.metrics.score,review.verdict,review.why));
                    b.recycle();
                }catch(Exception ignored){}
            }
            Collections.sort(candidates,(a,b)->Integer.compare(b.quality,a.quality));
            ui.post(()->{
                if(closing)return;
                if(candidates.isEmpty()){caption.setText("Nessuna fotografia decodificabile.");return;}
                String[] entries=new String[candidates.size()];
                for(int i=0;i<candidates.size();i++) {
                    Candidate c=candidates.get(i);
                    String name=c.uri.getLastPathSegment();
                    if(name!=null && name.length()>34)name=name.substring(name.length()-34);
                    entries[i]=(i==0?"▲ ":"")+(i+1)+". indice tecnico "+c.quality+"/100 · "+name;
                }
                new AlertDialog.Builder(this).setTitle("Ordine per parametri tecnici")
                    .setMessage("QUESTA NON È UNA CLASSIFICA ARTISTICA: luce e dettaglio non indicano il valore della scena. Tocca una foto per aprirla e usare Qwen-VL.")
                    .setItems(entries,(d,n)->load(candidates.get(n).uri))
                    .setPositiveButton("MIGLIORE",(d,w)->load(candidates.get(0).uri))
                    .setNegativeButton("CHIUDI",null).show();
                caption.setText("Analisi completata: "+candidates.size()+" foto");
            });
        });
    }
    private static final class Candidate{
        final Uri uri;final int score,quality;final String description,why;
        Candidate(Uri u,int score,int quality,String d,String why){
            uri=u;this.score=score;this.quality=quality;description=d;this.why=why;
        }
    }
    private void refresh(){
        Bitmap base=working;
        if(base==null)return;
        int generationNow=generation.incrementAndGet();
        ColorLooks.Look look=selectedLook;
        float b=brightness==null?0:brightness.getProgress()-100;
        float c=contrast==null?1:contrast.getProgress()/100f;
        float s=saturation==null?1:saturation.getProgress()/100f;
        worker.execute(()->{
            try{
                Bitmap edited=ColorLooks.render(base,look,b,c,s);
                VisualInspector.Report visual=showVisual ? VisualInspector.analyze(edited) : null;
                ui.post(()->{
                    if(closing||generation.get()!=generationNow)return;
                    display=edited;
                    image.setImageBitmap(before?original:edited);
                    if(compareOn && original!=null)comparison.setImages(original,edited);
                    crop.setBitmapSize((before?original:edited).getWidth(),(before?original:edited).getHeight());
                    lesson.configure(edited.getWidth(),edited.getHeight(),PhotoReview.autoCrop(edited));
                    if(visual!=null)visualOverlay.configure(visual,edited.getWidth(),edited.getHeight());
                });
            }catch(Exception e){ui.post(()->caption.setText("Preview: "+e.getMessage()));}
        });
    }
    private void applyCrop(){
        if(working==null)return;
        RectF r=crop.normalized();
        Bitmap result=PhotoReview.crop(working,r);
        working=result;crop.setBitmapSize(result.getWidth(),result.getHeight());
        crop.setAspect(0);crop.setCropEnabled(false);
        caption.setText("Ritaglio applicato alla copia di lavoro.");refresh();updateButtonAvailability();
    }
    private void chooseRatio(){
        new AlertDialog.Builder(this).setTitle("Ritaglio manuale: trascina gli angoli")
            .setItems(new String[]{"LIBERO","1:1","4:5","3:2","16:9"},(d,i)->{
                if(working==null)return;
                float[] ratios={0f,1f,4f/5f,3f/2f,16f/9f};
                crop.setCropEnabled(true);crop.setAspect(ratios[i]);lesson.setVisibility(View.GONE);
                updateButtonAvailability();
                caption.setText("Ritaglio "+new String[]{"libero","1:1","4:5","3:2","16:9"}[i]
                    +" · trascina gli angoli e tocca APPLICA CROP");
            }).show();
    }
    private void autoCompose(){
        if(working==null)return;
        PhotoReview review=PhotoReview.inspect(working);
        working=PhotoReview.crop(working,review.suggestedCrop);
        selectedLook=review.look.look;looks.setSelection(selectedLook.ordinal());
        crop.setCropEnabled(false);crop.setBitmapSize(working.getWidth(),working.getHeight());
        caption.setText("Automated Composer: ritaglio automatico + look "+selectedLook.label);
        lesson.setVisibility(View.VISIBLE);refresh();explain();
    }
    private void rotate(){
        if(working==null)return;
        Matrix m=new Matrix();m.postRotate(90);
        working=Bitmap.createBitmap(working,0,0,working.getWidth(),working.getHeight(),m,true);
        crop.setBitmapSize(working.getWidth(),working.getHeight());crop.setAspect(0);refresh();
    }
    private void mirror(){
        if(working==null)return;
        Matrix m=new Matrix();m.postScale(-1,1);
        working=Bitmap.createBitmap(working,0,0,working.getWidth(),working.getHeight(),m,true);
        crop.setBitmapSize(working.getWidth(),working.getHeight());crop.setAspect(0);refresh();
    }
    private void toggleBefore(){
        if(original==null)return;
        if(compareOn){compareOn=false;comparison.setVisibility(View.GONE);}
        if(showVisual){showVisual=false;visualOverlay.setVisibility(View.GONE);}
        before=!before;image.setImageBitmap(before?original:display);
        updateButtonAvailability();
        crop.setVisibility(before?View.GONE:View.VISIBLE);
        lesson.setVisibility(before?View.GONE:View.VISIBLE);
        caption.setText(before?"ORIGINALE non modificato":"PROPOSTA in anteprima");
    }
    private void restore(){
        if(original==null)return;
        stopAutoLesson();compareOn=false;comparison.setVisibility(View.GONE);
        showVisual=false;visualOverlay.setVisibility(View.GONE);
        masterSession=null;activePlan=null;
        working=original;before=false;
        selectedLook=ColorLooks.Look.NATURALE;looks.setSelection(0);
        brightness.setProgress(100);contrast.setProgress(100);saturation.setProgress(100);
        crop.setCropEnabled(false);crop.setBitmapSize(original.getWidth(),original.getHeight());
        refresh();showStep(0);updateButtonAvailability();
    }
    private void explain(){
        if(original==null||display==null)return;
        PhotoReview proposal=PhotoReview.inspect(display);
        String info=(inspection!=null?
            inspection.fullReport()+"\n\nSOGGETTO ML KIT: "+subjectDescription+
            "\nSPIEGAZIONE DEL SOGGETTO: "+subjectExplanation+"\n\n":"")+
            (visionResult!=null?
             visionResult.narrative()+"\n\n":
             "VERDETTO ARTISTICO NON DISPONIBILE: avvia ANALIZZA SCENA CON QWEN-VL.\n\n")+
            (masterSession!=null?
             masterSession.verdict+"\n\n"+masterSession.nextShot+"\n\n":
             "")+PhotoReview.compare(originalReview,proposal)
            +"\n\nPERCHÉ L'ORIGINALE PUÒ ESSERE MIGLIORATO:\n"+originalReview.why
            +"\n\nCOS'HO CAMBIATO:\n"+originalReview.comparison
            +"\n\nCOME MIGLIORARE IL PROSSIMO SCATTO:\n"+originalReview.how;
        new AlertDialog.Builder(this).setTitle("Il maestro spiega il tuo scatto")
            .setMessage(info).setPositiveButton("CLASSE VISIVA",(d,w)->showStep(0))
            .setNegativeButton("CHIUDI",null).show();
    }
    private void showStep(int index){
        lessonStep=Math.max(0,Math.min(4,index));
        stepInfo.setText(steps[lessonStep]);
        if(working==null){teacher.setText("Apri una fotografia per iniziare.");return;}
        lesson.setVisibility(View.VISIBLE);crop.setCropEnabled(false);
        visualOverlay.setVisibility(showVisual&&lessonStep<=2?View.VISIBLE:View.GONE);
        PhotoReview r=originalReview;
        if(r==null)return;
        lesson.configure(working.getWidth(),working.getHeight(),PhotoReview.autoCrop(working));
        lesson.showStep(lessonStep);
        if(!playing)lesson.pause();
        String message;
        switch(lessonStep){
            case 0:
                message="Osserva l'immagine originale. "+
                    (visionResult!=null?visionResult.narrative():
                        "Non attribuisco alcun voto artistico senza riconoscere la scena. ")+
                    " "+subjectExplanation+
                    " "+(inspection==null?"":inspection.summary)+". "+r.why;
                break;
            case 1:
                message="Osserva le alte luci e le ombre. "+
                    (inspection==null?"":inspection.explanation)+". "+r.how+
                    " Ricorda: le correzioni JPEG non recuperano dettagli mai registrati.";
                break;
            case 2:
                message="Guarda il movimento del rettangolo: ti illustro il ritaglio proposto "+
                    "per concentrare l'attenzione. Questa scelta non è sempre migliore: "+
                    "un'immagine può beneficiare anche di uno sfondo ampio e della simmetria.";
                break;
            case 3:
                message=masterSession!=null?
                    "Ho prodotto più versioni usando filtri e livelli tonali. "+
                    masterSession.best.plan.changes()+
                    ". Puoi selezionare un'altra versione usando il pulsante VERSIONI AI.":
                    "Il look consigliato è "+r.look.look.label+": "+r.look.reason+
                    ". Puoi intervenire manualmente sui controlli. Il gusto personale resta decisivo.";
                break;
            default:
                message=visionResult!=null?
                    "La mia analisi semantica della fotografia originale: "+
                    visionResult.narrative()+"; per giudicare la versione elaborata "+
                    "serve una seconda analisi visiva, non basta il contrasto.":
                    masterSession!=null?
                    "Confronto tecnico, non artistico: "+masterSession.verdict+
                    " Per il prossimo scatto, "+masterSession.nextShot+
                    " Usa SPLIT SCORREVOLE per confrontare le due immagini.":
                    "Confronta originale e proposta con SPLIT SCORREVOLE; "+
                    "il voto è un'indicazione tecnica, non un giudizio artistico assoluto.";
                break;
        }
        teacher.setText("MAESTRO · "+message);say(message);
    }
    private void changeStep(int shift){showStep(lessonStep+shift);}
    private void say(String text){
        if(voice&&speech!=null)speech.speak(text,TextToSpeech.QUEUE_FLUSH,null,"lesson-"+lessonStep);
    }

    /** Local autonomous multi-variant renderer. Never edits the source Uri or camera RAW. */
    private void autoMaster(){
        if(original==null){caption.setText("Carica prima una fotografia.");return;}
        stopAutoLesson();
        compareOn=false;comparison.setVisibility(View.GONE);
        caption.setText("MASTER AI · elaboro 9 versioni e confronto i risultati in locale…");
        Bitmap source=original;
        worker.execute(()->{
            try{
                AutoMasterEngine.Session session=AutoMasterEngine.analyze(source);
                Bitmap output=AutoMasterEngine.render(source,session.best.plan);
                ui.post(()->{
                    if(closing||original!=source)return;
                    masterSession=session;
                    activePlan=session.best.plan;
                    updateButtonAvailability();
                    working=output;before=false;
                    selectedLook=ColorLooks.Look.NATURALE;
                    looks.setSelection(0);
                    brightness.setProgress(100);contrast.setProgress(100);saturation.setProgress(100);
                    crop.setCropEnabled(false);
                    crop.setBitmapSize(output.getWidth(),output.getHeight());
                    lesson.configure(output.getWidth(),output.getHeight(),session.best.plan.rect);
                    caption.setText("AUTO LOOK · "+session.best.plan.name+
                        " · indice solo tecnico "+String.format(Locale.ITALIAN,"%.1f",session.best.quality)+"/100");
                    report.setText("VERDETTO\n"+session.verdict+
                        "\n\nMODIFICHE\n"+session.best.plan.changes()+
                        "\n\nPROSSIMO SCATTO\n"+session.nextShot);
                    refresh();
                    beginAutoLesson();
                });
            }catch(Exception e){ui.post(()->caption.setText("Rendering AI non riuscito: "+e.getMessage()));}
        });
    }
    private void showVersions(){
        AutoMasterEngine.Session session=masterSession;
        if(session==null){caption.setText("Usa prima MASTER AI AUTO per generare le varianti.");return;}
        String[] items=new String[session.ranked.size()];
        for(int i=0;i<items.length;i++) {
            AutoMasterEngine.Candidate c=session.ranked.get(i);
            items[i]=(c==session.best?"★ ":"")+c.plan.name+" · "+
                String.format(Locale.ITALIAN,"%.1f/100",c.quality);
        }
        new AlertDialog.Builder(this).setTitle("Seleziona una versione renderizzata")
            .setMessage("Le proposte sono ordinate da una metrica tecnica euristica, non dal gusto artistico.")
            .setItems(items,(d,index)->applyVariant(session.ranked.get(index)))
            .setNegativeButton("CHIUDI",null).show();
    }
    private void applyVariant(AutoMasterEngine.Candidate c){
        Bitmap source=original;
        if(source==null)return;
        caption.setText("Rendering versione "+c.plan.name+"…");
        worker.execute(()->{
            try{
                Bitmap output=AutoMasterEngine.render(source,c.plan);
                ui.post(()->{
                    if(closing||original!=source)return;
                    activePlan=c.plan;working=output;before=false;
                    updateButtonAvailability();
                    selectedLook=ColorLooks.Look.NATURALE;looks.setSelection(0);
                    brightness.setProgress(100);contrast.setProgress(100);saturation.setProgress(100);
                    crop.setCropEnabled(false);crop.setBitmapSize(output.getWidth(),output.getHeight());
                    caption.setText("Versione "+c.plan.name+" · indice tecnico "+
                        String.format(Locale.ITALIAN,"%.1f",c.quality)+"/100, non voto artistico");
                    report.setText("PERCHÉ QUESTA VERSIONE\n"+c.rationale+
                        "\n\nMODIFICHE\n"+c.plan.changes());
                    refresh();
                });
            }catch(Exception e){ui.post(()->caption.setText("Errore variante: "+e.getMessage()));}
        });
    }
    private void toggleComparison(){
        if(original==null||display==null){caption.setText("Apri prima una foto.");return;}
        compareOn=!compareOn;
        comparison.setVisibility(compareOn?View.VISIBLE:View.GONE);
        if(compareOn){showVisual=false;visualOverlay.setVisibility(View.GONE);}
        updateButtonAvailability();
        if(compareOn){
            comparison.setImages(original,display);
            lesson.setVisibility(View.GONE);
            crop.setCropEnabled(false);
            caption.setText("Trascina la linea: originale a sinistra, proposta a destra.");
        }else{
            lesson.setVisibility(View.VISIBLE);
            caption.setText("Confronto chiuso. Puoi modificare la proposta.");
        }
    }
    private void stopAutoLesson() {
        autoLesson=false;
        if(autoLessonButton!=null)AmpyButtons.setToggle(autoLessonButton,false);
        if(lessonAdvance!=null){ui.removeCallbacks(lessonAdvance);lessonAdvance=null;}
        if(speech!=null)speech.stop();
    }
    private void beginAutoLesson(){
        if(original==null){caption.setText("Carica una fotografia per la lezione.");return;}
        stopAutoLesson();
        compareOn=false;comparison.setVisibility(View.GONE);
        showVisual=true;
        playing=true;voice=true;
        getPreferences(MODE_PRIVATE).edit().putBoolean("teacher_voice",true).apply();
        autoLesson=true;
        updateButtonAvailability();
        showStep(0);
        scheduleNext();
    }
    private void scheduleNext(){
        if(!autoLesson)return;
        lessonAdvance=()->{
            if(closing||!autoLesson)return;
            if(lessonStep>=4){
                autoLesson=false;
                updateButtonAvailability();
                if(original!=null&&display!=null){
                    compareOn=true;showVisual=false;comparison.setImages(original,display);
                    updateButtonAvailability();
                    comparison.setVisibility(View.VISIBLE);
                    lesson.setVisibility(View.GONE);
                    visualOverlay.setVisibility(View.GONE);
                }
                return;
            }
            showStep(lessonStep+1);
            scheduleNext();
        };
        ui.postDelayed(lessonAdvance,11500);
    }



    /** Fallback triggers only on still photos when ML Kit cannot describe the subject. */
    private void runExtraRecognition(boolean automatic){
        Bitmap image=original;
        if(image==null)return;
        if(extraRecognition==null || extraRecognition.isWorking())return;
        if(automatic&&!extraRecognition.autoEnabled())return;
        final int generation=extraRequests;
        extraRecognition.classify(image,automatic,new ExtraRecognition.Callback(){
            @Override public void status(String message){
                if(closing||original!=image||generation!=extraRequests)return;
                modelStatus.setText("AI EXTRA · "+message);
            }
            @Override public void result(ExtraRecognition.Recognition result){
                if(closing||original!=image||generation!=extraRequests)return;
                String guess=result.describe();
                modelStatus.setText(guess);
                if(result.recognized){
                    // Preserve the original ML Kit localization when present.
                    subjectDescription="Modello aggiuntivo: "+result.label;
                    subjectExplanation=guess;
                    report.setText("SOGGETTO ML KIT / MOBILE NET\n"+
                        subjectExplanation+"\n\n"+
                        (inspection==null?"":inspection.summary)+"\n"+
                        (originalReview==null?"":originalReview.how));
                    if(voice&&autoLesson && lessonStep==0) {
                        teacher.setText("MAESTRO · "+guess);
                        say(guess);
                    }
                }else{
                    report.setText("AI EXTRA · "+guess+"\n\n"+
                        (inspection==null?"":inspection.summary));
                }
            }
        });
    }




    private void pickQwen(){
        new AlertDialog.Builder(this)
            .setTitle("Importa Qwen3-VL offline")
            .setMessage("Se hai già scaricato i modelli nella cartella Download, "+
                "scegli prima il GGUF Q4_K_M (1,11 GB) e poi ripeti per il "+
                "proiettore mmproj Q8_0 (445 MB). "+
                "Ogni file viene verificato con SHA-256. Nessuna fotografia "+
                "verrà inviata fuori dal Pixel.")
            .setPositiveButton("SCEGLI FILE",(d,w)->{
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i,PICK_QWEN);
            })
            .setNegativeButton("ANNULLA",null).show();
    }
    private void importQwen(Uri uri){
        visionStatus.setText("Qwen3-VL · Importazione modello dalla memoria Android…");
        qwenModels.importFile(uri,new QwenModelManager.Listener(){
            @Override public void onProgress(String message,long bytes,long total){
                if(!closing)visionStatus.setText("Qwen3-VL · "+message);
            }
            @Override public void onComplete(boolean success,String message){
                if(!closing)visionStatus.setText("Qwen3-VL · "+message);
            }
        });
    }

    private void downloadQwen(){
        if(qwenModels==null)return;
        new AlertDialog.Builder(this)
            .setTitle("Qwen3-VL 2B · Modello fotografico sul Pixel")
            .setMessage("Scarico da Hugging Face i GGUF ufficiali Q4_K_M e mmproj Q8_0 "+
                "(circa 1,55 GB). Puoi usare Wi-Fi o 4G/5G, ma sulla rete mobile "+
                "consumerai traffico dati. È possibile riprendere il trasferimento "+
                "se si interrompe. I due file saranno verificati con SHA-256 "+
                "prima dell'uso. NON vengono caricate fotografie sulla rete. "+
                "Il motore llama.cpp è integrato nell'app, non serve Termux.")
            .setNegativeButton("ANNULLA",null)
            .setPositiveButton("SCARICA SUL PIXEL",(dialog,which)->{
                qwenDownloadButton.setEnabled(false);
                visionStatus.setText("Qwen3-VL · Download sul telefono in corso…");
                qwenModels.download(new QwenModelManager.Listener(){
                    @Override public void onProgress(String message,long current,long total){
                        if(!closing)visionStatus.setText("QWEN3-VL · "+message);
                    }
                    @Override public void onComplete(boolean success,String message){
                        if(closing)return;
                        visionStatus.setText("QWEN3-VL · "+message);
                        qwenDownloadButton.setEnabled(true);
                        updateButtonAvailability();
                    }
                });
            }).show();
    }
    private void analyzeVision(){
        Bitmap source=original;
        if(source==null){visionStatus.setText("Apri una foto da analizzare.");return;}
        if(qwenModels==null||!qwenModels.isReady()){
            visionStatus.setText("QWEN3-VL · Modello non ancora scaricato. Premi SCARICA QWEN.");
            return;
        }
        if(!NativeQwenVision.available()){
            visionStatus.setText("Motore nativo Qwen non disponibile: "+NativeQwenVision.reason());
            return;
        }
        qwenReviewButton.setEnabled(false);
        visionStatus.setText("QWEN3-VL · Analisi nel Pixel, SENZA INTERNET. "+
            "Caricamento dei pesi e valutazione della foto…");
        worker.execute(()->{
            try{
                String json=NativeQwenVision.analyze(source,qwenModels,
                    LocalVisionReviewer.offlinePrompt());
                LocalVisionReviewer.Result result=
                    LocalVisionReviewer.parseOffline(json,"Qwen3-VL 2B (llama.cpp nativo)");
                ui.post(()->{
                    if(closing||original!=source)return;
                    visionResult=result;
                    visionStatus.setText("QWEN3-VL LOCALE · "+result.scoreLabel()+
                        " · "+result.subject);
                    subjectDescription=result.subject;
                    subjectExplanation=result.scene+" "+result.composition;
                    String description=result.narrative();
                    report.setText(description);
                    caption.setText("VERDETTO QWEN LOCALE: "+result.scoreLabel());
                    teacher.setText("MAESTRO QWEN LOCALE · "+description);
                    if(voice)say("Analisi del soggetto: "+result.subject+
                        ". "+result.scene+". "+result.composition+
                        ". Verdetto: "+result.scoreLabel()+". "+result.why);
                });
            }catch(Throwable e){
                ui.post(()->{
                    if(closing||original!=source)return;
                    visionStatus.setText("QWEN3-VL LOCALE · "+
                        (e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));
                    caption.setText("VALUTAZIONE NON DISPONIBILE · "+
                        "La foto non è stata inviata fuori dal Pixel.");
                });
            }finally{
                ui.post(()->{
                    if(!closing)qwenReviewButton.setEnabled(original!=null);
                });
            }
        });
    }

    private void showAnalysis(){
        if(inspection==null){caption.setText("Carica una foto per vedere l'analisi.");return;}
        String message=inspection.fullReport()+"\n\nSOGGETTO RILEVATO CON ML KIT\n"+
            subjectDescription+"\n"+subjectExplanation+
            "\nMODELLO AGGIUNTIVO: "+modelStatus.getText()+
            "\n\nPERCHÉ L'ORIGINALE PUÒ ESSERE MIGLIORATO\n"+
            (originalReview==null?"":originalReview.why);
        new AlertDialog.Builder(this).setTitle("Analisi tecnica e compositiva")
            .setMessage(message)
            .setPositiveButton("MOSTRA A VIDEO",(dialog,n)->{
                showVisual=true;
                visualOverlay.setVisibility(View.VISIBLE);
                lesson.setVisibility(View.GONE);
                comparison.setVisibility(View.GONE);compareOn=false;
                caption.setText("ARANCIO: dettaglio · TURCHESE: terzi · GIALLO: crop · VIOLA: possibile linea · VERDE: soggetto ML Kit");
                voice=true;updateButtonAvailability();
                say("Ora ti mostro la mappa dei dettagli. "+inspection.summary+". "+
                    inspection.improvements+" "+subjectExplanation);
            })
            .setNegativeButton("CHIUDI",null).show();
    }
    private void toggleTechnicalOverlay(){
        if(inspection==null){caption.setText("Apri una fotografia.");return;}
        showVisual=!showVisual;
        visualOverlay.setVisibility(showVisual?View.VISIBLE:View.GONE);
        updateButtonAvailability();
        if(showVisual){
            compareOn=false;comparison.setVisibility(View.GONE);
            lesson.setVisibility(View.GONE);
            caption.setText("OVERLAY VISIVO · heatmap dei contorni, terzi, crop, soggetto e linee candidate");
        }else lesson.setVisibility(View.VISIBLE);
    }
    private void autoStraighten(){
        if(working==null){caption.setText("Apri una fotografia.");return;}
        Bitmap src=working;
        caption.setText("Analizzo le linee orizzontali: ruoto solo se affidabili…");
        worker.execute(()->{
            try{
                VisualInspector.Report candidate=VisualInspector.analyze(src);
                Bitmap output=AutoOptimizer.straighten(src,
                    candidate.lineAngleDegrees,candidate.lineConfidence);
                ui.post(()->{
                    if(closing||working!=src)return;
                    if(output==src){
                        caption.setText("Nessuna correzione affidabile: evita di inclinare inutilmente lo scatto.");
                        return;
                    }
                    working=output;crop.setBitmapSize(output.getWidth(),output.getHeight());
                    caption.setText(String.format(Locale.ITALIAN,
                        "AUTO RADDRIZZA: corretti %.1f° (linea candidata, non necessariamente orizzonte)",
                        candidate.lineAngleDegrees));
                    report.setText("La rotazione corregge una linea dominante "+
                        "e implica un piccolo ritaglio. Verifica che non sia una diagonale intenzionale.");
                    refresh();
                });
            }catch(Exception e){ui.post(()->caption.setText("Raddrizzamento: "+e.getMessage()));}
        });
    }
    private void autoTone(){
        if(working==null){caption.setText("Apri una fotografia.");return;}
        Bitmap src=working;
        caption.setText("AUTO TONI · correzione locale prudente…");
        worker.execute(()->{
            try{
                Bitmap corrected=AutoOptimizer.tone(src,PhotoAnalyzer.fromBitmap(src));
                ui.post(()->{
                    if(closing||working!=src)return;
                    working=corrected;
                    report.setText("AUTO TONI: schiarimento selettivo delle ombre e "+
                        "compressione delle zone luminose, nei limiti del JPEG. "+
                        "Non recupera dettagli mancanti dal file.");
                    caption.setText("Correzione tonale applicata alla copia di lavoro.");
                    refresh();
                });
            }catch(Exception e){ui.post(()->caption.setText("Auto Toni: "+e.getMessage()));}
        });
    }
    private void chooseResize(){
        if(working==null){caption.setText("Apri una fotografia.");return;}
        AutoOptimizer.ExportPreset[] presets=AutoOptimizer.ExportPreset.values();
        new AlertDialog.Builder(this).setTitle("AutoResize · formato di destinazione")
            .setItems(AutoOptimizer.ExportPreset.labels(),(dialog,index)->{
                AutoOptimizer.ExportPreset preset=presets[index];
                if(preset==AutoOptimizer.ExportPreset.ORIGINAL){
                    caption.setText("Formato originale: nessun resize.");
                    return;
                }
                new AlertDialog.Builder(this).setTitle("Come adattare il formato?")
                    .setItems(new String[]{"RIEMPI: crop guidato dall'area di dettagli",
                            "ADATTA: mantieni tutta la foto, aggiungi bande"},
                        (d,mode)->resizeNow(preset,mode==0)).show();
            }).show();
    }
    private void resizeNow(AutoOptimizer.ExportPreset preset,boolean fill){
        Bitmap source=working;
        if(source==null)return;
        caption.setText("AutoResize "+preset.label+"…");
        worker.execute(()->{
            try{
                VisualInspector.Report visual=VisualInspector.analyze(source);
                Bitmap result=AutoOptimizer.resize(source,preset,fill,visual.focusX,visual.focusY);
                ui.post(()->{
                    if(closing||working!=source)return;
                    working=result;crop.setBitmapSize(result.getWidth(),result.getHeight());
                    crop.setCropEnabled(false);
                    caption.setText("Dimensioni esportazione: "+result.getWidth()+"×"+result.getHeight());
                    report.setText(AutoOptimizer.describeResize(source,preset,fill));
                    refresh();
                });
            }catch(Exception e){ui.post(()->caption.setText("AutoResize fallito: "+e.getMessage()));}
        });
    }
    private void autoPro(){
        if(original==null){caption.setText("Carica una fotografia.");return;}
        stopAutoLesson();
        Bitmap source=original;
        caption.setText("AUTO PRO: varianti, raddrizzamento e correzione tonale…");
        worker.execute(()->{
            try{
                VisualInspector.Report visual=VisualInspector.analyze(source);
                AutoMasterEngine.Session session=AutoMasterEngine.analyze(source);
                Bitmap candidate=AutoMasterEngine.render(source,session.best.plan);
                Bitmap straight=AutoOptimizer.straighten(candidate,
                    visual.lineAngleDegrees,visual.lineConfidence);
                Bitmap result=AutoOptimizer.tone(straight,PhotoAnalyzer.fromBitmap(straight));
                PhotoReview finalReview=PhotoReview.inspect(result);
                PhotoReview initialReview=PhotoReview.inspect(source);
                int delta=finalReview.metrics.score-initialReview.metrics.score;
                String finalVerdict=PhotoReview.compare(initialReview,finalReview);
                ui.post(()->{
                    if(closing||original!=source)return;
                    masterSession=session;activePlan=session.best.plan;
                    working=result;before=false;compareOn=false;
                    updateButtonAvailability();
                    comparison.setVisibility(View.GONE);
                    selectedLook=ColorLooks.Look.NATURALE;looks.setSelection(0);
                    brightness.setProgress(100);contrast.setProgress(100);saturation.setProgress(100);
                    crop.setCropEnabled(false);crop.setBitmapSize(result.getWidth(),result.getHeight());
                    report.setText("AUTO PRO COMPLETO · VERDETTO RICALCOLATO\n"+
                        finalVerdict+
                        "\nDifferenza punteggio tecnico preview: "+(delta>=0?"+":"")+delta+"/100."+
                        "\n\nRENDERING: "+session.best.plan.changes()+
                        "\nRADDRIZZAMENTO: "+
                        (straight==candidate?"nessuna linea affidabile":"applicato con ritaglio protettivo")+
                        "\nTONI: regolazione moderata di luci e ombre."+
                        "\nNessun dettaglio RAW ricreato o generato.");
                    caption.setText("AUTO PRO · "+session.best.plan.name+" · "+
                        "indice tecnico post-elaborazione "+finalReview.metrics.score+
                        "/100; giudizio artistico non assegnato");
                    refresh();beginAutoLesson();
                });
            }catch(Exception e){ui.post(()->caption.setText("Auto Pro: "+e.getMessage()));}
        });
    }

    private void save(){
        if(display==null){caption.setText("Nessuna immagine da salvare");return;}
        Bitmap captured=display;
        worker.execute(()->{
            try{
                ContentValues cv=new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME,
                    "AmpyEOS_"+new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date())+".jpg");
                cv.put(MediaStore.MediaColumns.MIME_TYPE,"image/jpeg");
                if(Build.VERSION.SDK_INT>=29)cv.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/AmpyEOS");
                Uri uri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cv);
                if(uri==null)throw new Exception("Impossibile creare il file");
                try(OutputStream out=getContentResolver().openOutputStream(uri)){
                    if(out==null || !captured.compress(Bitmap.CompressFormat.JPEG,94,out))throw new Exception("JPEG non salvato");
                }
                ui.post(()->caption.setText("COPIA SALVATA in Pictures/AmpyEOS · originale preservato"));
            }catch(Exception e){ui.post(()->caption.setText("Salvataggio fallito: "+e.getMessage()));}
        });
    }
    @Override protected void onDestroy(){
        closing=true;stopAutoLesson();
        if(speech!=null){speech.stop();speech.shutdown();}
        if(stillSubject!=null)stillSubject.close();
        if(extraRecognition!=null)extraRecognition.close();
        if(qwenModels!=null)qwenModels.close();
        // Stop an active native decode before Activity resources disappear.
        if(NativeQwenVision.available())NativeQwenVision.cancel();
        worker.shutdownNow();
        super.onDestroy();
    }
}
