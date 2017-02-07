package it.this1.wificonnector;

import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.Timer;
import java.util.TimerTask;


import it.this1.wificonnector.iotter.wifi.WifiConnector;
import it.this1.wificonnector.iotter.ws.IotterWsClientBase;
import it.this1.wificonnector.iotter.ws.IotterWsScanList;


public class MainActivity extends AppCompatActivity {
    private static  final String TAG = "WifiConnectorActivity";
    private WifiConnector wifiConnector;
    private String ssid;
    private String password;
    private long startTime;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        wifiConnector = new WifiConnector(getApplicationContext());
    }


    /**
     * Example to show the use of {@link WifiConnector}
     * Connect to an AP whose SSID and password are passed by user.
     * Signals progress of operation with Toast.
     */
    private void connect2AccessPoint() {
        try {
            startTime = System.currentTimeMillis();
            wifiConnector.connect(ssid, password, new WifiConnector.connectorListener() {
                @Override
                public void onConnected(WifiInfo info) {
                    Toast.makeText(MainActivity.this, "CONNECTED time elapsed :"+ (System.currentTimeMillis()- startTime)/1000f+"s\n"+info.toString().replace(",","\n"),
                            Toast.LENGTH_LONG).show();
                    WSScanList1();
                }

                @Override
                public void onDisconnected() {
                    Toast.makeText(MainActivity.this, "SSID "+ssid+" disconnected!",
                            Toast.LENGTH_LONG).show();
                }

                @Override
                public void onSSIDFoundError() {
                    Toast.makeText(MainActivity.this, "SSID "+ssid+" not found",
                            Toast.LENGTH_LONG).show();
                }

                @Override
                public void onAuthenticationError() {
                    Toast.makeText(MainActivity.this, "SSID "+ssid+" authentication error",
                            Toast.LENGTH_LONG).show();
                }

                @Override
                public void onSSIDFound(ScanResult info) {
                    Toast.makeText(MainActivity.this, "FOUND time elapsed :"+(System.currentTimeMillis()- startTime)+"ms\n"+info.toString().replace(",","\n"),
                            Toast.LENGTH_LONG).show();
                }

                @Override
                public void onTimeout() {
                    showToast("TimeOut time elapsed :"+(System.currentTimeMillis()- startTime)+"ms",
                            Toast.LENGTH_LONG);
                }
            });
        } catch (WifiConnector.ParamInvalid paramInvalid) {
            paramInvalid.printStackTrace();
            showToast("Invalid Parameter! ssid:"+ssid+" password:"+password, Toast.LENGTH_LONG);
        }
    }

    public void onConnect(View view) {
        EditText edt = (EditText)findViewById(R.id.editTextSSID);
        ssid = edt.getText().toString();
        edt = (EditText)findViewById(R.id.editTextPassword);
        password = edt.getText().toString();
        Toast.makeText(MainActivity.this, "Try connection to SSID "+ssid+" and password "+password,
                Toast.LENGTH_SHORT).show();

        connect2AccessPoint();
    }

    private void showToast(final String error, final int to) {
        MainActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, error, to).show();
            }
        });
    }

    /**
     * @param view
     */
    public void onWSTest(View view) {
        WSScanList1();
    }


    /**
     * Example to show how call {@link IotterWsClientBase}
     * Connect method accept a callback for events firing.
     * In this example we ask to scan wifi network, then launch a timer
     * that ask for scan list. Timer is stopped when we found at least 1 ap.
     * Important is the use of close field in the Command object because
     * write only command are apply when ws is closed.
     */
    private void WSScanList() {
        IotterWsClientBase.run(new IotterWsClientBase.IottersWsCallbacks() {
            Timer timer = new Timer();
            @Override
            public String onGetURI() {
                return "ws://192.168.4.1/web.cgi";
            }

            @Override
            public void onConnected() {}

            @Override
            public void onDisconnected() {}

            @Override
            public void onError(final String error) {
                if(timer != null) timer.cancel();
                showToast("WS ERROR "+error, Toast.LENGTH_LONG);
            }

            @Override
            public Command onExecuteCommand() {
                Log.d(TAG, "onExecuteCommand");

                // Launch a timer to check scan result
                timer.scheduleAtFixedRate(new TimerTask() {
                    private int counter = 0;
                    @Override
                    public void run() {
                        IotterWsClientBase.run(new IotterWsClientBase.IottersWsCallbacks() {

                            @Override
                            public String onGetURI() { return "ws://192.168.4.1/web.cgi";}

                            @Override
                            public void onConnected() {}

                            @Override
                            public void onDisconnected() {}

                            @Override
                            public void onError(final String error) {showToast("WS ERROR "+error, Toast.LENGTH_LONG);}

                            @Override
                            public Command onExecuteCommand() {
                                // prepare scan list returned command
                                return new Command("wifi_scan", false);
                            }

                            @Override
                            public boolean onCommandResponse(final String response) {
                                Log.d(TAG, "scan list "+response);
                                // i.e. response is an XML parse it and decide if stop timer
                                try {
                                    XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
                                    factory.setNamespaceAware(true);
                                    XmlPullParser xpp = factory.newPullParser();

                                    String startTag = "";

                                    xpp.setInput(new StringReader(response));
                                    int eventType = xpp.getEventType();
                                    while (eventType != XmlPullParser.END_DOCUMENT) {
                                        String tagname = xpp.getName();
                                        if (eventType == XmlPullParser.START_DOCUMENT) {
                                            //System.out.println("Start document");
                                        } else if (eventType == XmlPullParser.START_TAG) {
                                            //System.out.println("Start tag " + xpp.getName());
                                            startTag = xpp.getName();
                                        } else if (eventType == XmlPullParser.END_TAG) {
                                            //System.out.println("End tag " + xpp.getName());
                                        } else if (eventType == XmlPullParser.TEXT) {
                                            //System.out.println("Text " + xpp.getText());
                                            if(startTag.equalsIgnoreCase("total")) {
                                                int total = Integer.parseInt(xpp.getText());
                                                if(total > 0) {
                                                    showToast("SCAN LIST time elapsed:"+ (System.currentTimeMillis()- startTime)/1000+"s\n"+response, Toast.LENGTH_LONG);
                                                    timer.cancel();
                                                    // Delay wifi disconnection and reconnection to let gracefully
                                                    // close socket.
                                                    new Timer().schedule(new TimerTask() {
                                                        @Override
                                                        public void run() {
                                                            wifiConnector.disconnect();
                                                            connect2AccessPoint();
                                                        }
                                                    }, 5000);
                                                }
                                            }
                                        }
                                        eventType = xpp.next();
                                    }
                                }
                                catch(Exception e) {
                                    Log.e(TAG, "onCommandResponse", e);
                                }
                                return true;
                            }

                            @Override
                            public boolean onCommandTimeout() {
                                showToast("TIMEOUT", Toast.LENGTH_SHORT);
                                return false;
                            }
                        });
                    }
                }, 3000, 2000);

                // return scan command
                return new Command("wifi_scan=1", true);
            }

            @Override
            // this should not arrive
            public boolean onCommandResponse(String response) {
                Log.d(TAG, "onCommandResponse() "+response);
                return true;
            }

            @Override
            public boolean onCommandTimeout() {
                showToast("TIMEOUT", Toast.LENGTH_SHORT);
                return false;
            }
        });
    }

    private IotterWsScanList.Callbacks scanListCB = new IotterWsScanList.Callbacks() {
        @Override
        public void onResult(String msg) {
            showToast("SCAN LIST time elapsed:"+ (System.currentTimeMillis()- startTime)/1000+"s\n"+msg, Toast.LENGTH_LONG);
            // invoke next operation
            WSStationStatus();
        }

        @Override
        public void onError(int errcode) {
            showToast("ERROR:"+errcode, Toast.LENGTH_SHORT);
            WSScanList1();
        }
    };

    private void WSScanList1() {
        IotterWsClientBase.run(new IotterWsScanList(scanListCB));
    }


    private void WSStationStatus() {
        IotterWsClientBase.run(new IotterWsClientBase.IottersWsCallbacks() {

            @Override
            public String onGetURI() {
                return "ws://192.168.4.1/web.cgi";
            }

            @Override
            public void onConnected() {

            }

            @Override
            public void onDisconnected() {

            }

            @Override
            public void onError(String error) {
                showToast("ERROR:"+error, Toast.LENGTH_SHORT);
            }

            @Override
            public Command onExecuteCommand() {
                return new Command("wifi_st_sta", false);
            }

            @Override
            public boolean onCommandResponse(String response) {
                showToast("WIFI Station time elapsed:"+ (System.currentTimeMillis()- startTime)/1000+"s\nstatus:"+response, Toast.LENGTH_LONG);

                wifiConnector.disconnect();
                //wifiConnector.forget();

                // Delay wifi disconnection and reconnection to let system gracefully
                // close socket.
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        connect2AccessPoint();
                    }
                }, 10000);
                return true;
            }

            @Override
            public boolean onCommandTimeout() {
                showToast("TIMEOUT", Toast.LENGTH_SHORT);
                return false;
            }
        });
    }

    /**
     * Example of use of {@link WifiConnector#disconnect()}
     * Check if entry is really removed from android applet
     * @param view
     */
    public void onWifiDisconnect(View view) {
        wifiConnector.disconnect();
    }
}
