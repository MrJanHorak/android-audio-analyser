#include <jni.h>
#include <math.h>

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

extern "C" JNIEXPORT void JNICALL
Java_com_example_audioanalyser_NativeFFT_nativeFft(JNIEnv* env, jclass /*clazz*/, jdoubleArray realArr, jdoubleArray imagArr, jint n) {
    jdouble* real = env->GetDoubleArrayElements(realArr, NULL);
    jdouble* imag = env->GetDoubleArrayElements(imagArr, NULL);
    int N = (int)n;

    if (N <= 1) {
        env->ReleaseDoubleArrayElements(realArr, real, 0);
        env->ReleaseDoubleArrayElements(imagArr, imag, 0);
        return;
    }

    // Bit-reversal permutation
    int j = 0;
    for (int i = 1; i < N; ++i) {
        int bit = N >> 1;
        while (j >= bit) {
            j -= bit;
            bit >>= 1;
        }
        j += bit;
        if (i < j) {
            double tr = real[i];
            real[i] = real[j];
            real[j] = tr;
            double ti = imag[i];
            imag[i] = imag[j];
            imag[j] = ti;
        }
    }

    // Cooley-Tukey iterative
    int len = 2;
    while (len <= N) {
        int half = len / 2;
        double theta = -2.0 * M_PI / len;
        double wlenR = cos(theta);
        double wlenI = sin(theta);
        for (int i = 0; i < N; i += len) {
            double wr = 1.0;
            double wi = 0.0;
            for (int k = 0; k < half; ++k) {
                int idx = i + k;
                int idy = idx + half;
                double ur = real[idx];
                double ui = imag[idx];
                double vr = real[idy] * wr - imag[idy] * wi;
                double vi = real[idy] * wi + imag[idy] * wr;
                real[idx] = ur + vr;
                imag[idx] = ui + vi;
                real[idy] = ur - vr;
                imag[idy] = ui - vi;
                double nextWr = wr * wlenR - wi * wlenI;
                double nextWi = wr * wlenI + wi * wlenR;
                wr = nextWr;
                wi = nextWi;
            }
        }
        len <<= 1;
    }

    env->ReleaseDoubleArrayElements(realArr, real, 0);
    env->ReleaseDoubleArrayElements(imagArr, imag, 0);
}
