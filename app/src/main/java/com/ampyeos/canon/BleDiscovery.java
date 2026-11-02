package com.ampyeos.canon;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.HashSet;
import java.util.Set;

/** Optional BLE discovery only. Pairing/handover belongs to a validated Canon protocol session. */
@SuppressLint("MissingPermission")
public final class BleDiscovery {
    public interface Listener {void found(String name,String mac,int rssi);void error(String reason);}
    private BluetoothLeScanner scanner;
    private ScanCallback callback;
    private final Set<String> seen=new HashSet<>();
    public void stop(){
        if(scanner!=null && callback!=null) {
            try{scanner.stopScan(callback);}catch(Exception ignored){}
        }
        scanner=null;callback=null;
    }
    public void start(Context ctx,Listener listener) {
        stop();seen.clear();
        if(Build.VERSION.SDK_INT>=31 && (ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)) {
            listener.error("Consenti ricerca e connessione Bluetooth.");return;
        }
        BluetoothManager bm=(BluetoothManager)ctx.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter adapter=bm==null?null:bm.getAdapter();
        if(adapter==null||!adapter.isEnabled()) {listener.error("Attiva Bluetooth prima di cercare la Canon.");return;}
        scanner=adapter.getBluetoothLeScanner();
        if(scanner==null){listener.error("Scanner BLE non disponibile.");return;}
        callback=new ScanCallback(){
            @Override public void onScanResult(int type,ScanResult r){
                String name=r.getScanRecord()!=null?r.getScanRecord().getDeviceName():null;
                if(name==null)try{name=r.getDevice().getName();}catch(Exception ignored){}
                if(name==null)return;
                String test=name.toLowerCase(java.util.Locale.ROOT);
                if(!test.contains("canon")&&!test.contains("eos"))return;
                String mac=r.getDevice().getAddress();
                if(seen.add(mac))listener.found(name,mac,r.getRssi());
            }
            @Override public void onScanFailed(int code){listener.error("Ricerca BLE non riuscita: "+code);}
        };
        try{scanner.startScan(null,new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),callback);}
        catch(Exception e){listener.error("Errore BLE: "+e.getMessage());stop();}
    }
}
