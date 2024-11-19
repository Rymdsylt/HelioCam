package com.summersoft.heliocam.status;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.os.Build;
import android.widget.Toast;
import android.util.Log;

public class IMEI_Util {

   public static final String TAG = "IMEIUtil"; // Log tag for easy identification in Logcat

    public static void showIMEI(Context context) {
        String imei = getIMEI(context);
        if (imei != null) {
            // Display IMEI in a Toast message box
            Toast.makeText(context, "IMEI: " + imei, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(context, "Unable to retrieve IMEI.", Toast.LENGTH_LONG).show();
        }
    }

   public static String getIMEI(Context context) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (telephonyManager != null) {

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        String imei = telephonyManager.getImei();
                        Log.d(TAG, "IMEI: " + imei);
                        return imei;
                    } else {
                        // For devices below Android 10, you can directly get the IMEI
                        String imei = telephonyManager.getDeviceId(); // Device ID (IMEI) for older versions
                        Log.d(TAG, "IMEI: " + imei); // Log IMEI to Logcat
                        return imei;
                    }
                }
            } else {
                // Handle permission request or show a message to user
                Toast.makeText(context, "Permission to access phone state not granted.", Toast.LENGTH_LONG).show();
            }
        } else {
            // For versions below Android 6.0, the permission is granted by default
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (telephonyManager != null) {
                String imei = telephonyManager.getDeviceId(); // Device ID (IMEI) for older versions
                Log.d(TAG, "IMEI: " + imei); // Log IMEI to Logcat
                return imei;
            }
        }
        return null;
    }
}
