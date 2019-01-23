package tun.proxy;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import tun.utils.CertificateUtil;

import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MyApplication extends Application {
    private static Context context;

    public static Context getContext() {
        return context;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContext();
    }
//    public byte [] getTrustCA() {
//        try {
//            X509Certificate cert = CertificateUtil.getCACertificate("/sdcard/", "");
//            return cert.getEncoded();
//        } catch (CertificateEncodingException e) {
//            e.printStackTrace();
//        }
////        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
////        String trustca_preference = sharedPreferences.getString( "trusted_ca", null );
////        if (trustca_preference != null)  {
////            return CertificateUtil.decode(trustca_preference);
////        }
//        return null;
//    }

    public int getVPNMode() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String vpn_connection_mode = sharedPreferences.getString("vpn_connection_mode", String.valueOf(SimplePreferenceFragment.PackageListPreferenceFragment.VPNMode.DISALLOW.ordinal()));
        return Integer.parseInt( vpn_connection_mode );
    }

    public Set<String> getAllowedApplication() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Set<String> allowed_preference = sharedPreferences.getStringSet("vpn_allowed_application", new HashSet<String>() );
        return allowed_preference;
    }

    public Set<String> getDisallowedApplication() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Set<String> disallowed_preference = sharedPreferences.getStringSet("vpn_disallowed_application", new HashSet<String>() );
        return disallowed_preference;
    }

}
