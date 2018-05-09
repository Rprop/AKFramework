/*
 *
 *  @author : Rprop (r_prop@outlook.com)
 *  https://github.com/Rprop/AKFramework
 *
 */
#define AK_TAG          "AKFramework"
#define AK_CONSOLE      0
#define AK_MAX_RETRIES  5
#define AK_TIME_LIMIT   160 // seconds
#define AK_FILE_PREFIX  ".__ak"
#define AK_TMP_DIR      "/data/local/tmp/"
#define AK_PRIVATE_DIR  AK_TMP_DIR AK_FILE_PREFIX "/"
#define AK_GUARD_FILE   AK_TMP_DIR AK_FILE_PREFIX "_guard"
#include <jni.h>
#include <inttypes.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <unistd.h>
#include <time.h>
#include <pthread.h>
#include <sys/stat.h>
#include <sys/file.h>
#include <AndHook.h>
#include "AKPlatform.h"
#include "AKSELinux.h"
#include "AKLog.h"
#include "AKJniHelpers.h"
#include "android_util_Log.h"
#include "android_filesystem_config.h"

//-------------------------------------------------------------------------

static AKInvokeInterface *ak_functions;

//-------------------------------------------------------------------------

static void disable_inline_optimizing(JavaVMInitArgs *vm_args)
{
    // Note: 
    // -Xcompiler-option, used for runtime compilation(DexClassLoader)
    // -Ximage-compiler-option, used for boot-image compilation on device
    // 
    // --inline-max-code-units=
    // --inline-depth-limit=    Android_M
    if (vm_args != NULL && vm_args->version >= JNI_VERSION_1_4) {
        vm_args->ignoreUnrecognized = JNI_TRUE;

        static JavaVMOption def_options[128] = {};
        if (vm_args->nOptions < jint(sizeof(def_options) / sizeof(def_options[0]) - 4u)) {
            bool has_any = false;
            for (jint i = 0; i < vm_args->nOptions; ++i) {
                if (strstr(vm_args->options[i].optionString, "--inline-max-code-units=") != NULL) {
                    vm_args->options[i].optionString = "--inline-max-code-units=0";
                    has_any = true;
                } //if
            }
            if (!has_any) {
                AKINFO("disabling any inline optimizations...");

                const jint i = vm_args->nOptions;
                memcpy(def_options, vm_args->options, sizeof(def_options[0]) * i);
                def_options[i + 0].optionString = "-Xcompiler-option";
                def_options[i + 1].optionString = "--inline-max-code-units=0";
                def_options[i + 2].optionString = "-Ximage-compiler-option";
                def_options[i + 3].optionString = "--inline-max-code-units=0";
                vm_args->nOptions += 4;
                vm_args->options   = def_options;
            } //if
        } //if
    } //if
}

//-------------------------------------------------------------------------

static void register_native_methods(JNIEnv *env, jclass clazz, jobject loader)
{
    static void(JNICALL *registerFrameworkNatives)(JNIEnv *, jclass, jobject) = [](JNIEnv *env, jclass, jobject classloader) {
        ak_functions->RegisterLibrary(env, classloader);
    };
    static void(JNICALL *makeWorldAccessible)(JNIEnv *, jclass, jstring) = [](JNIEnv *env, jclass, jstring path) {
        ScopedUtfChars cp(env, path);
        if (cp.c_str() == NULL) return;

        AKSELinux::setcon(cp.c_str(), "u:object_r:system_data_file:s0");
        chmod(cp.c_str(), S_IRWXU | S_IRWXG | S_IRWXO);
        if (AKPlatform::sdk_version() > 20) {
            chown(cp.c_str(), AID_EVERYBODY, AID_EVERYBODY);
        } else {
            chown(cp.c_str(), AID_SDCARD_R, AID_INET);
        } //if
    };
    static const JNINativeMethod methods[] = {
        { "makeWorldAccessible", "(Ljava/lang/String;)V", reinterpret_cast<void *>(makeWorldAccessible) }, // MUST be first
        { "registerFrameworkNatives",  "(Ljava/lang/ClassLoader;)V", reinterpret_cast<void *>(registerFrameworkNatives) },
    };
    if (env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0])) < 0 || env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    } //if

    clazz = ak_functions->LoadClass(env, loader, "de/robv/android/xposed/XposedInit", NULL);
    if (clazz != NULL) {
        if (env->RegisterNatives(clazz, methods, 1) < 0 || env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
        } //if
    } //if
}

