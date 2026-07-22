package com.ekam.baton.core.data.repository

import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.network.security.AgentSecurityConfigProvider
import com.ekam.baton.core.network.security.AgentSecurityDetails
import org.json.JSONArray
import org.json.JSONObject

class AgentSecurityConfigProviderImpl constructor(
    private val agentDao: AgentDao
) : AgentSecurityConfigProvider {

    override suspend fun getSecurityConfig(agentId: String): AgentSecurityDetails? {
        val agent = agentDao.getAgentById(agentId) ?: return null
        
        return try {
            val json = JSONObject(agent.securityConfig)
            val clientPrivateKeyBase64 = json.optString("client_private_key_enc").takeIf { it.isNotEmpty() }
            val clientPrivateKeyIvBase64 = json.optString("client_private_key_iv").takeIf { it.isNotEmpty() }
            val peerPublicKeyHex = json.optString("peer_public_key").takeIf { it.isNotEmpty() }
            val ratchetStateBase64 = json.optString("ratchet_state").takeIf { it.isNotEmpty() }
            
            val pins = mutableListOf<String>()
            val pinsArray = json.optJSONArray("cert_pins")
            if (pinsArray != null) {
                for (i in 0 until pinsArray.length()) {
                    pins.add(pinsArray.getString(i))
                }
            }

            AgentSecurityDetails(
                securityMode = agent.securityMode,
                clientPrivateKeyBase64 = clientPrivateKeyBase64,
                clientPrivateKeyIvBase64 = clientPrivateKeyIvBase64,
                peerPublicKeyHex = peerPublicKeyHex,
                certificatePins = pins,
                ratchetStateBase64 = ratchetStateBase64
            )
        } catch (e: Exception) {
            AgentSecurityDetails(
                securityMode = agent.securityMode,
                clientPrivateKeyBase64 = null,
                clientPrivateKeyIvBase64 = null,
                peerPublicKeyHex = null,
                certificatePins = emptyList(),
                ratchetStateBase64 = null
            )
        }
    }

    override suspend fun saveRatchetState(agentId: String, stateBase64: String) {
        val agent = agentDao.getAgentById(agentId) ?: return
        try {
            val json = JSONObject(agent.securityConfig)
            json.put("ratchet_state", stateBase64)
            agentDao.upsertAgent(agent.copy(securityConfig = json.toString()))
        } catch (e: Exception) {
            // Ignore JSON parse errors
        }
    }
}
