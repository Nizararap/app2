LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE            := libdobby
LOCAL_SRC_FILES         := Tam/Tools/Dobby/libraries/$(TARGET_ARCH_ABI)/libdobby.a
LOCAL_EXPORT_C_INCLUDES := $(LOCAL_PATH)/Tam/Tools/Dobby/
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)

LOCAL_MODULE   := dexkit
LOCAL_CFLAGS   := -fvisibility=hidden -ffunction-sections -fdata-sections -w \
                  -fno-rtti -fno-exceptions -fpermissive
LOCAL_CPPFLAGS := -fvisibility=hidden -ffunction-sections -fdata-sections -w -s -std=c++17 \
                  -fms-extensions -fno-rtti -fno-exceptions -fpermissive
LOCAL_LDFLAGS  += -Wl,--gc-sections,--strip-all -llog

# EGL dan GLES dihapus dari LDLIBS karena kita tidak render UI lagi
LOCAL_LDLIBS   := -llog -landroid -lz

LOCAL_C_INCLUDES := $(LOCAL_PATH)
LOCAL_C_INCLUDES += $(LOCAL_PATH)/KittyMemory

FILE_LIST  := $(wildcard $(LOCAL_PATH)/Tam/IL2CppSDKGenerator/*.c*)
FILE_LIST  += $(wildcard $(LOCAL_PATH)/Tam/Tools/*.c*)
FILE_LIST  += $(wildcard $(LOCAL_PATH)/KittyMemory/*.c*)
FILE_LIST  += $(wildcard $(LOCAL_PATH)/*.c*)

LOCAL_SRC_FILES        := $(FILE_LIST:$(LOCAL_PATH)/%=%)
LOCAL_STATIC_LIBRARIES := libdobby
LOCAL_CPP_FEATURES     := exceptions

include $(BUILD_SHARED_LIBRARY)