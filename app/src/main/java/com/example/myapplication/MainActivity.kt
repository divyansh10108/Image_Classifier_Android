package com.example.myapplication

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ImageClassificationApp()
        }
    }

    companion object {
        // Used to load the 'classifier' library on application startup.
        init {
            System.loadLibrary("myapplication")
        }
    }
}

enum class ModelType {
    MODEL_MOBILENETV1,
    MODEL_EFFICIENTNETV0,
    MODEL_EFFICIENTNETV1,
    MODEL_EFFICIENTNETV2
}
external fun stringFromJNI(): String;

@Composable
fun ImageClassificationApp() {
    val context = LocalContext.current
    val assetManager = context.assets
    val currentModel = ModelType.MODEL_MOBILENETV1
    val tfliteModel: Interpreter? = loadModelFile(assetManager, currentModel)

    if (tfliteModel != null) {
        ImageClassificationScreen(tfliteModel)
    } else {
        Text("Error loading model")
    }
}


@Composable
fun ImageClassificationScreen(tfliteModel: Interpreter) {
    val context = LocalContext.current
    var imageBitmapState by remember { mutableStateOf<Bitmap?>(null) }
    var maxProbIndex by remember { mutableStateOf(-1) } // Track maximum probability index
    val isProcessing = remember { AtomicBoolean(false) }

    val coroutineScope = rememberCoroutineScope()
    Text(text = stringFromJNI())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .padding(8.dp)
        ) {
            imageBitmapState?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Image"
                )
            }
        }

        Button(
            onClick = {
                if (!isProcessing.get()) {
                    isProcessing.set(true)
                    coroutineScope.launch {
                        try {
                            val bitmap = getRandomImageFromUrl()
                            imageBitmapState = bitmap  // Update image bitmap state
                            val predictedProbs = classifyImage(bitmap, tfliteModel)
                            // Find the index with maximum probability
                            val maxIndex = predictedProbs.indices.maxByOrNull { predictedProbs[it] }
                            maxProbIndex = maxIndex ?: -1 // Set max index or -1 if not found
                        } catch (e: Exception) {
                            e.printStackTrace()
                            // Log or handle error
                        } finally {
                            isProcessing.set(false)
                        }
                    }
                }
            },
            modifier = Modifier.padding(8.dp)
        ) {
            Text("Classify Image")
        }

        maxProbIndex.takeIf { it != -1 }?.let { index ->
            Text("Max Probability Index: $index")
        }
        
    }

    // LaunchedEffect to update image bitmap state
    LaunchedEffect(imageBitmapState) {
        imageBitmapState?.let { bitmap ->
            // This will trigger recomposition when imageBitmapState changes
            imageBitmapState = bitmap
        }
    }
}

suspend fun getRandomImageFromUrl(): Bitmap {
    return withContext(Dispatchers.IO) {
        val imageUrl = "https://random.imagecdn.app/128/128"
        val inputStream = java.net.URL(imageUrl).openStream()
        BitmapFactory.decodeStream(inputStream)
    }
}

const val IMAGE_MEAN = 127.5f
const val IMAGE_STD = 127.5f

private external fun preprocessImage(bitmap: Bitmap, outputBuffer: ByteBuffer)

fun classifyImage(bitmap: Bitmap, tfliteModel: Interpreter): FloatArray {
    val IMAGE_SIZE_X = 128
    val IMAGE_SIZE_Y = 128
    val DIM_BATCH_SIZE = 1
    val DIM_PIXEL_SIZE = 3
    val NUM_CLASS = 1001
    val outputBuffer = null;



    val imgData = ByteBuffer.allocateDirect(
        DIM_BATCH_SIZE *
                IMAGE_SIZE_X *
                IMAGE_SIZE_Y *
                DIM_PIXEL_SIZE *
                java.lang.Float.SIZE / java.lang.Byte.SIZE  // Size of Float in bytes
    )
    imgData.order(ByteOrder.nativeOrder())
    imgData.rewind()
    outputBuffer?.let { preprocessImage(bitmap, it) }

    val intValues = IntArray(IMAGE_SIZE_X * IMAGE_SIZE_Y)
    bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

    // Normalize and load the pixel data into imgData
    for (pixelValue in intValues) {
        imgData.putFloat(((pixelValue shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
        imgData.putFloat(((pixelValue shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
        imgData.putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
    }

    val labelProbArray = Array(1) { FloatArray(NUM_CLASS) }
    tfliteModel.run(imgData, labelProbArray)

    return labelProbArray[0]
}

fun loadModelFile(assetManager: AssetManager, currentModel: ModelType): Interpreter? {
    val modelName = when (currentModel) {
        ModelType.MODEL_MOBILENETV1 -> "1.tflite"
        ModelType.MODEL_EFFICIENTNETV0 -> "1.tflite"
        ModelType.MODEL_EFFICIENTNETV1 -> "1.tflite"
        ModelType.MODEL_EFFICIENTNETV2 -> "1.tflite"
    }

    try {
        val modelFilename = modelName
        val inputStream = assetManager.open(modelFilename)
        val modelByteBuffer = inputStream.readBytes().let {
            ByteBuffer.allocateDirect(it.size).apply {
                order(ByteOrder.nativeOrder())
                put(it)
                rewind()
            }
        }

        return Interpreter(modelByteBuffer)
    } catch (e: Exception) {
        e.printStackTrace()
        // Log error or handle exception as needed
        return null  // Return null or throw exception depending on your error handling strategy
    }
}
