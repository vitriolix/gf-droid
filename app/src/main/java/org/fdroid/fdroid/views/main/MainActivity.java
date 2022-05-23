/*
 * Copyright (C) 2016-2017 Peter Serwylo
 * Copyright (C) 2017 Christine Emrich
 * Copyright (C) 2017 Hans-Christoph Steiner
 * Copyright (C) 2018 Senecto Limited
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA 02110-1301, USA.
 */

package org.fdroid.fdroid.views.main;

import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.fdroid.fdroid.AppUpdateStatusManager;
import org.fdroid.fdroid.AppUpdateStatusManager.AppUpdateStatus;
import org.fdroid.fdroid.FDroidApp;
import org.fdroid.fdroid.NfcHelper;
import org.fdroid.fdroid.Preferences;
import org.fdroid.fdroid.R;
import org.fdroid.fdroid.UpdateService;
import org.fdroid.fdroid.Utils;
import org.fdroid.fdroid.data.NewRepoConfig;
import org.fdroid.fdroid.nearby.SDCardScannerService;
import org.fdroid.fdroid.nearby.SwapService;
import org.fdroid.fdroid.nearby.SwapWorkflowActivity;
import org.fdroid.fdroid.nearby.TreeUriScannerIntentService;
import org.fdroid.fdroid.nearby.WifiStateChangeService;
import org.fdroid.fdroid.views.AppDetailsActivity;
import org.fdroid.fdroid.views.ManageReposActivity;
import org.fdroid.fdroid.views.apps.AppListActivity;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;

import android.util.Log;
import org.greatfire.envoy.*;

import info.guardianproject.netcipher.NetCipher;

