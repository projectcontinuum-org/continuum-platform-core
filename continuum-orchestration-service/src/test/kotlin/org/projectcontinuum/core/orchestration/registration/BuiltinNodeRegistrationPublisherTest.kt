package org.projectcontinuum.core.orchestration.registration

import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.projectcontinuum.core.commons.constant.TaskQueues
import org.projectcontinuum.core.commons.event.Channels
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.model.LoopNode
import org.projectcontinuum.core.commons.node.builtin.LoopNodeDescriptor
import org.projectcontinuum.core.protocol.event.FeatureRegistrationRequest
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.Message
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The orchestration service registers the built-in Loop in the node registry at start-up, and a failed send never stops it. */
class BuiltinNodeRegistrationPublisherTest {

  /** Reads the manifest the way the message bridge does (Jackson 3 into the commons model). */
  private val bridgeMapper = jacksonObjectMapper()

  private fun publisherOn(streamBridge: StreamBridge, scheduler: VirtualTimeScheduler = VirtualTimeScheduler()) =
    BuiltinNodeRegistrationPublisher(
      sender = BuiltinNodeRegistrationSender(streamBridge = streamBridge),
      scheduler = scheduler,
      retryPolicy = RegistrationRetryPolicy(),
      shutdownAction = { scheduler.shutDown() }
    )

  private fun capturedMessages(streamBridge: StreamBridge, expectedSends: Int): List<Message<*>> {
    val captor = argumentCaptor<Any>()
    verify(streamBridge, times(expectedSends)).send(eq(Channels.CONTINUUM_FEATURE_REGISTRATION_OUTPUT), captor.capture())
    return captor.allValues.map { value -> value as Message<*> }
  }

  @Test
  fun `the ready event sends exactly one Loop registration with the contract fields`() {
    val streamBridge = mock<StreamBridge>()
    whenever(streamBridge.send(any<String>(), any<Any>())).thenReturn(true)
    val scheduler = VirtualTimeScheduler()
    val publisher = publisherOn(streamBridge = streamBridge, scheduler = scheduler)

    publisher.publishBuiltinNodeRegistrations()
    scheduler.runAll()

    val message = capturedMessages(streamBridge = streamBridge, expectedSends = 1).single()
    val request = message.payload as FeatureRegistrationRequest
    assertEquals(LoopNode.NODE_MODEL, request.getNodeId())
    assertEquals(LoopNode.NODE_MODEL, message.headers[KafkaHeaders.KEY])
    assertEquals("org.projectcontinuum.core.builtin", request.getFeatureId())
    assertEquals(TaskQueues.BUILTIN_NO_ACTIVITY, request.getTaskQueue())
    assertEquals("\$builtin", request.getTaskQueue())
    assertTrue(request.getWorkerId().startsWith("orchestration-"), "workerId was ${request.getWorkerId()}")
    assertEquals(listOf("Flow Control"), request.getCategories())
    assertEquals(emptyMap(), request.getExtensions())
    assertEquals(LoopNodeDescriptor.documentationMarkdown, request.getDocumentationMarkdown())
  }

  @Test
  fun `the manifest is the Loop descriptor and reads back as NodeData with both schemas`() {
    val request = BuiltinNodeRegistrationRequests.build(
      definition = BuiltinNodes.loop,
      workerId = "orchestration-test",
      registeredAt = Instant.parse("2026-11-25T10:00:00Z")
    )

    val manifest = bridgeMapper.readValue(request.getNodeManifest(), ContinuumWorkflowModel.NodeData::class.java)

    assertEquals(LoopNodeDescriptor.nodeData.copy(), manifest)
    assertEquals(LoopNodeDescriptor.propertiesSchema, manifest.propertiesSchema)
    assertEquals(LoopNodeDescriptor.propertiesUISchema, manifest.propertiesUISchema)
    assertEquals(LoopNodeDescriptor.defaultProperties, manifest.properties)
    assertNull(manifest.inputs)
    assertNull(manifest.outputs)
    assertEquals(Instant.parse("2026-11-25T10:00:00Z"), request.getRegisteredAtTimestampUtc())
  }

