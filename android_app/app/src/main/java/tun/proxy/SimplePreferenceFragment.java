package tun.proxy;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.*;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.*;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SimplePreferenceFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener {
    public static final String VPN_CONNECTION_MODE = "vpn_connection_mode";
    public static final String VPN_DISALLOWED_APPLICATION_LIST = "vpn_disallowed_application_list";
    public static final String VPN_ALLOWED_APPLICATION_LIST = "vpn_allowed_application_list";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        setHasOptionsMenu(true);

        /* Allowed / Disallowed Application */
        final ListPreference pkg_selection = (ListPreference) this.findPreference(VPN_CONNECTION_MODE);
        pkg_selection.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object value) {
            if (preference instanceof ListPreference) {
                ListPreference listPreference = (ListPreference) preference;
                int index = listPreference.findIndexOfValue((String) value);

                PreferenceScreen disallow = (PreferenceScreen) findPreference(VPN_DISALLOWED_APPLICATION_LIST);
                PreferenceScreen allow = (PreferenceScreen) findPreference(VPN_ALLOWED_APPLICATION_LIST);
                disallow.setEnabled(index == MyApplication.VPNMode.DISALLOW.ordinal());
                allow.setEnabled(index == MyApplication.VPNMode.ALLOW.ordinal());

                // Set the summary to reflect the new value.
                preference.setSummary(index >= 0 ? listPreference.getEntries()[index] : null);

            }
            return true;
            }
        });
        pkg_selection.setSummary(pkg_selection.getEntry());
        PreferenceScreen disallow = (PreferenceScreen) findPreference(VPN_DISALLOWED_APPLICATION_LIST);
        PreferenceScreen allow = (PreferenceScreen) findPreference(VPN_ALLOWED_APPLICATION_LIST);
        disallow.setEnabled(MyApplication.VPNMode.DISALLOW.name().equals(pkg_selection.getValue()));
        allow.setEnabled(MyApplication.VPNMode.ALLOW.name().equals(pkg_selection.getValue()));

        findPreference(VPN_DISALLOWED_APPLICATION_LIST).setOnPreferenceClickListener(this);
        findPreference(VPN_ALLOWED_APPLICATION_LIST).setOnPreferenceClickListener(this);

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
            case VPN_DISALLOWED_APPLICATION_LIST:
                transitionFragment(PackageListPreferenceFragment.newInstance(MyApplication.VPNMode.DISALLOW));
                break;
            case VPN_ALLOWED_APPLICATION_LIST:
                transitionFragment(PackageListPreferenceFragment.newInstance(MyApplication.VPNMode.ALLOW));
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
    public static class PackageListPreferenceFragment extends PreferenceFragment implements SearchView.OnQueryTextListener, SearchView.OnCloseListener {
        private MyApplication.VPNMode mode;
        private PreferenceScreen mRootPreferenceScreen;

        public static PackageListPreferenceFragment newInstance(MyApplication.VPNMode mode) {
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

        private String searchFilter = "";
        private SearchView searchView;

        protected void filter(String filter) {
            if (filter == null) {
                filter = searchFilter;
            }
            else {
                searchFilter = filter;
            }
            removeAllPreferenceScreen();
            buildPackagesPreferences(filter);
            loadSelectedPackage();
        }

        @Override
        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            super.onCreateOptionsMenu(menu, inflater);
            // Menuの設定
            inflater.inflate(R.menu.menu_search, menu);

            MenuItem menuItem = menu.findItem(R.id.search_menu_item);

            this.searchView = (SearchView)menuItem.getActionView();
            this.searchView.setOnQueryTextListener(this);
            this.searchView.setOnCloseListener(this);
            this.searchView.setSubmitButtonEnabled(false);
        }

        @Override
        public void onPause()  {
            super.onPause();
            storeSelectedPackageSet(this.getSelectedPackageSet());
        }

        @Override
        public void onResume() {
            super.onResume();
            filter(null);
        }

        private void removeAllPreferenceScreen() {
            mRootPreferenceScreen.removeAll();
        }

        private void buildPackagesPreferences(String filter) {
            final Context context = MyApplication.getInstance().getApplicationContext();
            final PackageManager pm = context.getPackageManager();
            final List<PackageInfo> installedPackages = pm.getInstalledPackages(PackageManager.GET_META_DATA);
            Collections.sort(installedPackages, new Comparator<PackageInfo>() {
                @Override
                public int compare(PackageInfo o1, PackageInfo o2) {
                    String t1 = o1.applicationInfo.loadLabel(pm).toString();
                    String t2 = o2.applicationInfo.loadLabel(pm).toString();
                    return t1.compareTo(t2);
                }
            });
            for (final PackageInfo pi : installedPackages) {
                String t1 = pi.applicationInfo.loadLabel(pm).toString();
                if (filter.trim().isEmpty() || t1.toLowerCase().contains(filter.toLowerCase())) {
                    final Preference preference = buildPackagePreferences(pm, pi);
                    mRootPreferenceScreen.addPreference(preference);
                }
            }
        }

        private Preference buildPackagePreferences(final PackageManager pm, final PackageInfo pi) {
            final CheckBoxPreference p = new CheckBoxPreference(getActivity());
            p.setIcon(pi.applicationInfo.loadIcon(pm));
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
                    if (pref_check.isChecked()) {
                        selected.add(pref_check.getSummary().toString());
                    }
                }
            }
            return selected;
        }

        private void setSelectedPackageSet(Set<String> selected) {
            for (int i = 0; i < this.mRootPreferenceScreen.getPreferenceCount(); i++) {
                Preference pref = this.mRootPreferenceScreen.getPreference(i);
                if ((pref instanceof CheckBoxPreference)) {
                    CheckBoxPreference pref_check = (CheckBoxPreference) pref;
                    if (selected.contains(pref_check.getSummary())) {
                        pref_check.setChecked(true);
                    }
                }
            }
        }

        private void loadSelectedPackage() {
            this.getArguments();
            mode  = MyApplication.getInstance().loadVPNMode();
            Set<String> selected = MyApplication.getInstance().loadVPNApplication(mode);
            setSelectedPackageSet(selected);
        }

        private void storeSelectedPackageSet(final Set<String> set) {
            MyApplication.getInstance().storeVPNMode(mode);
            MyApplication.getInstance().storeVPNApplication(mode, set);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            int id = item.getItemId();
            if (id == android.R.id.home) {
                startActivity(new Intent(getActivity(), SimplePreferenceActivity.class));
                return true;
            }
            return super.onOptionsItemSelected(item);
        }

        @Override
        public boolean onQueryTextSubmit(String query) {
            this.searchView.clearFocus();
            if (!query.trim().isEmpty()) {
                filter(query);
                return true;
            }
            else {
                filter("");
                return true;
            }
        }

        @Override
        public boolean onQueryTextChange(String newText) {
            return false;
        }

        @Override
        public boolean onClose() {
            filter("");
            return false;
        }
    }

}
