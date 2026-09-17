/*
 * Copyright 2026 Janada Sroor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <jni.h>
#include <string>
#include <vector>
#include <atomic>
#include <mutex>
#include <android/log.h>
#include <dlfcn.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <unistd.h>
#include <sys/stat.h>
#include <errno.h>

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
typedef bool (*ngSpice_Running_t)();

static ngSpice_Init_t p_ngSpice_Init = nullptr;
static ngSpice_Command_t p_ngSpice_Command = nullptr;
static ngSpice_Circ_t p_ngSpice_Circ = nullptr;
static ngSpice_CurPlot_t p_ngSpice_CurPlot = nullptr;
static ngSpice_AllPlots_t p_ngSpice_AllPlots = nullptr;
static ngSpice_AllVecs_t p_ngSpice_AllVecs = nullptr;
static ngSpice_GetVecInfo_t p_ngSpice_GetVecInfo = nullptr;
static ngSpice_Running_t p_ngSpice_Running = nullptr;

static JavaVM *g_vm = nullptr;
// Guarded by g_cb_mutex: ngspice callbacks read these on bg threads while
// nativeInit() swaps them on the UI thread.
static std::mutex g_cb_mutex;
static jobject g_callback_obj = nullptr;
static jmethodID g_on_log_method = nullptr;
static jmethodID g_on_init_data_method = nullptr;
static jmethodID g_on_data_method = nullptr;
static jmethodID g_on_status_method = nullptr;
// Cached java/lang/String global ref (bootstrap class: safe to cache in
// JNI_OnLoad). Avoids a FindClass + local-ref churn on every data callback.
static jclass g_string_class = nullptr;

/**
 * True while ngspice's background thread runs a simulation. Set when
 * bg_run is issued, cleared by the BGThreadRunning callback. The Kotlin
 * side polls it so the final vector flush happens AFTER all data arrived
 * (bg_run returns immediately; without the wait the plot ends up with
 * vector names but empty traces — typical for fast .ac runs).
 * atomic: written by ngspice bg threads, read by the Kotlin poll thread.
 */
static std::atomic<bool> g_bg_running{false};

// Callbacks from ngspice. The callback lock is held for the whole JNI
// section: nativeInit() may swap/delete the global ref on the UI thread,
// so snapshotting without holding it would still race the use.
static int cb_send_char(char *output, int ident, void *userdata) {
    if (!output) return 0;
    LOGI("[NGSPICE LOG] %s", output);
    std::lock_guard<std::mutex> lock(g_cb_mutex);
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
    // Non-UTF8 bytes make NewStringUTF return NULL with a pending
    // exception — must not CallVoidMethod through it.
    if (!jmsg || env->ExceptionCheck()) {
        env->ExceptionClear();
        if (needs_detach) {
            g_vm->DetachCurrentThread();
        }
        return 0;
    }
    env->CallVoidMethod(g_callback_obj, g_on_log_method, jmsg);
    env->DeleteLocalRef(jmsg);

    if (needs_detach) {
        g_vm->DetachCurrentThread();
    }
    return 0;
}

