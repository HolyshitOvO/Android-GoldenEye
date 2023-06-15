package co.infinum.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import co.infinum.example.databinding.ActivityMainBinding
import co.infinum.goldeneye.GoldenEye
import co.infinum.goldeneye.InitCallback
import co.infinum.goldeneye.Logger
import co.infinum.goldeneye.config.CameraConfig
import co.infinum.goldeneye.config.CameraInfo
import co.infinum.goldeneye.models.CameraApi
// import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.min

@SuppressLint("SetTextI18n")
class MainActivity : AppCompatActivity() {

	private lateinit var goldenEye: GoldenEye
	private lateinit var videoFile: File
	private var isRecording = false
	private var rotateValue = 0
	private var isMirrorInverted = false

	private val mainHandler = Handler(Looper.getMainLooper())
	private var settingsAdapter = SettingsAdapter(listOf())

	private val initCallback = object : InitCallback() {
		override fun onReady(config: CameraConfig) {
			binding.zoomView.text = "Zoom: ${config.zoom.toPercentage()}"
		}

		override fun onError(t: Throwable) {
			t.printStackTrace()
		}
	}


	private val binding by lazy {
		ActivityMainBinding.inflate(layoutInflater)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(binding.root)
		AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
		initGoldenEye()
		videoFile = File.createTempFile("vid", "")
		initListeners()
	}

	private fun initListeners() {
		binding.settingsView.setOnClickListener {
			prepareItems()
			binding.settingsRecyclerView.apply {
				visibility = View.VISIBLE
				layoutManager = LinearLayoutManager(this@MainActivity)
				adapter = settingsAdapter
			}
		}

		binding.takePictureView.setOnClickListener { _ ->
			goldenEye.takePicture(
				onPictureTaken = { bitmap ->
					if (bitmap.width <= 4096 && bitmap.height <= 4096) {
						displayPicture(bitmap)
					} else {
						reducePictureSize(bitmap)
					}
				},
				onError = { it.printStackTrace() }
			)
		}

		binding.recordVideoView.setOnClickListener { _ ->
			if (isRecording) {
				isRecording = false
				binding.recordVideoView.setImageResource(R.drawable.ic_record_video)
				goldenEye.stopRecording()
			} else {
				startRecording()
			}
		}
		// 切换 前置 后置
		binding.switchCameraView.setOnClickListener { _ ->
			val currentIndex = goldenEye.availableCameras.indexOfFirst { goldenEye.config?.id == it.id }
			val nextIndex = (currentIndex + 1) % goldenEye.availableCameras.size
			openCamera(goldenEye.availableCameras[nextIndex])
		}
		// 旋转画面
		binding.btnTest1.setOnClickListener {
			rotateValue+=90
			if(rotateValue>270){
				rotateValue=0
			}
			val currentIndex = goldenEye.availableCameras.indexOfFirst { goldenEye.config?.id == it.id }
			// goldenEye.rotatePreview(rotateValue)
			goldenEye.availableCameras[currentIndex].rotateValue = rotateValue
			openCamera(goldenEye.availableCameras[currentIndex])
		}
		binding.btnTest2.setOnClickListener {
			goldenEye.setZoomInOrZoomOut(300)
		}
		binding.btnTest3.setOnClickListener {
			goldenEye.setZoomInOrZoomOut(-300)
		}
		binding.btnTest4.setOnClickListener {
			isMirrorInverted=!isMirrorInverted
			val currentIndex = goldenEye.availableCameras.indexOfFirst { goldenEye.config?.id == it.id }
			// goldenEye.rotatePreview(rotateValue)
			goldenEye.availableCameras[currentIndex].isMirrorInverted = isMirrorInverted
			openCamera(goldenEye.availableCameras[currentIndex])
		}
	}

	private fun reducePictureSize(bitmap: Bitmap) {
		Executors.newSingleThreadExecutor().execute {
			try {
				val scaleX = 4096f / bitmap.width
				val scaleY = 4096f / bitmap.height
				val scale = min(scaleX, scaleY)
				val newBitmap = Bitmap.createScaledBitmap(
					bitmap,
					(bitmap.width * scale).toInt(),
					(bitmap.height * scale).toInt(),
					true
				)
				mainHandler.post {
					displayPicture(newBitmap)
				}
			} catch (t: Throwable) {
				toast("Picture is too big. Reduce picture size in settings below 4096x4096.")
			}
		}
	}

