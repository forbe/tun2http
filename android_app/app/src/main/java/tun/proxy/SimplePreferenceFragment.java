package tun.proxy;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.view.MenuItem;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import tun.utils.CertificateUtil;

public class SimplePreferenceFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        setHasOptionsMenu(true);

        /* Allowed / Disallowed Application */
        final ListPreference pkg_selection = (ListPreference) this.findPreference("vpn_connection_mode");
        pkg_selection.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
                if (preference instanceof ListPreference) {
                    ListPreference listPreference = (ListPreference) preference;
                    int index = listPreference.findIndexOfValue((String) value);

                    PreferenceScreen disallow = (PreferenceScreen) findPreference("disallowed_application_list");
                    PreferenceScreen allow = (PreferenceScreen) findPreference("allowed_application_list");
                    disallow.setEnabled(index == 0);
                    allow.setEnabled(index != 0);


                    // Set the summary to reflect the new value.
                    preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);

                }
                return true;
            }
        });
        pkg_selection.setSummary(pkg_selection.getEntry());
        PreferenceScreen disallow = (PreferenceScreen) findPreference("disallowed_application_list");
        PreferenceScreen allow = (PreferenceScreen) findPreference("allowed_application_list");
        disallow.setEnabled(Integer.parseInt(pkg_selection.getValue()) == 0);
        allow.setEnabled(Integer.parseInt(pkg_selection.getValue()) != 0);

        findPreference("allowed_application_list").setOnPreferenceClickListener(this);
        findPreference("disallowed_application_list").setOnPreferenceClickListener(this);

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            startActivity(new Intent(getActivity(), MainActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // リスナー部分
    @Override
    public boolean onPreferenceClick(Preference preference) {
        // keyを見てクリックされたPreferenceを特定
        switch (preference.getKey()) {
            case "allowed_application_list":
                transitionFragment(PackageListPreferenceFragment.newInstance(PackageListPreferenceFragment.VPNMode.ALLOW));
                break;
            case "disallowed_application_list":
                transitionFragment(PackageListPreferenceFragment.newInstance(PackageListPreferenceFragment.VPNMode.DISALLOW));
                break;
        }
        return false;
    }

    private void transitionFragment(PreferenceFragment nextPreferenceFragment) {
        // replaceによるFragmentの切り替えと、addToBackStackで戻るボタンを押した時に前のFragmentに戻るようにする
        getFragmentManager()
            .beginTransaction()
            .addToBackStack(null)
            .replace(android.R.id.content, nextPreferenceFragment)
            .commit();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public static class PackageListPreferenceFragment extends PreferenceFragment {
        enum VPNMode {DISALLOW, ALLOW};
        private VPNMode mode;
        private PreferenceScreen mRootPreferenceScreen;

        private String pref_key[] = {"vpn_disallowed_application", "vpn_allowed_application"};

        public static PackageListPreferenceFragment newInstance(VPNMode mode) {
            PackageListPreferenceFragment fragment = new PackageListPreferenceFragment();
            fragment.mode = mode;
            return fragment;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setHasOptionsMenu(true);
            mRootPreferenceScreen = getPreferenceManager().createPreferenceScreen(getActivity());
            setPreferenceScreen(mRootPreferenceScreen);
        }

        @Override
        public void onResume() {
            super.onResume();
            removeAllPreferenceScreen();
            buildPackagesPreferences();
            loadSelectedPackage();
        }

        private void removeAllPreferenceScreen() {
            mRootPreferenceScreen.removeAll();
        }

        private void buildPackagesPreferences() {
            Context context = MyApplication.getContext().getApplicationContext();
            PackageManager pm = context.getPackageManager();
            List<PackageInfo> installedPackages = pm.getInstalledPackages(PackageManager.GET_META_DATA);
            for (final PackageInfo pi : installedPackages) {
                final Preference preference = buildPackagePreferences(pm, pi);
                mRootPreferenceScreen.addPreference(preference);
            }
        }

        private Preference buildPackagePreferences(final PackageManager pm, final PackageInfo pi) {
            final CheckBoxPreference p = new CheckBoxPreference(getActivity());
            p.setTitle(pi.applicationInfo.loadLabel(pm).toString());
            p.setSummary(pi.packageName);
            return p;
        }

        private Set<String> getSelectedPackageSet() {
            Set<String> selected = new HashSet<>();
            for (int i = 0; i < this.mRootPreferenceScreen.getPreferenceCount(); i++) {
                Preference pref = this.mRootPreferenceScreen.getPreference(i);
                if ((pref instanceof CheckBoxPreference)) {
                    CheckBoxPreference pref_check = (CheckBoxPreference) pref;
                    if (pref_check.isChecked()) selected.add(pref_check.getSummary().toString());
                }
            }
            return selected;
        }

        private void setSelectedPackageSet(Set<String> selected) {
            for (int i = 0; i < this.mRootPreferenceScreen.getPreferenceCount(); i++) {
                Preference pref = this.mRootPreferenceScreen.getPreference(i);
                if ((pref instanceof CheckBoxPreference)) {
                    CheckBoxPreference pref_check = (CheckBoxPreference) pref;
                    if (selected.contains(pref_check.getSummary()))
                        pref_check.setChecked(true);
                }
            }
        }

        private void loadSelectedPackage() {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences( getActivity());
            this.getArguments();
            Set<String> selected = prefs.getStringSet( pref_key[mode.ordinal()], new HashSet<String>());
            setSelectedPackageSet(selected);
        }

        private void storeSelectedPackageSet(final Set<String> set) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
            final SharedPreferences.Editor editor = prefs.edit();
            editor.putStringSet(pref_key[mode.ordinal()], set);
            editor.commit();
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                storeSelectedPackageSet(this.getSelectedPackageSet());
                startActivity(new Intent(getActivity(), SimplePreferenceActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

    }

}
