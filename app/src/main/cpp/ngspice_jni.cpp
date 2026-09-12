#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>

#define LOG_TAG "NgSpiceJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// ngspice C structs matching sharedspice.h
typedef struct vector_info {
    char *v_name;
    int v_type;
    short v_flags;
    double *v_realdata;
    void *v_compdata;
    int v_length;
} vector_info;

typedef struct vecvalues {
    char *name;
    double creal;
    double cimag;
    bool is_scale;
    bool is_complex;
} vecvalues, *pvecvalues;

typedef struct vecvaluesall {
    int veccount;
    int vecindex;
    pvecvalues *vecsa;
} vecvaluesall, *pvecvaluesall;

typedef struct vecinfo {
    int number;
    char *vecname;
    bool is_real;
    void *pdvec;
    void *pdvecscale;
} vecinfo, *pvecinfo;

typedef struct vecinfoall {
    char *name;
    char *title;
    char *date;
    char *type;
    int veccount;
    pvecinfo *vecs;
} vecinfoall, *pvecinfoall;

// ngspice callback function pointer types
typedef int (*SendChar)(char *output, int ident, void *userdata);
typedef int (*SendStat)(char *status, int ident, void *userdata);
typedef int (*ControlledExit)(int exit_status, bool immediate, bool quit, int ident, void *userdata);
typedef int (*SendData)(pvecvaluesall vdata, int numvecs, int ident, void *userdata);
typedef int (*SendInitData)(pvecinfoall vinfo, int ident, void *userdata);
typedef int (*BGThreadRunning)(bool running, int ident, void *userdata);

// Exported C functions from libngspice.so
typedef int (*ngSpice_Init_t)(SendChar, SendStat, ControlledExit, SendData, SendInitData, BGThreadRunning, void*);
typedef int (*ngSpice_Command_t)(char *command);
typedef int (*ngSpice_Circ_t)(char **netlist);
typedef char* (*ngSpice_CurPlot_t)();
typedef char** (*ngSpice_AllPlots_t)();
typedef char** (*ngSpice_AllVecs_t)(char *plotname);
typedef vector_info* (*ngSpice_GetVecInfo_t)(char *vecname);

static ngSpice_Init_t p_ngSpice_Init = nullptr;
static ngSpice_Command_t p_ngSpice_Command = nullptr;
static ngSpice_Circ_t p_ngSpice_Circ = nullptr;
static ngSpice_CurPlot_t p_ngSpice_CurPlot = nullptr;
static ngSpice_AllPlots_t p_ngSpice_AllPlots = nullptr;
static ngSpice_AllVecs_t p_ngSpice_AllVecs = nullptr;
static ngSpice_GetVecInfo_t p_ngSpice_GetVecInfo = nullptr;

static JavaVM *g_vm = nullptr;
static jobject g_callback_obj = nullptr;
static jmethodID g_on_log_method = nullptr;
static jmethodID g_on_init_data_method = nullptr;
static jmethodID g_on_data_method = nullptr;
static jmethodID g_on_status_method = nullptr;

// Callbacks from ngspice
static int cb_send_char(char *output, int ident, void *userdata) {
    if (!output) return 0;
    LOGI("[NGSPICE LOG] %s", output);
    if (!g_vm || !g_callback_obj || !g_on_log_method) return 0;

    JNIEnv *env = nullptr;
    bool needs_detach = false;
    if (g_vm->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            needs_detach = true;
        } else {
            return 0;
        }
    }

    jstring jmsg = env->NewStringUTF(output);
    env->CallVoidMethod(g_callback_obj, g_on_log_method, jmsg);
    env->DeleteLocalRef(jmsg);

    if (needs_detach) {
        g_vm->DetachCurrentThread();
    }
    return 0;
}

static int cb_send_stat(char *status, int ident, void *userdata) {
    if (!status) return 0;
    if (!g_vm || !g_callback_obj || !g_on_status_method) return 0;

    JNIEnv *env = nullptr;
    bool needs_detach = false;
    if (g_vm->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            needs_detach = true;
        } else {
            return 0;
        }
    }

    jstring jstat = env->NewStringUTF(status);
    env->CallVoidMethod(g_callback_obj, g_on_status_method, jstat);
    env->DeleteLocalRef(jstat);

    if (needs_detach) {
        g_vm->DetachCurrentThread();
    }
    return 0;
}

static int cb_controlled_exit(int exit_status, bool immediate, bool quit, int ident, void *userdata) {
    LOGI("[NGSPICE EXIT] status: %d", exit_status);
    return 0;
}

