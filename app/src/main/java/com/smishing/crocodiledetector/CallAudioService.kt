package com.smishing.crocodiledetector

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlin.concurrent.thread

class CallAudioService : AccessibilityService() {

    private val TAG = "CrocodileDetector"
    private val sampleRate = 16000

    private var audioRecord: AudioRecord? = null
    private var recognizer: OnlineRecognizer? = null
    private var audioManager: AudioManager? = null

    @Volatile
    private var isCapturing = false

    private val phishingKeywords = listOf(
        "계좌번호", "송금", "이체", "비밀번호",
        "검찰", "경찰", "금융감독원", "금융위원회",
        "사기", "범죄", "수사", "체포",
        "대출", "저금리", "한도", "승인",
        "개인정보", "주민번호", "공인인증서"
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        Log.i(TAG, "✅ CallAudioService 생성됨")
        initRecognizer()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "✅ Accessibility Service 연결됨")
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        }
    }

    private fun initRecognizer() {
        thread {
            try {
                val modelDir = "sherpa-onnx-streaming-zipformer-korean-2024-06-16"
                val config = OnlineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = sampleRate, featureDim = 80),
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
                recognizer = OnlineRecognizer(
                    assetManager = application.assets,
                    config = config,
                )
                Log.i(TAG, "✅ STT 엔진 초기화 완료")
            } catch (e: Exception) {
                Log.e(TAG, "❌ STT 엔진 초기화 실패: ${e.message}")
            }
        }
    }

    fun startCapture() {
        if (isCapturing) {
            Log.i(TAG, "이미 캡처 중")
            return
        }

        thread {
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "❌ 마이크 권한 없음")
                return@thread
            }

            var waited = 0
            while (recognizer == null && waited < 10000) {
                Thread.sleep(100)
                waited += 100
            }
            if (recognizer == null) {
                Log.e(TAG, "❌ STT 엔진 초기화 타임아웃")
                return@thread
            }

            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            Log.i(TAG, "🔊 AudioManager MODE_IN_COMMUNICATION 설정")

            val bufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌ VOICE_RECOGNITION 실패 → VOICE_COMMUNICATION 시도")
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    sampleRate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌ VOICE_COMMUNICATION 실패 → MIC 시도")
                audioRecord?.release()
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, bufferSize * 2
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌ 모든 AudioSource 실패")
                audioManager?.mode = AudioManager.MODE_NORMAL
                return@thread
            }

            audioRecord?.startRecording()
            isCapturing = true
            Log.i(TAG, "🎤 오디오 캡처 시작")
            processAudio()
        }
    }

    fun stopCapture() {
        if (!isCapturing) return
        Log.i(TAG, "🛑 오디오 캡처 중지")
        isCapturing = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioManager?.mode = AudioManager.MODE_NORMAL
        Log.i(TAG, "🔊 AudioManager MODE_NORMAL 복원")
    }

    private fun processAudio() {
        val recognizer = recognizer ?: return
        val stream = recognizer.createStream()
        val buffer = ShortArray((0.1 * sampleRate).toInt())
        var lastText = ""

        Log.i(TAG, "🔄 STT 처리 시작")

        while (isCapturing) {
            val ret = audioRecord?.read(buffer, 0, buffer.size) ?: break
            if (ret > 0) {
                val samples = FloatArray(ret) { buffer[it] / 32768.0f }
                stream.acceptWaveform(samples, sampleRate)

                while (recognizer.isReady(stream)) {
                    recognizer.decode(stream)
                }

                val text = recognizer.getResult(stream).text
                if (text.isNotBlank() && text != lastText) {
                    Log.i(TAG, "📝 STT 결과: $text")
                    lastText = text
                    checkKeywords(text)
                }

                if (recognizer.isEndpoint(stream)) {
                    if (lastText.isNotBlank()) {
                        Log.i(TAG, "✅ 문장 완성: $lastText")
                    }
                    recognizer.reset(stream)
                    lastText = ""
                }
            }
        }

        stream.release()
        Log.i(TAG, "🔄 STT 처리 종료")
    }

    private fun checkKeywords(text: String) {
        val detected = phishingKeywords.filter { text.contains(it) }

        // ✅ 오버레이 있으면 STT 텍스트 업데이트
        DetectionService.overlayView?.updateSttText(text)

        if (detected.isNotEmpty()) {
            Log.w(TAG, "🚨 보이스피싱 키워드 탐지: $detected")

            // ✅ 오버레이 없으면 처음 생성 (방식 B - 키워드 감지 시 팝업)
            if (DetectionService.overlayView == null || !DetectionService.overlayView!!.isShowing) {
                DetectionService.overlayView = OverlayView(this)
                DetectionService.overlayView?.show()
            }

            // ✅ 감지 개수에 따라 단계 결정
            val stage = if (detected.size >= 3) OverlayView.Stage.DANGER
            else OverlayView.Stage.WARNING
            DetectionService.overlayView?.setStage(stage)
            DetectionService.overlayView?.updateSttText(text)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        Log.i(TAG, "Accessibility Service 중단됨")
        stopCapture()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCapture()
        instance = null
        Log.i(TAG, "CallAudioService 종료됨")
    }

    companion object {
        var instance: CallAudioService? = null
            private set
    }
}