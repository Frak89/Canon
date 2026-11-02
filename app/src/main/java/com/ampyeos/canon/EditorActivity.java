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
    private static final int PICK=1, PICK_GROUP=2, CYAN=0xff43eadb, BG=0xff080f1a;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final AtomicInteger generation=new AtomicInteger();
    private Bitmap original, working, display;
    private Uri originUri;
    private ImageView image;
    private CropView crop;
    private LessonOverlay lesson;
    private BeforeAfterView comparison;
    private AutoMasterEngine.Session masterSession;
    private AutoMasterEngine.Plan activePlan;
    private boolean compareOn=false,autoLesson=false;
    private Runnable lessonAdvance;
    private Spinner looks;
    private SeekBar brightness,contrast,saturation;
    private TextView caption,report,teacher,stepInfo;
    private boolean before=false, voice=false, playing=true;
    private TextToSpeech speech;
    private int lessonStep=0;
    private PhotoReview originalReview;
    private ColorLooks.Look selectedLook=ColorLooks.Look.NATURALE;
    private volatile boolean closing=false;
    private final String[] steps={"1 · OSSERVA","2 · LUCE","3 · COMPOSIZIONE","4 · COLORE","5 · CONFRONTA"};

    private int dp(int x){return Math.round(getResources().getDisplayMetrics().density*x);}
    private TextView txt(String value,int size){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);
        t.setPadding(dp(8),dp(6),dp(8),dp(6));return t;
    }
    private Button button(String name,LinearLayout row,View.OnClickListener listener){
        Button b=new Button(this);b.setText(name);b.setTextSize(12);b.setAllCaps(false);
        b.setOnClickListener(listener);row.addView(b,new LinearLayout.LayoutParams(dp(125),dp(48)));
        return b;
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
        TextView title=txt("AMPY▲EOS  /  COLOR LAB + CLASSROOM",18);title.setTextColor(CYAN);
        root.addView(title);
        FrameLayout area=new FrameLayout(this);area.setBackgroundColor(Color.BLACK);
        root.addView(area,new LinearLayout.LayoutParams(-1,0,1));
        image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        area.addView(image,new FrameLayout.LayoutParams(-1,-1));
        crop=new CropView(this);crop.setCropEnabled(false);
        area.addView(crop,new FrameLayout.LayoutParams(-1,-1));
        lesson=new LessonOverlay(this);
        area.addView(lesson,new FrameLayout.LayoutParams(-1,-1));
        comparison=new BeforeAfterView(this);
        comparison.setVisibility(View.GONE);
        area.addView(comparison,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroller=new ScrollView(this);
        root.addView(scroller,new LinearLayout.LayoutParams(-1,dp(370)));
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(panel);
        caption=txt("Apri una foto oppure confronta più scatti.",13);caption.setTextColor(CYAN);panel.addView(caption);
        report=txt("L'originale non viene mai sovrascritto.",12);panel.addView(report);
        LinearLayout tools=row(panel);
        button("APRI FOTO",tools,v->pick(false));
        button("SCEGLI MIGLIORE",tools,v->pick(true));
        button("MASTER AI AUTO",tools,v->autoMaster());
        button("VERSIONI AI",tools,v->showVersions());
        button("AUTO COMPONI",tools,v->autoCompose());
        button("RITAGLIA",tools,v->chooseRatio());
        button("APPLICA CROP",tools,v->applyCrop());
        button("RUOTA 90°",tools,v->rotate());
        button("SPECCHIO",tools,v->mirror());
        button("PRIMA / DOPO",tools,v->toggleBefore());
        button("SPLIT SCORREVOLE",tools,v->toggleComparison());
        button("SPIEGA",tools,v->explain());
        button("SALVA COPIA",tools,v->save());
        button("RIPRISTINA",tools,v->restore());

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
        button("PLAY / PAUSA",classroom,v->{playing=!playing;if(playing)lesson.play();else lesson.pause();});
        button("VOCE ON/OFF",classroom,v->{
            voice=!voice;getPreferences(MODE_PRIVATE).edit().putBoolean("teacher_voice",voice).apply();
            caption.setText(voice?"LEZIONE VOCALE ATTIVA":"LEZIONE VOCALE DISATTIVA");
            if(!voice&&speech!=null)speech.stop();
            else say(teacher.getText().toString());
        });
        button("LEZIONE AUTOMATICA",classroom,v->beginAutoLesson());
        stepInfo=txt(steps[0],12);panel.addView(stepInfo);
        voice=getPreferences(MODE_PRIVATE).getBoolean("teacher_voice",true);
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
                ui.post(()->{
                    if(closing)return;
                    stopAutoLesson();comparison.setVisibility(View.GONE);compareOn=false;
                    masterSession=null;activePlan=null;
                    original=b;working=b;originalReview=r;
                    selectedLook=r.look.look;
                    looks.setSelection(selectedLook.ordinal());
                    brightness.setProgress(100);contrast.setProgress(100);saturation.setProgress(100);
                    before=false;crop.setBitmapSize(b.getWidth(),b.getHeight());
                    crop.setCropEnabled(false);crop.setAspect(0);
                    lesson.configure(b.getWidth(),b.getHeight(),r.suggestedCrop);
                    caption.setText("VALUTAZIONE INIZIALE: "+r.score10+"/10 · "+r.verdict);
                    report.setText("PERCHÉ: "+r.why+"\nCOME: "+r.how);
                    refresh();showStep(0);
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
                    candidates.add(new Candidate(u,review.score10,review.metrics.score,review.verdict,review.why));
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
                    entries[i]=(i==0?"★ ":"")+(i+1)+". "+c.score+"/10 · "+name;
                }
                new AlertDialog.Builder(this).setTitle("Scatti migliori · classifica tecnica")
                    .setMessage("Il voto valuta l'immagine, non il contenuto artistico. Tocca una foto per vedere perché è stata scelta.")
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
                ui.post(()->{
                    if(closing||generation.get()!=generationNow)return;
                    display=edited;
                    image.setImageBitmap(before?original:edited);
                    if(compareOn && original!=null)comparison.setImages(original,edited);
                    crop.setBitmapSize((before?original:edited).getWidth(),(before?original:edited).getHeight());
                    lesson.configure(edited.getWidth(),edited.getHeight(),PhotoReview.autoCrop(edited));
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
        caption.setText("Ritaglio applicato alla copia di lavoro.");refresh();
    }
    private void chooseRatio(){
        new AlertDialog.Builder(this).setTitle("Ritaglio manuale: trascina gli angoli")
            .setItems(new String[]{"LIBERO","1:1","4:5","3:2","16:9"},(d,i)->{
                if(working==null)return;
                float[] ratios={0f,1f,4f/5f,3f/2f,16f/9f};
                crop.setCropEnabled(true);crop.setAspect(ratios[i]);lesson.setVisibility(View.GONE);
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
        before=!before;image.setImageBitmap(before?original:display);
        crop.setVisibility(before?View.GONE:View.VISIBLE);
        lesson.setVisibility(before?View.GONE:View.VISIBLE);
        caption.setText(before?"ORIGINALE non modificato":"PROPOSTA in anteprima");
    }
    private void restore(){
        if(original==null)return;
        stopAutoLesson();compareOn=false;comparison.setVisibility(View.GONE);
        masterSession=null;activePlan=null;
        working=original;before=false;
        selectedLook=ColorLooks.Look.NATURALE;looks.setSelection(0);
        brightness.setProgress(100);contrast.setProgress(100);saturation.setProgress(100);
        crop.setCropEnabled(false);crop.setBitmapSize(original.getWidth(),original.getHeight());
        refresh();showStep(0);
    }
    private void explain(){
        if(original==null||display==null)return;
        PhotoReview proposal=PhotoReview.inspect(display);
        String info=(masterSession!=null?
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
        PhotoReview r=originalReview;
        if(r==null)return;
        lesson.configure(working.getWidth(),working.getHeight(),PhotoReview.autoCrop(working));
        lesson.showStep(lessonStep);
        if(!playing)lesson.pause();
        String message;
        switch(lessonStep){
            case 0:
                message="Osserva l'immagine originale. La prima valutazione è "+r.score10+
                    " su dieci. "+r.verdict+". "+r.why;
                break;
            case 1:
                message="Osserva le alte luci e le ombre. "+r.how+
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
                message=masterSession!=null?
                    "Il mio verdetto finale: "+masterSession.verdict+
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
                    working=output;before=false;
                    selectedLook=ColorLooks.Look.NATURALE;
                    looks.setSelection(0);
                    brightness.setProgress(100);contrast.setProgress(100);saturation.setProgress(100);
                    crop.setCropEnabled(false);
                    crop.setBitmapSize(output.getWidth(),output.getHeight());
                    lesson.configure(output.getWidth(),output.getHeight(),session.best.plan.rect);
                    caption.setText("MASTER AI · "+session.best.plan.name+
                        " · punteggio euristico "+session.best.vote+"/10");
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
                    selectedLook=ColorLooks.Look.NATURALE;looks.setSelection(0);
                    brightness.setProgress(100);contrast.setProgress(100);saturation.setProgress(100);
                    crop.setCropEnabled(false);crop.setBitmapSize(output.getWidth(),output.getHeight());
                    caption.setText("Versione "+c.plan.name+" · voto tecnico stimato "+c.vote+"/10");
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
        if(lessonAdvance!=null){ui.removeCallbacks(lessonAdvance);lessonAdvance=null;}
        if(speech!=null)speech.stop();
    }
    private void beginAutoLesson(){
        if(original==null){caption.setText("Carica una fotografia per la lezione.");return;}
        stopAutoLesson();
        compareOn=false;comparison.setVisibility(View.GONE);
        playing=true;voice=true;
        getPreferences(MODE_PRIVATE).edit().putBoolean("teacher_voice",true).apply();
        autoLesson=true;
        showStep(0);
        scheduleNext();
    }
    private void scheduleNext(){
        if(!autoLesson)return;
        lessonAdvance=()->{
            if(closing||!autoLesson)return;
            if(lessonStep>=4){
                autoLesson=false;
                if(original!=null&&display!=null){
                    compareOn=true;comparison.setImages(original,display);
                    comparison.setVisibility(View.VISIBLE);
                    lesson.setVisibility(View.GONE);
                }
                return;
            }
            showStep(lessonStep+1);
            scheduleNext();
        };
        ui.postDelayed(lessonAdvance,11500);
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
        worker.shutdownNow();
        super.onDestroy();
    }
}
