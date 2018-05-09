/*
 *
 *  @author : Rprop (r_prop@outlook.com)
 *  https://github.com/Rprop/AKFramework
 *
 */
#pragma once
#include <stdlib.h>
#include <stdio.h>
#include <sys/xattr.h>
#include <linux/xattr.h>
#ifndef XATTR_NAME_SELINUX
# define XATTR_NAME_SELINUX "security.selinux"
#endif // !XATTR_NAME_SELINUX
#define INITCONTEXTLEN 255

class AKSELinux
{
public:
    static int getcon(const char *const path, char context[INITCONTEXTLEN + 1]) {
        return getxattr(path, XATTR_NAME_SELINUX, context, INITCONTEXTLEN);
    }
    static int setcon(const char *const path, const char *const context) {
        int rt = setxattr(path, XATTR_NAME_SELINUX, context, strlen(context) + 1, 0);
        if (rt == -1) {
            char command[PATH_MAX];
            snprintf(command, sizeof(command), "chcon %s %s", context, path);
            system(command);
        } //if
        return rt;
    }
};
