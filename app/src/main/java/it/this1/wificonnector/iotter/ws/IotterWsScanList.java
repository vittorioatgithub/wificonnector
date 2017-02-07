package it.this1.wificonnector.iotter.ws;

import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Vittorio on 29/01/17.
 */

public class IotterWsScanList implements IotterWsClientBase.IottersWsCallbacks {

    private static final String TAG="IotterWsScanList";
    private String mScanList;
    private final int ST_INIT = 1;
    private final int ST_SCANNING = 2;
    private final int ST_RETRIEVING = 3;
    private int currentState = ST_INIT;
    private final Callbacks cb;
    private Timer timer = new Timer();

    public interface Callbacks {
        public void onResult(String msg);
        public void onError(int errcode);
    }

    private IotterWsScanList() {cb = null;}
    public  IotterWsScanList(Callbacks cb) {
        this.cb = cb;
    }

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
        if(timer != null) timer.cancel();
        cb.onError(-1);
    }

    @Override
    public Command onExecuteCommand() {
        if(currentState == ST_INIT) {
            currentState = ST_SCANNING;
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    IotterWsClientBase.run(IotterWsScanList.this);
                }
            },3000,2000);
            return new Command("wifi_scan=1", true);
        }
        else  {
            currentState = ST_RETRIEVING;
            return new Command("wifi_scan", false);
        }
    }

    @Override
    public boolean onCommandResponse(String response) {
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
                            timer.cancel();
                            cb.onResult(response);
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
        return true;
    }
}
