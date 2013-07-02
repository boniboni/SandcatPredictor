/***************************************
 * 
 * Android Bluetooth Oscilloscope
 * yus	-	projectproto.blogspot.com
 * September 2010
 *  
 ***************************************/

package org.sandcat.phys;

import android.app.Activity;

/*import android.bluetooth.BluetoothDevice;



import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;*/
import org.sandcat.phys.R;
import org.sandcat.phys.WifiApManager;
import android.content.Intent;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.content.Context;
import android.view.Window;
import android.view.View.OnClickListener;

import android.widget.Button;
//import android.widget.RadioButton;
import org.sandcat.phys.DeviceListActivity;

import android.widget.Toast;
//import android.widget.ToggleButton;
//import android.widget.TextView;
// import org.sandcat.phys.ClientScanResult;
import android.net.wifi.WifiConfiguration;

// implements Button.OnClickListener 
public class BluetoothOscilloscope extends Activity {

	// Message types sent from the BluetoothRfcommClient Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	private Button mConnectButton;
	public static final int MESSAGE_TOAST = 5;

	// Key names received from the BluetoothRfcommClient Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";

	static String[] timebase = { "5us", "10us", "20us", "50us", "100us",
		"200us", "500us", "1ms", "2ms", "5ms", "10ms", "20ms", "50ms" };
	static String[] ampscale = { "10mV", "20mV", "50mV", "100mV", "200mV",
		"500mV", "1V", "2V", "GND" };
	static byte timebase_index = 5;
	static byte ch1_index = 4, ch2_index = 5;
	static byte ch1_pos = 24, ch2_pos = 17; // 0 to 40

	// stay awake
	//protected PowerManager.WakeLock mWakeLock;

	// implements WifiApManager class
	public WifiApManager wifiApManager;
	public WifiConfiguration WifiBackup;
	public WifiConfiguration WifiConfig;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Set up the window layout
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);


		// initialize WifiApManager class and backup settings
		if (startWifi() == false) {
			Toast.makeText(this, "Sandcat is sad. Cannot start.",
					Toast.LENGTH_LONG).show();
			finish();			
			return;
		}
	}

	// initialize WifiApManager class and backup settings
	public boolean startWifi() {
		wifiApManager = new WifiApManager(this);
		WifiBackup = new WifiConfiguration();
		WifiConfig = new WifiConfiguration();

		WifiConfig.preSharedKey = "sandcat123456";
		WifiConfig.SSID = "Sandcat";
		WifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
		WifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
		WifiConfig.allowedKeyManagement.set(4);
		WifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
		WifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
		WifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
		WifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);

		if (wifiApManager.setWifiApEnabled(WifiConfig, true) == true) {
			return true;
		} else {
			return false;
		}
	}

	@Override
	public void onStart() {
		super.onStart();

		// If BT is not on, request that it be enabled.
		//if (!mBluetoothAdapter.isEnabled()) {
		//	Intent enableIntent = new Intent(
		//			BluetoothAdapter.ACTION_REQUEST_ENABLE);
		//	startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
		//}
		// Otherwise, setup the Oscillosope session
		//else {
		//	if (mRfcommClient == null)
		//		setupOscilloscope();
		//}
		// If Wifi is not on, request that it be enabled.
		if (wifiApManager.isWifiApEnabled() == true) {

			// AP is now enabled, so set up the oscilloscope
			setupOscilloscope();			
			return;
		}			
		else if (startWifi() == true) {

			// AP is now enabled, so set up the oscilloscope
			setupOscilloscope();
			return;
		} else {
			// User did not enable Wifi AP or an error occured
			Toast.makeText(this, R.string.wifi_not_enabled_leaving, Toast.LENGTH_SHORT).show();
			finish(); 
			return;
		}
	}


	@Override
	public synchronized void onResume() {
		super.onResume();
	}


	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	private void setupOscilloscope() {

		mConnectButton = (Button) findViewById(R.id.button_connect);
		mConnectButton.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				Log.e("wut", "onCreate called");
				WFConnect();
			}
		});
	}

	/**
	 * 
	 */
	public void WFConnect() {
		Intent intent = new Intent(this, DeviceListActivity.class);
	    startActivity(intent);
	}
}
