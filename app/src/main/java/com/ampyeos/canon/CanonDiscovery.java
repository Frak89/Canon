package com.ampyeos.canon;

import android.content.Context;
import android.net.wifi.WifiManager;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/** AmpyLink-inspired SSDP/UPnP discovery. Returns candidate LAN hosts, not verified cameras. */
public final class CanonDiscovery {
    private CanonDiscovery(){}
    public static String discover(Context ctx) throws Exception {
        WifiManager wifi=(WifiManager)ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiManager.MulticastLock lock=null;
        if(wifi!=null) {
            lock=wifi.createMulticastLock("ampyeos-ssdp");
            lock.setReferenceCounted(false);lock.acquire();
        }
        Set<String> ips=new LinkedHashSet<>();
        try(MulticastSocket s=new MulticastSocket()) {
            s.setSoTimeout(1000);
            String msg="M-SEARCH * HTTP/1.1\r\nHOST:239.255.255.250:1900\r\n"
               +"MAN:\"ssdp:discover\"\r\nMX:1\r\nST:ssdp:all\r\n\r\n";
            byte[] q=msg.getBytes(StandardCharsets.US_ASCII);
            s.send(new DatagramPacket(q,q.length,InetAddress.getByName("239.255.255.250"),1900));
            long end=System.currentTimeMillis()+2300;
            while(System.currentTimeMillis()<end) {
                try {
                    byte[] buf=new byte[4096];DatagramPacket p=new DatagramPacket(buf,buf.length);
                    s.receive(p);
                    String raw=new String(p.getData(),0,p.getLength(),StandardCharsets.US_ASCII).toLowerCase();
                    // Canon UPnP advertisements vary: treat results as candidates, never blindly trust SSDP.
                    if(raw.contains("canon")||raw.contains("eos")||raw.contains("ptp"))
                        ips.add(p.getAddress().getHostAddress());
                }catch(java.net.SocketTimeoutException e){/* try until deadline */}
            }
        }finally{if(lock!=null && lock.isHeld())lock.release();}
        return String.join(", ",ips);
    }
}
