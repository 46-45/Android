package com.example.facerecognition

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.annotations.SerializedName
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var captureButton: Button
    private lateinit var predictionTextView: TextView

    private val CAMERA_PERMISSION_CODE = 1
    private val IMAGE_CAPTURE_CODE = 2

    private val imageCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data: Intent? = result.data
//                val imageUri = data?.data
                val imageBitmap = data?.extras?.get("data") as Bitmap
                val grayImage = convertToGrayscale(imageBitmap)
                imageView.setImageBitmap(imageBitmap)

                sendImageToColab(imageBitmap)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.imageView)
        captureButton = findViewById(R.id.captureButton)
        predictionTextView = findViewById(R.id.predictionTextView)

        captureButton.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                captureImage()
            } else {
                requestCameraPermission()
            }
        }
    }

    private fun captureImage() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        imageCaptureLauncher.launch(intent)
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                captureImage()
            }
        }
    }

    private fun sendImageToColab(image: Bitmap) {
        val file = File(cacheDir, "temp_image.jpg")
        val outputStream = FileOutputStream(file)
        image.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        outputStream.flush()
        outputStream.close()

        val requestFile =
            file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val imagePart = MultipartBody.Part.createFormData("Image", file.name, requestFile)

        val loggingInterceptor =
            HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY)
        val client = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://8b30-110-137-194-79.ngrok-free.app/") //
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()

        val apiService = retrofit.create(ApiService::class.java)
        val call = apiService.sendImage(imagePart)
        call.enqueue(object : Callback<PredictionResponse> {
            override fun onResponse(
                call: Call<PredictionResponse>,
                response: Response<PredictionResponse>
            ) {
                if (response.isSuccessful) {
                    val prediction = response.body()?.name
                    predictionTextView.text = "Prediction: $prediction"
                }
            }

            override fun onFailure(call: Call<PredictionResponse>, t: Throwable) {
                t.printStackTrace()
            }
        })
    }

    private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val grayBitmap =
            Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.RGB_565)
        val canvas = Canvas(grayBitmap)
        val paint = Paint()
        val colorMatrix = ColorMatrix()
        colorMatrix.setSaturation(0f)
        val filter = ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = filter
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return grayBitmap
    }
}

interface ApiService {

    @Multipart
    @POST("absen")
    fun sendImage(@Part image: MultipartBody.Part): Call<PredictionResponse>
}

data class PredictionResponse(

    @field:SerializedName("result")
    val result: String,

    @field:SerializedName("name")
    val name: String,

    @field:SerializedName("code")
    val code: Int
)
