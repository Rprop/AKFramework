/* //device/libs/android_runtime/android_util_Log.cpp
**
** Copyright 2006, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/
#pragma once
#include <android/log.h>
#define LOGGER_ENTRY_MAX_PAYLOAD 4068

enum {
    LOG_ID_MIN = 0,

    LOG_ID_MAIN = 0,
    LOG_ID_RADIO = 1,
    LOG_ID_EVENTS = 2,
    LOG_ID_SYSTEM = 3,
    LOG_ID_CRASH = 4,

    LOG_ID_MAX
};

static jboolean isLoggable(const char* tag, jint level) {
    return JNI_TRUE;
}

static jboolean android_util_Log_isLoggable(JNIEnv* env, jobject clazz, jstring tag, jint level) {
    if (tag == NULL) {
        return false;
    }

    const char* chars = env->GetStringUTFChars(tag, NULL);
    if (!chars) {
        return false;
    }

    jboolean result = isLoggable(chars, level);

    env->ReleaseStringUTFChars(tag, chars);
    return result;
}

/*
 * In class android.util.Log:
 *  public static native int println_native(int buffer, int priority, String tag, String msg)
 */
static jint android_util_Log_println_native(JNIEnv* env, jobject clazz,
        jint bufID, jint priority, jstring tagObj, jstring msgObj) {
    const char* tag = NULL;
    const char* msg = NULL;

    if (msgObj == NULL) {
        // println needs a message
        return -1;
    }

    if (bufID < 0 || bufID >= LOG_ID_MAX) {
        // bad bufID
        return -1;
    }

    if (tagObj != NULL)
        tag = env->GetStringUTFChars(tagObj, NULL);
    msg = env->GetStringUTFChars(msgObj, NULL);

    int res = __android_log_write(/*bufID, */(android_LogPriority)priority, tag, msg);

    if (tag != NULL)
        env->ReleaseStringUTFChars(tagObj, tag);
    env->ReleaseStringUTFChars(msgObj, msg);

    return res;
}

/*
 * In class android.util.Log:
 *  private static native int logger_entry_max_payload_native()
 */
static jint android_util_Log_logger_entry_max_payload_native(JNIEnv *, jobject)
{
    return static_cast<jint>(LOGGER_ENTRY_MAX_PAYLOAD);
}

/*
 * JNI registration.
 */
static const JNINativeMethod gMethods[] = {
    /* name, signature, funcPtr */
    { "isLoggable",      "(Ljava/lang/String;I)Z", (void*) android_util_Log_isLoggable },
    { "println_native",  "(IILjava/lang/String;Ljava/lang/String;)I", (void*) android_util_Log_println_native }
};
static const JNINativeMethod gMethods_N[] = {
    /* name, signature, funcPtr */
    { "logger_entry_max_payload_native",  "()I", (void*) android_util_Log_logger_entry_max_payload_native }
};
static void register_android_util_Log(JNIEnv *env)
{
    jclass clazz = env->FindClass("android/util/Log");
    if (env->RegisterNatives(clazz, gMethods, 2) >= 0) {
        env->RegisterNatives(clazz, gMethods_N, 1);
    } //if

    env->ExceptionClear();
}