LOCAL_PATH := $(call my-dir)
AK_GADGET  := android_runtime
AK_VERSION := 20180505

include $(CLEAR_VARS)
LOCAL_MODULE            := ak_installer
LOCAL_SRC_FILES         := AKInstaller.cpp
LOCAL_C_INCLUDES        := $(LOCAL_PATH)
LOCAL_C_INCLUDES        += $(LOCAL_PATH)/AK/include
LOCAL_CFLAGS            := -D__GADGET__="""$(AK_GADGET)$(TARGET_SONAME_EXTENSION)"""
LOCAL_CFLAGS            += -D__AK_LIB__="""libAK$(TARGET_SONAME_EXTENSION)""" -D__AK_VER__=$(AK_VERSION)
LOCAL_LDLIBS            := -llog
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE            := $(AK_GADGET)
LOCAL_SRC_FILES         := AKGadget.cpp
LOCAL_C_INCLUDES        := $(LOCAL_PATH)
LOCAL_C_INCLUDES        += $(LOCAL_PATH)/AK/include
LOCAL_CFLAGS            := -D__AK_LIB__="""libAK$(TARGET_SONAME_EXTENSION)""" -D__AK_VER__=$(AK_VERSION)
LOCAL_LDFLAGS           := -Wl,-soname,$(AK_GADGET).""so
LOCAL_MODULE_FILENAME   := $(AK_GADGET)
LOCAL_LDLIBS            := -llog
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE            := copy_dependencies
LOCAL_SRC_FILES         := AKCopyDependencies.cpp
LOCAL_SHARED_LIBRARIES  := AK
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE            := AK
LOCAL_SRC_FILES         := $(LOCAL_PATH)/AK/lib/$(TARGET_ARCH_ABI)/lib$(LOCAL_MODULE)$(TARGET_SONAME_EXTENSION)
include $(PREBUILT_SHARED_LIBRARY)