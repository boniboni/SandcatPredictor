/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.sandcat.phys;


import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import java.util.ArrayList;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import org.sandcat.phys.ClientScanResult;


/**
 * This Activity appears as a dialog. It lists any paired devices and
 * devices detected in the area after discovery. When a device is chosen
 * by the user, the MAC address of the device is sent back to the parent
 * Activity in the result Intent.
 */
public class DeviceListActivity extends Activity {

	public static final int CONNECT_STATUS = 1;
	public static final String NAME = "device_name";
	public static final String IP = "device_ip";
	public static final String EXTRA_DEVICE_ADDRESS = "device_address";
	
	private String dIp = null;
	private String dName = null;

	//private BluetoothAdapter mBtAdapter;
	private ArrayAdapter<String> mPairedDevicesArrayAdapter;
	private ArrayList<ClientScanResult> clients;
	//	private ArrayAdapter<String> mNewDevicesArrayAdapter;
	private ListView pairedListView;
	//	private ListView newDevicesListView;
	private String TAG = "SANDCAT";
	private boolean VERBOSE = true;
	private UDPCommClient UDPDiscovery;

	@Override
	protected void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);

		// Setup the window
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.device_list);
		setProgressBarIndeterminateVisibility(true);

		// Set result CANCELED incase the user backs out
		setResult(Activity.RESULT_CANCELED);
		
		// initialize handler to treat UDP discoveries
		UDPDiscovery = new UDPCommClient(this, mHandlerDevice);

		// Initialize the button to perform device discovery
		Button scanButton = (Button) findViewById(R.id.button_scan);
		scanButton.setOnClickListener(new OnClickListener(){
			public void onClick(View v){
				doDiscovery();
				v.setVisibility(View.GONE);
			}
		});
		doDiscovery();
	}

	@Override
	protected void onDestroy(){
		super.onDestroy();
		// Make sure we're not doing discovery anymore
		//if (mBtAdapter != null) {
		//    mBtAdapter.cancelDiscovery();
		//}
		// Unregister broadcast listeners
		//this.unregisterReceiver(mReceiver);
	}

	/**
	 * Start device discover with the BluetoothAdapter
	 */
	private void doDiscovery(){
		setTitle(R.string.scanning);
		setProgressBarIndeterminateVisibility(true);

		// Turn on sub-title for new devices
		//	findViewById(R.id.title_new_devices).setVisibility(View.VISIBLE);

		mPairedDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_name);
		//	mNewDevicesArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_name);

		pairedListView = (ListView) findViewById(R.id.paired_devices);
		//		newDevicesListView = (ListView) findViewById(R.id.new_devices);

		// Find and set up the ListView for paired devices
		pairedListView.setAdapter(mPairedDevicesArrayAdapter);
		pairedListView.setOnItemClickListener(mDeviceClickListener);

		// Find and set up the ListView for newly discovered devices

		//	newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
		//	newDevicesListView.setOnItemClickListener(mDeviceClickListener);
		UDPDiscovery.connectB();
	}

	// The on-click listener for all devices in the ListViews
	private OnItemClickListener mDeviceClickListener = new OnItemClickListener(){

		public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3){
			// Cancel discovery because it's costly and we're about to connect
			//mBtAdapter.cancelDiscovery();

			// Get the device IP address, which is the last 17 chars in the View
			String info = ((TextView) v).getText().toString();
			String address = info.substring(info.length() - 14);

			// Create the result Intent and include the IP address
			Intent intent = new Intent();
			intent.putExtra(EXTRA_DEVICE_ADDRESS, address);

			// Set result and finish this Activity
			setResult(Activity.RESULT_OK, intent);
			finish();
		}
	};

	private final Handler mHandlerDevice = new Handler(){
		@Override
		public void handleMessage(Message msg){
			switch (msg.what){
			case CONNECT_STATUS:
				switch (msg.arg1){
				case UDPCommClient.NEW_DEVICE:
					dName = msg.getData().getString(NAME);
					dIp = msg.getData().getString(IP);
					if (VERBOSE) { Log.v(TAG, "TESTE: " + dIp + " " + dName); }  
                    clients = new ArrayList<ClientScanResult>();
					clients.add(new ClientScanResult(dIp, dName));
					if (clients.size() > 0){
						findViewById(R.id.title_paired_devices).setVisibility(View.VISIBLE);
						for (ClientScanResult clientScanResult : clients) {
							mPairedDevicesArrayAdapter.add(clientScanResult.getName() + "\n" + clientScanResult.getIpAddr());
						}
						setTitle(R.string.select_device);
					}
					else {
						String noDevices = getResources().getText(R.string.none_paired).toString();
						mPairedDevicesArrayAdapter.add(noDevices);
						setTitle(R.string.none_paired);
					}
					setProgressBarIndeterminateVisibility(false);
					break;
				case UDPCommClient.STATE_CONNECTING:
					//mBTStatus.setText(R.string.title_connecting);
					break;
				case UDPCommClient.STATE_NONE:
					//mBTStatus.setText(R.string.title_not_connected);
					break;
				case UDPCommClient.STATE_FAILED:
					setProgressBarIndeterminateVisibility(false);
					String noDevices = getResources().getText(R.string.none_paired).toString();
					mPairedDevicesArrayAdapter.add(noDevices);
					setTitle(R.string.none_paired);
					break;
				}
				break;
			}
		}
	};
}

