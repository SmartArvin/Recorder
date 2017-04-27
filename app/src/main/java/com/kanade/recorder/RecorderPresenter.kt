package com.kanade.recorder

import android.media.CamcorderProfile
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import java.io.File

class RecorderPresenter : IRecorderContract.Presenter, SurfaceHolder.Callback {
    private val TAG = "CameraPresenter"
    private val MAX_DURATION = 10
    private var duration = 0f
    private var isRecording = false
    private lateinit var filePath: String
    private lateinit var view: IRecorderContract.View
    private lateinit var cameraManager: ICameraManager
    private lateinit var mediaRecorderManager: MediaRecorderManager

    private val profile: CamcorderProfile by lazy { initProfile() }

    override fun attach(v: IRecorderContract.View, filePath: String) {
        this.view = v
        this.filePath = filePath
        this.mediaRecorderManager = MediaRecorderManager()
    }

    override fun detach() {
        mediaRecorderManager.releaseMediaRecorder()
    }

    override fun init(holder: SurfaceHolder, width: Int, height: Int) {
        cameraManager = CameraManager()
        cameraManager.init(holder, width, height)
        holder.addCallback(this)
    }

    override fun startPreview() {
        cameraManager.connectCamera()
    }

    override fun handleFocusMetering(x: Float, y: Float) = cameraManager.handleFocusMetering(x, y)

    override fun onTouch(v: View, event: MotionEvent) =
        when (event.action) {
            MotionEvent.ACTION_DOWN -> startRecord()
            MotionEvent.ACTION_UP -> recordComplete()
            else -> {}
        }

    override fun reconnect() {
        view.stopVideo()
        deleteFile()
        startPreview()
    }

    override fun recording() {
        duration++
        val sec = duration / 10.0
        view.updateProgress((sec / MAX_DURATION * 100).toInt())
        if (sec > MAX_DURATION) {
            recordComplete()
        }
    }

    override fun setResult() {
        val result = RecorderResult(filePath, (duration / 10.0).toInt())
        view.setResult(result)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        cameraManager.init(holder, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        cameraManager.releaseCamera()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        cameraManager.init(holder)
    }

    private fun startRecord() {
        duration = 0f
        isRecording = true
        view.startRecord()
        val camera = (cameraManager as CameraManager).getCamera()
        mediaRecorderManager.record(camera, profile, filePath)
    }

    private fun recordComplete() {
        if (isRecording) {
            stopRecord()
            view.recordComplete()
            view.playVideo(filePath)
        }
    }

    private fun stopRecord() {
        mediaRecorderManager.stopRecord()
        cameraManager.releaseCamera()
        isRecording = false
    }

    private fun deleteFile() {
        val file = File(filePath)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun initProfile(): CamcorderProfile {
        val profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH)
        val size = cameraManager.getVideoSize()
        // 这里是重点，分辨率和比特率
        // 分辨率越大视频大小越大，比特率越大视频越清晰
        // 清晰度由比特率决定，视频尺寸和像素量由分辨率决定
        // 比特率越高越清晰（前提是分辨率保持不变），分辨率越大视频尺寸越大。
        profile.videoFrameWidth = size.first
        profile.videoFrameHeight = size.second
        profile.videoBitRate = size.first * size.second
        return profile
    }
}