static int cb_send_stat(char *status, int ident, void *userdata) {
    if (!status) return 0;
    std::lock_guard<std::mutex> lock(g_cb_mutex);
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
    if (!jstat || env->ExceptionCheck()) {
        env->ExceptionClear();
        if (needs_detach) {
            g_vm->DetachCurrentThread();
        }
        return 0;
    }
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
    std::lock_guard<std::mutex> lock(g_cb_mutex);
    if (!g_vm || !g_callback_obj || !g_on_data_method || !g_string_class) return 0;

    JNIEnv *env = nullptr;
    bool needs_detach = false;
    if (g_vm->GetEnv((void **)&env, JNI_VERSION_1_6) != JNI_OK) {
        if (g_vm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            needs_detach = true;
        } else {
            return 0;
        }
    }

    jobjectArray name_array = env->NewObjectArray(numvecs, g_string_class, nullptr);
    jdoubleArray val_array = env->NewDoubleArray(numvecs);
    if (!name_array || !val_array || env->ExceptionCheck()) {
        env->ExceptionClear();
        if (name_array) env->DeleteLocalRef(name_array);
        if (val_array) env->DeleteLocalRef(val_array);
        if (needs_detach) {
            g_vm->DetachCurrentThread();
        }
        return 0;
    }

    jdouble *vals = env->GetDoubleArrayElements(val_array, nullptr);
    if (!vals) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        env->DeleteLocalRef(name_array);
        env->DeleteLocalRef(val_array);
        if (needs_detach) {
            g_vm->DetachCurrentThread();
        }
        return 0;
    }
    for (int i = 0; i < numvecs && i < vdata->veccount; i++) {
        pvecvalues vec = vdata->vecsa[i];
        if (vec) {
            if (vec->name) {
                jstring name = env->NewStringUTF(vec->name);
                if (name) {
                    env->SetObjectArrayElement(name_array, i, name);
                    env->DeleteLocalRef(name);
                } else if (env->ExceptionCheck()) {
                    env->ExceptionClear();
                }
            }
            // AC vectors are complex: plot the magnitude so traces are
            // never an empty-looking bare real part. Transient/DC stay real.
            vals[i] = vec->is_complex
                ? sqrt(vec->creal * vec->creal + vec->cimag * vec->cimag)
                : vec->creal;
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
    std::lock_guard<std::mutex> lock(g_cb_mutex);
    if (!g_vm || !g_callback_obj || !g_on_init_data_method || !g_string_class) return 0;

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
    if (!scale_str || !title || !name || !type || env->ExceptionCheck()) {
        env->ExceptionClear();
        if (scale_str) env->DeleteLocalRef(scale_str);
        if (title) env->DeleteLocalRef(title);
        if (name) env->DeleteLocalRef(name);
        if (type) env->DeleteLocalRef(type);
        if (needs_detach) {
            g_vm->DetachCurrentThread();
        }
        return 0;
    }

    jobjectArray vec_names = env->NewObjectArray(vinfo->veccount, g_string_class, nullptr);
    if (!vec_names || env->ExceptionCheck()) {
        env->ExceptionClear();
        env->DeleteLocalRef(scale_str);
        env->DeleteLocalRef(title);
        env->DeleteLocalRef(name);
        env->DeleteLocalRef(type);
        if (vec_names) env->DeleteLocalRef(vec_names);
        if (needs_detach) {
            g_vm->DetachCurrentThread();
        }
        return 0;
    }
    for (int i = 0; i < vinfo->veccount; i++) {
        if (vinfo->vecs && vinfo->vecs[i] && vinfo->vecs[i]->vecname) {
            jstring vname = env->NewStringUTF(vinfo->vecs[i]->vecname);
            if (vname) {
                env->SetObjectArrayElement(vec_names, i, vname);
                env->DeleteLocalRef(vname);
            } else if (env->ExceptionCheck()) {
                env->ExceptionClear();
            }
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
    g_bg_running = running;
    return 0;
}

extern "C"
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    g_vm = vm;
    // Cache java/lang/String once (bootstrap class loader: safe here) so
    // the kHz data path never calls FindClass or churns local refs.
    JNIEnv *env = nullptr;
    if (vm->GetEnv((void **)&env, JNI_VERSION_1_6) == JNI_OK) {
        jclass local = env->FindClass("java/lang/String");
        if (local) {
            g_string_class = (jclass)env->NewGlobalRef(local);
            env->DeleteLocalRef(local);
        }
        if (env->ExceptionCheck()) env->ExceptionClear();
    }
    return JNI_VERSION_1_6;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_jnd_ngdroid_engine_NativeNgSpice_nativeInit(JNIEnv *env, jobject thiz, jobject callback) {
    {
        std::lock_guard<std::mutex> lock(g_cb_mutex);
        if (g_callback_obj) {
            env->DeleteGlobalRef(g_callback_obj);
            g_callback_obj = nullptr;
        }
        g_callback_obj = env->NewGlobalRef(callback);
        if (!g_callback_obj || env->ExceptionCheck()) {
            env->ExceptionClear();
            g_callback_obj = nullptr;
            return JNI_FALSE;
        }

        jclass callback_cls = env->GetObjectClass(callback);
        if (!callback_cls || env->ExceptionCheck()) {
            env->ExceptionClear();
            return JNI_FALSE;
        }
        g_on_log_method = env->GetMethodID(callback_cls, "onLog", "(Ljava/lang/String;)V");
        g_on_init_data_method = env->GetMethodID(callback_cls, "onInitData", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;)V");
        g_on_data_method = env->GetMethodID(callback_cls, "onData", "([Ljava/lang/String;[D)V");
        g_on_status_method = env->GetMethodID(callback_cls, "onStatus", "(Ljava/lang/String;)V");
        env->DeleteLocalRef(callback_cls);
        if (env->ExceptionCheck()) env->ExceptionClear();
        if (!g_on_log_method || !g_on_init_data_method || !g_on_data_method || !g_on_status_method) {
            LOGE("nativeInit: callback method lookup failed");
            return JNI_FALSE;
        }
    }

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
    p_ngSpice_Running = (ngSpice_Running_t)dlsym(handle, "ngSpice_running");
    LOGI("ngSpice_running %s", p_ngSpice_Running ? "available" : "MISSING");

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

    // bg_run only launches the background thread: mark running so the
    // Kotlin side waits for real completion (BGThreadRunning clears it).
    if (ret_cmd == 0) {
        g_bg_running = true;
    }
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
JNIEXPORT jboolean JNICALL
Java_com_jnd_ngdroid_engine_NativeNgSpice_nativeSetWorkDir(JNIEnv *env, jobject thiz, jstring path) {
    // libngspice drops implicit temp files (new*.plt/.data, cider.log,
    // dc-sweep.out) into the process CWD. Point CWD at an app-private dir
    // so runs never litter the project root (desktop tests) or the app
    // sandbox root (device). No-op safe to call before nativeInit.
    if (!path) return JNI_FALSE;
    const char *str = env->GetStringUTFChars(path, nullptr);
    if (!str) return JNI_FALSE;
    // Best-effort mkdir -p equivalent for a single level (Kotlin ensures
    // the dir exists; this covers races where it was deleted).
    mkdir(str, 0700);
    int ret = chdir(str);
    if (ret != 0) {
        LOGE("nativeSetWorkDir: chdir(%s) failed: %s", str, strerror(errno));
    }
    env->ReleaseStringUTFChars(path, str);
    return (ret == 0) ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_com_jnd_ngdroid_engine_NativeNgSpice_nativeIsRunning(JNIEnv *env, jobject thiz) {
    // Canonical polling API: false once the bg thread exits, including
    // aborted runs (which don't always deliver a final status callback).
    if (p_ngSpice_Running) return p_ngSpice_Running() ? JNI_TRUE : JNI_FALSE;
    return g_bg_running ? JNI_TRUE : JNI_FALSE;
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
