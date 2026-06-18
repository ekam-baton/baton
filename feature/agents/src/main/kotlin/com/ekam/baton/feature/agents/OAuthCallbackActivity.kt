package com.ekam.baton.feature.agents

import androidx.activity.ComponentActivity
import android.os.Bundle
import android.widget.Toast
import com.ekam.baton.core.network.auth.OAuthFlowManager
import com.ekam.baton.core.data.repository.AgentRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class OAuthCallbackActivity : ComponentActivity() {

    @Inject
    lateinit var oauthFlowManager: OAuthFlowManager

    @Inject
    lateinit var agentRepository: AgentRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent.data
        if (uri != null && uri.scheme == "com.ekam.baton" && uri.host == "oauth" && uri.path == "/callback") {
            val code = uri.getQueryParameter("code")
            val state = uri.getQueryParameter("state")
            val error = uri.getQueryParameter("error")

            if (error != null) {
                Toast.makeText(this, "OAuth Error: $error", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            if (code != null && state != null) {
                val agentId = oauthFlowManager.getAgentIdByState(state)
                val config = oauthFlowManager.getConfigByState(state)

                if (agentId != null && config != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        val result = oauthFlowManager.handleCallback(agentId, config, code, state)
                        
                        if (result.isSuccess) {
                            // Update agent status in DB
                            val agent = agentRepository.getAgentById(agentId)
                            if (agent != null) {
                                agentRepository.upsertAgent(
                                    agent.copy(
                                        isAuthenticated = true,
                                        lastAuthAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        }

                        withContext(Dispatchers.Main) {
                            if (result.isSuccess) {
                                Toast.makeText(this@OAuthCallbackActivity, "Authentication successful!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@OAuthCallbackActivity, "Authentication failed.", Toast.LENGTH_SHORT).show()
                            }
                            finish()
                        }
                    }
                } else {
                    Toast.makeText(this, "Invalid state or flow expired.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } else {
                finish()
            }
        } else {
            finish()
        }
    }
}
