package com.share.socketchat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.ArrayList;
import java.util.List;

public class BottomDialog extends BottomSheetDialogFragment {
    MainActivity mainActivity;
    ListView listViewDevice;
    private IntentFilter mIntentFilter;
    private WifiP2pManager mManager;
    BroadcastReceiver mReceiver;

    WifiP2pConfig configAddress;
    WifiP2pDevice deviceTOConnect;
    WifiP2pManager.Channel mChannel;

    ArrayAdapter<String> arrayAdapter;

    public BottomDialog() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_layout_dialog, container, false);

        initView(view);
        initExecution();
        initListener();
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        mainActivity.registerReceiver(mReceiver, mIntentFilter);
    }

    @Override
    public void onPause() {
        super.onPause();
        mainActivity.unregisterReceiver(mReceiver);
    }

    private void initView(View view) {
        mainActivity = (MainActivity) getActivity();
        listViewDevice = view.findViewById(R.id.list_devices);
    }

    private void initExecution() {
        mManager = mainActivity.mManager;
        mChannel = mainActivity.mChannel;
        mReceiver = mainActivity.mReceiver;
        mIntentFilter = mainActivity.mIntentFilter;

        arrayAdapter = new ArrayAdapter<>(mainActivity, android.R.layout.simple_list_item_1, mainActivity.deviceNames);
        listViewDevice.setAdapter(arrayAdapter);
    }

    public void notifyDataChange(){
        if (arrayAdapter == null) return;
        arrayAdapter.notifyDataSetChanged();
    }

    private void initListener() {
        listViewDevice.setOnItemClickListener((parent, view, position, id) -> {
            deviceTOConnect = mainActivity.devices[position];
            configAddress = new WifiP2pConfig();
            configAddress.deviceAddress = deviceTOConnect.deviceAddress;

            new AlertDialog.Builder(requireContext())
                    .setTitle("Connexion")
                    .setMessage("Etablir une connection avec l'appareil : " + deviceTOConnect.deviceName)
                    .setPositiveButton("Oui", (dialog, which) -> {
                        // User clicked Yes button
                        // Perform your action here
                        makeConnection();
                    })
                    .setNegativeButton("Non", (dialog, which) -> {
                        // User clicked No button
                        // Dismiss the dialog or perform any other action
                    }).create().show();

        });
    }

    private void makeConnection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(mainActivity, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(mainActivity, android.Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(mainActivity, "Need permission", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        mManager.connect(mChannel, configAddress, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Toast.makeText(mainActivity.getApplicationContext(), "Connexion établie ! Votre appareil est maintenant associé à " + deviceTOConnect.deviceName, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(mainActivity.getApplicationContext(), "connexion échouée", Toast.LENGTH_SHORT).show();
            }
        });
    }


}
