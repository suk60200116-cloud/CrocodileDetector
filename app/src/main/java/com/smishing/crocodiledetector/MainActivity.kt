package com.smishing.crocodiledetector

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

class MainActivity : AppCompatActivity() {

    private val TAG = "CrocodileDetector"

    private lateinit var tvStatus: TextView
    private lateinit var tvResult: TextView
    private lateinit var tvIndicator: TextView
    private lateinit var btnTest: Button
    private lateinit var btnStart: Button

    private val PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.POST_NOTIFICATIONS,
    )
    private val REQUEST_PERMISSIONS = 100
    private val REQUEST_OVERLAY_PERMISSION = 1234

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus    = findViewById(R.id.tvStatus)
        tvResult    = findViewById(R.id.tvResult)
        tvIndicator = findViewById(R.id.tvIndicator)
        btnTest     = findViewById(R.id.btnTest)
        btnStart    = findViewById(R.id.btnStart)

        checkAndRequestPermissions()

        btnStart.setOnClickListener {
            tvStatus.text = "감지 기능은 다음 단계에서 구현됩니다"
        }
    }

    override fun onResume() {
        super.onResume()

        // ✅ 오버레이 권한 체크
        if (!Settings.canDrawOverlays(this)) {
            tvStatus.text = "⚠️ 오버레이 권한이 필요합니다\n\n설정에서 '다른 앱 위에 표시'\n권한을 허용해주세요"
            tvIndicator.text = "● OVERLAY PERMISSION REQUIRED"
            tvIndicator.setTextColor(0xFFFFAA00.toInt())
            btnTest.text = "오버레이 권한 설정"
            btnTest.isEnabled = true
            btnTest.setOnClickListener {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                // ✅ startActivityForResult로 변경 → 돌아올 때 자동 감지
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
            }
            return
        }

        // ✅ 접근성 + 오버레이 둘 다 OK
        if (CallAudioService.instance != null) {
            tvStatus.text = "✅ 모든 설정 완료\n보이스피싱 탐지 준비됨"
            tvIndicator.text = "● READY"
            tvIndicator.setTextColor(0xFF00FF00.toInt())
            btnTest.text = "STT 엔진 테스트"
            btnTest.isEnabled = true
            btnStart.isEnabled = true
            btnTest.setOnClickListener {
                testSttEngine()
            }
        } else {
            onPermissionsGranted()
        }
    }

    // ✅ 설정에서 돌아올 때 자동으로 UI 갱신
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            onResume()
        }
    }

    private fun checkAndRequestPermissions() {
        val missing = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_PERMISSIONS)
        } else {
            onPermissionsGranted()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                onPermissionsGranted()
            } else {
                tvStatus.text = "마이크 권한이 필요합니다"
                tvIndicator.text = "● PERMISSION DENIED"
                tvIndicator.setTextColor(0xFFFF0000.toInt())
            }
        }
    }

    private fun onPermissionsGranted() {
        if (CallAudioService.instance == null) {
            tvStatus.text = "⚠️ Accessibility Service를\n활성화해주세요\n\n" +
                    "설정 → 접근성 → 설치된 앱 →\nCrocodileDetector → 사용"
            tvIndicator.text = "● SETUP REQUIRED"
            tvIndicator.setTextColor(0xFFFFAA00.toInt())
            btnTest.text = "접근성 설정 열기"
            btnTest.setOnClickListener {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
        } else {
            tvStatus.text = "✅ 모든 설정 완료\n보이스피싱 탐지 준비됨"
            tvIndicator.text = "● READY"
            tvIndicator.setTextColor(0xFF00FF00.toInt())
            btnTest.text = "STT 엔진 테스트"
            btnTest.setOnClickListener {
                testSttEngine()
            }
            btnStart.isEnabled = true
        }
    }

    private fun testSttEngine() {
        tvStatus.text = "STT 엔진 초기화 중..."
        tvIndicator.text = "● LOADING"
        tvIndicator.setTextColor(0xFFFFAA00.toInt())
        btnTest.isEnabled = false

        Thread {
            try {
                val modelDir = "sherpa-onnx-streaming-zipformer-korean-2024-06-16"
                val config = OnlineRecognizerConfig(
                    featConfig = FeatureConfig(
                        sampleRate = 16000,
                        featureDim = 80
                    ),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = "$modelDir/encoder-epoch-99-avg-1.int8.onnx",
                            decoder = "$modelDir/decoder-epoch-99-avg-1.onnx",
                            joiner  = "$modelDir/joiner-epoch-99-avg-1.int8.onnx",
                        ),
                        tokens = "$modelDir/tokens.txt",
                        modelType = "zipformer",
                    ),
                    endpointConfig = EndpointConfig(),
                    enableEndpoint = true,
                )

                val recognizer = OnlineRecognizer(
                    assetManager = application.assets,
                    config = config,
                )

                Log.i(TAG, "STT 엔진 초기화 성공")

                runOnUiThread {
                    tvStatus.text = "STT 엔진 초기화 성공"
                    tvResult.text = "한국어 음성 인식 준비 완료"
                    tvIndicator.text = "● READY"
                    tvIndicator.setTextColor(0xFF00FF00.toInt())
                    btnTest.isEnabled = true
                    btnStart.isEnabled = true
                }

            } catch (e: Exception) {
                Log.e(TAG, "STT 엔진 초기화 실패: ${e.message}")
                runOnUiThread {
                    tvStatus.text = "초기화 실패\n${e.message}"
                    tvIndicator.text = "● ERROR"
                    tvIndicator.setTextColor(0xFFFF0000.toInt())
                    btnTest.isEnabled = true
                }
            }
        }.start()
    }
}