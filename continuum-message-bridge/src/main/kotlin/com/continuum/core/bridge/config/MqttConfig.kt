package com.continuum.core.bridge.config

import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MqttConfig {

    @Bean
    fun mqttClient(
        @Value("\${continuum.core.bridge.mqtt.server-uri:tcp://localhost:31883}")
        serverURI: String,
        @Value("\${continuum.core.bridge.mqtt.client-id:continuum-message-bridge}")
        mqttClientId: String
    ): MqttClient {
        val options = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = true
            connectionTimeout = 10
        }
        return MqttClient(
            serverURI,
            mqttClientId
        ).also {
            it.connect(options)
        }
    }

}