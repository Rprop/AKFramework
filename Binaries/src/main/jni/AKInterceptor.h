/*
 *
 *  @author : Rprop (r_prop@outlook.com)
 *  https://github.com/Rprop/AKFramework
 *
 */
#pragma once
#include <stdio.h>
#include <stdlib.h>
#include <errno.h>
#include <fcntl.h>
#include <elf.h>
#include <assert.h>
#include <limits.h>
#include <linux/elf.h>
#include <linux/stat.h>
#include <link.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <sys/mount.h>
#include "AKPlatform.h"
#include "AKSELinux.h"
#include "AKLog.h"

#define  __BIN_DIR__      "/system/bin/"
#ifdef __LP64__
# define __LIB_DIR__      "/system/lib64/"
# define __TARGET__       __BIN_DIR__ "app_process64"
# define __ELFCLASS__     ELFCLASS64
#else
# define __LIB_DIR__      "/system/lib/"
# define __TARGET__       __BIN_DIR__ "app_process32"
# define __ELFCLASS__     ELFCLASS32
#endif // __LP64__
#define  __TMP_SUFFIX__   "_tmp"
#define  __BAK_SUFFIX__   "_bak"
#define  __TARGET_TMP__   __TARGET__ __TMP_SUFFIX__
#define  __TARGET_BAK__   __TARGET__ __BAK_SUFFIX__
#define  __GADGET_PATH__  __LIB_DIR__ __GADGET__
#if !defined(__GADGET__) || !defined(__AK_LIB__)
# error  __GADGET__ __AK_LIB__
# define __GADGET__       "android_runtime.so"
# define __AK_LIB__       "libAK.so"
#else
  static_assert(__GADGET__[0] == 'a' && __GADGET__[1] == 'n', __GADGET__);
#endif //__GADGET__

/*
 *  Static injection based on modifying the ELF format
 */
