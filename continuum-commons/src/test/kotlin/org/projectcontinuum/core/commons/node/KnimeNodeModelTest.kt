package org.projectcontinuum.core.commons.node

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import org.projectcontinuum.core.commons.exception.NodeRuntimeException
import org.projectcontinuum.core.commons.model.ContinuumWorkflowModel
import org.projectcontinuum.core.commons.utils.NodeOutputWriter
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class KnimeNodeModelTest {

  @TempDir
  lateinit var tempDir: Path

  private class TestKnimeNode(
    override val knimeExecutablePath: String,
    root: Path
  ) : KnimeNodeModel() {
    override val knimeWorkspacesRoot: String = root.resolve("workspaces").toString()
    override val knimeWorkflowRootDir: String = root.resolve("workflows").toString()
    override val knimeNodeFactoryClass: String = "org.example.Factory"
    override val knimeNodeName: String = "Test"
    override val inputPorts: Map<String, ContinuumWorkflowModel.NodePort> = emptyMap()
    override val outputPorts: Map<String, ContinuumWorkflowModel.NodePort> = emptyMap()
    override val categories: List<String> = listOf("Test")
    override val metadata = ContinuumWorkflowModel.NodeData(
      description = "test",
      title = "Test",
      nodeModel = "TestKnimeNode"
    )

    override fun getProcessingNodeSettingsTemplate(): String = ""
  }

  private val hostileNames = listOf("\$(id)", "a'b", "x; rm -rf ~", "a\nb", "`id`", "a b && echo pwned")

  @Test
  fun `command is an argv list with hostile paths passed verbatim and no shell`() {
    val node = TestKnimeNode("/opt/knime/knime", tempDir)
    hostileNames.forEach { hostile ->
      val workflowPath = tempDir.resolve(hostile)
      val workspaceDir = File(tempDir.toFile(), "ws $hostile")
      val inputOption = "-option=0,inputPathOrUrl,${tempDir.resolve("$hostile.json")},String"

      val command = node.buildKnimeCommand(workflowPath, workspaceDir, listOf(inputOption), emptyList())

      assertEquals("/opt/knime/knime", command.first())
      assertFalse(command.any { it == "/bin/bash" || it == "bash" || it == "-c" || it == "sh" })
      assertTrue(command.contains("-workflowDir=${workflowPath.toAbsolutePath()}"))
      assertTrue(command.contains(workspaceDir.absolutePath))
      assertEquals(inputOption, command.last())
    }
  }

  @Test
  @EnabledOnOs(OS.LINUX, OS.MAC)
  fun `shell metacharacters in the workflow path are not executed`() {
    val marker = tempDir.resolve("pwned").toFile()
    val workflowPath = tempDir.resolve("wf-\$(touch ${marker.absolutePath})")
    workflowPath.toFile().mkdirs()
    val node = TestKnimeNode("/bin/echo", tempDir)

    val exitCode = node.executeKnimeWorkflow(workflowPath, emptyMap(), NodeOutputWriter(tempDir))
      .get(30, TimeUnit.SECONDS)

    assertEquals(0, exitCode)
    assertFalse(marker.exists(), "\$(touch ...) must not be evaluated by a shell")
  }

  @Test
  fun `workflow dir resolves under the KNIME workflow root`() {
    val node = TestKnimeNode("/opt/knime/knime", tempDir)

    val dir = node.resolveWorkflowDir("0195c2a1-7d1e-7b3c-9a4f-1c2d3e4f5a6b", "12")

    assertEquals(tempDir.resolve("workflows").toFile().canonicalFile, dir.parentFile.parentFile)
    assertEquals("12", dir.name)
  }

  @Test
  fun `node ids that escape the workflow root are rejected as non-retriable`() {
    val node = TestKnimeNode("/opt/knime/knime", tempDir)
    listOf("../../escape", "../..", "/../../../etc").forEach { hostile ->
      val ex = assertThrows<NodeRuntimeException> { node.resolveWorkflowDir("run-1", hostile) }
      assertFalse(ex.isRetriable)
    }
    assertThrows<NodeRuntimeException> { node.resolveWorkflowDir("..", "..") }
  }

  @Test
  fun `shell metacharacters in a node id stay a literal directory name`() {
    val node = TestKnimeNode("/opt/knime/knime", tempDir)

    val dir = node.resolveWorkflowDir("run-1", "\$(id)")

    assertEquals("\$(id)", dir.name)
  }
}