//-------------------------------------------------------------------------

static jmethodID find_method_nothrow(JNIEnv *env, jclass clazz, const char *name, const char *sig)
{
    jmethodID method = env->GetStaticMethodID(clazz, name, sig);
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();

        method = env->GetMethodID(clazz, name, sig);
        if (env->ExceptionCheck()) {
            env->ExceptionDescribe();
            env->ExceptionClear();
            return NULL;
        } //if
    } //if

    return method;
}

//-------------------------------------------------------------------------

static void register_fork_events(JavaVM *vm, JNIEnv *env, jclass clazz)
{
    static JavaVM   *s_vm;
    static jclass    s_clazz;
    static jmethodID s_callbacks[3];

    s_vm           = vm;
    s_clazz        = static_cast<jclass>(env->NewGlobalRef(clazz));
    s_callbacks[0] = find_method_nothrow(env, clazz, "preFork", "()V");
    s_callbacks[1] = find_method_nothrow(env, clazz, "postForkParent", "()V");
    s_callbacks[2] = find_method_nothrow(env, clazz, "postForkChild", "()V");

    static auto invoke_callback = [](intptr_t k) {
        JNIEnv *env;
        if (s_callbacks[k] != NULL &&
            s_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) {
            env->CallStaticVoidMethod(s_clazz, s_callbacks[k]);
            if (env->ExceptionCheck()) {
                env->ExceptionDescribe();
                env->ExceptionClear();
            } //if
        } //if
    };

    pthread_atfork([]() { // prepare
        invoke_callback(0);
    }, []() { // parent
        invoke_callback(1);
    }, []() { // child
        invoke_callback(2);
    });
}

//-------------------------------------------------------------------------

static jint(JNICALL *JNI_CreateJavaVM_System)(JavaVM **p_vm, JNIEnv **p_env, JavaVMInitArgs *vm_args);
extern "C" jint JNICALL JNI_CreateJavaVM_Impl(JavaVM **p_vm, JNIEnv **p_env, JavaVMInitArgs *vm_args)
{
    AKINFO("p_vm = %p, p_env = %p, vm_args = %p", p_vm, p_env, vm_args);

    disable_inline_optimizing(vm_args);
    jint r = JNI_CreateJavaVM_System(p_vm, p_env, vm_args);

    AKINFO("JavaVM created, vm = %p, env = %p, ret = %d", *p_vm, *p_env, r);

    while (r >= JNI_OK) {
        mkdir(AK_PRIVATE_DIR, S_IRWXU);
        ak_functions->SetCacheDirectory(AK_PRIVATE_DIR);
        if (ak_functions->InitializeOnce(*p_env, *p_vm) != JNI_VERSION_1_6)
            break;

        AKINFO("cache_dir = %s", ak_functions->GetCacheDirectory());
        AKINFO("lib_dir = %s", ak_functions->GetNativeLibsDirectory());

        ScopedLocalFrame frame(*p_env);

        jobject loader = ak_functions->LoadFileDex(*p_env, AK_TMP_DIR "AKCore.apk", NULL, NULL, NULL);
        if (loader == NULL) break;
        
        jclass clazz = ak_functions->LoadClass(*p_env, loader, "ak/core/Main", NULL);
        if (clazz == NULL) break;

        jmethodID method = find_method_nothrow(*p_env, clazz, "onVmCreated", "()V");
        if (method == NULL) break;

        ak_functions->RegisterLibrary(*p_env, loader);
        register_native_methods(*p_env, clazz, loader);
        register_android_util_Log(*p_env);

        (*p_env)->CallStaticVoidMethod(clazz, method);
        if ((*p_env)->ExceptionCheck()) {
            (*p_env)->ExceptionDescribe();
            (*p_env)->ExceptionClear();
        } //if

        register_fork_events(*p_vm, *p_env, clazz);
        break;
    } //if

    return r;
}

