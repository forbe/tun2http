package tun.proxy;

import android.app.Application;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

public class MyApplication extends Application {
    public static final String PREF_VPN_MODE = "vpn_connection_mode";

    private static MyApplication instance;

    public static MyApplication getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public enum VPNMode {DISALLOW, ALLOW};

    public enum AppSortBy {APPNAME, PKGNAME};

    private final String pref_key[] = {"vpn_disallowed_application", "vpn_allowed_application"};

    public VPNMode loadVPNMode() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String vpn_mode = sharedPreferences.getString(PREF_VPN_MODE, MyApplication.VPNMode.DISALLOW.name());
        return VPNMode.valueOf(vpn_mode);
    }

    public void storeVPNMode(VPNMode mode) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_VPN_MODE, mode.name());
        return;
    }

    public Set<String> loadVPNApplication(VPNMode mode) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Set<String> preference = prefs.getStringSet(pref_key[mode.ordinal()], new HashSet<String>());
        return preference;
    }

    public void storeVPNApplication(VPNMode mode, final Set<String> set) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        final SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(pref_key[mode.ordinal()], set);
        editor.commit();
        return;
    }

}
