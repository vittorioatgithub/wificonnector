package it.this1.wificonnector.iotter.wifi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Vittorio on 28/01/17.
 */

/**
 * Class that manages connection to an AP
 * Provides an callback interface to signal all
 * major events during association process
 * Connect method need ssid/password and a listener to receive
 * events.
 * Major events are:
 *      {@link WifiConnector.connectorListener#onConnected(WifiInfo)}
 *      fired when AP desired is connected
 *      {@link WifiConnector.connectorListener#onAuthenticationError()}
 *      fired when AP key provided is not correct
 *      {@link WifiConnector.connectorListener#onAuthenticationError()}
 */
public class WifiConnector {

    private static  final String TAG = "IotterWifiConnector";
    private WifiManager mWifiManager;
    private List<ScanResult> mScanResults;
    private String mSsid;
    private String mPassword;
    private Context mContext;
    private connectorListener mListener;
    private Timer setUpTimer;
    private int SETUP_TO    = 30*1000;

    public class ParamInvalid extends Exception {

    }

    public interface connectorListener {
        /**
         * Fired when connection succeeds
         * @param info
         */
        public void onConnected(WifiInfo info);

        /**
         * Fired when connection interrupts
         */
        public void onDisconnected();

        /**
         * Fired when SSID provided was not found
         */
        public void onSSIDFoundError();

        /**
         * Fired when provided security key
         * is not accepts by AP
         */
        public void onAuthenticationError();

        /**
         * Informational event fired when SSID si found
         * @param info
         */
        public void onSSIDFound(ScanResult info);

        /**
         * Fired when internal timeout (20s) elapsed.
         */
        public void onTimeout();

    }

    private WifiConnector() {}

    /**
     * Constructor. A context must be provided
     * @param mContext {@link Context}
     */
    public WifiConnector(Context mContext) {
        this.mContext = mContext;
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    }

    /**
     * Let to run to a WIFI network identified by SSID.
     * Accept a listener to monitoring operations.
     * @param ssid
     * @param password
     * @param mListener {@link connectorListener} can be null
     */
    public void connect(String ssid, String password, connectorListener mListener) throws ParamInvalid {
        this.mListener = mListener;
        if(ssid == null || ssid.isEmpty() || password == null || password.length() < 6) throw  new ParamInvalid();
        mSsid = ssid;
        mPassword = password;
        setUpWifi();
    }

    /**
     * Disconnect and forget current WIFI connection
     * @return  true if ok
     *          false otherwise
     */
    public boolean forget() {
        WifiInfo wifiInfo = mWifiManager.getConnectionInfo();

        if (wifiInfo != null && wifiInfo.getNetworkId() != -1) {
            boolean rm = mWifiManager.removeNetwork(wifiInfo.getNetworkId());
            boolean sc = mWifiManager.saveConfiguration();

            return rm && sc;
        } else
            return false;
    }

    /**
     * Disconnect only from current WIFI connection
     * @return
     */
    public boolean disconnect() {
        return mWifiManager.disconnect();
    }

    private WifiInfo getWifiInfo() {
        ConnectivityManager connManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if(networkInfo != null) {
            if (networkInfo.isConnected()) {
                return mWifiManager.getConnectionInfo();
            }
        }
        return null;
    }