class AKInterceptor
{
private:
#ifdef LIEF_ELF_SUPPORT
    static int inject_elf(LIEF::ELF::Binary *binary_) {
        // @TODO
        binary_->add_library(__GADGET__);
        binary_->write(__TARGET_TMP__);

        return 0;
    }
#else
    static int inject_elf(bool inst = true) {
        static char buffer[256U * 1024U] = {}; // 256kb

        int fd = TEMP_FAILURE_RETRY(open(__TARGET__, O_RDONLY, NULL));
        int sz = TEMP_FAILURE_RETRY(read(fd, buffer, sizeof(buffer)));
        close(fd);

        auto ehdr = reinterpret_cast<ElfW(Ehdr) *>(buffer);
        auto phdr = reinterpret_cast<ElfW(Phdr) *>(buffer + ehdr->e_phoff);
        if (ehdr->e_ident[EI_CLASS] != __ELFCLASS__ ||
            ehdr->e_ident[EI_DATA] != ELFDATA2LSB) {
            AKERR("unexpected elf format! target = %s, size = %d", __TARGET__, sz);
            return ELIBBAD;
        } //if
        
        int pn = 0;
        while (phdr->p_type != PT_DYNAMIC) {
            ++phdr;
            if (++pn >= ehdr->e_phnum) {
                AKERR("failed to locate PT_DYNAMIC segment! target = %s, size = %d", __TARGET__, sz);
                return ELIBBAD;
            } //if
        }

        auto dyn = reinterpret_cast<ElfW(Dyn) *>(buffer + phdr->p_offset);
        do {
            if (dyn->d_tag == DT_STRTAB) {
                auto strtab = buffer + dyn->d_un.d_ptr;
                auto liboff = UINTPTR_MAX;
                auto dbgdyn = dyn - 1;
                auto gaddyn = dyn - 1;
                dyn = reinterpret_cast<ElfW(Dyn) *>(buffer + phdr->p_offset);
                do {
                    switch (dyn->d_tag) {
                    case DT_NEEDED: {
                            auto so_name = strtab + dyn->d_un.d_val;
                            if (strcmp(so_name, __GADGET__) == 0) {
                                if (inst) {
                                    AKERR("framework is already installed!");
                                    return EEXIST;
                                } //if
                                gaddyn = dyn;
                            } else if (strcmp(so_name + 3U, __GADGET__) == 0) {
                                liboff = dyn->d_un.d_val + 3U;
                            } //if
                        }
                        break;
                    case DT_DEBUG:
                        dbgdyn = dyn;
                        break;
                    }
                } while ((++dyn)->d_tag != DT_NULL);

                // modify ELF in place
                if (inst) {
                    if (liboff == UINTPTR_MAX || dbgdyn == dyn - 1) {
                        AKERR("no proper injection point found! gadget = %s", __GADGET__);
                        return ENOTSUP;
                    } //if

                    dbgdyn->d_tag      = DT_NEEDED;
                    dbgdyn->d_un.d_val = liboff;
                } else {
                    if (gaddyn == dyn - 1) {
                        AKERR("framework was not installed!");
                        return EINVAL;
                    } //if

                    gaddyn->d_tag      = DT_DEBUG;
                    gaddyn->d_un.d_val = 0u;
                } //if

                dyn = NULL;
                break;
            } //if
        } while ((++dyn)->d_tag != DT_NULL);
        if (dyn != NULL) {
            AKERR("failed to locate DT_STRTAB entry! target = %s, size = %d", __TARGET__, sz);
            return ENOTSUP;
        } //if

        fd = TEMP_FAILURE_RETRY(open(__TARGET_TMP__,
                                     O_CREAT | O_CLOEXEC | O_WRONLY | O_TRUNC,
                                     S_IRWXU | S_IRGRP | S_IXGRP | S_IROTH | S_IXOTH));
        if (fd == -1) {
            AKERR("error writing %s! errno = %d", __TARGET_TMP__, errno);
            return errno;
        } //if
        
        TEMP_FAILURE_RETRY(write(fd, buffer, sz));
        close(fd);

        return 0;
    }
#endif // LIEF_ELF_SUPPORT

private:
    static int elf_check(const char *const path) {
        char command[PATH_MAX];
        strcpy(command, path);
        strcat(command, AKPlatform::sdk_version() > 20 ? " -showversion" : " -help / /");

        int rt = system(command);
        return WIFEXITED(rt) ? WEXITSTATUS(rt) : rt;
    }
    static int copy_file(const char *src, const char *dst) {
        int sd = TEMP_FAILURE_RETRY(open(src, O_RDONLY | O_CLOEXEC, 0));
        if (sd == -1) {
            AKERR("error opening %s! errno = %d", src, errno);
            return errno;
        } //if

        // shall fail if the file exists
        int dd = TEMP_FAILURE_RETRY(open(dst, O_CREAT | O_CLOEXEC | O_WRONLY | O_EXCL,
                                         S_IRWXU | S_IRGRP | S_IXGRP | S_IROTH | S_IXOTH));
        if (dd == -1) {
            AKERR("error creating %s! errno = %d", dst, errno);
            return errno;
        } //if

        char buffer[1024 * 1024];
        int r;
        while ((r = TEMP_FAILURE_RETRY(read(sd, buffer, sizeof(buffer)))) > 0) {
            r = TEMP_FAILURE_RETRY(write(dd, buffer, r));
            if (r < 0) break;
        }
        if (r < 0) {
            r = errno;
            AKERR("error reading %s or writing %s! errno = %d", src, dst, r);
        } else {
            r = 0;
        } //if

        close(dd);
        close(sd);
        return r;
    }

public:
    static int preverify() {
        AKINFO("-->Verifying environment and performing prerequisite checks...");
#ifndef __LP64__
        if (AKPlatform::sdk_version() <= 20) {
            // that's ok as if newpath exists it will not be overwritten
            symlink(__BIN_DIR__ "app_process", __TARGET__);
        } //if
#endif // __LP64__

        if (getuid() != 0) {
            AKERR("failed to detect root privileges! uid = %u", getuid());
            return EACCES;
        } //if

        // test file system
        int fd, rt = 0;
        do {
            fd = TEMP_FAILURE_RETRY(open(__TARGET_TMP__,
                                         O_CREAT | O_CLOEXEC | O_WRONLY | O_TRUNC,
                                         S_IRWXU | S_IRGRP | S_IXGRP | S_IROTH | S_IXOTH));
            if (fd == -1) {
                if (rt != 0) {
                    AKERR("error writing %s: read-only filesystem? errno = %d", __TARGET_TMP__, errno);
                    return EROFS;
                } //if

                if (errno != EROFS) {
                    AKERR("unexpected errno %d, should be %d", errno, EROFS);
                } //if

                // mount("/dev/block/sda6", "/system", "ext4", O_RDWR | MS_REMOUNT, "")
                rt = system("mount -o rw,remount /system");
                if (rt != -1) rt = WEXITSTATUS(rt);
                if (rt != 0) {
                    AKERR("failed to remount /system! exit = %d, errno = %d", rt, errno);
                } //if

                // try again
                rt = -1;
            } else {
                close(fd);
                unlink(__TARGET_TMP__);
                break;
            } //if
        } while (true);
        assert(fd != -1);

        AKINFO("environment is successfully preverified!");
        return 0;
    }
    static int install() {
        AKINFO("-->Installing and configuring framework...");

        typedef struct stat stat_t;
        stat_t statbuf;
        if (stat(__TARGET__, &statbuf) == -1) {
            AKERR("stat %s failed! errno = %d", __TARGET__, errno);
            return errno;
        } //if

        int rt = elf_check(__TARGET__);
        if (rt != 0) {
            AKERR("failed to execute %s! exit = %d, errno = %d", __TARGET__, rt, errno);
            return ENOTSUP;
        } //if

#ifdef LIEF_ELF_SUPPORT
        LIEF::ELF::Binary *binary_ = NULL;
        try {
            binary_ = LIEF::ELF::Parser::parse(__TARGET__);
            if (binary_->has_library(__GADGET__)) {
                AKERR("framework is already installed!");
                rt = EEXIST;
            } else {
                rt = inject_elf(binary_);
            } //if
        } catch (const LIEF::exception &e) {
            AKERR("error: %s", e.what());
            rt = ELIBBAD;
        }
        if (binary_ != NULL) binary_->release();
#else
        rt = inject_elf();
#endif // LIEF_ELF_SUPPORT
        if (rt != 0) return rt;

        // setting proper ownership
        if (chown(__TARGET_TMP__, statbuf.st_uid, statbuf.st_gid) == -1) {
            AKERR("chown %s failed! errno = %d", __TARGET_TMP__, errno);
            return errno;
        } //if

        // setting proper permissions
        if (chmod(__TARGET_TMP__, S_IRWXU | S_IRGRP | S_IXGRP | S_IROTH | S_IXOTH) == -1) {
            AKERR("chmod %s failed! errno = %d", __TARGET_TMP__, errno);
            return errno;
        } //if

        // removing old gadget and its dependencies
        unlink(__GADGET_PATH__);
        unlink(__LIB_DIR__ __AK_LIB__);

        // pre check
        rt = elf_check(__TARGET_TMP__);
        if (rt == 0) { // should be something like `library __GADGET__ not found`
            AKERR("%s may be corrupted! exit = %d, errno = %d", __TARGET_TMP__, rt, errno);
            return ELIBBAD;
        } //if

        // copying new files
        chdir("/data/local/tmp");
        if ((rt = copy_file(__GADGET__, __GADGET_PATH__)) != 0 ||
            (rt = copy_file(__AK_LIB__, __LIB_DIR__ __AK_LIB__)) != 0) {
            char pat[1234];
            AKINFO("%s", getcwd(pat, 1234));
            return rt;
        } //if

        // post check
        rt = elf_check(__TARGET_TMP__);
        if (rt != 0) {
            AKERR("%s was corrupted! exit = %d, errno = %d", __TARGET_TMP__, rt, errno);
            return ELIBBAD;
        } //if

        // expand all symbolic links
        char __REAL_TARGET__[PATH_MAX] = __TARGET__;
        if (realpath(__TARGET__, __REAL_TARGET__) == NULL) {
            AKERR("realpath %s! errno = %d", __TARGET__, errno);
            return errno;
        } //if

        // backup
        unlink(__TARGET_BAK__);
        if (link(__REAL_TARGET__, __TARGET_BAK__) == -1) {
            AKERR("link %s to %s failed! errno = %d", __REAL_TARGET__, __TARGET_BAK__, errno);
            return errno;
        } //if

        // critical operations
        unlink(__REAL_TARGET__);
        if (rename(__TARGET_TMP__, __REAL_TARGET__) == -1) {
            // recovery
            if (rename(__TARGET_BAK__, __REAL_TARGET__) == -1) {
                link(__TARGET_BAK__, __REAL_TARGET__);
            } //if

            AKERR("rename %s to %s failed! errno = %d", __TARGET_TMP__, __REAL_TARGET__, errno);
            return errno;
        } //if

        // selinux
        AKSELinux::setcon(__REAL_TARGET__, "u:object_r:zygote_exec:s0");

        AKINFO("framework installed successfully!");
        return 0;
    }
    static int uninstall() {
        AKINFO("-->Preparing for removal of framework...");

        int rt = elf_check(__TARGET_BAK__);
        if (rt != 0) {
            AKERR("failed to execute %s! exit = %d, errno = %d", __TARGET_BAK__, rt, errno);
            return ENOTSUP;
        } //if
        
        // expand all symbolic links
        char __REAL_TARGET__[PATH_MAX] = __TARGET__;
        if (realpath(__TARGET__, __REAL_TARGET__) == NULL) {
            AKERR("realpath %s! errno = %d", __TARGET__, errno);
            return errno;
        } //if

        // critical operations
        if (rename(__TARGET_BAK__, __REAL_TARGET__) != -1) {
            unlink(__GADGET_PATH__);
            unlink(__LIB_DIR__ __AK_LIB__);
        } else {
            AKERR("rename %s to %s failed! errno = %d", __TARGET_BAK__, __REAL_TARGET__, errno);
            return errno;
        } //if

        // selinux
        AKSELinux::setcon(__REAL_TARGET__, "u:object_r:zygote_exec:s0");

        AKINFO("framework has been successfully uninstalled!");
        return 0;
    }
};