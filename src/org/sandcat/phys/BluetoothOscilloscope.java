package org.sandcat.phys;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

// implements Button.OnClickListener 
public class BluetoothOscilloscope extends Activity implements  Button.OnClickListener {

	// Message types sent from the BluetoothRfcommClient Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
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

	// bt-uart constants
	private static final int MAX_SAMPLES = 640;
	private static final int  MAX_LEVEL	= 240;
	private static final int  DATA_START = (MAX_LEVEL + 1);
	private static final int  DATA_END = (MAX_LEVEL + 2);

	private static final byte  REQ_DATA = 0x00;
	private static final byte  ADJ_HORIZONTAL = 0x01;
	private static final byte  ADJ_VERTICAL = 0x02;
	private static final byte  ADJ_POSITION = 0x03;

	private static final byte  CHANNEL1 = 0x01;
	private static final byte  CHANNEL2 = 0x02;
	protected static final int READ_HEARTBEAT = 11;

	// Run/Pause status
	private boolean bReady = false;
	// receive data 
	private int[] ch1_data = new int[MAX_SAMPLES/2];
	private int[] ch2_data = new int[MAX_SAMPLES/2];
	private int dataIndex=0, dataIndex1=0, dataIndex2=0;
	private boolean bDataAvailable=false;

	// Layout Views
	private TextView mBTStatus;
	private TextView heartBeat;
	private TextView heartBeat2;
	private Button mConnectButton;
	private RadioButton rb1, rb2;
	private TextView ch1pos_label, ch2pos_label;
	private Button btn_pos_up, btn_pos_down;
	private TextView ch1_scale, ch2_scale;
	private Button btn_scale_up, btn_scale_down;
	private TextView time_per_div;
	private Button timebase_inc, timebase_dec;
	private ToggleButton run_buton;
	private String connectedIP;
	private Vibrator v;

	//	public WaveformView mWaveform = null;

	ImageView imgView;
	public static AnimationDrawable frameAnimation;

	// Name of the connected device
	private String mConnectedDeviceName = null;
	// Member object for the RFCOMM services
	private UDPCommClient UDPComm = null;

	static String[] timebase = {"5us", "10us", "20us", "50us", "100us", "200us", "500us", "1ms", "2ms", "5ms", "10ms", "20ms", "50ms" };
	static String[] ampscale = {"10mV", "20mV", "50mV", "100mV", "200mV", "500mV", "1V", "2V", "GND"};
	static byte timebase_index = 5;
	static byte ch1_index = 4, ch2_index = 5;
	static byte ch1_pos = 24, ch2_pos = 17;	// 0 to 40

	// stay awake
	protected PowerManager.WakeLock mWakeLock;    

	// implements WifiApManager class
	WifiApManager wifiApManager;
	WifiConfiguration WifiBackup;
	WifiConfiguration WifiConfig;
	WifiManager mWManager;
	WifiInfo WifiInfo;
	int lastID;
	private String TAG = "SANDCAT";
	private boolean VERBOSE = true;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (VERBOSE) { Log.v(TAG, "-- ON CREATE --"); }

