package com.example.carelyo.agent.infra

// Standard message payload simulating formal agent communication protocols
data class CarelyoMessage(
    val sender: String,
    val receiver: String,
    val messageType: String,
    val content: Map<String, Any?>
)

interface CarelyoAgent {
    val agentName: String
    fun processIncomingMessage(message: CarelyoMessage)
}

object CarelyoMessageBroker {
    private val activeAgents = mutableMapOf<String, CarelyoAgent>()

    fun registerAgent(agent: CarelyoAgent) {
        activeAgents[agent.agentName] = agent
    }

    fun unregisterAgent(agentName: String) {
        activeAgents.remove(agentName)
    }

    fun passMessage(message: CarelyoMessage) {
        if (message.receiver == "BROADCAST") {
            activeAgents.values.forEach { agent ->
                if (agent.agentName != message.sender) {
                    agent.processIncomingMessage(message)
                }
            }
        } else {
            activeAgents[message.receiver]?.processIncomingMessage(message)
        }
    }
}