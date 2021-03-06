/***************************************
 * 
 * Android Bluetooth Oscilloscope
 * yus	-	projectproto.blogspot.com
 * September 2010
 *  
 ***************************************/

package org.sandcat.phys;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.net.DhcpInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.MulticastLock;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.net.DatagramPacket; 
import java.net.DatagramSocket; 
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.sandcat.phys.WifiApManager;


public class UDPCommClient {
	public static final int SERVERPORT = 6666;
	public static final int SERVERPORT2 = 6667;

	// Member fields
	private static ConnectThread mConnectThread;
	private static ConnectedThread mConnectedThread;
	private int mState;
	private final Handler mHandler;
	public WifiApManager wApManager;
	public WifiManager wifi;
	public static boolean inUse = false;
	public static boolean Connected = false;

	// Constants that indicate the current connection state
	public static final int STATE_NONE = 0;       // we're doing nothing
	public static final int STATE_FAILED = 4;
	public static final int NEW_DEVICE = 6;
	//public static final int STATE_LISTEN = 1;     // now listening for incoming connections
	public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
	public static final int STATE_CONNECTED = 3;  // now connected to a remote device
	public static final String TAG = "SANDCAT";
	public static final boolean VERBOSE = true;
	Context contexto;

	/**
	 * Constructor. Prepares a new BluetoothChat session.
	 * - context - The UI Activity Context
	 * - handler - A Handler to send messages back to the UI Activity
	 */
	public UDPCommClient(Context context, Handler handler) {
		this.contexto = context;
		wApManager = new WifiApManager (context);
		wifi = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		//	mAdapter = BluetoothAdapter.getDefaultAdapter();
		mState = STATE_NONE;
		mHandler = handler;
	}

