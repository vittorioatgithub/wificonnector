package it.this1.wificonnector.iotter.ws;

import android.util.Log;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketException;
import com.neovisionaries.ws.client.WebSocketFactory;
import com.neovisionaries.ws.client.WebSocketFrame;
import com.neovisionaries.ws.client.WebSocketListener;
import com.neovisionaries.ws.client.WebSocketState;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Vittorio on 28/01/17.
 */

/**
 * Class that represents a generic WebSocket command/response
 * utility.
 * Manages connection state and simply its model
 * offering a listener interface to allow cascading of commands.
 * It's is base on nv-websocket-client client
 * {@see <a href="https://github.com/TakahikoKawasaki/nv-websocket-client">https://github.com/TakahikoKawasaki/nv-websocket-client</a>
 *
 * To compile remember to add following to grade
 * {@code compile 'com.neovisionaries:nv-websocket-client:1.31'}*
 */
public class IotterWsClientBase implements WebSocketListener  {


    /**
     * The interface Iotters ws callbacks.
     */
    public interface IottersWsCallbacks {

        /**
         * The type Command.
         */
        public class Command {
            private  String command;
            private boolean close;
            private Command() {}

            /**
             * Instantiates a new Command.
             *
             * @param command the command
             * @param close   the close
             */
            public Command(String command, boolean close) {
                this.command = command;
                this.close = close;
            }
        }

        /**
         * Called when remote URI is needed
         *
         * @return String containing URI like "ws://xyx:8000/xyz
         */
        public String onGetURI();

        /**
         * Called when web socket is connected
         */
        public void onConnected();

        /**
         * Called when web socket disconnect
         */
        public void onDisconnected();

        /**
         * Called when an error arises during connestion
         * (usually because host is not reachable
         *
         * @param error String of error
         */
        public void onError(String error);

        /**
         * Called when we are able to send a command,
         * usually after connection
         *
         * @return {@link Command} command to execute
         */
        public Command onExecuteCommand();

        /**
         * Called when we receive somenthing
         * Usually after a read command
         *
         * @param response the response
         * @return true socket is not closed and we want to continue to issue command          false close socket
         */
        public boolean onCommandResponse(String response);

        /**
         * Generated when there was none response after a command.
         * The timeout is a default (500ms)
         *
         * @return boolean
         */
        public boolean onCommandTimeout();

    }

    /**
     * The enum State.
     */
    enum State {
        /**
         * None state.
         */
        none,
        /**
         * Connecting state.
         */
        connecting,
        /**
         * Connected state.
         */
        connected,
        /**
         * Waiting data state.
         */
        waiting_data,
        /**
         * Close state.
         */
        close
    }


    private static  final String TAG = "IotterWsClientBase";
    private State currentState = State.close;
    private Timer commandTimer = new Timer();
    private long DEFAULT_CMD_TO = 1000;
    private final IotterWsClientBase.IottersWsCallbacks listener;
    private WebSocket ws;

    private IotterWsClientBase() {listener = null;}

    /**
     * Instantiates a new Iotter ws client base.
     *
     * @param listener the listener
     */
    public IotterWsClientBase(IotterWsClientBase.IottersWsCallbacks listener) {
        this.listener = listener;
    }

    /**
     * Static method to run a command described by {@link IottersWsCallbacks}
     *
     * @param listener the listener
     */
    public static void run(IottersWsCallbacks listener) {
        IotterWsClientBase base = new IotterWsClientBase(listener);
        base.execute();
    }

    /**
     * Connect and execute command to web socket
     */
    private void execute() {
        try {
            if(listener != null) {
                ws = new WebSocketFactory().createSocket(listener.onGetURI(), 5000);
                ws.addListener(this);

                // Connect to the server and perform an opening handshake.
                // This method blocks until the opening handshake is finished.
                ws.connectAsynchronously();
            }
        } catch (IOException e) {
            Log.e(TAG, "createSocket", e);
            listener.onError(e.getLocalizedMessage());
        } catch(IllegalArgumentException e) {
            Log.e(TAG, "createSocket", e);
            listener.onError(e.getLocalizedMessage());
        }
    }


    @Override
    public void onStateChanged(WebSocket websocket, WebSocketState newState) throws Exception {

    }

