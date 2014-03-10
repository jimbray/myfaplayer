
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_ARM_MODE := arm
ifeq ($(BUILD_WITH_NEON),1)
LOCAL_ARM_NEON := true
endif

LOCAL_MODULE := pixman

LOCAL_CFLAGS += \
    -DHAVE_CONFIG_H \
    -DPIXMAN_NO_TLS

LOCAL_C_INCLUDES += \
    $(LOCAL_PATH) \
    $(LOCAL_PATH)/pixman

LOCAL_SRC_FILES := \
    pixman/pixman-access-accessors.c \
    pixman/pixman-access.c \
    pixman/pixman-bits-image.c \
    pixman/pixman-combine32.c \
    pixman/pixman-combine64.c \
    pixman/pixman-conical-gradient.c \
    pixman/pixman-cpu.c \
    pixman/pixman-edge-accessors.c \
    pixman/pixman-edge.c \
    pixman/pixman-fast-path.c \
    pixman/pixman-general.c \
    pixman/pixman-gradient-walker.c \
    pixman/pixman-image.c \
    pixman/pixman-implementation.c \
    pixman/pixman-linear-gradient.c \
    pixman/pixman-matrix.c \
    pixman/pixman-radial-gradient.c \
    pixman/pixman-region16.c \
    pixman/pixman-region32.c \
    pixman/pixman-solid-fill.c \
    pixman/pixman-timer.c \
    pixman/pixman-trap.c \
    pixman/pixman-utils.c \
    pixman/pixman.c

ifeq ($(BUILD_WITH_NEON),1)
LOCAL_CFLAGS += -DUSE_ARM_NEON
LOCAL_SRC_FILES += \
    pixman/pixman-arm-neon.c \
    pixman/pixman-arm-neon-asm.S \
    pixman/pixman-arm-neon-asm-bilinear.S
else
LOCAL_CFLAGS += -DUSE_ARM_SIMD
LOCAL_SRC_FILES += \
    pixman/pixman-arm-simd.c \
    pixman/pixman-arm-simd-asm.S
endif

include $(BUILD_STATIC_LIBRARY)

