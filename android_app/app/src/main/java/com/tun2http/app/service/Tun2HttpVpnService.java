package com.tun2http.app.service;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.VpnService;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import com.tun2http.app.R;
import com.tun2http.app.utils.IPUtil;
import com.tun2http.app.utils.Util;

import java.io.IOException;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;


public class Tun2HttpVpnService extends VpnService {
    private static final String TAG = "Tun2Http.Service";
    private static final String ACTION_START = "start";
    private static final String ACTION_STOP = "stop";
    public static final String PREF_PROXY_HOST = "pref_proxy_host";
    public static final String PREF_PROXY_PORT = "pref_proxy_port";
    public static final String PREF_RUNNING = "pref_running";


    private Tun2HttpVpnService.Builder lastBuilder = null;
    private ParcelFileDescriptor vpn = null;

    static {
        System.loadLibrary("tun2http");
    }


    private static volatile PowerManager.WakeLock wlInstance = null;

    private native void jni_init();

    private native void jni_start(int tun, boolean fwd53, int rcode, String proxyIp, int proxyPort);

    private native void jni_stop(int tun);

    private native int jni_get_mtu();

    private native int jni_done();


    synchronized private static PowerManager.WakeLock getLock(Context context) {
        if (wlInstance == null) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            wlInstance = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, context.getString(R.string.app_name) + " wakelock");
            wlInstance.setReferenceCounted(true);
        }
        return wlInstance;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new ServiceBinder();
    }

    public class ServiceBinder extends Binder {
        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            // see Implementation of android.net.VpnService.Callback.onTransact()
            if (code == IBinder.LAST_CALL_TRANSACTION) {
                onRevoke();
                return true;
            }
            return super.onTransact(code, data, reply, flags);
        }

        public Tun2HttpVpnService getService() {
            return Tun2HttpVpnService.this;
        }
    }

    public boolean isRunning() {
        return vpn != null;
    }

    private void start() {
        if (vpn == null) {
            lastBuilder = getBuilder();
            vpn = startVPN(lastBuilder);
            if (vpn == null)
                throw new IllegalStateException(getString((R.string.msg_start_failed)));

            startNative(vpn);
        }
    }

    private void stop() {
        if (vpn != null) {
            stopNative(vpn);
            stopVPN(vpn);
            vpn = null;
        }
        stopForeground(true);
    }


    @Override
    public void onRevoke() {
        Log.i(TAG, "Revoke");

        stop();
        vpn = null;

        super.onRevoke();
    }

    private ParcelFileDescriptor startVPN(Builder builder) throws SecurityException {
        try {
            return builder.establish();
        } catch (SecurityException ex) {
            throw ex;
        } catch (Throwable ex) {
            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
            return null;
        }
    }

    private Builder getBuilder() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Build VPN service
        Builder builder = new Builder();
        builder.setSession(getString(R.string.app_name));

        // VPN address
        String vpn4 = prefs.getString("vpn4", "10.1.10.1");
        builder.addAddress(vpn4, 32);
        String vpn6 = prefs.getString("vpn6", "fd00:1:fd00:1:fd00:1:fd00:1");
        builder.addAddress(vpn6, 128);

        builder.addRoute("0.0.0.0", 0);

        builder.addRoute("0:0:0:0:0:0:0:0", 0);

        // MTU
        int mtu = jni_get_mtu();
        Log.i(TAG, "MTU=" + mtu);
        builder.setMtu(mtu);

        // Add list of allowed applications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                builder.addAllowedApplication("com.android.chrome");
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            }
        }

        // Add list of allowed applications
        return builder;
    }


    private void startNative(ParcelFileDescriptor vpn) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String proxyHost = prefs.getString(PREF_PROXY_HOST, "");
        int proxyPort = prefs.getInt(PREF_PROXY_PORT, 0);
        if (proxyPort != 0 && !TextUtils.isEmpty(proxyHost)) {
            jni_start(vpn.getFd(), false, 3, proxyHost, proxyPort);

            prefs.edit().putBoolean(PREF_RUNNING, true).apply();
        }
    }

    private void stopNative(ParcelFileDescriptor vpn) {
        try {
            jni_stop(vpn.getFd());

        } catch (Throwable ex) {
            // File descriptor might be closed
            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
            jni_stop(-1);
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean(PREF_RUNNING, false).apply();
    }


    private void stopVPN(ParcelFileDescriptor pfd) {
        Log.i(TAG, "Stopping");
        try {
            pfd.close();
        } catch (IOException ex) {
            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
        }
    }

    // Called from native code
    private void nativeExit(String reason) {
        Log.w(TAG, "Native exit reason=" + reason);
        if (reason != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit().putBoolean("enabled", false).apply();
        }
    }

    // Called from native code
    private void nativeError(int error, String message) {
        Log.w(TAG, "Native error " + error + ": " + message);
    }


    private boolean isSupported(int protocol) {
        return (protocol == 1 /* ICMPv4 */ ||
                protocol == 59 /* ICMPv6 */ ||
                protocol == 6 /* TCP */ ||
                protocol == 17 /* UDP */);
    }


    @Override
    public void onCreate() {

        // Native init
        jni_init();

        super.onCreate();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received " + intent);
        // Handle service restart
        if (intent == null) {
            return START_STICKY;
        }

        if (ACTION_START.equals(intent.getAction())) {
            start();
        }
        if (ACTION_STOP.equals(intent.getAction())) {
            stop();
        }
        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        Log.i(TAG, "Destroy");

        try {
            if (vpn != null) {
                stopNative(vpn);
                stopVPN(vpn);
                vpn = null;
            }
        } catch (Throwable ex) {
            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
        }

        jni_done();

        super.onDestroy();
    }

    private class Builder extends VpnService.Builder {
        private NetworkInfo networkInfo;
        private int mtu;
        private List<String> listAddress = new ArrayList<>();
        private List<String> listRoute = new ArrayList<>();
        private List<InetAddress> listDns = new ArrayList<>();

        private Builder() {
            super();
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            networkInfo = cm.getActiveNetworkInfo();
        }

        @Override
        public VpnService.Builder setMtu(int mtu) {
            this.mtu = mtu;
            super.setMtu(mtu);
            return this;
        }

        @Override
        public Builder addAddress(String address, int prefixLength) {
            listAddress.add(address + "/" + prefixLength);
            super.addAddress(address, prefixLength);
            return this;
        }

        @Override
        public Builder addRoute(String address, int prefixLength) {
            listRoute.add(address + "/" + prefixLength);
            super.addRoute(address, prefixLength);
            return this;
        }

        @Override
        public Builder addDnsServer(InetAddress address) {
            listDns.add(address);
            super.addDnsServer(address);
            return this;
        }

        @Override
        public boolean equals(Object obj) {
            Builder other = (Builder) obj;

            if (other == null)
                return false;

            if (this.networkInfo == null || other.networkInfo == null ||
                    this.networkInfo.getType() != other.networkInfo.getType())
                return false;

            if (this.mtu != other.mtu)
                return false;

            if (this.listAddress.size() != other.listAddress.size())
                return false;

            if (this.listRoute.size() != other.listRoute.size())
                return false;

            if (this.listDns.size() != other.listDns.size())
                return false;

            for (String address : this.listAddress)
                if (!other.listAddress.contains(address))
                    return false;

            for (String route : this.listRoute)
                if (!other.listRoute.contains(route))
                    return false;

            for (InetAddress dns : this.listDns)
                if (!other.listDns.contains(dns))
                    return false;

            return true;
        }
    }


    public static void start(Context context) {
        Intent intent = new Intent(context, Tun2HttpVpnService.class);
        intent.setAction(ACTION_START);
        context.startService(intent);
    }


    public static void stop(Context context) {
        Intent intent = new Intent(context, Tun2HttpVpnService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
    }
}