//-------------------------------------------------------------------------

static bool should_disable_framewrok()
{
    // If we are booting without the real /data, don't load framework.
    char vold_decrypt[PROP_VALUE_MAX] = {};
    if ((__system_property_get("vold.decrypt", vold_decrypt) > 0) &&
        (strcmp(vold_decrypt, "trigger_restart_min_framework") == 0 || strcmp(vold_decrypt, "1") == 0)) {
        AKINFO("deferring framework loading until after vold decrypt");
        return true;
    } //if

    mkdir(AK_TMP_DIR, S_IRWXU | S_IRWXG | S_IRWXO);

    int fd = TEMP_FAILURE_RETRY(open(AK_GUARD_FILE,
                                     O_RDWR | O_CREAT | O_CLOEXEC | O_SYNC,
                                     S_IRWXU | S_IRWXG | S_IRWXO));
    if (fd == -1) {
        AKERR("error opening %s, errno = %d", AK_GUARD_FILE, errno);
        return true;
    } //if

    flock(fd, LOCK_EX);

    bool should_disable = false;

    int64_t ts[2] = {};
    TEMP_FAILURE_RETRY(read(fd, ts, sizeof(ts)));

    if (static_cast<int64_t>(time(NULL)) - ts[0] <= AK_TIME_LIMIT) {
        should_disable = (++ts[1] >= AK_MAX_RETRIES);
        AKINFO("last = %" PRId64 ", retry = %" PRId64, ts[0], ts[1]);
    } else {
        ts[0] = time(NULL);
        ts[1] = 0;
    } //if

    lseek(fd, 0, SEEK_SET);
    TEMP_FAILURE_RETRY(write(fd, ts, sizeof(ts)));

//  fdatasync(fd);
    flock(fd, LOCK_UN);
    close(fd);

    return should_disable;
}

//-------------------------------------------------------------------------

class static_initializer
{
public:
    static_initializer() {
        AKINFO("-->Starting framework...");

        if (should_disable_framewrok()) {
            AKWARN("framework is temporarily disabled, skipping...");
            return;
        } //if

        void *h = dlopen(__AK_LIB__, RTLD_GLOBAL | RTLD_NOW);
        if (h == NULL) {
            AKERR("dlopen %s failed, error = %s!", __AK_LIB__, dlerror());
            return;
        } //if

        void *q = dlsym(h, __STRING(AKGetInvokeInterface));
        if (q == NULL) {
            AKERR("dlsym %s failed, error = %s!", __STRING(AKGetInvokeInterface), dlerror());
            return;
        } //if

        ak_functions = reinterpret_cast<__typeof__(&AKGetInvokeInterface)>(q)();
        if (ak_functions == NULL) {
            AKERR("ak_functions = %p!", ak_functions);
            return;
        } //if

        AKINFO("ak_functions = %p, version = %" PRIdPTR, ak_functions, ak_functions->version);
        if (ak_functions->version != __AK_VER__) {
            AKERR("ak_functions version mismatched, current = %d!", __AK_VER__);
            return;
        } //if

        void *p = dlsym(RTLD_DEFAULT, __STRING(JNI_CreateJavaVM));
        if (p == NULL) {
            AKERR("dlsym %s failed, error = %s!", __STRING(JNI_CreateJavaVM), dlerror());
            return;
        } //if

        AKINFO("JNI_CreateJavaVM = %p", p);
        ak_functions->HookFunction(p,
                                   reinterpret_cast<void *>(&JNI_CreateJavaVM_Impl),
                                   reinterpret_cast<void **>(&JNI_CreateJavaVM_System));

        AKINFO("-->Framework started.");
    }
};
static static_initializer __s;
// extern "C" void __attribute__((constructor)) __init(void) {}