/**
 * Main view shown to users upon starting F-Droid.
 * <p>
 * Shows a bottom navigation bar, with the following entries:
 * + Whats new
 * + Categories list
 * + App swap
 * + Updates
 * + Settings
 * <p>
 * Users navigate between items by using the bottom navigation bar, or by swiping left and right.
 * When switching from one screen to the next, we stay within this activity. The new screen will
 * get inflated (if required)
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    public static final String EXTRA_VIEW_UPDATES = "org.fdroid.fdroid.views.main.MainActivity.VIEW_UPDATES";
    public static final String EXTRA_VIEW_NEARBY = "org.fdroid.fdroid.views.main.MainActivity.VIEW_NEARBY";
    public static final String EXTRA_VIEW_SETTINGS = "org.fdroid.fdroid.views.main.MainActivity.VIEW_SETTINGS";

    static final int REQUEST_LOCATION_PERMISSIONS = 0xEF0F;
    static final int REQUEST_STORAGE_PERMISSIONS = 0xB004;
    public static final int REQUEST_STORAGE_ACCESS = 0x40E5;

    private static final String ADD_REPO_INTENT_HANDLED = "addRepoIntentHandled";

    private static final String ACTION_ADD_REPO = "org.fdroid.fdroid.MainActivity.ACTION_ADD_REPO";
    public static final String ACTION_REQUEST_SWAP = "requestSwap";

    private RecyclerView pager;
    private MainViewAdapter adapter;

    // copied from org.greatfire.envoy.NetworkIntentService.kt, could not be found in imported class
    public static final String BROADCAST_VALID_URL_FOUND = "org.greatfire.envoy.VALID_URL_FOUND";
    public static final String EXTENDED_DATA_VALID_URLS = "org.greatfire.envoy.VALID_URLS";

    private int currentPageId = 0;
    public static final String CURRENT_PAGE_ID = "org.greatfire.envoy.CURRENT_PAGE_ID";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        FDroidApp fdroidApp = (FDroidApp) getApplication();
        fdroidApp.applyPureBlackBackgroundInDarkTheme(this);

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        adapter = new MainViewAdapter(this);

        pager = (RecyclerView) findViewById(R.id.main_view_pager);
        pager.setHasFixedSize(true);
        pager.setLayoutManager(new NonScrollingHorizontalLayoutManager(this));
        pager.setAdapter(adapter);

        // Without this, the focus is completely busted on pre 15 devices. Trying to use them
        // without this ends up with each child view showing for a fraction of a second, then
        // reverting back to the "Latest" screen again, in completely non-deterministic ways.
        if (Build.VERSION.SDK_INT <= 15) {
            pager.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        }

        // TODO - implement badge for update icon
        // updatesBadge = bottomNavigation.getOrCreateBadge(R.id.updates);
        // updatesBadge.setVisible(false);

        // set up custom navigation bar to allow shifted icons with frames
        ImageView newestView = (ImageView) findViewById((R.id.newest_button));
        ImageView categoryView = (ImageView) findViewById((R.id.category_button));
        ImageView updateView = (ImageView) findViewById((R.id.update_button));
        ImageView nearbyView = (ImageView) findViewById((R.id.nearby_button));
        ImageView settingsView = (ImageView) findViewById((R.id.settings_button));

        newestView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPageId != 0) {
                    pager.scrollToPosition(0);
                    setNavSelection(0);
                }
            }
        });

        categoryView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPageId != 1) {
                    pager.scrollToPosition(1);
                    setNavSelection(1);
                }
            }
        });

        updateView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPageId != 2) {
                    pager.scrollToPosition(2);
                    setNavSelection(2);
                }
            }
        });

        nearbyView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPageId != 3) {
                    pager.scrollToPosition(3);
                    NearbyViewBinder.updateUsbOtg(MainActivity.this);
                    setNavSelection(3);
                }
            }
        });

        settingsView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentPageId != 4) {
                    pager.scrollToPosition(4);
                    setNavSelection(4);
                }
            }
        });

        IntentFilter updateableAppsFilter = new IntentFilter(AppUpdateStatusManager.BROADCAST_APPSTATUS_LIST_CHANGED);
        updateableAppsFilter.addAction(AppUpdateStatusManager.BROADCAST_APPSTATUS_CHANGED);
        updateableAppsFilter.addAction(AppUpdateStatusManager.BROADCAST_APPSTATUS_REMOVED);
        LocalBroadcastManager.getInstance(this).registerReceiver(onUpdateableAppsChanged, updateableAppsFilter);

        // register to receive valid proxy urls
        LocalBroadcastManager.getInstance(this).registerReceiver(onUrlsReceived, new IntentFilter(BROADCAST_VALID_URL_FOUND));

        // start shadowsocks service
        Intent shadowsocksIntent = new Intent(this, ShadowsocksService.class);
        // put shadowsocks proxy url here, should look like ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpwYXNz@127.0.0.1:1234 (base64 encode user/password)
        shadowsocksIntent.putExtra("org.greatfire.envoy.START_SS_LOCAL", "ss://Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTppZXNvaHZvOHh1Nm9oWW9yaWUydGhhZWhvaFBoOFRoYQ==@172.104.163.54:8388");
        ContextCompat.startForegroundService(getApplicationContext(), shadowsocksIntent);

        // TODO - initialize one or more string values containing the urls of available http/https proxies (include trailing slash)
        // TODO - we don't have the ip/port for our http/https proxies that are required by netcipher
        String httpUrl = "http://";
        String httpsUrl = "http://";

        // include shadowsocks local proxy url (submitting local shadowsocks url with no active service may cause an exception)
        String ssUrl = "socks5://127.0.0.1:1080";  // default shadowsocks url, change if there are port conflicts

        // TODO - it appears that only ip:port format proxies are supported by NetCipher
        ArrayList<String> possibleUrls = new ArrayList<String>(Arrays.asList(ssUrl));  // add all string values to this list value
        NetworkIntentService.submit(this, possibleUrls);  // submit list of urls to envoy for evaluation

        // delay until after proxy urls have been validated
        // initialRepoUpdateIfRequired();

        Intent intent = getIntent();
        handleSearchOrAppViewIntent(intent);
    }

    private void refreshNavSelection() {
        setNavSelection(currentPageId);
    }

    private void setNavSelection(int position) {

        currentPageId = position;

        ImageView newestView = (ImageView) findViewById(R.id.newest_button);
        ImageView categoryView = (ImageView) findViewById(R.id.category_button);
        ImageView updateView = (ImageView) findViewById(R.id.update_button);
        ImageView nearbyView = (ImageView) findViewById(R.id.nearby_button);
        ImageView settingsView = (ImageView) findViewById(R.id.settings_button);

        TextView newestText = (TextView) findViewById(R.id.newest_text);
        TextView categoryText = (TextView) findViewById(R.id.category_text);
        TextView updateText = (TextView) findViewById(R.id.update_text);
        TextView nearbyText = (TextView) findViewById(R.id.nearby_text);
        TextView settingsText = (TextView) findViewById(R.id.settings_text);

        // clear all current selections
        newestView.setBackground(getDrawable(R.drawable.ic_gf_newest_unfocus));
        newestText.setTextColor(getResources().getColor(R.color.fdroid_grey_light));
        newestText.setPadding(0, 0, 0, getResources().getDimensionPixelOffset(R.dimen.nav_unfocus_padding));
        categoryView.setBackground(getDrawable(R.drawable.ic_gf_category_unfocus));
        categoryText.setTextColor(getResources().getColor(R.color.fdroid_grey_light));
        categoryText.setPadding(0, 0, 0, getResources().getDimensionPixelOffset(R.dimen.nav_unfocus_padding));
        updateView.setBackground(getDrawable(R.drawable.ic_gf_update_unfocus));
        updateText.setTextColor(getResources().getColor(R.color.fdroid_grey_light));
        updateText.setPadding(0, 0, 0, getResources().getDimensionPixelOffset(R.dimen.nav_unfocus_padding));
        nearbyView.setBackground(getDrawable(R.drawable.ic_gf_nearby_unfocus));
        nearbyText.setTextColor(getResources().getColor(R.color.fdroid_grey_light));
        nearbyText.setPadding(0, 0, 0, getResources().getDimensionPixelOffset(R.dimen.nav_unfocus_padding));
        settingsView.setBackground(getDrawable(R.drawable.ic_gf_settings_unfocus));
        settingsText.setTextColor(getResources().getColor(R.color.fdroid_grey_light));
        settingsText.setPadding(0, 0, 0, getResources().getDimensionPixelOffset(R.dimen.nav_unfocus_padding));

        switch(currentPageId) {
            case 0:
                newestView.setBackground(getDrawable(R.drawable.ic_gf_newest_focus));
                newestText.setTextColor(getResources().getColor(R.color.color_primary));
                newestText.setPadding(0, 0, 0, getResources().getDimensionPixelOffset(R.dimen.nav_focus_padding));
                break;
            case 1:
                categoryView.setBackground(getDrawable(R.drawable.ic_gf_category_focus));
                categoryText.setTextColor(getResources().getColor(R.color.color_primary));
                categoryText.setPadding(0, 0, 0, getResources().getDimensionPixelOffset(R.dimen.nav_focus_padding));
                break;
            case 2:
                updateView.setBackground(getDrawable(R.drawable.ic_gf_update_focus));
                updateText.setTextColor(getResources().getColor(R.color.color_primary));
                updateText.setPadding(0, 0, 0, getResources().getDimensionPixelOffset(R.dimen.nav_focus_padding));
                break;
            case 3:
                nearbyView.setBackground(getDrawable(R.drawable.ic_gf_nearby_focus));
                nearbyText.setTextColor(getResources().getColor(R.color.color_primary));
                nearbyText.setPadding(0, 0, 0, getResources().getDimensionPixelOffset(R.dimen.nav_focus_padding));
                break;
            case 4:
                settingsView.setBackground(getDrawable(R.drawable.ic_gf_settings_focus));
                settingsText.setTextColor(getResources().getColor(R.color.color_primary));
                settingsText.setPadding(0, 0, 0, getResources().getDimensionPixelOffset(R.dimen.nav_focus_padding));
                break;
            default:
                break;
        }
    }

    private void setSelectedMenuInNav(int menuId) {
        int position = adapter.adapterPositionFromItemId(menuId);
        pager.scrollToPosition(position);
        setNavSelection(position);
    }

    private void initialRepoUpdateIfRequired() {
        if (Preferences.get().isIndexNeverUpdated() && !UpdateService.isUpdating()) {
            Utils.debugLog(TAG, "We haven't done an update yet. Forcing repo update.");
            UpdateService.updateNow(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        FDroidApp.checkStartTor(this, Preferences.get());

        if (getIntent().hasExtra(EXTRA_VIEW_UPDATES)) {
            getIntent().removeExtra(EXTRA_VIEW_UPDATES);
            setSelectedMenuInNav(R.id.updates);
        } else if (getIntent().hasExtra(EXTRA_VIEW_NEARBY)) {
            getIntent().removeExtra(EXTRA_VIEW_NEARBY);
            setSelectedMenuInNav(R.id.nearby);
        } else if (getIntent().hasExtra(EXTRA_VIEW_SETTINGS)) {
            getIntent().removeExtra(EXTRA_VIEW_SETTINGS);
            setSelectedMenuInNav(R.id.settings);
        } else {
            refreshNavSelection();
        }

        // AppDetailsActivity and RepoDetailsActivity set different NFC actions, so reset here
        NfcHelper.setAndroidBeam(this, getApplication().getPackageName());
        checkForAddRepoIntent(getIntent());
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);

        // save current page
        outState.putInt(CURRENT_PAGE_ID, currentPageId);
    }

    @Override
    protected void onRestoreInstanceState(final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        // restore current page
        currentPageId = savedInstanceState.getInt(CURRENT_PAGE_ID, 0);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleSearchOrAppViewIntent(intent);

        // This is called here as well as onResume(), because onNewIntent() is not called the first
        // time the activity is created. An alternative option to make sure that the add repo intent
        // is always handled is to call setIntent(intent) here. However, after this good read:
        // http://stackoverflow.com/a/7749347 it seems that adding a repo is not really more
        // important than the original intent which caused the activity to start (even though it
        // could technically have been an add repo intent itself).
        // The end result is that this method will be called twice for one add repo intent. Once
        // here and once in onResume(). However, the method deals with this by ensuring it only
        // handles the same intent once.
        checkForAddRepoIntent(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_STORAGE_ACCESS) {
            TreeUriScannerIntentService.onActivityResult(this, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) { // NOCHECKSTYLE LineLength
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSIONS) {
            WifiStateChangeService.start(this, null);
            ContextCompat.startForegroundService(this, new Intent(this, SwapService.class));
        } else if (requestCode == REQUEST_STORAGE_PERMISSIONS) {
            Toast.makeText(this,
                    this.getString(R.string.scan_removable_storage_toast, ""),
                    Toast.LENGTH_SHORT).show();
            SDCardScannerService.scan(this);
        }
    }

    /**
     * Since any app could send this {@link Intent}, and the search terms are
     * fed into a SQL query, the data must be strictly sanitized to avoid
     * SQL injection attacks.
     */
    private void handleSearchOrAppViewIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            performSearch(query);
            return;
        }

        final Uri data = intent.getData();
        if (data == null) {
            return;
        }

        final String scheme = data.getScheme();
        final String path = data.getPath();
        String packageName = null;
        String query = null;
        if (data.isHierarchical()) {
            final String host = data.getHost();
            if (host == null) {
                return;
            }
            switch (host) {
                case "f-droid.org":
                case "www.f-droid.org":
                case "staging.f-droid.org":
                    if (path.startsWith("/app/") || path.startsWith("/packages/")
                            || path.matches("^/[a-z][a-z][a-zA-Z_-]*/packages/.*")) {
                        // http://f-droid.org/app/packageName
                        packageName = data.getLastPathSegment();
                    } else if (path.startsWith("/repository/browse")) {
                        // http://f-droid.org/repository/browse?fdfilter=search+query
                        query = data.getQueryParameter("fdfilter");

                        // http://f-droid.org/repository/browse?fdid=packageName
                        packageName = data.getQueryParameter("fdid");
                    } else if ("/app".equals(data.getPath()) || "/packages".equals(data.getPath())) {
                        packageName = null;
                    }
                    break;
                case "details":
                    // market://details?id=app.id
                    packageName = data.getQueryParameter("id");
                    break;
                case "search":
                    // market://search?q=query
                    query = data.getQueryParameter("q");
                    break;
                case "play.google.com":
                    if (path.startsWith("/store/apps/details")) {
                        // http://play.google.com/store/apps/details?id=app.id
                        packageName = data.getQueryParameter("id");
                    } else if (path.startsWith("/store/search")) {
                        // http://play.google.com/store/search?q=foo
                        query = data.getQueryParameter("q");
                    }
                    break;
                case "apps":
                case "amazon.com":
                case "www.amazon.com":
                    // amzn://apps/android?p=app.id
                    // http://amazon.com/gp/mas/dl/android?s=app.id
                    packageName = data.getQueryParameter("p");
                    query = data.getQueryParameter("s");
                    break;
            }
        } else if ("fdroid.app".equals(scheme)) {
            // fdroid.app:app.id
            packageName = data.getSchemeSpecificPart();
        } else if ("fdroid.search".equals(scheme)) {
            // fdroid.search:query
            query = data.getSchemeSpecificPart();
        }

        if (!TextUtils.isEmpty(query)) {
            // an old format for querying via packageName
            if (query.startsWith("pname:")) {
                packageName = query.split(":")[1];
            }

            // sometimes, search URLs include pub: or other things before the query string
            if (query.contains(":")) {
                query = query.split(":")[1];
            }
        }

        if (!TextUtils.isEmpty(packageName)) {
            // sanitize packageName to be a valid Java packageName and prevent exploits
            packageName = packageName.replaceAll("[^A-Za-z\\d_.]", "");
            Utils.debugLog(TAG, "FDroid launched via app link for '" + packageName + "'");
            Intent intentToInvoke = new Intent(this, AppDetailsActivity.class);
            intentToInvoke.putExtra(AppDetailsActivity.EXTRA_APPID, packageName);
            startActivity(intentToInvoke);
            finish();
        } else if (!TextUtils.isEmpty(query)) {
            Utils.debugLog(TAG, "FDroid launched via search link for '" + query + "'");
            performSearch(query);
        }
    }

    /**
     * These strings might end up in a SQL query, so strip all non-alpha-num
     */
    static String sanitizeSearchTerms(String query) {
        return query.replaceAll("[^\\p{L}\\d_ -]", " ");
    }

    /**
     * Initiates the {@link AppListActivity} with the relevant search terms passed in via the query arg.
     */
    private void performSearch(String query) {
        Intent searchIntent = new Intent(this, AppListActivity.class);
        searchIntent.putExtra(AppListActivity.EXTRA_SEARCH_TERMS, sanitizeSearchTerms(query));
        startActivity(searchIntent);
    }

    private void checkForAddRepoIntent(Intent intent) {
        // Don't handle the intent after coming back to this view (e.g. after hitting the back button)
        // http://stackoverflow.com/a/14820849
        if (!intent.hasExtra(ADD_REPO_INTENT_HANDLED)) {
            intent.putExtra(ADD_REPO_INTENT_HANDLED, true);
            NewRepoConfig parser = new NewRepoConfig(this, intent);
            if (parser.isValidRepo()) {
                if (parser.isFromSwap()) {
                    SwapWorkflowActivity.requestSwap(this, intent.getData());
                } else {
                    Intent clean = new Intent(ACTION_ADD_REPO, intent.getData(), this, ManageReposActivity.class);
                    if (intent.hasExtra(ManageReposActivity.EXTRA_FINISH_AFTER_ADDING_REPO)) {
                        clean.putExtra(ManageReposActivity.EXTRA_FINISH_AFTER_ADDING_REPO,
                                intent.getBooleanExtra(ManageReposActivity.EXTRA_FINISH_AFTER_ADDING_REPO, true));
                    }
                    startActivity(clean);
                }
                finish();
            } else if (parser.getErrorMessage() != null) {
                Toast.makeText(this, parser.getErrorMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    // TODO - implement badge for update icon
    /*
    private void refreshUpdatesBadge(int canUpdateCount) {
        if (canUpdateCount == 0) {
            updatesBadge.setVisible(false);
            updatesBadge.clearNumber();
        } else {
            updatesBadge.setNumber(canUpdateCount);
            updatesBadge.setVisible(true);
        }
    }
    */

    private static class NonScrollingHorizontalLayoutManager extends LinearLayoutManager {
        NonScrollingHorizontalLayoutManager(Context context) {
            super(context, LinearLayoutManager.HORIZONTAL, false);
        }

        @Override
        public boolean canScrollHorizontally() {
            return false;
        }

        @Override
        public boolean canScrollVertically() {
            return false;
        }
    }

    /**
     * There are a bunch of reasons why we would get notified about app statuses.
     * The ones we are interested in are those which would result in the "items requiring user interaction"
     * to increase or decrease:
     * * Change in status to:
     * * {@link AppUpdateStatusManager.Status#ReadyToInstall} (Causes the count to go UP by one)
     * * {@link AppUpdateStatusManager.Status#Installed} (Causes the count to go DOWN by one)
     */
    private final BroadcastReceiver onUpdateableAppsChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean updateBadge = false;

            AppUpdateStatusManager manager = AppUpdateStatusManager.getInstance(context);

            String reason = intent.getStringExtra(AppUpdateStatusManager.EXTRA_REASON_FOR_CHANGE);
            switch (intent.getAction()) {
                // Apps which are added/removed from the list due to becoming ready to install or a repo being
                // disabled both cause us to increase/decrease our badge count respectively.
                case AppUpdateStatusManager.BROADCAST_APPSTATUS_LIST_CHANGED:
                    if (AppUpdateStatusManager.REASON_READY_TO_INSTALL.equals(reason) ||
                            AppUpdateStatusManager.REASON_REPO_DISABLED.equals(reason)) {
                        updateBadge = true;
                    }
                    break;

                // Apps which were previously "Ready to install" but have been removed. We need to lower our badge
                // count in response to this.
                case AppUpdateStatusManager.BROADCAST_APPSTATUS_REMOVED:
                    AppUpdateStatus status = intent.getParcelableExtra(AppUpdateStatusManager.EXTRA_STATUS);
                    if (status != null && status.status == AppUpdateStatusManager.Status.ReadyToInstall) {
                        updateBadge = true;
                    }
                    break;
            }

            // Check if we have moved into the ReadyToInstall or Installed state.
            AppUpdateStatus status = manager.get(
                    intent.getStringExtra(org.fdroid.fdroid.net.Downloader.EXTRA_CANONICAL_URL));
            boolean isStatusChange = intent.getBooleanExtra(AppUpdateStatusManager.EXTRA_IS_STATUS_UPDATE, false);
            if (isStatusChange
                    && status != null
                    && (status.status == AppUpdateStatusManager.Status.ReadyToInstall || status.status == AppUpdateStatusManager.Status.Installed)) { // NOCHECKSTYLE LineLength
                updateBadge = true;
            }

            if (updateBadge) {
                int count = 0;
                for (AppUpdateStatus s : manager.getAll()) {
                    if (s.status == AppUpdateStatusManager.Status.ReadyToInstall) {
                        count++;
                    }
                }

                // TODO - implement badge for update icon
                // refreshUpdatesBadge(count);
            }
        }
    };

    // this receiver listens for the results from the NetworkIntentService started below
    // it should receive a result if no valid urls are found but not if the service throws an exception
    private final BroadcastReceiver onUrlsReceived = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean enableProxy = false;

            if (intent != null && context != null) {
                ArrayList<String> validUrls = intent.getStringArrayListExtra(EXTENDED_DATA_VALID_URLS);
                // if there are no valid urls, the proxy setting in preferences will be disabled
                // currently there is a delay when using gost and the app must be restarted
                if (validUrls != null && !validUrls.isEmpty()) {
                    // select the fastest valid option (urls are ordered by latency)
                    String envoyUrl = validUrls.get(0);

                    Log.d(TAG, "found valid proxy url: " + envoyUrl);

                    // format expected: <protocol>://x.x.x.x:y
                    String[] urlParts = envoyUrl.split(":");
                    if (urlParts.length != 3) {
                        Log.e(TAG, "proxy url had an unexpected format");
                    } else {
                        String protocolPart = urlParts[0].toLowerCase(Locale.ROOT);
                        String hostPart = urlParts[1].replace("//", "");
                        int portPart = Integer.valueOf(urlParts[2].replace("/", ""));

                        if (protocolPart.startsWith("s")) {
                            Log.d(TAG, "set netcipher socks proxy: " + protocolPart + " / " + hostPart + " / " + portPart);
                            InetSocketAddress isa = new InetSocketAddress(hostPart, portPart);
                            NetCipher.setProxy(new Proxy(Proxy.Type.SOCKS, isa));
                            enableProxy = true;
                        } else if (protocolPart.startsWith("h")) {
                            Log.d(TAG, "set netcipher http proxy: " + protocolPart + " / " + hostPart + " / " + portPart);
                            InetSocketAddress isa = new InetSocketAddress(hostPart, portPart);
                            NetCipher.setProxy(new Proxy(Proxy.Type.HTTP, isa));
                            enableProxy = true;
                        } else {
                            Log.e(TAG, "proxy url had an unexpected protocol");
                        }
                    }
                } else {
                    Log.e(TAG, "no valid proxy url was found");
                }
            }

            Log.d(TAG, "manually enable or disable proxy preference");

            if (enableProxy) {
                Preferences.get().enableProxy();
            } else {
                Preferences.get().disableProxy();
            }

            Log.d(TAG, "do delayed repo update (if needed)");

            initialRepoUpdateIfRequired();
        }
    };
}
