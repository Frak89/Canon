package com.ampyeos.canon;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * User-started foreground download independent of EditorActivity lifecycle.
 * Stores exclusively verified Qwen GGUF weights in public Downloads, never
 * photos. Once begun it continues when the user switches apps/screen off.
 *
 * Android 15+ dataSync FGS has an OS-enforced six-hour/day budget.
 * System-initiated process kills may still interrupt the transfer; .part
 * bytes are left in Downloads so a later user tap can resume with Range.
 */
public final class QwenDownloadService extends Service {
    public static final String ACTION_START="com.ampyeos.canon.QWEN_DOWNLOAD_START";
    public static final String ACTION_CANCEL="com.ampyeos.canon.QWEN_DOWNLOAD_CANCEL";
    public static final String PREFS="qwen_background_transfer";
    public static final String KEY_ACTIVE="active";
    public static final String KEY_MESSAGE="message";
    public static final String KEY_BYTES="bytes";
    public static final String KEY_TOTAL="total";
    public static final String KEY_SUCCESS="success";
    public static final String KEY_FINISHED="finished";
    public static final String KEY_UPDATED="updated_elapsed";
    private static final String CHANNEL="ampyeos_qwen_download";
    private static final int NOTIFICATION_ID=2071;
    private QwenModelManager manager;
    private NotificationManager notifications;
    private volatile boolean active=false;
    private long lastNotification=0;
    private long lastBytes=-1;
    private final Handler heartbeatHandler=new Handler(Looper.getMainLooper());
    private final Runnable heartbeat=new Runnable(){
        @Override public void run(){
            if(!active)return;
            status(QwenDownloadService.this).edit()
                .putLong(KEY_UPDATED,SystemClock.elapsedRealtime()).apply();
            heartbeatHandler.postDelayed(this,5000);
        }
    };

    public static void begin(Context context) {
        Intent intent=new Intent(context,QwenDownloadService.class);
        intent.setAction(ACTION_START);
        context.startForegroundService(intent);
    }
    public static void cancelDownload(Context context) {
        Intent intent=new Intent(context,QwenDownloadService.class);
        intent.setAction(ACTION_CANCEL);
        // Only used when a user has an already-running foreground service.
        context.startService(intent);
    }
    public static SharedPreferences status(Context context){
        return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    public static boolean isActive(Context context){
        SharedPreferences p=status(context);
        long stamp=p.getLong(KEY_UPDATED,0);
        return p.getBoolean(KEY_ACTIVE,false)&&stamp>0 &&
            SystemClock.elapsedRealtime()-stamp<20000;
    }

    @Override public void onCreate(){
        super.onCreate();
        notifications=(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel=new NotificationChannel(CHANNEL,
            "Qwen3-VL · download locale",
            NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Progresso del modello Qwen scaricato in Download/AmpyEOS.");
        notifications.createNotificationChannel(channel);
        manager=new QwenModelManager(getApplicationContext());
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        String action=intent==null?ACTION_START:intent.getAction();
        if(ACTION_CANCEL.equals(action)){
            if(manager!=null)manager.cancel();
            if(!active)stopSelf(startId);
            else update("Annullamento richiesto · trasferimento parziale conservato",
                lastBytes<0?0:lastBytes,QwenModelManager.TOTAL_BYTES);
            return START_NOT_STICKY;
        }
        if(active)return START_NOT_STICKY;
        active=true;
        heartbeatHandler.post(heartbeat);
        // startForeground immediately, before lengthy I/O or hashing.
        startForeground(NOTIFICATION_ID,
            buildNotification("Ricerca dei GGUF già presenti in Download",0,
                QwenModelManager.TOTAL_BYTES,true),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        writeStatus(true,"Ricerca modelli Qwen già presenti in Download",0,
            QwenModelManager.TOTAL_BYTES,false,false);
        manager.download(new QwenModelManager.Listener(){
            @Override public void onProgress(String message,long bytes,long total) {
                if(!active)return;
                update(message,bytes,total);
            }
            @Override public void onComplete(boolean success,String message){
                active=false;
                writeStatus(false,message,0,QwenModelManager.TOTAL_BYTES,success,true);
                // Keep a final notification describing actual failure/success.
                notifications.notify(NOTIFICATION_ID,
                    buildNotification(message,0,1,false));
                stopForeground(STOP_FOREGROUND_DETACH);
                stopSelf();
            }
        });
        return START_NOT_STICKY;
    }

    private Notification buildNotification(String message,long bytes,long total,
                                           boolean running){
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent content=PendingIntent.getActivity(this,12,open,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(running?"AmpyEOS · Qwen in Download":
                "AmpyEOS · trasferimento terminato")
            .setContentText(message)
            .setStyle(new Notification.BigTextStyle().bigText(message))
            .setOngoing(running)
            .setAutoCancel(!running)
            .setContentIntent(content)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_PROGRESS);
        if(running){
            if(total>0 && bytes>0){
                int percent=(int)Math.min(100,bytes*100L/total);
                builder.setProgress(100,percent,false);
                builder.setSubText(percent+"% · Download/AmpyEOS/Qwen3-VL");
            }else builder.setProgress(0,0,true);
            Intent cancel=new Intent(this,QwenDownloadService.class);
            cancel.setAction(ACTION_CANCEL);
            PendingIntent stop=PendingIntent.getService(this,13,cancel,
                PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            builder.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,"Sospendi",stop).build());
        }else builder.setSmallIcon(android.R.drawable.stat_sys_download_done);
        return builder.build();
    }
    private void update(String message,long bytes,long total){
        writeStatus(true,message,bytes,total,false,false);
        long now=SystemClock.elapsedRealtime();
        if(now-lastNotification<900 && (bytes<=lastBytes||bytes-lastBytes<4L*1024*1024))
            return;
        lastNotification=now;lastBytes=bytes;
        notifications.notify(NOTIFICATION_ID,buildNotification(message,bytes,total,true));
    }
    private void writeStatus(boolean running,String message,long bytes,long total,
                             boolean success,boolean finished){
        status(this).edit().putBoolean(KEY_ACTIVE,running)
            .putString(KEY_MESSAGE,message)
            .putLong(KEY_BYTES,bytes)
            .putLong(KEY_TOTAL,total)
            .putBoolean(KEY_SUCCESS,success)
            .putBoolean(KEY_FINISHED,finished)
            .putLong(KEY_UPDATED,SystemClock.elapsedRealtime()).apply();
    }
    /** Android 15+ enforces the dataSync foreground-service six-hour quota. */
    @Override public void onTimeout(int startId,int fgsType){
        if(manager!=null)manager.cancel();
        writeStatus(false,"Android ha raggiunto il limite per download in background. "+
            "I file .part restano in Download: riprendi manualmente.",0,
            QwenModelManager.TOTAL_BYTES,false,true);
        active=false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }
    @Override public void onDestroy(){
        heartbeatHandler.removeCallbacks(heartbeat);
        if(active){
            writeStatus(false,"Download interrotto dal sistema. "+
                "I file parziali in Download restano disponibili per la ripresa.",
                lastBytes<0?0:lastBytes,QwenModelManager.TOTAL_BYTES,false,true);
        }
        active=false;
        if(manager!=null)manager.close();
        super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent){return null;}
}
