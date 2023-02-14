# Created by @blackier, base on:
#   https://github.com/shadowsocks/shadowsocks-android/blob/master/core/src/main/jni/Android.mk
cmake_minimum_required(VERSION 3.4.1)
project(V2rayNG)

########################################################
## tun2socks
########################################################

add_definitions(-std=gnu99)
add_definitions(-DBADVPN_THREADWORK_USE_PTHREAD -DBADVPN_LINUX -DBADVPN_BREACTOR_BADVPN -D_GNU_SOURCE)
add_definitions(-DBADVPN_USE_SIGNALFD -DBADVPN_USE_EPOLL)
add_definitions(-DBADVPN_LITTLE_ENDIAN -DBADVPN_THREAD_SAFE)
add_definitions(-DNDEBUG -DANDROID)
add_definitions(-Wno-unused-value -Wno-parentheses)

set(LIBNETTLE_SOURCES
    badvpn/base/BLog_syslog.c
    badvpn/system/BReactor_badvpn.c
    badvpn/system/BSignal.c
    badvpn/system/BConnection_common.c
    badvpn/system/BConnection_unix.c
    badvpn/system/BTime.c
    badvpn/system/BUnixSignal.c
    badvpn/system/BNetwork.c
    badvpn/flow/StreamRecvInterface.c
    badvpn/flow/PacketRecvInterface.c
    badvpn/flow/PacketPassInterface.c
    badvpn/flow/StreamPassInterface.c
    badvpn/flow/SinglePacketBuffer.c
    badvpn/flow/BufferWriter.c
    badvpn/flow/PacketBuffer.c
    badvpn/flow/PacketStreamSender.c
    badvpn/flow/PacketPassConnector.c
    badvpn/flow/PacketProtoFlow.c
    badvpn/flow/PacketPassFairQueue.c
    badvpn/flow/PacketProtoEncoder.c
    badvpn/flow/PacketProtoDecoder.c
    badvpn/socksclient/BSocksClient.c
    badvpn/tuntap/BTap.c
    badvpn/lwip/src/core/udp.c
    badvpn/lwip/src/core/memp.c
    badvpn/lwip/src/core/init.c
    badvpn/lwip/src/core/pbuf.c
    badvpn/lwip/src/core/tcp.c
    badvpn/lwip/src/core/tcp_out.c
    badvpn/lwip/src/core/netif.c
    badvpn/lwip/src/core/def.c
    badvpn/lwip/src/core/ip.c
    badvpn/lwip/src/core/mem.c
    badvpn/lwip/src/core/tcp_in.c
    badvpn/lwip/src/core/stats.c
    badvpn/lwip/src/core/inet_chksum.c
    badvpn/lwip/src/core/timeouts.c
    badvpn/lwip/src/core/ipv4/icmp.c
    badvpn/lwip/src/core/ipv4/igmp.c
    badvpn/lwip/src/core/ipv4/ip4_addr.c
    badvpn/lwip/src/core/ipv4/ip4_frag.c
    badvpn/lwip/src/core/ipv4/ip4.c
    badvpn/lwip/src/core/ipv4/autoip.c
    badvpn/lwip/src/core/ipv6/ethip6.c
    badvpn/lwip/src/core/ipv6/inet6.c
    badvpn/lwip/src/core/ipv6/ip6_addr.c
    badvpn/lwip/src/core/ipv6/mld6.c
    badvpn/lwip/src/core/ipv6/dhcp6.c
    badvpn/lwip/src/core/ipv6/icmp6.c
    badvpn/lwip/src/core/ipv6/ip6.c
    badvpn/lwip/src/core/ipv6/ip6_frag.c
    badvpn/lwip/src/core/ipv6/nd6.c
    badvpn/lwip/custom/sys.c
    badvpn/tun2socks/tun2socks.c
    badvpn/base/DebugObject.c
    badvpn/base/BLog.c
    badvpn/base/BPending.c
    badvpn/system/BDatagram_unix.c
    badvpn/flowextra/PacketPassInactivityMonitor.c
    badvpn/tun2socks/SocksUdpGwClient.c
    badvpn/udpgw_client/UdpGwClient.c

    libancillary/fd_recv.c
    libancillary/fd_send.c
)

set(LIBNETTLE_INCLUDES
    ${CMAKE_CURRENT_SOURCE_DIR}
    libancillary/
    badvpn/
    badvpn/lwip/custom
    badvpn/lwip/src/include
    badvpn/lwip/src/include/ipv4
    badvpn/lwip/src/include/ipv6
)

# exe but .so, it will be include to libs :)
add_executable(libtun2socks.so ${LIBNETTLE_SOURCES})
target_include_directories(libtun2socks.so PUBLIC ${LIBNETTLE_INCLUDES})
target_compile_definitions(libtun2socks.so PRIVATE ${LIBNETTLE_DEFINITIONS})
target_link_libraries(libtun2socks.so dl log)
