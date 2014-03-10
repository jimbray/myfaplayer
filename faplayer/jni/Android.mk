
LOCAL_PATH := $(call my-dir)

BUILD_WITH_NEON := 1

# XXX: fix cortex-a9
ifeq ($(APP_ABI),armeabi)
BUILD_WITH_NEON := 0
endif

VLCROOT := $(LOCAL_PATH)/vlc
EXTROOT := $(LOCAL_PATH)/ext

include $(CLEAR_VARS)

include $(call all-makefiles-under,$(LOCAL_PATH))

