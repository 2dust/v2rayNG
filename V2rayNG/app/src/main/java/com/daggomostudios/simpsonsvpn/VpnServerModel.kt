package com.daggomostudios.simpsonsvpn

import com.google.gson.annotations.SerializedName

data class VpnServerModel(
    @SerializedName("nome")
    val nome: String,
    
    @SerializedName("protocolo")
    val protocolo: String,
    
    @SerializedName("config")
    val config: String
)

data class VpnConfigResponse(
    @SerializedName("versao")
    val versao: Int,
    
    @SerializedName("lista_servidores")
    val listaServidores: List<VpnServerModel>
)