	private fun displayPicture(bitmap: Bitmap) {
		binding.previewPictureView.apply {
			setImageBitmap(bitmap)
			visibility = View.VISIBLE
		}
		mainHandler.postDelayed(
			{ binding.previewPictureView.visibility = View.GONE },
			2000
		)
	}

	private fun initGoldenEye() {
		goldenEye = GoldenEye.Builder(this)
			.setCameraApi(CameraApi.CAMERA2)
			.setLogger(logger)
			.setOnZoomChangedCallback { binding.zoomView.text = "Zoom: ${it.toPercentage()}" }
			.build()
	}

	override fun onStart() {
		super.onStart()
		if (goldenEye.availableCameras.isNotEmpty()) {
			openCamera(goldenEye.availableCameras[0])
		}
	}

	override fun onStop() {
		super.onStop()
		goldenEye.release()
	}

	private fun openCamera(cameraInfo: CameraInfo) {
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
			goldenEye.open(binding.textureView, cameraInfo, initCallback)
		} else {
			ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 0x1)
		}
	}

	private fun startRecording() {
		if (ActivityCompat.checkSelfPermission(
				this,
				Manifest.permission.RECORD_AUDIO
			) == PackageManager.PERMISSION_GRANTED
		) {
			record()
		} else {
			ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 0x2)
		}
	}

	private fun record() {
		isRecording = true
		binding.recordVideoView.setImageResource(R.drawable.ic_stop)
		goldenEye.startRecording(
			file = videoFile,
			onVideoRecorded = {
				binding.previewVideoContainer.visibility = View.VISIBLE
				if (binding.previewVideoView.isAvailable) {
					startVideo()
				} else {
					binding.previewVideoView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
						override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
						override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
						override fun onSurfaceTextureDestroyed(surface: SurfaceTexture) = true

						override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
							startVideo()
						}
					}
				}
			},
			onError = { it.printStackTrace() }
		)
	}

	@SuppressLint("MissingPermission")
	override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
		if (requestCode == 0x1) {
			if (grantResults.getOrNull(0) == PackageManager.PERMISSION_GRANTED) {
				goldenEye.open(binding.textureView, goldenEye.availableCameras[0], initCallback)
			} else {
				AlertDialog.Builder(this)
					.setTitle("GoldenEye")
					.setMessage("Smartass Detected!")
					.setPositiveButton("I am smartass") { _, _ ->
						throw SmartassException
					}
					.setNegativeButton("Sorry") { _, _ ->
						openCamera(goldenEye.availableCameras[0])
					}
					.setCancelable(false)
					.show()
			}
		} else if (requestCode == 0x2) {
			record()
		}
	}

	@Deprecated("Deprecated in Java")
	override fun onBackPressed() {
		if (binding.settingsRecyclerView.visibility == View.VISIBLE) {
			binding.settingsRecyclerView.visibility = View.GONE
		} else {
			super.onBackPressed()
		}
	}

	private fun prepareItems() {
		goldenEye.config?.apply {
			prepareItems(this@MainActivity, settingsAdapter)
		}
	}

	private fun startVideo() {
		MediaPlayer().apply {
			setSurface(Surface(binding.previewVideoView.surfaceTexture))
			setDataSource(videoFile.absolutePath)
			setOnCompletionListener {
				mainHandler.postDelayed({
					binding.previewVideoContainer.visibility = View.GONE
					release()
				}, 1500)
			}
			setOnVideoSizeChangedListener { _, width, height ->
				binding.previewVideoView.apply {
					layoutParams = layoutParams.apply {
						val scaleX = binding.previewVideoContainer.width / width.toFloat()
						val scaleY = binding.previewVideoContainer.height / height.toFloat()
						val scale = min(scaleX, scaleY)

						this.width = (width * scale).toInt()
						this.height = (height * scale).toInt()
					}
				}
			}
			prepare()
			start()
		}
	}

	private val logger = object : Logger {
		override fun log(message: String) {
			Log.e("GoldenEye", message)
		}

		override fun log(t: Throwable) {
			t.printStackTrace()
		}
	}
}

object SmartassException : Throwable()
