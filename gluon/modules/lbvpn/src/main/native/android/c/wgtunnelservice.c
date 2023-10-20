#include "util.h"

static jclass jWgTunnelServiceClass;
static jobject jWgTunnelService;
static jmethodID jGetRunningTunnelNamesMethod;

static void initializeDalvikHandles() {
	jWgTunnelServiceClass = GET_REGISTER_DALVIK_CLASS(jWgTunnelServiceClass, "com/logonbox/vpn/client/attach/WgTunnelService");
    ATTACH_DALVIK();
    jmethodID jLogServiceInitMethod = (*dalvikEnv)->GetMethodID(dalvikEnv, jWgTunnelServiceClass, "<init>", "(Landroid/app/Activity;)V");
    jGetRunningTunnelNamesMethod = (*dalvikEnv)->GetMethodID(dalvikEnv, jWgTunnelServiceClass, "getRunningTunnelNames", "(V;)Ljava/util/Set");

    jobject jActivity = substrateGetActivity();
    jobject jtmpobj = (*dalvikEnv)->NewObject(dalvikEnv, jWgTunnelServiceClass, jLogServiceInitMethod, jActivity);
    jWgTunnelService = (*dalvikEnv)->NewGlobalRef(dalvikEnv, jtmpobj);
    DETACH_DALVIK();
}

//////////////////////////
// From Graal to native //
//////////////////////////


JNIEXPORT jint JNICALL
JNI_OnLoad_wgtunnelservice(JavaVM *vm, void *reserved)
{
    ATTACH_LOG_INFO("JNI_OnLoad_wgtunnelservice called");
#ifdef JNI_VERSION_1_8
    JNIEnv* graalEnv;
    if ((*vm)->GetEnv(vm, (void **)&graalEnv, JNI_VERSION_1_8) != JNI_OK) {
        ATTACH_LOG_WARNING("Error initializing native Wg Tunnel from OnLoad");
        return JNI_FALSE;
    }
    ATTACH_LOG_FINE("[Wg Tunnel Service] Initializing native Log from OnLoad");
    initializeDalvikHandles();
    return JNI_VERSION_1_8;
#else
    #error Error: Java 8+ SDK is required to compile Attach
#endif
}

// from Java to Android

JNIEXPORT jobject JNICALL Java_com_logonbox_vpn_client_attach_wireguard_impl_AndroidPlatformService_getTunnelNames
(JNIEnv *env, jclass jClass)
{
    ATTACH_DALVIK();
    jstring tunnelNames = (*dalvikEnv)->CallObjectMethod(dalvikEnv, jWgTunnelService, jGetRunningTunnelNamesMethod);

    /* TODO can we do this? just return the same object? All other official attach modules seem to use callbacks way more.
     * That doesn't fit so well with PlatformService
     */
    return tunnelNames;
    DETACH_DALVIK();
}