static int cb_send_data(pvecvaluesall vdata, int numvecs, int ident, void *userdata) {
    if (!vdata || numvecs <= 0) return 0;
    if (!g_vm || !g_callback_obj || !g_on_data_method) return 0;

    JNIEnv *env = nullptr;
    bool needs_detach = false;
    if (g_vm->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            needs_detach = true;
        } else {
            return 0;
        }
    }

    jobjectArray name_array = env->NewObjectArray(numvecs, env->FindClass("java/lang/String"), nullptr);
    jdoubleArray val_array = env->NewDoubleArray(numvecs);

    jdouble *vals = env->GetDoubleArrayElements(val_array, nullptr);
    for (int i = 0; i < numvecs && i < vdata->veccount; i++) {
        pvecvalues vec = vdata->vecsa[i];
        if (vec) {
            if (vec->name) {
                jstring name = env->NewStringUTF(vec->name);
                env->SetObjectArrayElement(name_array, i, name);
                env->DeleteLocalRef(name);
            }
            vals[i] = vec->creal;
        }
    }
    env->ReleaseDoubleArrayElements(val_array, vals, 0);

    env->CallVoidMethod(g_callback_obj, g_on_data_method, name_array, val_array);

    env->DeleteLocalRef(name_array);
    env->DeleteLocalRef(val_array);

    if (needs_detach) {
        g_vm->DetachCurrentThread();
    }
    return 0;
}

static int cb_send_init_data(pvecinfoall vinfo, int ident, void *userdata) {
    if (!vinfo) return 0;
    LOGI("[NGSPICE INIT DATA] Plot: %s, Veccount: %d", vinfo->name ? vinfo->name : "null", vinfo->veccount);
    if (!g_vm || !g_callback_obj || !g_on_init_data_method) return 0;

    JNIEnv *env = nullptr;
    bool needs_detach = false;
    if (g_vm->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            needs_detach = true;
        } else {
            return 0;
        }
    }

    const char *scale_name = "time";
    for (int i = 0; i < vinfo->veccount; i++) {
        if (vinfo->vecs && vinfo->vecs[i]) {
            if (vinfo->vecs[i]->pdvec == vinfo->vecs[i]->pdvecscale && vinfo->vecs[i]->vecname) {
                scale_name = vinfo->vecs[i]->vecname;
                break;
            }
        }
    }

    jstring scale_str = env->NewStringUTF(scale_name);
    jstring title = env->NewStringUTF(vinfo->title ? vinfo->title : "");
    jstring name = env->NewStringUTF(vinfo->name ? vinfo->name : "");
    jstring type = env->NewStringUTF(vinfo->type ? vinfo->type : "");

    jobjectArray vec_names = env->NewObjectArray(vinfo->veccount, env->FindClass("java/lang/String"), nullptr);
    for (int i = 0; i < vinfo->veccount; i++) {
        if (vinfo->vecs && vinfo->vecs[i] && vinfo->vecs[i]->vecname) {
            jstring vname = env->NewStringUTF(vinfo->vecs[i]->vecname);
            env->SetObjectArrayElement(vec_names, i, vname);
            env->DeleteLocalRef(vname);
        }
    }

    env->CallVoidMethod(g_callback_obj, g_on_init_data_method, scale_str, title, name, type, vec_names);

    env->DeleteLocalRef(scale_str);
    env->DeleteLocalRef(title);
    env->DeleteLocalRef(name);
    env->DeleteLocalRef(type);
    env->DeleteLocalRef(vec_names);

    if (needs_detach) {
        g_vm->DetachCurrentThread();
    }
    return 0;
}

