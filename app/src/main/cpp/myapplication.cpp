#include <jni.h>
#include <string>
#include <android/bitmap.h>
#include <android/log.h>


extern "C"
JNIEXPORT void JNICALL
Java_com_example_myapplication_MainActivityKt_preprocessImage(JNIEnv *env, jclass clazz,
                                                              jobject bitmap,
                                                              jobject output_buffer) {

    if (bitmap == nullptr || output_buffer == nullptr) {
        return;
    }

    void *outputPtr = env->GetDirectBufferAddress(output_buffer);
    uint8_t *outputBufferPtr = static_cast<uint8_t *>(outputPtr);

    int32_t imgWidth = 128;
    int32_t imgHeight = 128;

    int32_t *inPixels = (int32_t *) 0;
    int32_t *outPixels = (int32_t *) outputBufferPtr;
    float IMAGE_MEAN = 127.5f;
    float IMAGE_STD = 127.5f;



    for (int y = 0; y < imgHeight; y++) {
        for (int x = 0; x < imgWidth; x++) {
            int32_t pixel = inPixels[y * imgWidth + x];
            int32_t r = (pixel >> 16) & 0xFF;
            int32_t g = (pixel >> 8) & 0xFF;
            int32_t b = pixel & 0xFF;

            float normalizedR = (r - IMAGE_MEAN) / IMAGE_STD;
            float normalizedG = (g - IMAGE_MEAN) / IMAGE_STD;
            float normalizedB = (b - IMAGE_MEAN) / IMAGE_STD;
            int32_t processedPixel = ((int32_t) normalizedR << 16) | ((int32_t) normalizedG << 8) | ((int32_t) normalizedB);
            outPixels[y * imgWidth + x] = processedPixel;
        }
    }
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_example_myapplication_MainActivityKt_stringFromJNI(JNIEnv *env, jclass clazz) {
    // TODO: implement stringFromJNI()
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}