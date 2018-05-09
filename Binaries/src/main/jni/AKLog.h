/*
 *
 *  @author : Rprop (r_prop@outlook.com)
 *  https://github.com/Rprop/AKFramework
 *
 */
#pragma once
#include <stdio.h>
#include <android/log.h>

#ifndef AK_TAG
# define AK_TAG __FUNCTION__
#endif // AK_TAG

#ifndef AK_CONSOLE
# define AK_CONSOLE 1
#endif // AK_CONSOLE

#if AK_CONSOLE
# define AKINFO(...) AKLog::__printf(ANDROID_LOG_INFO, AK_TAG, __VA_ARGS__)
# define AKWARN(...) AKLog::__printf(ANDROID_LOG_WARN, AK_TAG, __VA_ARGS__)
# define AKERR(...)  AKLog::__printf(ANDROID_LOG_ERROR, AK_TAG, __VA_ARGS__)
class AKLog
{
public:
    static int __printf(const int _Priority, char const *const _Tag, char const *const _Format, ...) {
        va_list _Args;
        va_start(_Args, _Format);

        char _Buffer[8192];
        int  _Ret = vsprintf(_Buffer, _Format, _Args);

        __android_log_write(_Priority, _Tag, _Buffer);
        fprintf(_Priority >= ANDROID_LOG_ERROR ? stderr : stdout,
                "%s\n", _Buffer);

        va_end(_Args);
        return _Ret;
    }
};
#else
# define AKINFO(...) __android_log_print(ANDROID_LOG_INFO, AK_TAG, __VA_ARGS__)
# define AKWARN(...) __android_log_print(ANDROID_LOG_WARN, AK_TAG, __VA_ARGS__)
# define AKERR(...)  __android_log_print(ANDROID_LOG_ERROR, AK_TAG, __VA_ARGS__)
#endif // AK_CONSOLE

