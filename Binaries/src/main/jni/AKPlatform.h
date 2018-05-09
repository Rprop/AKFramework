/*
 *
 *  @author : Rprop (r_prop@outlook.com)
 *  https://github.com/Rprop/AKFramework
 *
 */
#pragma once
#include <stdlib.h>
#include <sys/system_properties.h>

class AKPlatform
{
public:
    static int sdk_version() {
        static int SDK_INT = -1;
        if (SDK_INT <= 0) {
            char s[PROP_VALUE_MAX];
            __system_property_get("ro.build.version.sdk", s);
            SDK_INT = atoi(s);
        } //if

        return SDK_INT;
    }
};