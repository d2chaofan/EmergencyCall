package com.d2chaofan.emergencycall;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.core.app.ActivityCompat;

public class LocationHelper {

    private Context context;
    private OnLocationReceivedListener listener;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Handler handler;
    private boolean isGpsAvailable = false;

    public interface OnLocationReceivedListener {
        void onLocationReceived(Location location);
        void onLocationFailed(String errorMessage);
    }

    public LocationHelper(Context context, OnLocationReceivedListener listener) {
        this.context = context;
        this.listener = listener;
        this.handler = new Handler(Looper.getMainLooper());
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    public void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.e("EmergencyApp", "位置权限未授予");
            listener.onLocationFailed("位置权限未授予");
            return;
        }
        
        android.util.Log.d("EmergencyApp", "开始获取位置...");
        
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                android.util.Log.d("EmergencyApp", "位置获取成功: " + location.getLatitude() + ", " + location.getLongitude());
                stopLocationUpdates();
                listener.onLocationReceived(location);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {
                android.util.Log.e("EmergencyApp", "定位 provider 被禁用: " + provider);
            }
        };

        try {
            isGpsAvailable = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            android.util.Log.d("EmergencyApp", "GPS 可用: " + isGpsAvailable);
            
            boolean isNetworkAvailable = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            android.util.Log.d("EmergencyApp", "Network Provider 可用: " + isNetworkAvailable);

            if (isGpsAvailable) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener, Looper.getMainLooper());
            }
            
            if (isNetworkAvailable) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener, Looper.getMainLooper());
            }

            Location lastKnownLocation = null;
            if (isGpsAvailable) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            }
            if (lastKnownLocation == null && isNetworkAvailable) {
                lastKnownLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }

            if (lastKnownLocation != null) {
                android.util.Log.d("EmergencyApp", "使用缓存位置: " + lastKnownLocation.getLatitude() + ", " + lastKnownLocation.getLongitude());
                stopLocationUpdates();
                listener.onLocationReceived(lastKnownLocation);
            } else {
                android.util.Log.d("EmergencyApp", "无缓存位置，等待实时定位...");
                handler.postDelayed(() -> {
                    if (locationListener != null) {
                        android.util.Log.e("EmergencyApp", "等待定位超时");
                        stopLocationUpdates();
                        listener.onLocationFailed("无法获取位置（可能需要在室外或开启定位）");
                    }
                }, 15000);
            }

        } catch (Exception e) {
            android.util.Log.e("EmergencyApp", "定位异常: " + e.getMessage());
            listener.onLocationFailed("定位服务异常: " + e.getMessage());
        }
    }

    private void stopLocationUpdates() {
        try {
            if (locationManager != null && locationListener != null) {
                locationManager.removeUpdates(locationListener);
                locationListener = null;
            }
        } catch (Exception e) {
            android.util.Log.e("EmergencyApp", "停止定位失败: " + e.getMessage());
        }
    }
}