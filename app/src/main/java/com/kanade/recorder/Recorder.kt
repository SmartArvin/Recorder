package com.kanade.recorder

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.support.v7.app.AppCompatActivity
import android.view.MotionEvent
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import kotlinx.android.synthetic.main.activity_recorder.*
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.OnShowRationale
import permissions.dispatcher.PermissionRequest
import permissions.dispatcher.RuntimePermissions
import android.support.v7.app.AlertDialog
import android.widget.Toast
import permissions.dispatcher.OnNeverAskAgain
import permissions.dispatcher.OnPermissionDenied

@RuntimePermissions
class Recorder : AppCompatActivity(), View.OnClickListener, IRecorderContract.View,
        VideoProgressBtn.AniEndListener, MediaPlayer.OnPreparedListener {
    private lateinit var presenter: IRecorderContract.Presenter
    private lateinit var handler: Handler
    // 是否正在播放
    private var isPlaying = false

    private val focusAni: AnimatorSet by lazy { initvideo_record_focusAni() }
    private val showCompleteAni: AnimatorSet by lazy { initShowSendViewAni() }
    private val hideCompleteAni: AnimatorSet by lazy { initHideShowViewAni() }
    private val runnable: Runnable by lazy { initRunnable() }

    companion object {
        private const val RESULT_FILEPATH = "result_filepath"
        private const val ARG_FILEPATH = "arg_filepath"
        @JvmStatic
        fun newIntent(ctx: Context, filePath: String): Intent =
                Intent(ctx, Recorder::class.java)
                        .putExtra(ARG_FILEPATH, filePath)

        @JvmStatic
        fun getResult(intent: Intent): RecorderResult =
                intent.getParcelableExtra(RESULT_FILEPATH)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,  WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(R.layout.activity_recorder)
        val filePath = intent.getStringExtra(ARG_FILEPATH)
        presenter = RecorderPresenter()
        presenter.attach(this, filePath)

        RecorderPermissionsDispatcher.initViewWithCheck(this)
    }

    override fun onStop() {
        super.onStop()
        finish()
    }

    @NeedsPermission(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
    fun initView() {
        handler = Handler()

        // 先让video_record_focus渲染一次，下面要用到其宽高
        focusAni.start()

        recorder_back.setOnClickListener(this)
        recorder_cancelbtn.setOnClickListener(this)
        recorder_certainbtn.setOnClickListener(this)

        recorder_progress.setListener(this)
        recorder_progress.setOnTouchListener(progressOnTouched)
        recorder_vv.setOnPreparedListener(this)
        recorder_vv.setOnTouchListener(svOnTouched)

        recorder_vv.viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                recorder_vv.viewTreeObserver.removeOnGlobalLayoutListener(this)
                presenter.init(recorder_vv.holder, recorder_vv.width, recorder_vv.height)
                presenter.startPreview()
            }
        })
    }

    @OnShowRationale(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
    fun showRationaleForRecorder(request: PermissionRequest) {
        AlertDialog.Builder(this)
                .setMessage(R.string.permission_recorder_rationale)
                .setPositiveButton(R.string.button_allow, { _, _ -> request.proceed() })
                .setNegativeButton(R.string.button_deny, { _, _ -> request.cancel(); finish() })
                .show()
    }

    @OnPermissionDenied(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
    fun showDeniedForRecorder() {
        Toast.makeText(this, R.string.permission_recorder_denied, Toast.LENGTH_SHORT).show()
        finish()
    }

    @OnNeverAskAgain(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
    fun showNeverAskForRecorder() {
        Toast.makeText(this, R.string.permission_recorder_denied, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        RecorderPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults)
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.detach()
    }

    override fun startRecord() {
        recorder_progress.recording()
    }

    override fun updateProgress(p: Int) {
        recorder_progress.setProgress(p)
    }

    override fun recordComplete() {
        recorder_progress.recordComplete()
        showCompleteView()
    }

    private fun focus(x: Float, y: Float) {
//        Log.d(TAG, "focus x: $x, focus y: $y")
//        Log.d(TAG, "focus Width: ${recorder_focus_btn.width}, focus Height: ${recorder_focus_btn.height}")
        if (isPlaying) return
        recorder_focus.x = x - recorder_focus.width / 2
        recorder_focus.y = y - recorder_focus.height / 2
        if (focusAni.isRunning) {
            focusAni.cancel()
        }
        focusAni.start()
        presenter.handleFocusMetering(x, y)
    }

    private fun showCompleteView() {
        if (showCompleteAni.isRunning) {
            showCompleteAni.cancel()
        }
        showCompleteAni.start()
    }

    private fun hideCompleteView() {
        if (hideCompleteAni.isRunning) {
            hideCompleteAni.cancel()
        }
        hideCompleteAni.start()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.recorder_back -> finish()
            R.id.recorder_cancelbtn -> {
                hideCompleteView(); presenter.reconnect()
            }
            R.id.recorder_certainbtn -> presenter.setResult()
        }
    }

    override fun setResult(result: RecorderResult) {
        val data = Intent()
        data.putExtra(RESULT_FILEPATH, result)
        setResult(RESULT_OK, data)
        finish()
    }

    override fun playVideo(filePath: String) {
        recorder_vv.setVideoPath(filePath)
        recorder_vv.start()
        recorder_vv.setOnPreparedListener(this)
    }

    override fun stopVideo() {
        recorder_vv.stopPlayback()
        isPlaying = false
    }

    override fun onPrepared(mp: MediaPlayer) {
        isPlaying = true
        mp.start()
        mp.isLooping = true
    }

    override fun touched() {
        handler.removeCallbacks(runnable)
        handler.post(runnable)
    }

    override fun untouched() {
        recorder_progress.setProgress(0)
        handler.removeCallbacks(runnable)
    }

    private fun initRunnable() = Runnable {
        presenter.recording()
        handler.postDelayed(runnable, 100)
    }

    private val svOnTouched = View.OnTouchListener { _, event ->
        if (event.action == MotionEvent.ACTION_DOWN && event.y <= recorder_progress.y && !isPlaying) {
            focus(event.x, event.y)
            return@OnTouchListener true
        }
        return@OnTouchListener false
    }

    private val progressOnTouched = View.OnTouchListener { v, event -> presenter.onTouch(v, event); true }

    private fun initvideo_record_focusAni(): AnimatorSet {
        val focusScaleAni = scaleAnis(recorder_focus, 1.5f, 1f)
        val focusAlphaAni = ObjectAnimator.ofFloat(recorder_focus, "alpha", 1f, 0f)
        val set = AnimatorSet()
        focusScaleAni.first.duration = 500
        focusScaleAni.second.duration = 500
        focusAlphaAni.duration = 750
        set.play(focusScaleAni.first)
                .with(focusScaleAni.second)
                .before(focusAlphaAni)
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator?) {
                super.onAnimationStart(animation)
                recorder_focus.alpha = 1f
                recorder_focus.visibility = VISIBLE
            }

            override fun onAnimationEnd(animation: Animator?) {
                super.onAnimationEnd(animation)
                recorder_focus.visibility = GONE
            }
        })
        return set
    }

    private fun initShowSendViewAni(): AnimatorSet =
        animatorSet(350,
                object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator?) {
                        super.onAnimationStart(animation)
                        recorder_back.visibility = GONE
                        recorder_cancelbtn.visibility = VISIBLE
                        recorder_certainbtn.visibility = VISIBLE
                    }

                    override fun onAnimationEnd(animation: Animator?) {
                        super.onAnimationEnd(animation)
                        recorder_progress.visibility = GONE
                    }
                },
                scaleAnis(recorder_cancelbtn, 0f, 1f),
                scaleAnis(recorder_certainbtn, 0f, 1f),
                scaleAnis(recorder_progress, 1f, 0f))

    private fun initHideShowViewAni(): AnimatorSet =
            animatorSet(350,
                    object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator?) {
                            super.onAnimationEnd(animation)
                            recorder_progress.visibility = VISIBLE
                            recorder_back.visibility = VISIBLE
                            recorder_cancelbtn.visibility = GONE
                            recorder_certainbtn.visibility = GONE
                        }
                    },
                    scaleAnis(recorder_cancelbtn, 1f, 0f),
                    scaleAnis(recorder_certainbtn, 1f, 0f),
                    scaleAnis(recorder_progress, 0f, 1f))

    private fun animatorSet(duration: Long, adapter: AnimatorListenerAdapter, vararg args: Pair<ObjectAnimator, ObjectAnimator>): AnimatorSet {
        val set = AnimatorSet()
        set.duration = duration
        args.forEach { set.playTogether(it.first, it.second) }
        set.addListener(adapter)
        return set
    }

    private fun scaleAnis(target: View, from: Float, to: Float): Pair<ObjectAnimator, ObjectAnimator>
            = Pair(ObjectAnimator.ofFloat(target, "scaleX", from, to), ObjectAnimator.ofFloat(target, "scaleY", from, to))

    override fun getContext(): Context = getContext()
}