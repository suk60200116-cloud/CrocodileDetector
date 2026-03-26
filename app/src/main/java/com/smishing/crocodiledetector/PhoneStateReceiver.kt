package com.smishing.crocodiledetector

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import android.util.Log

class PhoneStateReceiver : BroadcastReceiver() {

    private val TAG = "CrocodileDetector"

    companion object {
        var isUnknownCall = false  // 모르는 번호 여부 상태
    }

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        when (state) {

            // 전화 울릴 때 → 연락처 확인만 함
            TelephonyManager.EXTRA_STATE_RINGING -> {
                Log.i(TAG, "📞 전화 수신 중: $incomingNumber")
                isUnknownCall = !isNumberInContacts(context, incomingNumber)
                if (isUnknownCall) {
                    Log.i(TAG, "🚨 모르는 번호 확인됨 → 수신 대기")
                } else {
                    Log.i(TAG, "✅ 아는 번호 → 무시")
                }
            }

            // 전화 받았을 때 → 모르는 번호면 서비스 + 캡처 시작
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                if (isUnknownCall && !DetectionService.isRunning) {
                    Log.i(TAG, "📞 모르는 번호 통화 수신 → 탐지 시작!")
                    startDetectionService(context)
                } else {
                    Log.i(TAG, "📞 무시 (아는 번호 or 이미 실행 중)")
                }
            }

            // 전화 끊겼을 때 → 무조건 중지 + 초기화
            TelephonyManager.EXTRA_STATE_IDLE -> {
                Log.i(TAG, "📞 통화 종료 → 모든 탐지 중지")
                stopDetectionService(context)
                isUnknownCall = false
            }
        }
    }

    private fun isNumberInContacts(context: Context, number: String?): Boolean {
        if (number == null) return false
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null, null, null
        )
        val isKnown = (cursor?.count ?: 0) > 0
        cursor?.close()
        return isKnown
    }

    private fun startDetectionService(context: Context) {
        val intent = Intent(context, DetectionService::class.java).apply {
            action = DetectionService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    private fun stopDetectionService(context: Context) {
        val intent = Intent(context, DetectionService::class.java)
        context.stopService(intent)
    }
}