package com.obelus.data.service

import android.content.Context
import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MqttTelemetryService @Inject constructor() {

    private var mqttClient: MqttAsyncClient? = null
    private val serverUri = "tcp://broker.hivemq.com:1883" // Example public broker
    private val TAG = "MqttTelemetry"

    fun connect(context: Context, clientId: String = "Obelus_User_${System.currentTimeMillis()}") {
        try {
            mqttClient = MqttAsyncClient(serverUri, clientId, MemoryPersistence())
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                keepAliveInterval = 60
                connectionTimeout = 30
            }

            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {
                    Log.d(TAG, "Connected to MQTT Broker")
                }

                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    Log.e(TAG, "Failed to connect to MQTT: ${exception?.message}")
                }
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun publishTelemetry(topic: String, message: String) {
        if (mqttClient?.isConnected == true) {
            val mqttMessage = MqttMessage(message.toByteArray()).apply {
                qos = 1
            }
            mqttClient?.publish(topic, mqttMessage)
        }
    }

    fun disconnect() {
        try {
            mqttClient?.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
