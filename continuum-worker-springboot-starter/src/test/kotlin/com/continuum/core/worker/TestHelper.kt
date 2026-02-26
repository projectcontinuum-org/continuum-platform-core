package com.continuum.core.worker

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.stream.binder.test.InputDestination
import org.springframework.cloud.stream.binder.test.OutputDestination
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment
import org.springframework.messaging.Message
import org.springframework.stereotype.Component

@Component
@Import(TestChannelBinderConfiguration::class)
class TestHelper(
  private val environment: Environment
) {

  @Autowired
  lateinit var inputDestination: InputDestination

  @Autowired
  lateinit var outputDestination: OutputDestination

  private fun getDestination(channelName: String) =
    environment.getProperty("spring.cloud.stream.bindings.${channelName}.destination")

  fun sendMessage(message: Message<*>, channelName: String) {
    inputDestination.send(message, getDestination("${channelName}-in-0"))
  }

  fun receiveAll(channelName: String, messages: MutableList<Message<*>>) {
    receiveAll(1000, channelName, messages)
  }

  fun receiveAll(timeout: Long, channelName: String, messages: MutableList<Message<*>>) {
    do {
      val message = outputDestination.receive(timeout, getDestination(channelName))
      if (message != null)
        messages.add(message)
    } while (message != null)
  }

  fun clearChannel(channelName: String) {
    outputDestination.clear(getDestination(channelName))
  }
}