    private void setUpWifi() {
        mWifiManager.setWifiEnabled(true);
        final IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        mContext.registerReceiver(mScanReceiver, filter);
        setUpTimer = new Timer();
        setUpTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if(mListener != null) {
                    mListener.onTimeout();
                }
            }
        },SETUP_TO);
    }

    /**
     * Receiver used only with scan result so can be unregister
     * after found SSID provisioned or error.
     * Receiver will fire {@link connectorListener} callbacks
     */
    private BroadcastReceiver mScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                Log.d(TAG, "onReceive() SCAN_RESULTS_AVAILABLE_ACTION");
                mScanResults = mWifiManager.getScanResults();
                if (mScanResults != null) {
                    Log.d(TAG, mScanResults.toString());
                    for (ScanResult result : mScanResults) {
                        if (result.SSID.equals(mSsid)) {
                            Log.d(TAG, "SSID " + mSsid + " found try to run to");
                            Wifi.connectToNewNetwork(mContext, mWifiManager, result, mPassword, 1);
                            context.unregisterReceiver(this);
                            final IntentFilter filter1 = new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION);
                            context.registerReceiver(mReceiver, filter1);
                            if(mListener != null) {
                                mListener.onSSIDFound(result);
                            }
                            return;
                        }
                    }
                    Log.e(TAG, "SSID " + mSsid + " not found!");
                    context.unregisterReceiver(this);
                    if(mListener != null) {
                        mListener.onSSIDFoundError();
                    }
                    if(setUpTimer != null) {
                        setUpTimer.cancel();
                        setUpTimer = null;
                    }
                    return;
                }

                // TODO: add counter???
            }
            if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                NetworkInfo mInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (mInfo != null && mInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    if (mInfo.isConnectedOrConnecting()) {
                        NetworkInfo.DetailedState currentState = mInfo.getDetailedState();
                        Log.d(TAG, currentState.toString());
                        WifiInfo info = mWifiManager.getConnectionInfo();
                        if (info != null) {
                            Log.d(TAG, info.toString());
                            String quotedString = StringUtils.convertToQuotedString(mSsid);
                            boolean ssidEquals = quotedString.equals(info.getSSID());
                            Log.d(TAG, String.format("Connected SSID %s, desired %s", info.getSSID(), mSsid));
                            if (ssidEquals) {
                                if(setUpTimer != null) {
                                    setUpTimer.cancel();
                                    setUpTimer = null;
                                }
                                mContext.unregisterReceiver(this);
//                                if (mListener != null) {
//                                    mListener.onConnected(info);
//                                }
                                generateConnectedDelayedEvent(info);
                                return;
                            }
                        }
                    }
                    Log.d(TAG, "start wifi scan");
                    mWifiManager.startScan();
                }
            }
        }
    };


    /**
     * Receiver used only for net state changes to check if we
     * were able to run to AP. Using detailed state
     * we are able to detect some particular error as authentication error.
     * Receiver will fire {@link connectorListener} callbacks
     */
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        private NetworkInfo.DetailedState lastKnownState;
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                Log.d(TAG, "onReceive() NETWORK_STATE_CHANGED_ACTION");
                NetworkInfo mInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (mInfo != null && mInfo.getType() == ConnectivityManager.TYPE_WIFI) {
                    Log.d(TAG, mInfo.toString());
                    // Check macro state
                    if(mInfo.isConnected()) {
                        NetworkInfo.DetailedState currentState = mInfo.getDetailedState();
                        Log.d(TAG, currentState.toString());
                        final WifiInfo info = mWifiManager.getConnectionInfo();
                        if(info != null) {
                            String quotedString = StringUtils.convertToQuotedString(mSsid);
                            boolean ssidEquals = quotedString.equals(info.getSSID());
                            Log.d(TAG, String.format("Connected SSID %s, desired %s", info.getSSID(), mSsid));
                            if (ssidEquals) {
                                Log.d(TAG, "CONNECTED\n"+info.toString());
                                setUpTimer.cancel();
                                context.unregisterReceiver(mReceiver);
                                if (mListener != null) {
                                    // Delay events cause some time it is not  so "sharp"
                                    // to assure that remote hosts are IP reachable and
                                    // to avoid ENETUNREACH (Network is unreachable) when
                                    // opening a socket into this handler.
                                    // We use AsyncTask to communicate with UI thread
                                    // during callback (i.e. Toast)
                                    /*new AsyncTask<Integer, Void, Void>() {
                                        protected void onPreExecute() {
                                            // Pre Code
                                        }
                                        protected Void doInBackground(Integer... wait) {
                                            try {
                                                Thread.sleep(wait[0]);
                                            } catch (InterruptedException e) {
                                                e.printStackTrace();
                                            }
                                            return null;
                                        }
                                        protected void onPostExecute(Void unused) {
                                            Log.d(TAG, "Delayed CONNECTED event");
                                            mListener.onConnected(mWifiInfo);
                                        }
                                    }.execute(500);*/
                                    generateConnectedDelayedEvent(info);
                                }
                            }
                            else {
                                Log.e(TAG, "Connect to AP but not to SSID "+mSsid);
                                if(setUpTimer != null) {
                                    setUpTimer.cancel();
                                    setUpTimer = null;
                                }
                                context.unregisterReceiver(mReceiver);
                                if(mListener != null) {
                                    mListener.onDisconnected();
                                }
                            }
                        }
                        else {
                            Log.e(TAG, "mWifiInfo is null!");
                        }
                    }
                    // check detailed state
                    else {
                        NetworkInfo.DetailedState currentState = mInfo.getDetailedState();
                        Log.d(TAG, currentState.toString());
                        if(lastKnownState != null) {
                            if(lastKnownState.equals(NetworkInfo.DetailedState.AUTHENTICATING) &&
                                    currentState.equals(NetworkInfo.DetailedState.DISCONNECTED) ) {
                                context.unregisterReceiver(mReceiver);
                                if(setUpTimer != null) {
                                    setUpTimer.cancel();
                                    setUpTimer = null;
                                }
                                if(mListener != null) {
                                    mListener.onAuthenticationError();
                                }
                            }
                        }
                        lastKnownState = currentState;
                    }
                }
                else {
                    Log.e(TAG, "Network is null or not WIFI");
                }
            }
        }
    };


    private void generateConnectedDelayedEvent(final WifiInfo info) {
        if (mListener != null) {
            // Delay events cause some time it is not  so "sharp"
            // to assure that remote hosts are IP reachable and
            // to avoid ENETUNREACH (Network is unreachable) when
            // opening a socket into this handler.
            // We use AsyncTask to communicate with UI thread
            // during callback (i.e. Toast)
            new AsyncTask<Integer, Void, Void>() {
                protected void onPreExecute() {
                    // Pre Code
                }
                protected Void doInBackground(Integer... wait) {
                    try {
                        Thread.sleep(wait[0]);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
                protected void onPostExecute(Void unused) {
                    Log.d(TAG, "Delayed CONNECTED event");
                    mListener.onConnected(info);
                }
            }.execute(500);
        }
    }
}

