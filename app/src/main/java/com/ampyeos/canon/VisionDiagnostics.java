package com.ampyeos.canon;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/**
 * Privacy-first Qwen diagnostics: no image pixels, EXIF, file path, prompt,
 * subject text, or model response is ever recorded.
 */
public final class VisionDiagnostics {
    private static final int MAX_EVENTS=80;
    private final Context context;
    private final ArrayDeque<String> history=new ArrayDeque<>();
    private final long created=SystemClock.elapsedRealtime();
    public VisionDiagnostics(Context ctx){context=ctx.getApplicationContext();}
    public synchronized void add(String event){
        if(event==null)return;
        if(history.size()>=MAX_EVENTS)history.removeFirst();
        history.addLast(String.format(Locale.ITALIAN,"+%.1fs · %s",
            (SystemClock.elapsedRealtime()-created)/1000f, sanitize(event)));
    }
    private static String sanitize(String text) {
        return text.replaceAll("[\\r\\n\\t]"," ").replaceAll(
            "[/\\\\][^\\s]+","[percorso omesso]").substring(0,Math.min(text.length(),180));
    }
    public String memory() {
        ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        if(manager==null)return "Memoria: servizio Android non disponibile";
        ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(m);
        Runtime runtime=Runtime.getRuntime();
        long heap=runtime.totalMemory()-runtime.freeMemory();
        String state="RAM libera sistema "+(m.availMem/1024/1024)+" MiB / "+
            (m.totalMem/1024/1024)+" MiB · heap Java "+(heap/1024/1024)+" MiB";
        if(m.lowMemory)state+=" · Android segnala pressione di memoria";
        PowerManager pm=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
        if(pm!=null && Build.VERSION.SDK_INT>=29){
            state+=" · livello termico Android "+pm.getCurrentThermalStatus();
        }
        return state;
    }
    public boolean lowMemory(){
        ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
        if(manager==null)return false;
        ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        return info.lowMemory || info.availMem<768L*1024*1024;
    }
    public boolean severeThermal(){
        PowerManager manager=(PowerManager)context.getSystemService(Context.POWER_SERVICE);
        return manager!=null && Build.VERSION.SDK_INT>=29
            && manager.getCurrentThermalStatus()>=PowerManager.THERMAL_STATUS_SEVERE;
    }
    public synchronized String report(){
        StringBuilder b=new StringBuilder("AMPYEOS QWEN / DIAGNOSTICA LOCALE\n");
        b.append("Versione app: 0.8.0\n")
            .append("Dispositivo: Android ").append(Build.VERSION.RELEASE)
            .append(" / ").append(Build.SUPPORTED_ABIS.length>0?Build.SUPPORTED_ABIS[0]:"unknown")
            .append("\n").append(memory())
            .append("\nFoto, nomi file, percorsi privati e risultati IA non sono registrati.")
            .append("\nEVENTI:\n");
        for(String event:history)b.append(event).append('\n');
        return b.toString();
    }
}