  @Test
  fun `one worker id is shared by every registration of one start`() {
    val streamBridge = mock<StreamBridge>()
    whenever(streamBridge.send(any<String>(), any<Any>())).thenReturn(true)
    val scheduler = VirtualTimeScheduler()
    val publisher = publisherOn(streamBridge = streamBridge, scheduler = scheduler)
    val definitions = listOf(BuiltinNodes.loop, BuiltinNodes.loop.copy(nodeId = "org.projectcontinuum.core.builtin.Other"))

    publisher.publish(definitions = definitions, workerId = "orchestration-fixed", registeredAt = Instant.EPOCH)
    scheduler.runAll()

    val requests = capturedMessages(streamBridge = streamBridge, expectedSends = 2)
      .map { message -> message.payload as FeatureRegistrationRequest }
    assertEquals(listOf("orchestration-fixed", "orchestration-fixed"), requests.map { request -> request.getWorkerId() })
  }

  @Test
  fun `a throwing stream bridge is retried and the next node is still sent`() {
    val streamBridge = mock<StreamBridge>()
    whenever(streamBridge.send(any<String>(), any<Any>()))
      .thenThrow(IllegalStateException("schema registry unreachable"))
      .thenReturn(true)
    val scheduler = VirtualTimeScheduler()
    val publisher = publisherOn(streamBridge = streamBridge, scheduler = scheduler)
    val definitions = listOf(BuiltinNodes.loop, BuiltinNodes.loop.copy(nodeId = "org.projectcontinuum.core.builtin.Other"))

    publisher.publish(definitions = definitions, workerId = "orchestration-fixed", registeredAt = Instant.EPOCH)
    scheduler.runAll()

    val requests = capturedMessages(streamBridge = streamBridge, expectedSends = 3)
      .map { message -> message.payload as FeatureRegistrationRequest }
    assertEquals(
      setOf(LoopNode.NODE_MODEL, "org.projectcontinuum.core.builtin.Other"),
      requests.map { request -> request.getNodeId() }.toSet()
    )
    assertEquals(2, requests.count { request -> request.getNodeId() == LoopNode.NODE_MODEL }, "the failed Loop send is retried")
  }

  @Test
  fun `the ready event returns at once even when every send fails, and shutdown stops the retries`() {
    val streamBridge = mock<StreamBridge>()
    whenever(streamBridge.send(any<String>(), any<Any>())).thenThrow(IllegalStateException("broker down"))
    val scheduler = VirtualTimeScheduler()
    val publisher = publisherOn(streamBridge = streamBridge, scheduler = scheduler)

    publisher.publishBuiltinNodeRegistrations()

    verify(streamBridge, times(0)).send(any<String>(), any<Any>())
    assertEquals(1, scheduler.pendingCount, "the first attempt runs on the background scheduler")
    scheduler.advanceBy(duration = java.time.Duration.ofSeconds(1))
    publisher.stopRetries()
    scheduler.runAll()
    verify(streamBridge, times(1)).send(any<String>(), any<Any>())
  }

  @Test
  fun `a send the binder refuses does not throw and is retried`() {
    val streamBridge = mock<StreamBridge>()
    whenever(streamBridge.send(any<String>(), any<Any>())).thenReturn(false)
    val scheduler = VirtualTimeScheduler()
    val publisher = publisherOn(streamBridge = streamBridge, scheduler = scheduler)

    publisher.publishBuiltinNodeRegistrations()
    scheduler.runAll()

    capturedMessages(streamBridge = streamBridge, expectedSends = 5)
  }

  @Test
  fun `the production publisher sends on its own background thread`() {
    val streamBridge = mock<StreamBridge>()
    val sentOnThread = java.util.concurrent.CompletableFuture<String>()
    whenever(streamBridge.send(any<String>(), any<Any>())).thenAnswer {
      sentOnThread.complete(Thread.currentThread().name)
      true
    }
    val publisher = BuiltinNodeRegistrationPublisher(streamBridge = streamBridge)

    publisher.publishBuiltinNodeRegistrations()

    assertEquals("builtin-node-registration", sentOnThread.get(5, java.util.concurrent.TimeUnit.SECONDS))
    publisher.stopRetries()
  }
}
