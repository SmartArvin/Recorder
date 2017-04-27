package com.kanade.recorder

import android.content.Context
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder

interface IRecorderContract {
    interface View {
        fun getContext(): Context

        fun startRecord()

        fun playVideo(filePath: String)

        fun stopVideo()

        fun updateProgress(p: Int)

        fun recordComplete()

        fun setResult(result: RecorderResult)
    }

    interface Presenter {
        fun attach(v: View, filePath: String)

        fun detach()

        fun init(holder: SurfaceHolder, width: Int, height: Int)

        fun startPreview()

        fun handleFocusMetering(x: Float, y: Float)

        fun onTouch(v: android.view.View, event: MotionEvent)

        fun reconnect()

        fun setResult()

        fun recording()
    }
}