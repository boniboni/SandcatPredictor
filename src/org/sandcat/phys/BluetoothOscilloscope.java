/***************************************
 * 
 * Android Bluetooth Oscilloscope
 * yus	-	projectproto.blogspot.com
 * September 2010
 *  
 ***************************************/

package org.sandcat.phys;

import android.app.Activity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import android.content.Context;
import android.content.Intent;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;

import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.Menu;
import android.view.MenuItem;

import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.regex.Pattern;

import java.util.ArrayList;

import org.sandcat.phys.ClientScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.KeyMgmt;

public class BluetoothOscilloscope extends Activity implements
Button.OnClickListener {

	// Message types sent from the BluetoothRfcommClient Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int WPA2_PSK = 4;
	public static final int MESSAGE_READ = 2;
	public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;

	// Key names received from the BluetoothRfcommClient Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";

	// Intent request codes
	private static final int REQUEST_CONNECT_DEVICE = 1;
	private static final int REQUEST_ENABLE_BT = 2;
	private static final int REQUEST_ENABLE_WIFI = 3;

	// bt-uart constants
	private static final int MAX_SAMPLES = 640;
	private static final int MAX_LEVEL = 240;
	private static final int DATA_START = (MAX_LEVEL + 1);
	private static final int DATA_END = (MAX_LEVEL + 2);

	private static final byte REQ_DATA = 0x00;
	private static final byte ADJ_HORIZONTAL = 0x01;
	private static final byte ADJ_VERTICAL = 0x02;
	private static final byte ADJ_POSITION = 0x03;

	private static final byte CHANNEL1 = 0x01;
	private static final byte CHANNEL2 = 0x02;

	// Run/Pause status
	private boolean bReady = false;
	// receive data
	private int[] ch1_data = new int[MAX_SAMPLES / 2];
	private int[] ch2_data = new int[MAX_SAMPLES / 2];
	private int dataIndex = 0, dataIndex1 = 0, dataIndex2 = 0;
	private boolean bDataAvailable = false;

	// Layout Views
	private TextView mBTStatus;
	private Button mConnectButton;
	private RadioButton rb1, rb2;
	private TextView ch1pos_label, ch2pos_label;
	private Button btn_pos_up, btn_pos_down;
	private TextView ch1_scale, ch2_scale;
	private Button btn_scale_up, btn_scale_down;
	private TextView time_per_div;
	private Button timebase_inc, timebase_dec;
	private ToggleButton run_buton;

	public WaveformView mWaveform = null;

	// Name of the connected device
	private String mConnectedDeviceName = null;
	// Local Bluetooth adapter
	private BluetoothAdapter mBluetoothAdapter = null;
	// Member object for the RFCOMM services
	private BluetoothRfcommClient mRfcommClient = null;

	static String[] timebase = { "5us", "10us", "20us", "50us", "100us",
		"200us", "500us", "1ms", "2ms", "5ms", "10ms", "20ms", "50ms" };
	static String[] ampscale = { "10mV", "20mV", "50mV", "100mV", "200mV",
		"500mV", "1V", "2V", "GND" };
	static byte timebase_index = 5;
	static byte ch1_index = 4, ch2_index = 5;
	static byte ch1_pos = 24, ch2_pos = 17; // 0 to 40

	// stay awake
	protected PowerManager.WakeLock mWakeLock;

	// implements WifiApManager class
	public WifiApManager wifiApManager;
	public WifiConfiguration WifiBackup;
	public WifiConfiguration WifiConfig;
	private static final Pattern HEX_DIGITS = Pattern.compile("[0-9A-Fa-f]+");

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Set up the window layout
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.main);

		// Get local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// initialize WifiApManager class and backup settings
		if (!startWifi()) {
			Toast.makeText(this, "Sandcat is sad. Cannot start.",
					Toast.LENGTH_LONG).show();
			finish();
			return;
		}

		// If the adapter is null, then Bluetooth is not supported
		if (mBluetoothAdapter == null) {
			Toast.makeText(this, "Bluetooth is not available",
					Toast.LENGTH_LONG).show();
			finish();
			return;
		}

		// Prevent phone from sleeping
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		this.mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK,
				"My Tag");
		this.mWakeLock.acquire();
	}

	// initialize WifiApManager class and backup settings
	public boolean startWifi() {
		wifiApManager = new WifiApManager(this);
		WifiBackup = new WifiConfiguration();
		WifiConfig = new WifiConfiguration();
		String ssid = "Sandcat";
		String preshared = "sandcat123456";

		WifiConfig.preSharedKey = preshared; //quoteNonHex(preshared, 64);
		WifiConfig.SSID = ssid;
		WifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
		WifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
		WifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.WPA); // For WPA
		WifiConfig.allowedProtocols.set(WifiConfiguration.Protocol.RSN); // For WPA2
		WifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA2_PSK);
		WifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);
		WifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
		WifiConfig.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
		WifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
		WifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
	    WifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
	    WifiConfig.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP104);

		if (wifiApManager.setWifiApEnabled(WifiConfig, true)) {
			return true;
		} else {
			return false;
		}
	}

	private static String quoteNonHex(String value, int... allowedLengths) {
		return isHexOfLength(value, allowedLengths) ? value : convertToQuotedString(value);
	}

	/**
	 * Encloses the incoming string inside double quotes, if it isn't already quoted.
	 * @param string the input string
	 * @return a quoted string, of the form "input".  If the input string is null, it returns null
	 * as well.
	 */
	private static String convertToQuotedString(String string) {
		if (string == null || string.length() == 0) {
			return null;
		}
		// If already quoted, return as-is
		if (string.charAt(0) == '"' && string.charAt(string.length() - 1) == '"') {
			return string;
		}
		return '\"' + string + '\"';
	}

	/**
	 * @param value input to check
	 * @param allowedLengths allowed lengths, if any
	 * @return true if value is a non-null, non-empty string of hex digits, and if allowed lengths are given, has
	 *  an allowed length
	 */
	private static boolean isHexOfLength(CharSequence value, int... allowedLengths) {
		if (value == null || !HEX_DIGITS.matcher(value).matches()) {
			return false;
		}
		if (allowedLengths.length == 0) {
			return true;
		}
		for (int length : allowedLengths) {
			if (value.length() == length) {
				return true;
			}
		}
		return false;
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
		if (wifiApManager.isWifiApEnabled()) {

			// AP is now enabled, so set up the oscilloscope
			setupOscilloscope();
		}			
		else if (startWifi()) {

			// AP is now enabled, so set up the oscilloscope
			setupOscilloscope();
		} else {
			// User did not enable Wifi AP or an error occured
			Toast.makeText(this, R.string.wifi_not_enabled_leaving, Toast.LENGTH_SHORT).show();
			finish(); }
	}


	@Override
	public synchronized void onResume() {
		super.onResume();
		// Performing this check in onResume() covers the case in which BT was
		// not enabled during onStart(), so we were paused to enable it...
		// onResume() will be called when ACTION_REQUEST_ENABLE activity
		// returns.
		if (mRfcommClient != null) {
			// Only if the state is STATE_NONE, do we know that we haven't
			// started already
			if (mRfcommClient.getState() == BluetoothRfcommClient.STATE_NONE) {
				// Start the Bluetooth RFCOMM services
				mRfcommClient.start();
			}
		}
	}

	@Override
	public void onClick(View v) {
		int buttonID;
		buttonID = v.getId();
		switch (buttonID) {
		case R.id.btn_position_up:
			if (rb1.isChecked() && (ch1_pos < 38)) {
				ch1_pos += 1;
				ch1pos_label.setPadding(0, toScreenPos(ch1_pos), 0, 0);
				sendMessage(new String(new byte[] { ADJ_POSITION, CHANNEL1,
						ch1_pos }));
			} else if (rb2.isChecked() && (ch2_pos < 38)) {
				ch2_pos += 1;
				ch2pos_label.setPadding(0, toScreenPos(ch2_pos), 0, 0);
				sendMessage(new String(new byte[] { ADJ_POSITION, CHANNEL2,
						ch2_pos }));
			}
			break;
		case R.id.btn_position_down:
			if (rb1.isChecked() && (ch1_pos > 4)) {
				ch1_pos -= 1;
				ch1pos_label.setPadding(0, toScreenPos(ch1_pos), 0, 0);
				sendMessage(new String(new byte[] { ADJ_POSITION, CHANNEL1,
						ch1_pos }));
			} else if (rb2.isChecked() && (ch2_pos > 4)) {
				ch2_pos -= 1;
				ch2pos_label.setPadding(0, toScreenPos(ch2_pos), 0, 0);
				sendMessage(new String(new byte[] { ADJ_POSITION, CHANNEL2,
						ch2_pos }));
			}
			break;
		case R.id.btn_scale_increase:
			if (rb1.isChecked() && (ch1_index > 0)) {
				ch1_scale.setText(ampscale[--ch1_index]);
				sendMessage(new String(new byte[] { ADJ_VERTICAL, CHANNEL1,
						ch1_index }));
			} else if (rb2.isChecked() && (ch2_index > 0)) {
				ch2_scale.setText(ampscale[--ch2_index]);
				sendMessage(new String(new byte[] { ADJ_VERTICAL, CHANNEL2,
						ch2_index }));
			}
			break;
		case R.id.btn_scale_decrease:
			if (rb1.isChecked() && (ch1_index < (ampscale.length - 1))) {
				ch1_scale.setText(ampscale[++ch1_index]);
				sendMessage(new String(new byte[] { ADJ_VERTICAL, CHANNEL1,
						ch1_index }));
			} else if (rb2.isChecked() && (ch2_index < (ampscale.length - 1))) {
				ch2_scale.setText(ampscale[++ch2_index]);
				sendMessage(new String(new byte[] { ADJ_VERTICAL, CHANNEL2,
						ch2_index }));
			}
			break;
		case R.id.btn_timebase_increase:
			if (timebase_index < (timebase.length - 1)) {
				time_per_div.setText(timebase[++timebase_index]);
				sendMessage(new String(new byte[] { ADJ_HORIZONTAL,
						timebase_index }));
			}
			break;
		case R.id.btn_timebase_decrease:
			if (timebase_index > 0) {
				time_per_div.setText(timebase[--timebase_index]);
				sendMessage(new String(new byte[] { ADJ_HORIZONTAL,
						timebase_index }));
			}
			break;
		case R.id.tbtn_runtoggle:
			if (run_buton.isChecked()) {
				sendMessage(new String(new byte[] { ADJ_HORIZONTAL,
						timebase_index, ADJ_VERTICAL, CHANNEL1, ch1_index,
						ADJ_VERTICAL, CHANNEL2, ch2_index, ADJ_POSITION,
						CHANNEL1, ch1_pos, ADJ_POSITION, CHANNEL2, ch2_pos,
						REQ_DATA }));
				bReady = true;
			} else {
				bReady = false;
			}
			break;
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		// Stop the Bluetooth RFCOMM services
		if (mRfcommClient != null)
			mRfcommClient.stop();
		// release screen being on
		if (mWakeLock.isHeld()) {
			mWakeLock.release();
		}
	}

	/**
	 * Sends a message.
	 * 
	 * @param message
	 *            A string of text to send.
	 */
	private void sendMessage(String message) {
		// Check that we're actually connected before trying anything
		if (mRfcommClient.getState() != BluetoothRfcommClient.STATE_CONNECTED) {
			Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT)
			.show();
			return;
		}
		// Check that there's actually something to send
		if (message.length() > 0) {
			// Get the message bytes and tell the BluetoothRfcommClient to write
			byte[] send = message.getBytes();
			mRfcommClient.write(send);
		}
	}

	private void setupOscilloscope() {

		mBTStatus = (TextView) findViewById(R.id.txt_btstatus);

		mConnectButton = (Button) findViewById(R.id.button_connect);
		mConnectButton.setOnClickListener(new OnClickListener() {
			public void onClick(View arg0) {
				BTConnect();
			}
		});

		rb1 = (RadioButton) findViewById(R.id.rbtn_ch1);
		rb2 = (RadioButton) findViewById(R.id.rbtn_ch2);

		ch1pos_label = (TextView) findViewById(R.id.txt_ch1pos);
		ch2pos_label = (TextView) findViewById(R.id.txt_ch2pos);
		ch1pos_label.setPadding(0, toScreenPos(ch1_pos), 0, 0);
		ch2pos_label.setPadding(0, toScreenPos(ch2_pos), 0, 0);

		btn_pos_up = (Button) findViewById(R.id.btn_position_up);
		btn_pos_down = (Button) findViewById(R.id.btn_position_down);
		btn_pos_up.setOnClickListener(this);
		btn_pos_down.setOnClickListener(this);

		ch1_scale = (TextView) findViewById(R.id.txt_ch1_scale);
		ch2_scale = (TextView) findViewById(R.id.txt_ch2_scale);
		ch1_scale.setText(ampscale[ch1_index]);
		ch2_scale.setText(ampscale[ch2_index]);

		btn_scale_up = (Button) findViewById(R.id.btn_scale_increase);
		btn_scale_down = (Button) findViewById(R.id.btn_scale_decrease);
		btn_scale_up.setOnClickListener(this);
		btn_scale_down.setOnClickListener(this);

		time_per_div = (TextView) findViewById(R.id.txt_timebase);
		time_per_div.setText(timebase[timebase_index]);
		timebase_inc = (Button) findViewById(R.id.btn_timebase_increase);
		timebase_dec = (Button) findViewById(R.id.btn_timebase_decrease);
		timebase_inc.setOnClickListener(this);
		timebase_dec.setOnClickListener(this);

		run_buton = (ToggleButton) findViewById(R.id.tbtn_runtoggle);
		run_buton.setOnClickListener(this);

		// Initialize the BluetoothRfcommClient to perform bluetooth connections
		mRfcommClient = new BluetoothRfcommClient(this, mHandler);

		// waveform / plot area
		mWaveform = (WaveformView) findViewById(R.id.WaveformArea);
	}

	private void BTConnect() {
		Intent serverIntent = new Intent(this, DeviceListActivity.class);
		startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
	}

	private int toScreenPos(byte position) {
		// return ( (int)MAX_LEVEL - (int)position*6 );
		return ((int) MAX_LEVEL - (int) position * 6 - 7);
	}

	// The Handler that gets information back from the BluetoothRfcommClient
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE:
				switch (msg.arg1) {
				case BluetoothRfcommClient.STATE_CONNECTED:
					mBTStatus.setText(R.string.title_connected_to);
					mBTStatus.append("\n" + mConnectedDeviceName);
					break;
				case BluetoothRfcommClient.STATE_CONNECTING:
					mBTStatus.setText(R.string.title_connecting);
					break;
				case BluetoothRfcommClient.STATE_NONE:
					mBTStatus.setText(R.string.title_not_connected);
					break;
				}
				break;
			case MESSAGE_READ: // todo: implement receive data buffering
				byte[] readBuf = (byte[]) msg.obj;
				int data_length = msg.arg1;
				for (int x = 0; x < data_length; x++) {
					int raw = UByte(readBuf[x]);
					if (raw > MAX_LEVEL) {
						if (raw == DATA_START) {
							bDataAvailable = true;
							dataIndex = 0;
							dataIndex1 = 0;
							dataIndex2 = 0;
						} else if ((raw == DATA_END)
								|| (dataIndex >= MAX_SAMPLES)) {
							bDataAvailable = false;
							dataIndex = 0;
							dataIndex1 = 0;
							dataIndex2 = 0;
							mWaveform.set_data(ch1_data, ch2_data);
							if (bReady) { // send "REQ_DATA" again
								BluetoothOscilloscope.this
								.sendMessage(new String(
										new byte[] { REQ_DATA }));
							}
							// break;
						}
					} else if ((bDataAvailable) && (dataIndex < (MAX_SAMPLES))) { // valid
						// data
						if ((dataIndex++) % 2 == 0)
							ch1_data[dataIndex1++] = raw; // even data
						else
							ch2_data[dataIndex2++] = raw; // odd data
					}
				}
				break;
			case MESSAGE_DEVICE_NAME:
				// save the connected device's name
				mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
				Toast.makeText(getApplicationContext(),
						"Connected to " + mConnectedDeviceName,
						Toast.LENGTH_SHORT).show();
				break;
			case MESSAGE_TOAST:
				Toast.makeText(getApplicationContext(),
						msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
						.show();
				break;
			}
		}

		// signed to unsigned
		private int UByte(byte b) {
			if (b < 0) // if negative
				return (int) ((b & 0x7F) + 128);
			else
				return (int) b;
		}
	};

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case REQUEST_CONNECT_DEVICE:
			// When DeviceListActivity returns with a device to connect
			if (resultCode == Activity.RESULT_OK) {
				// Get the device MAC address
				String address = data.getExtras().getString(
						DeviceListActivity.EXTRA_DEVICE_ADDRESS);
				// Get the BLuetoothDevice object
				BluetoothDevice device = mBluetoothAdapter
						.getRemoteDevice(address);
				// Attempt to connect to the device
				mRfcommClient.connect(device);
			}
			break;
		case REQUEST_ENABLE_BT:
			// When the request to enable Bluetooth returns
			if (resultCode == Activity.RESULT_OK) {
				// Bluetooth is now enabled, so set up the oscilloscope
				setupOscilloscope();
			} else {
				// User did not enable Bluetooth or an error occured
				Toast.makeText(this, R.string.bt_not_enabled_leaving,
						Toast.LENGTH_SHORT).show();
				finish();
			}
			break;
		case REQUEST_ENABLE_WIFI:
			// When the request to enable Bluetooth returns
			if (resultCode == Activity.RESULT_OK) {
				// Bluetooth is now enabled, so set up the oscilloscope
				setupOscilloscope();
			} else {
				// User did not enable Bluetooth or an error occured
				Toast.makeText(this, R.string.bt_not_enabled_leaving,
						Toast.LENGTH_SHORT).show();
				finish();
			}
			break;
		}
	}
}
