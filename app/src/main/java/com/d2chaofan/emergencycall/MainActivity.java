package com.d2chaofan.emergencycall;

import android.Manifest;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity implements LocationHelper.OnLocationReceivedListener, SmsSentReceiver.OnSmsResultListener {

    private static final int REQUEST_CODE_PERMISSIONS = 100;
    private static final String PREF_NAME = "EmergencyPrefs";
    private static final String CONTACT_KEY = "EmergencyContact";
    private static final int MAX_SMS_RETRY_COUNT = 3;
    private static final int SMS_RETRY_DELAY_MS = 2000;
    private static final int SMS_TIMEOUT_MS = 10000;
    private boolean smsCallbackReceived = false;

    private EditText editTextContactNumber;
    private Button buttonSaveContact;
    private Button buttonTriggerEmergency;
    private SharedPreferences sharedPreferences;
    private LocationHelper locationHelper;
    private String currentEmergencyContactNumber = "";
    
    private int smsRetryCount = 0;
    private String currentMessage = "";
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isActivityRunning = false;
    
    private ProgressDialog progressDialog;
    private SmsSentReceiver smsSentReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        isActivityRunning = true;
        
        initViews();
        initSharedPreferences();
        
        String savedContact = sharedPreferences.getString(CONTACT_KEY, "");
        if (editTextContactNumber != null) {
            editTextContactNumber.setText(savedContact);
            android.util.Log.d("EmergencyApp", "已从 SharedPreferences 加载联系人: " + savedContact);
        }
        
        currentEmergencyContactNumber = savedContact.trim();
        boolean hasSavedContact = !currentEmergencyContactNumber.isEmpty();
        boolean hasAllPermissions = allPermissionsGranted();
        
        if (hasAllPermissions && hasSavedContact) {
            android.util.Log.d("EmergencyApp", "已设置联系人且有权限，自动触发紧急流程");
            startEmergencyProcess();
        } else {
            if (!hasAllPermissions) {
                android.util.Log.d("EmergencyApp", "权限不足，请求权限");
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
            }
            android.util.Log.d("EmergencyApp", "未设置联系人或正在请求权限，等待用户操作");
        }
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        isActivityRunning = true;
        try {
            smsSentReceiver = new SmsSentReceiver();
            SmsSentReceiver.setListener(this);
            registerReceiver(smsSentReceiver, new IntentFilter("SMS_SENT"), Context.RECEIVER_NOT_EXPORTED);
            android.util.Log.d("EmergencyApp", "BroadcastReceiver 已注册");
        } catch (Exception e) {
            android.util.Log.e("EmergencyApp", "注册 BroadcastReceiver 失败: " + e.getMessage());
        }
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        android.util.Log.d("EmergencyApp", "onStop 被调用");
        isActivityRunning = false;
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        android.util.Log.d("EmergencyApp", "========================================");
        android.util.Log.d("EmergencyApp", "onDestroy 被调用");
        isActivityRunning = false;
        
        // 关闭进度对话框
        hideProgress();
        
        // 注销 BroadcastReceiver
        try {
            unregisterReceiver(smsSentReceiver);
            android.util.Log.d("EmergencyApp", "BroadcastReceiver 已注销");
        } catch (IllegalArgumentException e) {
            // 如果接收器未注册，忽略异常
            android.util.Log.w("EmergencyApp", "BroadcastReceiver 未注册: " + e.getMessage());
        }
    }

    private void initViews() {
        editTextContactNumber = findViewById(R.id.editTextContactNumber);
        buttonSaveContact = findViewById(R.id.buttonSaveContact);
        buttonTriggerEmergency = findViewById(R.id.buttonTriggerEmergency);
        
        // 添加调试日志
        if (editTextContactNumber != null) {
            android.util.Log.d("EmergencyApp", "editTextContactNumber 初始化成功");
        } else {
            android.util.Log.e("EmergencyApp", "editTextContactNumber 初始化失败！找不到R.id.editTextContactNumber");
        }
        
        if (buttonSaveContact != null) {
            android.util.Log.d("EmergencyApp", "buttonSaveContact 初始化成功");
        } else {
            android.util.Log.e("EmergencyApp", "buttonSaveContact 初始化失败！");
        }
        
        if (buttonTriggerEmergency != null) {
            android.util.Log.d("EmergencyApp", "buttonTriggerEmergency 初始化成功");
            buttonTriggerEmergency.setOnClickListener(v -> {
                saveEmergencyContact();
                if (!currentEmergencyContactNumber.isEmpty()) {
                    startEmergencyProcess();
                }
            });
        } else {
            android.util.Log.e("EmergencyApp", "buttonTriggerEmergency 初始化失败！");
        }

        if (buttonSaveContact != null) {
            buttonSaveContact.setOnClickListener(v -> saveEmergencyContact());
        }
    }

    private void initSharedPreferences() {
        // 只负责获取 SharedPreferences 实例
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        // 初始化定位助手
        if (locationHelper == null) {
            locationHelper = new LocationHelper(this, this);
        }
        // 不在此处更新 UI
    }
    
    private void showProgress(String message) {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setCancelable(false);
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.setMessage(message);
        progressDialog.show();
        android.util.Log.d("EmergencyApp", "显示进度提示: " + message);
    }
    
    private void hideProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
            android.util.Log.d("EmergencyApp", "隐藏进度提示");
        }
    }

    private void saveEmergencyContact() {
        String contactNumber = editTextContactNumber.getText().toString().trim();
        if (contactNumber.isEmpty()) {
            Toast.makeText(this, "请输入电话号码", Toast.LENGTH_SHORT).show();
            return;
        }
        // 保存到 SharedPreferences
        sharedPreferences.edit().putString(CONTACT_KEY, contactNumber).apply();
        currentEmergencyContactNumber = contactNumber;
        Toast.makeText(this, "紧急联系人已保存", Toast.LENGTH_SHORT).show();
    }

    private void startEmergencyProcess() {
        android.util.Log.d("EmergencyApp", "========================================");
        android.util.Log.d("EmergencyApp", "开始紧急流程");
        
        if (!isActivityRunning) {
            android.util.Log.e("EmergencyApp", "Activity 未运行，无法启动紧急流程");
            return;
        }
        
        if (currentEmergencyContactNumber.isEmpty()) {
            android.util.Log.e("EmergencyApp", "紧急联系人号码为空");
            Toast.makeText(this, "请先设置紧急联系人", Toast.LENGTH_SHORT).show();
            return;
        }
        
        android.util.Log.d("EmergencyApp", "紧急联系人: " + currentEmergencyContactNumber);

        // 1. 先确保我们有权限
        if (!allPermissionsGranted()) {
            android.util.Log.e("EmergencyApp", "权限不足");
            Toast.makeText(this, "请先授予所有权限", Toast.LENGTH_SHORT).show();
            return;
        }

        // 显示进度提示
        showProgress("正在获取您的位置信息...");
        android.util.Log.d("EmergencyApp", "权限检查通过，开始获取位置");
        
        // 2. 确保定位助手已初始化
        if (locationHelper == null) {
            android.util.Log.w("EmergencyApp", "LocationHelper 未初始化，正在初始化");
            locationHelper = new LocationHelper(this, this);
        }
        
        // 3. 获取位置并发送短信（短信发送完成后再打电话）
        try {
            android.util.Log.d("EmergencyApp", "调用 locationHelper.getLastLocation()");
            locationHelper.getLastLocation();
        } catch (Exception e) {
            android.util.Log.e("EmergencyApp", "获取位置失败: " + e.getMessage());
            e.printStackTrace();
            Toast.makeText(this, "获取位置失败，正在尝试发送短信...", Toast.LENGTH_SHORT).show();
            // 即使定位失败，也尝试发送短信
            onLocationFailed("定位失败");
        }
    }

    private void makePhoneCall() {
        android.util.Log.d("EmergencyApp", "========================================");
        android.util.Log.d("EmergencyApp", "开始拨打电话流程");
        
        if (!isActivityRunning) {
            android.util.Log.w("EmergencyApp", "Activity 已停止，取消拨打电话");
            return;
        }
        
        if (currentEmergencyContactNumber == null || currentEmergencyContactNumber.isEmpty()) {
            android.util.Log.e("EmergencyApp", "紧急联系人号码为空，无法拨打电话");
            hideProgress();
            Toast.makeText(this, "紧急联系人号码为空", Toast.LENGTH_SHORT).show();
            return;
        }
        
        android.util.Log.d("EmergencyApp", "目标号码: " + currentEmergencyContactNumber);
        
        // 拨打电话（在短信发送完成后调用）
        Intent callIntent = new Intent(Intent.ACTION_CALL);
        callIntent.setData(Uri.parse("tel:" + currentEmergencyContactNumber));
        
        try {
            android.util.Log.d("EmergencyApp", "启动电话呼叫Intent");
            hideProgress();
            startActivity(callIntent);
            android.util.Log.d("EmergencyApp", "✓ 电话拨号请求已发送");
            finish();
        } catch (Exception e) {
            android.util.Log.e("EmergencyApp", "✗ 电话拨号失败: " + e.getMessage());
            e.printStackTrace();
            hideProgress();
            Toast.makeText(this, "电话拨号失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onLocationReceived(Location location) {
        android.util.Log.d("EmergencyApp", "========================================");
        android.util.Log.d("EmergencyApp", "位置获取成功");
        android.util.Log.d("EmergencyApp", "纬度: " + location.getLatitude());
        android.util.Log.d("EmergencyApp", "经度: " + location.getLongitude());
        
        // 更新进度提示
        showProgress("位置获取成功，正在发送短信...");
        Toast.makeText(this, "位置获取成功，正在发送短信...", Toast.LENGTH_SHORT).show();
        
        // 重置重试计数器
        smsRetryCount = 0;
        
        // 构建包含经纬度的真实位置消息
        currentMessage = String.format("紧急求助！我的位置: %.6f° N, %.6f° E。", location.getLatitude(), location.getLongitude());
        android.util.Log.d("EmergencyApp", "准备发送短信: " + currentMessage);
        
        sendSMS(currentMessage);
    }

    @Override
    public void onLocationFailed(String errorMessage) {
        android.util.Log.d("EmergencyApp", "========================================");
        android.util.Log.e("EmergencyApp", "位置获取失败: " + errorMessage);
        
        // 更新进度提示
        showProgress("位置获取失败，正在发送短信...");
        Toast.makeText(this, "位置获取失败，正在发送短信...", Toast.LENGTH_SHORT).show();
        
        // 重置重试计数器
        smsRetryCount = 0;
        
        // 即使定位失败，也发送一条通知
        currentMessage = "紧急求助！我可能需要帮助，但无法获取精确位置。";
        android.util.Log.d("EmergencyApp", "准备发送短信: " + currentMessage);
        
        sendSMS(currentMessage);
    }

    @Override
    public void onSmsSuccess() {
        if (!isActivityRunning) return;
        smsCallbackReceived = true;
        showProgress("短信已发送，正在拨打电话...");
        Toast.makeText(this, "短信已发送，正在拨打电话...", Toast.LENGTH_SHORT).show();
        makePhoneCall();
    }

    @Override
    public void onSmsError() {
        if (!isActivityRunning) return;
        smsCallbackReceived = true;
        if (!isActivityRunning) return;
        smsRetryCount++;
        android.util.Log.d("EmergencyApp", "当前重试次数: " + smsRetryCount + "/" + MAX_SMS_RETRY_COUNT);
        
        if (smsRetryCount < MAX_SMS_RETRY_COUNT) {
            android.util.Log.d("EmergencyApp", "尝试重新发送短信");
            Toast.makeText(this, "短信发送失败，正在重试...", Toast.LENGTH_SHORT).show();
            handler.postDelayed(() -> {
                if (isActivityRunning) {
                    sendSMS(currentMessage);
                }
            }, SMS_RETRY_DELAY_MS);
        } else {
            android.util.Log.e("EmergencyApp", "✗ 短信发送失败，已达到最大重试次数");
            hideProgress();
            Toast.makeText(this, "短信发送失败，正在尝试拨打电话...", Toast.LENGTH_SHORT).show();
            makePhoneCall();
        }
    }

    private void sendSMS(String message) {
        android.util.Log.d("EmergencyApp", "========================================");
        android.util.Log.d("EmergencyApp", "开始发送短信流程");
        
        if (!isActivityRunning) {
            android.util.Log.w("EmergencyApp", "Activity 已停止，取消发送短信");
            return;
        }
        
        if (currentEmergencyContactNumber == null || currentEmergencyContactNumber.isEmpty()) {
            android.util.Log.e("EmergencyApp", "紧急联系人号码为空，无法发送短信");
            hideProgress();
            Toast.makeText(this, "紧急联系人号码为空", Toast.LENGTH_SHORT).show();
            return;
        }
        
        android.util.Log.d("EmergencyApp", "目标号码: " + currentEmergencyContactNumber);
        android.util.Log.d("EmergencyApp", "短信内容: " + message);
        android.util.Log.d("EmergencyApp", "当前重试次数: " + smsRetryCount);
        
        try {
            SmsManager smsManager = SmsManager.getDefault();
            android.util.Log.d("EmergencyApp", "SmsManager 获取成功");
            
            // 创建 PendingIntent 来监听短信发送状态
            PendingIntent sentPI = PendingIntent.getBroadcast(
                this, 
                0, 
                new Intent("SMS_SENT"), 
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
            );
            android.util.Log.d("EmergencyApp", "PendingIntent 创建成功");
            
            // 发送短信
            smsManager.sendTextMessage(
                currentEmergencyContactNumber, 
                null, 
                message, 
                sentPI, 
                null
            );
            
            android.util.Log.d("EmergencyApp", "✓ 短信发送请求已提交，等待发送结果");
            Toast.makeText(this, "正在发送短信...", Toast.LENGTH_SHORT).show();
            
            smsCallbackReceived = false;
            handler.postDelayed(() -> {
                if (isActivityRunning && !smsCallbackReceived) {
                    android.util.Log.w("EmergencyApp", "短信发送超时，假设已发送成功");
                    makePhoneCall();
                }
            }, SMS_TIMEOUT_MS);
            
        } catch (Exception e) {
            android.util.Log.e("EmergencyApp", "✗ 短信发送异常: " + e.getMessage());
            e.printStackTrace();
            
            // 如果发生异常，直接重试
            smsRetryCount++;
            if (smsRetryCount < MAX_SMS_RETRY_COUNT) {
                android.util.Log.d("EmergencyApp", "尝试重新发送短信，重试次数: " + smsRetryCount);
                Toast.makeText(this, "短信发送失败，正在重试...", Toast.LENGTH_SHORT).show();
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (isActivityRunning) {
                            sendSMS(message);
                        }
                    }
                }, SMS_RETRY_DELAY_MS);
            } else {
                android.util.Log.e("EmergencyApp", "✗ 短信发送失败，已达到最大重试次数");
                hideProgress();
                Toast.makeText(this, "短信发送失败，正在尝试拨打电话...", Toast.LENGTH_SHORT).show();
                // 即使短信发送失败，也拨打电话
                makePhoneCall();
            }
        }
    }

    private String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        android.util.Log.d("EmergencyApp", "========================================");
        android.util.Log.d("EmergencyApp", "收到权限请求结果回调");
        android.util.Log.d("EmergencyApp", "请求码: " + requestCode);
        
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            android.util.Log.d("EmergencyApp", "检查权限授予状态");
            
            // 打印每个权限的授予状态
            for (int i = 0; i < permissions.length; i++) {
                android.util.Log.d("EmergencyApp", permissions[i] + " = " + (grantResults[i] == PackageManager.PERMISSION_GRANTED ? "已授予" : "未授予"));
            }
            
            if (allPermissionsGranted()) {
                android.util.Log.d("EmergencyApp", "✓ 所有运行时权限已获得");
                
                // 权限已授予，现在检查是否设置了联系人
                if (!currentEmergencyContactNumber.isEmpty()) {
                    android.util.Log.d("EmergencyApp", "权限和联系人均满足，开始紧急流程");
                    startEmergencyProcess();
                } else {
                    android.util.Log.d("EmergencyApp", "权限已获但未设置联系人，等待用户操作");
                    Toast.makeText(this, "请设置紧急联系人", Toast.LENGTH_SHORT).show();
                }
            } else {
                android.util.Log.e("EmergencyApp", "✗ 未能获得所有必需的权限");
                Toast.makeText(this, "应用需要所有权限才能正常工作", Toast.LENGTH_LONG).show();
            }
        }
    }
}