    @Override
    public void onConnected(WebSocket websocket, Map<String, List<String>> headers) throws Exception {
        Log.d(TAG, "onConnected");
        if(listener != null) {

            listener.onConnected();

            IottersWsCallbacks.Command cmd = listener.onExecuteCommand();
            if (cmd != null && cmd.command != null) {
                Log.d(TAG, "sending " + cmd.command);
                ws.sendText(cmd.command);
                currentState = State.waiting_data;
                // Check if closing socket
                if (cmd.close) {
                    // need to delay socket closure.
                    // we have seen that otherware socket is not closed
                    // the same do not happen in onTextMessage cb
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            ws.disconnect();
                        }
                    },50);
                }
            }
        }
    }

    @Override
    public void onConnectError(WebSocket websocket, WebSocketException cause) throws Exception {
        Log.w(TAG, "onConnectError()", cause);
        if(listener != null) {
            listener.onError(cause.getLocalizedMessage());
        }
    }

    @Override
    public void onDisconnected(WebSocket websocket, WebSocketFrame serverCloseFrame, WebSocketFrame clientCloseFrame, boolean closedByServer) throws Exception {
        Log.d(TAG, "onDisconnected() close by server "+(closedByServer?"yes":"no"));
        if(listener != null) {
            listener.onDisconnected();
        }
    }

    @Override
    public void onFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {

    }

    @Override
    public void onContinuationFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {

    }

    @Override
    public void onTextFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {
        // Log.d(TAG, "onTextFrame "+frame.toString());
    }

    @Override
    public void onBinaryFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {

    }

    @Override
    public void onCloseFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {

    }

    @Override
    public void onPingFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {

    }

    @Override
    public void onPongFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {

    }

    @Override
    public void onTextMessage(WebSocket websocket, String text) throws Exception {
        Log.d(TAG, "onTextMessage " + text);
        if(listener != null) {
            boolean close = listener.onCommandResponse(text);
            if(!close) {
                // ask again if client want to process a command
                IottersWsCallbacks.Command command = listener.onExecuteCommand();
                if(command != null && command.command != null) {
                    ws.sendText(command.command);
                    currentState = State.waiting_data;
                }
            }
            else {
                ws.disconnect();
            }
        }
    }

    @Override
    public void onBinaryMessage(WebSocket websocket, byte[] binary) throws Exception {

    }

    @Override
    public void onSendingFrame(WebSocket websocket, WebSocketFrame frame) throws Exception {

    }

    @Override
    public void onFrameSent(WebSocket websocket, WebSocketFrame frame) throws Exception {

    }

    @Override
    public void onFrameUnsent(WebSocket websocket, WebSocketFrame frame) throws Exception {

    }

    @Override
    public void onError(WebSocket websocket, WebSocketException cause) throws Exception {
        Log.e(TAG, "OnError()", cause);
    }

    @Override
    public void onFrameError(WebSocket websocket, WebSocketException cause, WebSocketFrame frame) throws Exception {

    }

    @Override
    public void onMessageError(WebSocket websocket, WebSocketException cause, List<WebSocketFrame> frames) throws Exception {
        Log.e(TAG, "onMessageError()", cause);
    }

    @Override
    public void onMessageDecompressionError(WebSocket websocket, WebSocketException cause, byte[] compressed) throws Exception {
        Log.e(TAG, "onMessageDecompressionError()", cause);
    }

    @Override
    public void onTextMessageError(WebSocket websocket, WebSocketException cause, byte[] data) throws Exception {
        Log.e(TAG, "onTextMessageError()", cause);
    }

    @Override
    public void onSendError(WebSocket websocket, WebSocketException cause, WebSocketFrame frame) throws Exception {

    }

    @Override
    public void onUnexpectedError(WebSocket websocket, WebSocketException cause) throws Exception {
        Log.e(TAG, "onUnexpectedError()", cause);
        if(listener != null) {
            listener.onError(cause.getLocalizedMessage());
        }
    }

    @Override
    public void handleCallbackError(WebSocket websocket, Throwable cause) throws Exception {

    }

    @Override
    public void onSendingHandshake(WebSocket websocket, String requestLine, List<String[]> headers) throws Exception {

    }

}
