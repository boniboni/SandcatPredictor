/*
 * Copyright 2013 WhiteByte (Nick Russler, Ahmet Yueksektepe).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sandcat.phys;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

import android.content.Context;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Log;

public class WifiApManager {
	private final WifiManager mWifiManager;

	public WifiApManager(Context context) {
		mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
	}

	/**
	 * Start AccessPoint mode with the specified
	 * configuration. If the radio is already running in
	 * AP mode, update the new configuration
	 * Note that starting in access point mode disables station
	 * mode operation
	 * @param wifiConfig SSID, security and channel details as part of WifiConfiguration
	 * @return {@code true} if the operation succeeds, {@code false} otherwise
	 */
	public boolean setWifiApEnabled(WifiConfiguration wifiConfig, boolean enabled) {
		if ((enabled == true) && (mWifiManager.isWifiEnabled() == true)) { // disable WiFi in any case
			mWifiManager.setWifiEnabled(false);
		}
		try {
			Method method = mWifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
			return (Boolean) method.invoke(mWifiManager, wifiConfig, enabled);
		} catch (Exception e) {
			Log.e(this.getClass().toString(), "", e);
			return false;
		}
	}

	/**
	 * Gets the Wi-Fi enabled state.
	 * @return {@link WIFI_AP_STATE}
	 * @see #isWifiApEnabled()
	 */
	public WIFI_AP_STATE getWifiApState() {
		try {
			Method method = mWifiManager.getClass().getMethod("getWifiApState");

			int tmp = ((Integer)method.invoke(mWifiManager));

			// Fix for Android 4
			if (tmp > 10) {
				tmp = tmp - 10;
			}

			return WIFI_AP_STATE.class.getEnumConstants()[tmp];
		} catch (Exception e) {
			Log.e(this.getClass().toString(), "", e);
			return WIFI_AP_STATE.WIFI_AP_STATE_FAILED;
		}
	}

	/**
	 * Return whether Wi-Fi AP is enabled or disabled.
	 * @return {@code true} if Wi-Fi AP is enabled
	 * @see #getWifiApState()
	 *
	 * @hide Dont open yet
	 */
	public boolean isWifiApEnabled() {
		return ((getWifiApState() == WIFI_AP_STATE.WIFI_AP_STATE_ENABLED) && (getWifiApSSID().equals("Sandcat")));
	}

	/**
	 * Gets the Wi-Fi AP Configuration.
	 * @return AP details in {@link WifiConfiguration}
	 */
	public WifiConfiguration getWifiApConfiguration() {
		try {
			Method method = mWifiManager.getClass().getMethod("getWifiApConfiguration");
			return (WifiConfiguration) method.invoke(mWifiManager);
		} catch (Exception e) {
			Log.e(this.getClass().toString(), "", e);
			return null;
		}
	}
	
	public String getWifiApSSID() {
		WifiConfiguration cfg = new WifiConfiguration();
		try {
			Method method = mWifiManager.getClass().getMethod("getWifiApConfiguration");
			cfg = (WifiConfiguration) method.invoke(mWifiManager);
			return cfg.SSID;
		} catch (Exception e) {
			Log.e(this.getClass().toString(), "", e);
			return null;
		}
	}

	/**
	 * Sets the Wi-Fi AP Configuration.
	 * @return {@code true} if the operation succeeded, {@code false} otherwise
	 */
	public boolean setWifiApConfiguration(WifiConfiguration wifiConfig) {
		try {
			Method method = mWifiManager.getClass().getMethod("setWifiApConfiguration", WifiConfiguration.class);
			return (Boolean) method.invoke(mWifiManager, wifiConfig);
		} catch (Exception e) {
			Log.e(this.getClass().toString(), "", e);
			return false;
		}
	}
	
	public String getWifiApIpAddress() {
	    try {
	        for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en
	                .hasMoreElements();) {
	            NetworkInterface intf = en.nextElement();
	            if (intf.getName().contains("wlan")) {
	                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr
	                        .hasMoreElements();) {
	                    InetAddress inetAddress = enumIpAddr.nextElement();
	                    if (!inetAddress.isLoopbackAddress()
	                            && (inetAddress.getAddress().length == 4)) {
	                        //Log.d(TAG, inetAddress.getHostAddress());
	                        return inetAddress.getHostAddress();
	                    }
	                }
	            }
	        }
	    } catch (SocketException ex) {
	        Log.e("", ex.toString());
	    }
	    return null;
	}

}