static int cb_bg_thread_running(bool running, int ident, void *userdata) {
    LOGI("[NGSPICE BG THREAD] Running: %d", running);
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_jnd_ngdroid_engine_NativeNgSpice_nativeInit(JNIEnv *env, jobject thiz, jobject callback) {
    if (g_callback_obj) {
        env->DeleteGlobalRef(g_callback_obj);
    }
    g_callback_obj = env->NewGlobalRef(callback);

    jclass callback_cls = env->GetObjectClass(callback);
    g_on_log_method = env->GetMethodID(callback_cls, "onLog", "(Ljava/lang/String;)V");
    g_on_init_data_method = env->GetMethodID(callback_cls, "onInitData", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)V");
    g_on_data_method = env->GetMethodID(callback_cls, "onData", "([Ljava/lang/String;[D)V");
    g_on_status_method = env->GetMethodID(callback_cls, "onStatus", "(Ljava/lang/String;)V");

    void *handle = dlopen("libngspice.so", RTLD_NOW | RTLD_GLOBAL);
    if (!handle) {
        LOGE("Failed to dlopen libngspice.so: %s", dlerror());
        return JNI_FALSE;
    }

    p_ngSpice_Init = (ngSpice_Init_t)dlsym(handle, "ngSpice_Init");
    p_ngSpice_Command = (ngSpice_Command_t)dlsym(handle, "ngSpice_Command");
    p_ngSpice_Circ = (ngSpice_Circ_t)dlsym(handle, "ngSpice_Circ");
    p_ngSpice_CurPlot = (ngSpice_CurPlot_t)dlsym(handle, "ngSpice_CurPlot");
    p_ngSpice_AllPlots = (ngSpice_AllPlots_t)dlsym(handle, "ngSpice_AllPlots");
    p_ngSpice_AllVecs = (ngSpice_AllVecs_t)dlsym(handle, "ngSpice_AllVecs");
    p_ngSpice_GetVecInfo = (ngSpice_GetVecInfo_t)dlsym(handle, "ngSpice_GetVecInfo");

    if (!p_ngSpice_Init || !p_ngSpice_Command || !p_ngSpice_Circ) {
        LOGE("Failed to find required ngspice symbols");
        return JNI_FALSE;
    }

    int ret = p_ngSpice_Init(
        cb_send_char,
        cb_send_stat,
        cb_controlled_exit,
        cb_send_data,
        cb_send_init_data,
        cb_bg_thread_running,
        nullptr
    );

    LOGI("ngSpice_Init returned: %d", ret);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_jnd_ngdroid_engine_NativeNgSpice_nativeRunNetlist(JNIEnv *env, jobject thiz, jobjectArray netlist_array) {
    if (!p_ngSpice_Circ || !p_ngSpice_Command) {
        LOGE("ngspice not initialized");
        return JNI_FALSE;
    }

    int count = env->GetArrayLength(netlist_array);
    char **netlist = (char **)malloc((count + 1) * sizeof(char *));

    for (int i = 0; i < count; i++) {
        jstring line = (jstring)env->GetObjectArrayElement(netlist_array, i);
        const char *str = env->GetStringUTFChars(line, nullptr);
        netlist[i] = strdup(str);
        env->ReleaseStringUTFChars(line, str);
        env->DeleteLocalRef(line);
    }
    netlist[count] = nullptr;

    int ret_circ = p_ngSpice_Circ(netlist);
    LOGI("ngSpice_Circ returned: %d", ret_circ);

    for (int i = 0; i < count; i++) {
        free(netlist[i]);
    }
    free(netlist);

    if (ret_circ != 0) {
        return JNI_FALSE;
    }

    int ret_cmd = p_ngSpice_Command((char *)"bg_run");
    LOGI("ngSpice_Command bg_run returned: %d", ret_cmd);

    return (ret_cmd == 0) ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_jnd_ngdroid_engine_NativeNgSpice_nativeHalt(JNIEnv *env, jobject thiz) {
    if (!p_ngSpice_Command) return JNI_FALSE;
    int ret = p_ngSpice_Command((char *)"bg_halt");
    return (ret == 0) ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_jnd_ngdroid_engine_NativeNgSpice_nativeResume(JNIEnv *env, jobject thiz) {
    if (!p_ngSpice_Command) return JNI_FALSE;
    int ret = p_ngSpice_Command((char *)"bg_resume");
    return (ret == 0) ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_jnd_ngdroid_engine_NativeNgSpice_nativeCommand(JNIEnv *env, jobject thiz, jstring cmd) {
    if (!p_ngSpice_Command) return JNI_FALSE;
    const char *str = env->GetStringUTFChars(cmd, nullptr);
    int ret = p_ngSpice_Command((char *)str);
    env->ReleaseStringUTFChars(cmd, str);
    return (ret == 0) ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jdoubleArray JNICALL
Java_com_jnd_ngdroid_engine_NativeNgSpice_nativeGetVectorData(JNIEnv *env, jobject thiz, jstring vec_name) {
    if (!p_ngSpice_GetVecInfo) return nullptr;

    const char *vname = env->GetStringUTFChars(vec_name, nullptr);
    vector_info *info = p_ngSpice_GetVecInfo((char *)vname);
    env->ReleaseStringUTFChars(vec_name, vname);

    if (!info || info->v_length <= 0) return nullptr;

    jdoubleArray data = env->NewDoubleArray(info->v_length);
    if (info->v_realdata) {
        env->SetDoubleArrayRegion(data, 0, info->v_length, info->v_realdata);
    }
    return data;
}
