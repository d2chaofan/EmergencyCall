package com.d2chaofan.emergencycall;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;
import android.app.ProgressDialog;

public class SmsSentReceiver extends BroadcastReceiver {
    
    private static final String TAG = "EmergencyApp";
    private static OnSmsResultListener listener;
    
    public interface OnSmsResultListener {
        void onSmsSuccess();
        void onSmsError();
    }
    
    public static void setListener(OnSmsResultListener l) {
        listener = l;
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "========================================");
        Log.d(TAG, "SmsSentReceiver 收到短信发送回调");
        
        int resultCode = getResultCode();
        Log.d(TAG, "短信发送结果码: " + resultCode);
        
        switch (resultCode) {
            case Activity.RESULT_OK:
                Log.d(TAG, "✓ 短信发送成功");
                Toast.makeText(context, "短信已发送，正在拨打电话...", Toast.LENGTH_SHORT).show();
                if (listener != null) {
                    listener.onSmsSuccess();
                }
                break;
            case android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                Log.e(TAG, "✗ 短信发送失败: 通用错误");
                if (listener != null) {
                    listener.onSmsError();
                }
                break;
            case android.telephony.SmsManager.RESULT_ERROR_NO_SERVICE:
                Log.e(TAG, "✗ 短信发送失败: 无服务");
                if (listener != null) {
                    listener.onSmsError();
                }
                break;
            case android.telephony.SmsManager.RESULT_ERROR_NULL_PDU:
                Log.e(TAG, "✗ 短信发送失败: PDU为空");
                if (listener != null) {
                    listener.onSmsError();
                }
                break;
            case android.telephony.SmsManager.RESULT_ERROR_RADIO_OFF:
                Log.e(TAG, "✗ 短信发送失败: 无线电关闭");
                if (listener != null) {
                    listener.onSmsError();
                }
                break;
            default:
                Log.e(TAG, "✗ 短信发送失败: 未知错误");
                if (listener != null) {
                    listener.onSmsError();
                }
                break;
        }
    }
}