	/**
	 * Set the current state o
	 * */
	private synchronized void setState(int state) {
		mState = state;

		// Give the new state to the Handler so the UI Activity can update
		mHandler.obtainMessage(BluetoothOscilloscope.MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
	}

	/**
	 * Return the current connection state. */
	public synchronized int getState() {
		return mState;
	}


	/**
	 * Start the ConnectThread to initiate a connection to a remote device.
	 * - device - The BluetoothDevice to connect
	 */
	public synchronized void connectB() {

		// Cancel any thread attempting to make a connection
		//	if (mState == STATE_CONNECTING) {
		//		if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
		//	}

		// Cancel any thread currently running a connection
		//	if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

		// Start the thread to connect with the given device
		if (UDPCommClient.inUse == false) {
			setState(STATE_CONNECTING);

			Thread t1 = new Thread(new ConnectThreadB());
			t1.setName("Thread-1");
			t1.start();
			if (VERBOSE) { Log.v(TAG, "Broadcast Thread Success!"); }  
		} else {
			Toast.makeText(contexto, "Still trying to connect...", Toast.LENGTH_LONG).show();

		}
	}

	public synchronized void connect(String address) {

		// Cancel any thread attempting to make a connection
		//	if (mState == STATE_CONNECTING) {
		//		if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
		//	}

		// Cancel any thread currently running a connection
		//	if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}

		// Start the thread to connect with the given device
		if (UDPCommClient.inUse == false) {
			setState(STATE_CONNECTING);
			String Ip = address;
			new Thread(new ConnectThread(Ip)).start();
			if (VERBOSE) { Log.v(TAG, "Broadcast Thread Success!"); }  
		} else {
			Toast.makeText(contexto, "Still trying to connect...", Toast.LENGTH_LONG).show();
		}
	}
	/**
	 * Start the ConnectedThread to begin managing a Bluetooth connection
	 * - socket - The BluetoothSocket on which the connection was made
	 * - device - The BluetoothDevice that has been connected
	 */
	public synchronized void connected(String address) {

		if (UDPCommClient.Connected == true) {
			setState(STATE_CONNECTED);
			String Ip = address;
			new Thread(new ConnectedThread(Ip)).start();
			if (VERBOSE) { Log.v(TAG, "Broadcast Thread Success!"); }  
		} else {
			Toast.makeText(contexto, "Still trying to connect...", Toast.LENGTH_LONG).show();
		}
	}
		
		
		
	
	public synchronized void stop() {
		//	if (mConnectThread != null) {mConnectThread.cancel(); mConnectThread = null;}
		//	if (mConnectedThread != null) {mConnectedThread.cancel(); mConnectedThread = null;}
		setState(STATE_NONE);
	}


	private void connectionFailed() {
		setState(STATE_NONE);
		// Send a failure message back to the Activity
		Message msg = mHandler.obtainMessage(BluetoothOscilloscope.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(BluetoothOscilloscope.TOAST, "Unable to connect device");
		msg.setData(bundle);
		mHandler.sendMessage(msg);
	}

	/**
	 * Indicate that the connection was lost and notify the UI Activity.
	 */
	private void connectionLost() {
		setState(STATE_NONE);
		// Send a failure message back to the Activity
		Message msg = mHandler.obtainMessage(BluetoothOscilloscope.MESSAGE_TOAST);
		Bundle bundle = new Bundle();
		bundle.putString(BluetoothOscilloscope.TOAST, "Device connection was lost");
		msg.setData(bundle);
		mHandler.sendMessage(msg);
	}
	/**
	 * This thread runs while attempting to make an outgoing connection
	 * with a device. It runs straight through; the connection either
	 * succeeds or fails.
	 */

	private class ConnectThread implements Runnable {
		DatagramPacket packet;
		DatagramPacket packetR;
		DatagramSocket socketR;
		DatagramSocket socket;
		InetAddress cIp;
		InetAddress sIp;
		int counter = 0;
		private boolean receive = true;
		private String ip;
		private byte[] buf = ("connect").getBytes();
		private String bufRS = "who";
		private String bufRSK;
		private String bufN;
		private byte[] bufR = new byte[1024];
		private int bufRL = 0;

		public ConnectThread(String address) {
			UDPCommClient.inUse = true;
			ip = address;
		}

		public void run() {

			try {
				cIp = InetAddress.getByName(ip);
				//		sIp = InetAddress.getByName(wApManager.getWifiApIpAddress());
				sIp = InetAddress.getByName("192.168.43.1");
			} catch (UnknownHostException e) {
				if (VERBOSE) { Log.v(TAG, "Hostname error!"); } }
			while(receive) {
				try {
					socket = new DatagramSocket(); 
					packet = new DatagramPacket(buf, buf.length, cIp, SERVERPORT);
					socketR = new DatagramSocket(SERVERPORT, sIp); 
					socket.setReuseAddress(true);
					socketR.setReuseAddress(true);
					packetR = new DatagramPacket(bufR, bufR.length); 					
					if (VERBOSE) { Log.v(TAG, "Client: Connecting"); }
					if (VERBOSE) { Log.v(TAG, "Client sent!"); }
					socket.send(packet);
					if (VERBOSE) { Log.v(TAG, "Client: Sending " + new String(buf)); }  
					if (VERBOSE) { Log.v(TAG, "Server: Listening"); } 
					socketR.setSoTimeout(3000);
					socketR.receive(packetR);
					bufRL = packetR.getLength();
					bufRSK = new String(packetR.getData()).substring(0,3);
					bufN = new String(packetR.getData()).substring(3, packetR.getLength());
				} catch (SocketTimeoutException e) {
					if (counter < 3) { 
						packet.setLength(buf.length);
						packetR.setLength(bufR.length);
						socketR.disconnect();
						socket.disconnect();
						socket.close();
						socketR.close();
						UDPCommClient.inUse = true;
						counter++;
						continue; } 
					else { 
						setState(STATE_FAILED);
						socketR.disconnect();
						socket.disconnect();
						socket.close();
						socketR.close();
						UDPCommClient.inUse = false;
						break; }
				} catch (SocketException e) { if (VERBOSE) { Log.v(TAG, "Error 1"); }
				} catch (IOException e) { if (VERBOSE) { Log.v(TAG, "Error 2"); } }
				if (bufRSK.equals(bufRS)) {
					//if (new String(packetR.getData()).equals(bufRS)) {
					if (VERBOSE) { Log.v(TAG, "Server message received: " + new String(packetR.getData()).substring(0, packetR.getLength())); }						
					receive = false; 
					packet.setLength(buf.length);
					packetR.setLength(bufR.length);
					// Send the name of the connected device back to the UI Activity
					Message msg = mHandler.obtainMessage(BluetoothOscilloscope.MESSAGE_DEVICE_NAME);
					Bundle bundle = new Bundle();
					bundle.putString(BluetoothOscilloscope.DEVICE_NAME, bufN);
					msg.setData(bundle);
					mHandler.sendMessage(msg);
					setState(STATE_CONNECTED);
					socketR.disconnect();
					socket.disconnect();
					socket.close();
					socketR.close(); 
					UDPCommClient.Connected = true;
					UDPCommClient.inUse = true;
				}
			}
		}

		public void cancel() {

		}
	}

	private class ConnectThreadB implements Runnable {
		DatagramPacket packet;
		DatagramPacket packetR; 
		DatagramSocket socketR;
		DatagramSocket socket;
		MulticastLock lock;
		InetAddress cIp;
		NetworkInterface temp;
		DhcpInfo dhcp;
		InetAddress sIp;
		InetAddress sIpB;
		private Message msg;
		private Bundle bundle;
		List<InterfaceAddress> addresses;
		private String ip;
		private byte[] bufP = "doctor".getBytes();
		//private byte[] bufK = "who".getBytes();
		private String bufK = "who";
		private String packetRS;
		private String bufN;
		private String senderIP;
		private byte[] bufACK = new byte[1024];
		private byte[] bufR = new byte[1024];
		private int bufRL = 0;

		public ConnectThreadB() {	
			UDPCommClient.inUse = true;
			try {
				sIp = InetAddress.getByName(wApManager.getWifiApIpAddress());
			} catch (UnknownHostException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			sIpB = null;
			try {
				temp = NetworkInterface.getByInetAddress(sIp);
				List<InterfaceAddress> addresses = temp.getInterfaceAddresses();
				for(InterfaceAddress inetAddress:addresses)
					sIpB=inetAddress.getBroadcast();
				if (VERBOSE) { Log.v(TAG, "sIpB: " + sIpB + " Ip: " + sIp); }		
			} catch (SocketException e) {

				e.printStackTrace();
				Log.d(TAG,"getBroadcast"+e.getMessage());
			}
		}

		public void run() {
			//		sIp2 = InetAddress.getByName(wApManager.getWifiApIpAddress());
			//lock = wifi.createMulticastLock("SANDCAT");
			//lock.acquire();
			//socketR = new DatagramSocket(SERVERPORT);
			//socketR.setReuseAddress(true);
			try {
				socket = new DatagramSocket(SERVERPORT);
				socket.setReuseAddress(true);   
				packetR = new DatagramPacket(bufACK, bufACK.length);
				packet = new DatagramPacket(bufP, bufP.length, sIpB, SERVERPORT);
				socket.setBroadcast(true);	
				if (VERBOSE) { Log.v(TAG, "Server: UDPBroadcast"); } 
				socket.send(packet);
				socket.setBroadcast(false);
				if (VERBOSE) { Log.v(TAG, "Server: Waiting..."); }
				socket.disconnect();
				socket.close();
				socketR = new DatagramSocket(SERVERPORT);
				socketR.setReuseAddress(true);
				socketR.setSoTimeout(5000); //5 sec wait for the client to connect
				socketR.receive(packetR);
				packetRS = new String(packetR.getData()).trim();
				if (VERBOSE) { Log.v(TAG, "Server received: " + packetRS); }  

				if (packetRS.substring(0,3).equals(bufK)) {
					bufN = packetRS.substring(4, packetRS.length());
					senderIP = packetR.getAddress().getHostAddress();
					if (VERBOSE) { Log.v(TAG, "Client found! " + senderIP + " Name: " + bufN); }  
					packet.setLength(bufP.length);
					packetR.setLength(bufACK.length);
					msg = mHandler.obtainMessage(DeviceListActivity.CONNECT_STATUS, NEW_DEVICE, -1);
					bundle = new Bundle();
					bundle.putString(DeviceListActivity.NAME, bufN);
					bundle.putString(DeviceListActivity.IP, senderIP);
					msg.setData(bundle);
					mHandler.sendMessage(msg);
					socketR.disconnect();
					socket.disconnect();
					socket.close();
					socketR.close();
					UDPCommClient.inUse = false;
				}
			} catch (SocketTimeoutException e) {
				if (VERBOSE) { Log.v(TAG, "Error 1"); 
				packet.setLength(bufP.length);
				packetR.setLength(bufACK.length);
				socketR.disconnect();
				socket.disconnect();
				socket.close();
				socketR.close();
				UDPCommClient.inUse = false;
				msg = mHandler.obtainMessage(DeviceListActivity.CONNECT_STATUS, STATE_FAILED, -1);
				mHandler.sendMessage(msg);
				}
			} catch (IOException e) {
				if (VERBOSE) { Log.v(TAG, "Error 2"); 
				packet.setLength(bufP.length);
				packetR.setLength(bufACK.length);
				socketR.disconnect();
				socket.disconnect();
				socket.close();
				socketR.close();
				UDPCommClient.inUse = false;
				msg = mHandler.obtainMessage(DeviceListActivity.CONNECT_STATUS, STATE_FAILED, -1);
				mHandler.sendMessage(msg);
				}
			} 
		}
	}

	private class ConnectedThread implements Runnable {
		DatagramPacket packet;
		DatagramPacket packetR; 
		DatagramSocket socketR;
		DatagramSocket socket;
		MulticastLock lock;
		InetAddress cIp;
		NetworkInterface temp;
		DhcpInfo dhcp;
		InetAddress sIp;
		InetAddress Ip;
		InetAddress sIpB;
		private Message msg;
		private Bundle bundle;
		List<InterfaceAddress> addresses;
		private String ip;
		private byte[] bufP = "close".getBytes();
		//private byte[] bufK = "who".getBytes();
		private String bufK = "who";
		private String packetRS;
		private String[] ss = new String[2];
		private String decoded;
		private String bufN;
		private String senderIP;
		private byte[] bufACK = new byte[11];
		private byte[] bufR = new byte[11];
		private int bufRL = 0;

		public ConnectedThread(String ip) {	
			UDPCommClient.Connected = true;
			try {
				Ip = InetAddress.getByName(ip);
			} catch (UnknownHostException e2) {
				// TODO Auto-generated catch block
				e2.printStackTrace();
			}
		/*	try {
				sIp = InetAddress.getByName(wApManager.getWifiApIpAddress());
			} catch (UnknownHostException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			sIpB = null;
			try {
				temp = NetworkInterface.getByInetAddress(sIp);
				List<InterfaceAddress> addresses = temp.getInterfaceAddresses();
				for(InterfaceAddress inetAddress:addresses)
					sIpB=inetAddress.getBroadcast();
				if (VERBOSE) { Log.v(TAG, "sIpB: " + sIpB + " " + sIp); }		
			} catch (SocketException e) {

				e.printStackTrace();
				Log.d(TAG,"getBroadcast"+e.getMessage());
			} */
		}

		public void run() {
			//		sIp2 = InetAddress.getByName(wApManager.getWifiApIpAddress());
			//lock = wifi.createMulticastLock("SANDCAT");
			//lock.acquire();
			//socketR = new DatagramSocket(SERVERPORT);
			//socketR.setReuseAddress(true);
			try {
				socketR = new DatagramSocket(SERVERPORT);
			socketR.setReuseAddress(true);   
			packetR = new DatagramPacket(bufACK, bufACK.length);
			socketR.setSoTimeout(8000);
			} catch (SocketException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace(); }
			
			while (UDPCommClient.Connected == true) {
			try {
				if (VERBOSE) { Log.v(TAG, "Server: Receiving"); }
				socketR.receive(packetR);
				if (packetR.getAddress().getHostAddress().equals(Ip.getHostAddress())) {
				decoded = new String(packetR.getData()).trim();
				ss=decoded.split("\\s+",2);
				if (VERBOSE) { Log.v(TAG, "Server received: " + ss[0] + ss[1] + " expected: " + Ip.getHostAddress() + " got: " + packetR.getAddress().getHostAddress()); }
				msg = mHandler.obtainMessage(BluetoothOscilloscope.READ_HEARTBEAT,  Integer.parseInt(ss[1]), Integer.parseInt(ss[0]));
				if (VERBOSE) { Log.v(TAG, "t6"); }
				BluetoothOscilloscope.frameAnimation.stop();
				mHandler.sendMessage(msg);
				packetR.setLength(bufACK.length); }
				packetR.setLength(bufACK.length);
			} catch (SocketTimeoutException e) {
				if (VERBOSE) { Log.v(TAG, "Error 1"); }
				packetR.setLength(bufACK.length);
				socketR.disconnect();
				UDPCommClient.inUse = true;
				msg = mHandler.obtainMessage(BluetoothOscilloscope.MESSAGE_STATE_CHANGE, UDPCommClient.STATE_NONE, -1);
				mHandler.sendMessage(msg);
		
			} catch (IOException e) {
				if (VERBOSE) { Log.v(TAG, "Error 2"); }
				packetR.setLength(bufACK.length);
				socketR.disconnect();
				socketR.close();
				UDPCommClient.inUse = false;
				msg = mHandler.obtainMessage(BluetoothOscilloscope.MESSAGE_STATE_CHANGE, UDPCommClient.STATE_NONE, -1);
				mHandler.sendMessage(msg);
				break;
				}
		}
			try {
			packetR.setLength(bufACK.length);
			socketR.setReuseAddress(true);   
			packet = new DatagramPacket(bufP, bufP.length, Ip, SERVERPORT);	
			socketR.send(packet);
			if (VERBOSE) { Log.v(TAG, "Server: Request "+ new String(packet.getData()).trim()); } 
			packet.setLength(bufP.length);
			socketR.disconnect();
			socketR.close();
			packet.setLength(bufACK.length);
			UDPCommClient.inUse = false;
			} catch (SocketException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
	} 
}
	