		// Set up the window layout
		requestWindowFeature(Window.FEATURE_NO_TITLE);        
		setContentView(R.layout.main);

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		this.mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "My Tag"); 
		this.mWakeLock.acquire();

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
		WifiConfig = new WifiConfiguration();
		mWManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		wifiApManager = new WifiApManager(this);
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
		boolean test = true;
		if (VERBOSE) { Log.v(TAG, "-- ON START --"); }
		v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
		// Prevent phone from sleeping
		// If AP is not on, request that it be enabled.
		if (wifiApManager.isWifiApEnabled() == false) {
			test = startWifi();
		}
		if (test == false ) {
			Toast.makeText(this, R.string.wifi_not_enabled_leaving, Toast.LENGTH_SHORT).show();
			finish(); 
			return;
		}
		setupOscilloscope();	

	}

	@Override
	public synchronized void onResume(){
		super.onResume();

		boolean test = true;

		if (VERBOSE) { Log.v(TAG, "-- ON RESUME --"); }

		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		this.mWakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "My Tag"); 
		this.mWakeLock.acquire();

		// Prevent phone from sleeping
		// If AP is not on, request that it be enabled.
		if (wifiApManager.isWifiApEnabled() == false) {
			test = startWifi();
		}
		if (test == false ) {
			Toast.makeText(this, R.string.wifi_not_enabled_leaving, Toast.LENGTH_SHORT).show();
			finish(); 
			return;
		}
		setupOscilloscope();		
	}

	@Override
	public void onPause() {
		super.onPause();  // Always call the superclass method first
		if (VERBOSE) { Log.v(TAG, "-- ON PAUSE --"); }    	   

		if (mWakeLock.isHeld()) { 
			mWakeLock.release();
		}	
	}

	//	if (mRfcommClient != null) {
	// Only if the state is STATE_NONE, do we know that we haven't started already
	//		if (mRfcommClient.getState() == BluetoothRfcommClient.STATE_NONE) {
	// Start the Bluetooth  RFCOMM services
	//		mRfcommClient.start();
	//	}
	@Override
	public void onDestroy(){
		super.onDestroy();
		UDPCommClient.Connected = false;
		UDPCommClient.inUse = false;
		if (VERBOSE) { Log.v(TAG, "-- ON DESTROY --"); }
		mWManager.setWifiEnabled(true);
	}

	@Override
	public void onStop() {
		super.onStop();
		UDPCommClient.Connected = false;
		UDPCommClient.inUse = false;
		if (VERBOSE) { Log.v(TAG, "-- ON STOP --"); }
	}


	@Override
	public void  onClick(View v){
		int buttonID;
		buttonID = v.getId();
		switch (buttonID){
		case R.id.btn_position_up :
			if(rb1.isChecked() && (ch1_pos<38) ){
				ch1_pos += 1; ch1pos_label.setPadding(0, toScreenPos(ch1_pos), 0, 0);
				sendMessage( new String(new byte[] {ADJ_POSITION, CHANNEL1, ch1_pos}) );
			}
			else if(rb2.isChecked() && (ch2_pos<38) ){
				ch2_pos += 1; ch2pos_label.setPadding(0, toScreenPos(ch2_pos), 0, 0);
				sendMessage( new String(new byte[] {ADJ_POSITION, CHANNEL2, ch2_pos}) );
			}
			break;
		case R.id.btn_position_down :
			if(rb1.isChecked() && (ch1_pos>4) ){
				ch1_pos -= 1; ch1pos_label.setPadding(0, toScreenPos(ch1_pos), 0, 0);
				sendMessage( new String(new byte[] {ADJ_POSITION, CHANNEL1, ch1_pos}) );
			}
			else if(rb2.isChecked() && (ch2_pos>4) ){
				ch2_pos -= 1; ch2pos_label.setPadding(0, toScreenPos(ch2_pos), 0, 0);
				sendMessage( new String(new byte[] {ADJ_POSITION, CHANNEL2, ch2_pos}) );
			}
			break;
		case R.id.btn_scale_increase :
			if(rb1.isChecked() && (ch1_index>0)){
				ch1_scale.setText(ampscale[--ch1_index]);
				sendMessage( new String(new byte[] {ADJ_VERTICAL, CHANNEL1, ch1_index}) );
			}
			else if(rb2.isChecked() && (ch2_index>0)){
				ch2_scale.setText(ampscale[--ch2_index]);
				sendMessage( new String(new byte[] {ADJ_VERTICAL, CHANNEL2, ch2_index}) );
			}
			break;
		case R.id.btn_scale_decrease :
			if(rb1.isChecked() && (ch1_index<(ampscale.length-1))){
				ch1_scale.setText(ampscale[++ch1_index]);
				sendMessage( new String(new byte[] {ADJ_VERTICAL, CHANNEL1, ch1_index}) );
			}
			else if(rb2.isChecked() && (ch2_index<(ampscale.length-1))){
				ch2_scale.setText(ampscale[++ch2_index]);
				sendMessage( new String(new byte[] {ADJ_VERTICAL, CHANNEL2, ch2_index}) );
			}
			break;
		case R.id.btn_timebase_increase :
			if(timebase_index<(timebase.length-1)){
				time_per_div.setText(timebase[++timebase_index]);
				sendMessage( new String(new byte[] {ADJ_HORIZONTAL, timebase_index}) );
			}
			break;
		case R.id.btn_timebase_decrease :
			if(timebase_index>0){
				time_per_div.setText(timebase[--timebase_index]);
				sendMessage( new String(new byte[] {ADJ_HORIZONTAL, timebase_index}) );
			}
			break;
		case R.id.tbtn_runtoggle :
			if(run_buton.isChecked()){
				/*	sendMessage( new String(new byte[] {
						ADJ_HORIZONTAL, timebase_index,
						ADJ_VERTICAL, CHANNEL1, ch1_index,
						ADJ_VERTICAL, CHANNEL2, ch2_index,
						ADJ_POSITION, CHANNEL1, ch1_pos,
						ADJ_POSITION, CHANNEL2, ch2_pos,
						REQ_DATA}) );
				bReady = true; */
			}else{
				bReady = false;
			}
			break;
		}
	} 

	/**
	 * Sends a message.
	 * @param message  A string of text to send.
	 */
	private void sendMessage(String message){
		// Check that we're actually connected before trying anything
		// if (mRfcommClient.getState() != BluetoothRfcommClient.STATE_CONNECTED) {
		//	Toast.makeText(this, R.string.not_connected, Toast.LENGTH_SHORT).show();
		//	return;
		// }
		// Check that there's actually something to send
		// if (message.length() > 0) {
		// Get the message bytes and tell the BluetoothRfcommClient to write
		//	byte[] send = message.getBytes();
		//	mRfcommClient.write(send);
		// }
	} 

	private void setupOscilloscope() {
		mBTStatus = (TextView) findViewById(R.id.txt_btstatus);

		mConnectButton = (Button) findViewById(R.id.button_connect);
		mConnectButton.setOnClickListener(new OnClickListener(){
			public void onClick(View arg0) {
				Log.v(TAG, "WFConnect");
				WFConnect();
			}    		
		});
		mBTStatus = (TextView) findViewById(R.id.txt_btstatus);
		heartBeat = (TextView)findViewById(R.id.hbeat);
		heartBeat2 = (TextView)findViewById(R.id.hbeat2);
		rb1 = (RadioButton)findViewById(R.id.rbtn_ch1);
		rb2 = (RadioButton)findViewById(R.id.rbtn_ch2);

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

		time_per_div = (TextView)findViewById(R.id.txt_timebase);
		time_per_div.setText(timebase[timebase_index]);
		timebase_inc = (Button) findViewById(R.id.btn_timebase_increase);
		timebase_dec = (Button) findViewById(R.id.btn_timebase_decrease);
		timebase_inc.setOnClickListener(this);
		timebase_dec.setOnClickListener(this);

		imgView = (ImageView) findViewById(R.id.animationImage);                                        
		imgView.setVisibility(ImageView.VISIBLE);
		imgView.setBackgroundResource(R.drawable.frame_animation);

		frameAnimation = (AnimationDrawable) imgView.getBackground();

		run_buton = (ToggleButton) findViewById(R.id.tbtn_runtoggle);
		run_buton.setOnClickListener(this);

		//Initialize the UDPClient to perform bluetooth connections
		UDPComm = new UDPCommClient(this, mHandler);

		// waveform / plot area
		// mWaveform = (WaveformView)findViewById(R.id.WaveformArea);
	}


	public void WFConnect() {
		boolean test = true;
		if (wifiApManager.isWifiApEnabled() == false) {
			test = startWifi();	
			if (test == false) {
				Toast.makeText(this, "Sandcat is sad. Cannot start.",
						Toast.LENGTH_LONG).show();
				finish();			
				return;
			} else { 
				Toast.makeText(this, "Activating Wifi AP... Wait and try again.", Toast.LENGTH_LONG).show();
			} }

		if (UDPCommClient.inUse == false) {
			Intent serverIntent = new Intent(this, DeviceListActivity.class);
			startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE); 
		} else if (UDPCommClient.Connected == true) {
			UDPCommClient.Connected = false;
			UDPCommClient.inUse = false;
			mHandler.obtainMessage(BluetoothOscilloscope.MESSAGE_STATE_CHANGE, UDPCommClient.STATE_NONE, -1).sendToTarget();
			Toast.makeText(this, "Disconnected...", Toast.LENGTH_LONG).show();
			frameAnimation.stop();
		} else {
			Toast.makeText(this, "Already connected...", Toast.LENGTH_LONG).show();

		}

	}

	private int toScreenPos(byte position){
		//return ( (int)MAX_LEVEL - (int)position*6 );
		return ( (int)MAX_LEVEL - (int)position*6 - 7);
	}

	// The Handler that gets information back from the BluetoothRfcommClient
	private final Handler mHandler = new Handler(){
		@Override
		public void handleMessage(Message msg){
			switch (msg.what){
			case MESSAGE_STATE_CHANGE:
				switch (msg.arg1){
				case UDPCommClient.STATE_CONNECTED:
					mBTStatus.setText(R.string.title_connected_to);
					mBTStatus.append(mConnectedDeviceName);
					break;
				case UDPCommClient.STATE_CONNECTING:
					mBTStatus.setText(R.string.title_connecting);
					break;
				case UDPCommClient.STATE_NONE:
					mBTStatus.setText(R.string.title_not_connected);
					break;
				case UDPCommClient.STATE_FAILED:
					mBTStatus.setText(R.string.title_failed);
					break;
				}
				break;
			case READ_HEARTBEAT:
					if (UDPCommClient.Connected == false) { 
						frameAnimation.stop();
						break; 
					}
				if ((msg.arg2) != 0) {
					frameAnimation.start();
					heartBeat.setText(String.valueOf(msg.arg2));
					heartBeat2.setText(String.valueOf(msg.arg1));
					v.vibrate(110);
					break;
				} else {
					heartBeat.setText("No pulse");
				frameAnimation.stop();
				}

				break;
				case MESSAGE_READ: // todo: implement receive data buffering
					byte[] readBuf = (byte[]) msg.obj;
					int data_length = msg.arg1;
					for(int x=0; x<data_length; x++){
						int raw = UByte(readBuf[x]);
						if( raw>MAX_LEVEL ){
							if( raw==DATA_START ){
								bDataAvailable = true;
								dataIndex = 0; dataIndex1=0; dataIndex2=0;
							}
							else if( (raw==DATA_END) || (dataIndex>=MAX_SAMPLES) ){
								bDataAvailable = false;
								dataIndex = 0; dataIndex1=0; dataIndex2=0;
								// mWaveform.set_data(ch1_data, ch2_data);
								if(bReady){ // send "REQ_DATA" again
									BluetoothOscilloscope.this.sendMessage( new String(new byte[] {REQ_DATA}) );
								}
								//break;
							}
						}
						else if( (bDataAvailable) && (dataIndex<(MAX_SAMPLES)) ){ // valid data
							if((dataIndex++)%2==0) ch1_data[dataIndex1++] = raw;	// even data
							else ch2_data[dataIndex2++] = raw;	// odd data
						}
					}
					break;
				case MESSAGE_DEVICE_NAME:
					// save the connected device's name
					mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
					Toast.makeText(getApplicationContext(), "Connected to "
							+ mConnectedDeviceName, Toast.LENGTH_SHORT).show();
					UDPComm.connected(connectedIP);
					break;
				case MESSAGE_TOAST:
					Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
							Toast.LENGTH_SHORT).show();
					break;
			}
		}
		// signed to unsigned
		private int UByte(byte b){
			if(b<0) // if negative
				return (int)( (b&0x7F) + 128 );
			else
				return (int)b;
		}
	};
	
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
	    if (event.getAction() == KeyEvent.ACTION_DOWN) {
	        switch (keyCode) {
	        case KeyEvent.KEYCODE_BACK :
	            finish();

	            return true;
	        }
	    }
	    return super.onKeyDown(keyCode, event);
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data){
		switch (requestCode) {
		case REQUEST_CONNECT_DEVICE:
			// When DeviceListActivity returns with a device to connect
			if (resultCode == Activity.RESULT_OK){

				String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
				if (VERBOSE) { Log.v(TAG, "teste" + address + "teste"); }
				connectedIP = address;
				UDPComm.connect(address);
			}
			break;
		case REQUEST_ENABLE_BT:
			// When the request to enable Bluetooth returns
			if (resultCode == Activity.RESULT_OK){
				// Bluetooth is now enabled, so set up the oscilloscope
				setupOscilloscope();
			}else{
				// User did not enable Bluetooth or an error occured
				Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
				finish();
			}
			break;
		}
	}
}