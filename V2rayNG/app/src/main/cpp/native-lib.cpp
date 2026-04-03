#include <jni.h>
#include <string>
#include <vector>

extern "C" JNIEXPORT jstring JNICALL
Java_com_daggomostudios_simpsonsvpn_NativeCrypto_getDecryptionKey(JNIEnv* env, jobject /* this */) {
    // A chave de 200 caracteres fornecida pelo utilizador
    // Armazenada como um array de caracteres para evitar deteção fácil
    // Chave de 64 caracteres hexadecimais (32 bytes) para evitar problemas de encoding
    // Esta string hexadecimal será usada diretamente para derivar a chave AES-256
    const char* key = "785439764c326b4d347051386a52317746356e59376243337a58366448306753";

    return env->NewStringUTF(key);
}
