# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in
# distributed under the License is distributed on an "AS
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
# See the License for the specific language governing permissions
# limitations under the License.
#
#
LOCAL_PATH := $(call my-dir)
ROOT_PATH := $(LOCAL_PATH)
########################################################
## libancillary
########################################################
include $(CLEAR_VARS)
ANCILLARY_SOURCE := fd_recv.c fd_send.c
LOCAL_MODULE := libancillary
LOCAL_C_INCLUDES := $(LOCAL_PATH)/libancillary
LOCAL_SRC_FILES := $(addprefix libancillary/, $(ANCILLARY_SOURCE))
include $(BUILD_STATIC_LIBRARY)
########################################################
## tun2socks
########################################################
include $(CLEAR_VARS)
LOCAL_CFLAGS := -std=gnu99
LOCAL_CFLAGS += -DBADVPN_THREADWORK_USE_PTHREAD -DBADVPN_LINUX -DBADVPN_BREACTOR_BADVPN -D_GNU_SOURCE
LOCAL_CFLAGS += -DBADVPN_USE_SIGNALFD -DBADVPN_USE_EPOLL
LOCAL_CFLAGS += -DBADVPN_LITTLE_ENDIAN -DBADVPN_THREAD_SAFE
LOCAL_CFLAGS += -DNDEBUG -DANDROID
LOCAL_C_INCLUDES := \
$(LOCAL_PATH)/badvpn/libancillary \
$(LOCAL_PATH)/badvpn/lwip/src/include/ipv4 \
$(LOCAL_PATH)/badvpn/lwip/src/include/ipv6 \
$(LOCAL_PATH)/badvpn/lwip/src/include \
$(LOCAL_PATH)/badvpn/lwip/custom \
$(LOCAL_PATH)/badvpn \
$(LOCAL_PATH)/libancillary
TUN2SOCKS_SOURCES := \
base/BLog_syslog.c \
system/BReactor_badvpn.c \
system/BSignal.c \
system/BConnection_common.c \
system/BConnection_unix.c \
system/BTime.c \
system/BUnixSignal.c \
system/BNetwork.c \
system/BDatagram_common.c \
system/BDatagram_unix.c \
flow/StreamRecvInterface.c \
flow/PacketRecvInterface.c \
