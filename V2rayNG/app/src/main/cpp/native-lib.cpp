#include <jni.h>
#include <string>
#include <vector>

extern "C" JNIEXPORT jstring JNICALL
Java_com_daggomostudios_simpsonsvpn_NativeCrypto_getDecryptionKey(JNIEnv* env, jobject /* this */) {
    // A chave de 200 caracteres fornecida pelo utilizador
    // Armazenada como um array de caracteres para evitar deteção fácil
    // A chave de 200 caracteres exata usada no script Python e no Kotlin
    const char* key = "xT9vL2kM4pQ8jR1wF5nY7bC3zX6dH0gS9vL2kM4pQ8jR1wF5nY7bC3zX6dH0gS9vL2kM4pQ8jR1wF5nY7bC3zX6dH0gS9vL2kM4pQ8jR1wF5nY7bC3zX6dH0gS9vL2kM4pQ8jR1wF5nY7bC3zX";

    return env->NewStringUTF(key);
}
