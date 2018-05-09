/*
 *
 *  @author : Rprop (r_prop@outlook.com)
 *  https://github.com/Rprop/AKFramework
 *
 */
#define AK_TAG "AKInstaller"
#include <AndHook.h>
#include "AKInterceptor.h"

int main(int argc, char *argv[])
{
    if (argc == 2) {
        if (strcmp(argv[1], "install") == 0) {
            int rt = AKInterceptor::preverify();
            if (rt == 0) 
                rt = AKInterceptor::install();
            return rt;
        } else if (strcmp(argv[1], "uninstall") == 0) {
            int rt = AKInterceptor::preverify();
            if (rt == 0)
                rt = AKInterceptor::uninstall();
            return rt;
        } else if (strcmp(argv[1], "reinstall") == 0) {
            int rt = AKInterceptor::preverify();
            if (rt == 0) {
                AKInterceptor::uninstall();
                rt = AKInterceptor::install();
            } //if
            return rt;
        } else if (strcmp(argv[1], "preverify") == 0) {
            return AKInterceptor::preverify();
        } //if
    } //if

    AKINFO("AKFramework installer v1.0.%d\n\nUsage: %s [install|uninstall|reinstall|preverify]\n",
           __AK_VER__, argv[0]);

    return 0;
}