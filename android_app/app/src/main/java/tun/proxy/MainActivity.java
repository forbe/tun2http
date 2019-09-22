package tun.proxy;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;

import java.util.EnumSet;
import java.util.Map;

import tun.proxy.service.Tun2HttpVpnService;
import tun.utils.CertificateUtil;

public class MainActivity extends AppCompatActivity {
    public static final int REQUEST_VPN = 1;
    public static final int REQUEST_CERT = 2;

    Button start;
    Button stop;
    EditText hostEditText;
    MenuItem menuSetting;
    Handler statusHandler = new Handler();

    private Tun2HttpVpnService service;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        start = findViewById(R.id.start);
        stop = findViewById(R.id.stop);
        hostEditText = findViewById(R.id.host);

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                MyApplication app = (MyApplication) getApplicationContext();
//                byte [] trust_ca = app.getTrustCA();
//                if (trust_ca != null) {
//                    Intent intent = CertificateUtil.trustRootCA(CertificateUtil.getCACertificate(trust_ca));
//                    if (intent != null) {
//                        startActivityForResult(intent, REQUEST_CERT);
//                    } else {
                startVpn();
//                    }
//                }
            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopVpn();
            }
        });
        start.setEnabled(true);
        stop.setEnabled(false);

        loadHostPort();
        requestPermission();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_activity_settings);
        item.setEnabled(start.isEnabled());
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_activity_settings:
                Intent intent = new android.content.Intent(this, SimplePreferenceActivity.class);
                startActivity(intent);
                break;
            case R.id.action_show_about:
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.app_name) + getVersionName())
                        .setMessage(R.string.app_name)
                        .show();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    String getVersionName() {
        PackageManager packageManager = getPackageManager();
        if (packageManager == null) {
            return null;
        }

        try {
            return packageManager.getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private ServiceConnection serviceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            Tun2HttpVpnService.ServiceBinder serviceBinder = (Tun2HttpVpnService.ServiceBinder) binder;
            service = serviceBinder.getService();
        }

        public void onServiceDisconnected(ComponentName className) {
            service = null;
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        start.setEnabled(false);
        stop.setEnabled(false);
        updateStatus();

        statusHandler.postDelayed(statusRunnable, 1000);

        Intent intent = new Intent(this, Tun2HttpVpnService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    boolean isRunning() {
        return service != null && service.isRunning();
    }

    Runnable statusRunnable = new Runnable() {
        @Override
        public void run() {
        updateStatus();
        statusHandler.postDelayed(statusRunnable, 1000);
        }
    };

    @Override
    protected void onPause() {
        super.onPause();
        statusHandler.removeCallbacks(statusRunnable);
        unbindService(serviceConnection);
    }

    void updateStatus() {
        if (service == null) {
            return;
        }
        if (isRunning()) {
            start.setEnabled(false);
            stop.setEnabled(true);
        } else {
            start.setEnabled(true);
            stop.setEnabled(false);
        }
    }

    private void stopVpn() {
        start.setEnabled(true);
        stop.setEnabled(false);
        Tun2HttpVpnService.stop(this);
    }

    private void startVpn() {
        Intent i = VpnService.prepare(this);
        if (i != null) {
            startActivityForResult(i, 0);
        } else {
            onActivityResult(0, REQUEST_VPN, null);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == REQUEST_VPN && parseAndSaveHostPort()) {
            start.setEnabled(false);
            stop.setEnabled(true);
            Tun2HttpVpnService.start(this);
        }
    }

    private void loadHostPort() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String proxyHost = prefs.getString(Tun2HttpVpnService.PREF_PROXY_HOST, "");
        int proxyPort = prefs.getInt(Tun2HttpVpnService.PREF_PROXY_PORT, 0);

        if (TextUtils.isEmpty(proxyHost)) {
            return;
        }

        if (proxyPort == 80) {
            hostEditText.setText(proxyHost);
        } else {
            hostEditText.setText(proxyHost + ":" + proxyPort);
        }
    }

    private boolean parseAndSaveHostPort() {
        String hostPort = hostEditText.getText().toString();
        if (hostPort.isEmpty()) {
            hostEditText.setError(getString(R.string.enter_host));
            return false;
        }

        String parts[] = hostPort.split(":");
        int port = 0;
        if (parts.length > 1) {
            try {
                port = Integer.parseInt(parts[1]);
            } catch (Exception e) {
                hostEditText.setError(getString(R.string.enter_host));
                return false;
            }
        }
        String[] ipParts = parts[0].split("\\.");
        if (ipParts.length != 4) {
            hostEditText.setError(getString(R.string.enter_host));
            return false;
        }
        String host = parts[0];
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor edit = prefs.edit();

        edit.putString(Tun2HttpVpnService.PREF_PROXY_HOST, host);
        edit.putInt(Tun2HttpVpnService.PREF_PROXY_PORT, port);

        edit.commit();
        return true;
    }

    private void requestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {//Can add more as per requirement
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 8000);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 8000: {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    requestPermission();
                }
                return;
            }
        }
    }

}
