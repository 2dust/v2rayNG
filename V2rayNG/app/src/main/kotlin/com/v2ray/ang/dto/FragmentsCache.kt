package com.v2ray.ang.dto
import com.v2ray.ang.dto.V2rayConfig.OutboundBean.OutSettingsBean.FragmentBean
data class FragmentsCache(val fragmentBean: FragmentBean,
                          var ping: Long = -2)