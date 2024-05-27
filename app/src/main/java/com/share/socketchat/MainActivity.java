package com.share.socketchat;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.os.Build;
import android.os.Bundle;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PORT = 56588;
    final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    WifiP2pManager mManager;
    WifiP2pManager.Channel mChannel;
    BroadcastReceiver mReceiver;
    IntentFilter mIntentFilter;

    private Button btnCreateGroup;
    private Button btnJoinGroup;
    private Button btnSend;
    private EditText messageInput;
    private ListView messageList;

    private List<String> messages = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    List<WifiP2pDevice> peers = new ArrayList<>();
    String[] deviceNames = {};
    WifiP2pDevice[] devices;
    BottomDialog bottomDialog;

    Socket socket;
    SendReceive sendReceive;

    private Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            String message = msg.getData().getString("message");
            messages.add(message);
            adapter.notifyDataSetChanged();
            return true;
        }
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnCreateGroup = findViewById(R.id.btnCreateGroup);
        btnJoinGroup = findViewById(R.id.btnJoinGroup);
        btnSend = findViewById(R.id.btnSend);
        messageInput = findViewById(R.id.messageInput);
        messageList = findViewById(R.id.messageList);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, messages);
        messageList.setAdapter(adapter);

        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
        registerReceiver(receiver, intentFilter);
        btnCreateGroup.setOnClickListener(v -> {
            discoverPeers();
        });

        btnJoinGroup.setOnClickListener(v -> {
            discoverPeers();
            openBottomDialog();
        });

        btnSend.setOnClickListener(v -> {
            sendMessage();
        });

        showStatePermission();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST_CODE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("hello", "Permission  LOCATION " + LOCATION_PERMISSION_REQUEST_CODE + " Granted!");
                } else {
                    Log.w("hello", "LOCATION Permission Denied!");
                }
        }
    }

    private void openBottomDialog() {
        if (bottomDialog == null){
            bottomDialog = new BottomDialog();
        }
        bottomDialog.show(getSupportFragmentManager(), "bottom_sheet_fragment");
    }

    WifiP2pManager.PeerListListener peerListListener = new WifiP2pManager.PeerListListener() {
        @Override
        public void onPeersAvailable(WifiP2pDeviceList wifiP2pDeviceList) {
            Log.d("hello", "onPeers Available");

            if (!wifiP2pDeviceList.getDeviceList().equals(peers)) {
                peers.clear();
                peers.addAll(wifiP2pDeviceList.getDeviceList());

                deviceNames = new String[wifiP2pDeviceList.getDeviceList().size()];
                devices = new WifiP2pDevice[wifiP2pDeviceList.getDeviceList().size()];
                int index = 0;

                for (WifiP2pDevice device : wifiP2pDeviceList.getDeviceList()) {
                    deviceNames[index] = device.deviceName;
                    devices[index] = device;
                    index++;
                }

                if (bottomDialog != null) {
                    bottomDialog.notifyDataChange();
                }
            }

            if (peers.size() == 0) {
                Toast.makeText(MainActivity.this, "No device found", Toast.LENGTH_SHORT).show();
            }
        }
    };

    private void showExplanation(String title,
                                 String message,
                                 final String permission,
                                 final int permissionRequestCode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, id) ->
                        requestPermission(new String[]{
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.NEARBY_WIFI_DEVICES,
                                Manifest.permission.CHANGE_WIFI_STATE
                        }, permissionRequestCode));
        builder.create().show();
    }

    private void showStatePermission() {
        int permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
                showExplanation("Permission Needed", "Rationale", Manifest.permission.READ_PHONE_STATE, LOCATION_PERMISSION_REQUEST_CODE);
            } else {
                requestPermission(new String[]{
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.NEARBY_WIFI_DEVICES,
                        Manifest.permission.CHANGE_WIFI_STATE
                }, LOCATION_PERMISSION_REQUEST_CODE);
            }
        } else {
            Log.d("hello", "Permission (already) Granted!");
//            Toast.makeText(this, "Permission (already) Granted!", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestPermission(String[] permissionName, int permissionRequestCode) {
        ActivityCompat.requestPermissions(this, permissionName, permissionRequestCode);
    }

    private void discoverPeers() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                Log.d("hello discoverPeers", "Need permission");
                return;
            }
        }
        mManager.removeGroup(mChannel, actionListener);
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(MainActivity.this, "Discovery Started", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(MainActivity.this, "Discovery Failed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
    }

    WifiP2pManager.ConnectionInfoListener connectionInfoListener = info -> {
        Log.d("hello connectionInfoListener", String.valueOf(info.groupFormed));
        Log.d("hello connectionInfoListener", String.valueOf(info.isGroupOwner));

        if (info.groupFormed && info.isGroupOwner) {
            ServerClass serverClass = new ServerClass();
            serverClass.start();
        } else if (info.groupFormed) {
            ClientClass clientClass = new ClientClass(info.groupOwnerAddress);
            clientClass.start();
        }
    };

    WifiP2pManager.ActionListener actionListener = new WifiP2pManager.ActionListener() {
        @Override
        public void onSuccess() {
            Log.i("hello", "Stop SUCCESS");
        }

        @Override
        public void onFailure(int i) {
            Log.w("hello", "Stop FAILLLLLLL");
        }
    };

    private void sendMessage(){
        if (socket == null){
            Toast.makeText(MainActivity.this, "Socket not available", Toast.LENGTH_SHORT).show();
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {
            writer.newLine();
            writer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public class ServerClass extends Thread{
        ServerSocket serverSocket;

        @Override
        public void run() {
            Log.i("hello serve", "SERVVVVVVVVVVVVVV OOOOOO OOOOOOOOOOOO");

            try{
                Log.i("hello", "SERVVVVVVVVVVVVVV OOOOOO before ");

                serverSocket = new ServerSocket(PORT);

                Log.d("hello serve", "serverSocket.getInetAddress() :" + serverSocket.getInetAddress());
                Log.i("hello serve", "SERVVVVVVVVVVVVVV OOOOOO New instance ");
                socket = serverSocket.accept();

                Log.i("hello serve", "SERVVVVVVVVVVVVVV OOOOOO Start ");

                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    Message msg = handler.obtainMessage();
                    Bundle bundle = new Bundle();
                    bundle.putString("message", "Client: " + line);
                    msg.setData(bundle);
                    handler.sendMessage(msg);
                }

                // Log.e("hello", String.valueOf(users.size()));
            } catch (SocketException e) {
                // Handle the SocketException
                Log.e("hello", "run: error", e);
                e.printStackTrace();
                // Show error dialog to the user
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    /*public class ClientClass extends Thread {
        String hostAddress;
        OutputStream outputStream;

        public ClientClass(InetAddress hostAddress) {
            this.hostAddress = hostAddress.getHostAddress();
            socket = new Socket();
        }

        @Override
        public void run() {
            Log.i("hello client", "CLIIIIIIIIIIIIIIIIIIIIIIIIIIIII");
            try (Socket socket = new Socket(hostAddress, PORT)) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    Message msg = handler.obtainMessage();
                    Bundle bundle = new Bundle();
                    bundle.putString("message", "Server: " + line);
                    msg.setData(bundle);
                    handler.sendMessage(msg);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }*/

    public class ClientClass extends Thread{
        Socket socket;
        String hostAddress;
        public ClientClass (InetAddress hostAddress){
            this.hostAddress = hostAddress.getHostAddress();
            socket = new Socket();
        }

        @Override
        public void run() {
            try {
                socket.connect(new InetSocketAddress(hostAddress, PORT), 500);
                sendReceive = new SendReceive(socket);
                sendReceive.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public class SendReceive extends Thread{
        private final Socket socket;
        private InputStream inputStream;
        private OutputStream outputStream;

        public SendReceive(Socket socket){
            this.socket = socket;
            try {
                inputStream = this.socket.getInputStream();
                outputStream = this.socket.getOutputStream();

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (socket != null){
                try {
                    bytes = inputStream.read(buffer);
                    if (bytes > 0){
                        handler.obtainMessage(1, bytes, -1, buffer).sendToTarget();
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        public void write(byte[] bytes){
            try {
                Log.e("hello", Arrays.toString(bytes));

                outputStream.write(bytes);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}