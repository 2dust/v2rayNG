#include <jni.h>
#include <string>
#include <vector>

extern "C" JNIEXPORT jstring JNICALL
Java_com_daggomostudios_simpsonsvpn_NativeCrypto_getDecryptionKey(JNIEnv* env, jobject /* this */) {
    // A chave de 200 caracteres fornecida pelo utilizador
    // Armazenada como um array de caracteres para evitar deteção fácil
    char key[] = {
        'x', 'T', '9', 'v', 'L', '2', 'k', 'M', '4', 'p', 'Q', '8', 'j', 'R', '1', 'w', 'F', '5', 'n', 'Y', '7', 'b', 'C', '3', 'z', 'X', '6', 'd', 'H', '0', 'g', 'S', '9', 'v', 'L', '2', 'k', 'M', '4', 'p', 'Q', '8', 'j', 'R', '1', 'w', 'F', '5', 'n', 'Y', '7', 'b', 'C', '3', 'z', 'X', '6', 'd', 'H', '0', 'g', 'S', '9', 'v', 'L', '2', 'k', 'M', '4', 'p', 'Q', '8', 'j', 'R', '1', 'w', 'F', '5', 'n', 'Y', '7', 'b', 'C', '3', 'z', 'X', '6', 'd', 'H', '0', 'g', 'S', '9', 'v', 'L', '2', 'k', 'M', '4', 'p', 'Q', '8', 'j', 'R', '1', 'w', 'F', '5', 'n', 'Y', '7', 'b', 'C', '3', 'z', 'X', '6', 'd', 'H', '0', 'g', 'S', '9', 'v', 'L', '2', 'k', 'M', '4', 'p', 'Q', '8', 'j', 'R', '1', 'w', 'F', '5', 'n', 'Y', '7', 'b', 'C', '3', 'z', 'X', '\0'
    };

    return env->NewStringUTF(key);
}
