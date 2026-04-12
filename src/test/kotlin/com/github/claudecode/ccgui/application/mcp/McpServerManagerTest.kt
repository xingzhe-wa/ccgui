package com.github.claudecode.ccgui.application.mcp

import com.github.claudecode.ccgui.model.mcp.McpScope
import com.github.claudecode.ccgui.model.mcp.McpServer
import com.github.claudecode.ccgui.model.mcp.McpServerStatus
import com.github.claudecode.ccgui.model.mcp.TestResult
import com.intellij.testFramework.LightPlatformTestCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * McpServerManager 单元测试
 */
class McpServerManagerTest : LightPlatformTestCase() {

    private lateinit var mcpServerManager: McpServerManager

    @Before
    override fun setUp() {
        super.setUp()
        mcpServerManager = McpServerManager.getInstance(project)
    }

    @After
    override fun tearDown() {
        super.tearDown()
        // 清理测试创建的服务器
        mcpServerManager.getAllServers()
            .filter { it.id.startsWith("test-") }
            .forEach { mcpServerManager.deleteServer(it.id) }
    }

    @Test
    fun `test addServer - should add server successfully`() {
        val server = McpServer(
            id = "test-server-1",
            name = "Test Server",
            command = "node",
            args = listOf("server.js"),
            scope = McpScope.PROJECT
        )

        val result = mcpServerManager.addServer(server)
        assertTrue(result)

        val retrieved = mcpServerManager.getServer("test-server-1")
        assertNotNull(retrieved)
        assertEquals("Test Server", retrieved.name)
    }

    @Test
    fun `test addServer - should reject duplicate server`() {
        val server = McpServer(
            id = "test-duplicate",
            name = "Duplicate Server",
            command = "node",
            args = listOf("server.js"),
            scope = McpScope.PROJECT
        )

        val result1 = mcpServerManager.addServer(server)
        assertTrue(result1)

        val result2 = mcpServerManager.addServer(server)
        assertFalse(result2)
    }

    @Test
    fun `test updateServer - should update existing server`() {
        val server = McpServer(
            id = "test-update",
            name = "Original Name",
            command = "node",
            args = listOf("server.js"),
            scope = McpScope.PROJECT
        )

        mcpServerManager.addServer(server)

        val updated = server.copy(
            name = "Updated Name",
            command = "python"
        )

        val result = mcpServerManager.updateServer(updated)
        assertTrue(result)

        val retrieved = mcpServerManager.getServer("test-update")
        assertEquals("Updated Name", retrieved?.name)
        assertEquals("python", retrieved?.command)
    }

    @Test
    fun `test deleteServer - should remove server`() {
        val server = McpServer(
            id = "test-delete",
            name = "Delete Server",
            command = "node",
            args = listOf("server.js"),
            scope = McpScope.PROJECT
        )

        mcpServerManager.addServer(server)
        assertNotNull(mcpServerManager.getServer("test-delete"))

        val result = mcpServerManager.deleteServer("test-delete")
        assertTrue(result)

        val retrieved = mcpServerManager.getServer("test-delete")
        assertEquals(null, retrieved)
    }

    @Test
    fun `test getServersByScope - should filter by scope`() {
        val projectServer = McpServer(
            id = "test-project",
            name = "Project Server",
            command = "node",
            args = listOf("server.js"),
            scope = McpScope.PROJECT
        )

        val globalServer = McpServer(
            id = "test-global",
            name = "Global Server",
            command = "python",
            args = listOf("server.py"),
            scope = McpScope.GLOBAL
        )

        mcpServerManager.addServer(projectServer)
        mcpServerManager.addServer(globalServer)

        val projectServers = mcpServerManager.getServersByScope(McpScope.PROJECT)
        assertEquals(1, projectServers.size)
        assertEquals("test-project", projectServers[0].id)

        val globalServers = mcpServerManager.getServersByScope(McpScope.GLOBAL)
        assertEquals(1, globalServers.size)
        assertEquals("test-global", globalServers[0].id)
    }

    @Test
    fun `test getEnabledServers - should return only enabled servers`() {
        val enabledServer = McpServer(
            id = "test-enabled",
            name = "Enabled Server",
            command = "node",
            args = listOf("server.js"),
            enabled = true,
            scope = McpScope.PROJECT
        )

        val disabledServer = McpServer(
            id = "test-disabled",
            name = "Disabled Server",
            command = "python",
            args = listOf("server.py"),
            enabled = false,
            scope = McpScope.PROJECT
        )

        mcpServerManager.addServer(enabledServer)
        mcpServerManager.addServer(disabledServer)

        val enabledServers = mcpServerManager.getEnabledServers()
        assertEquals(1, enabledServers.size)
        assertEquals("test-enabled", enabledServers[0].id)
    }

    @Test
    fun `test setServerEnabled - should toggle server status`() {
        val server = McpServer(
            id = "test-toggle",
            name = "Toggle Server",
            command = "node",
            args = listOf("server.js"),
            enabled = true,
            scope = McpScope.PROJECT
        )

        mcpServerManager.addServer(server)
        assertEquals(1, mcpServerManager.getEnabledServers().size)

        mcpServerManager.setServerEnabled("test-toggle", false)
        assertEquals(0, mcpServerManager.getEnabledServers().size)

        mcpServerManager.setServerEnabled("test-toggle", true)
        assertEquals(1, mcpServerManager.getEnabledServers().size)
    }

    @Test
    fun `test searchServers - should find matching servers`() {
        val server1 = McpServer(
            id = "test-search-1",
            name = "File System Server",
            description = "Provides file system access",
            command = "node",
            args = listOf("fs-server.js"),
            scope = McpScope.PROJECT
        )

        val server2 = McpServer(
            id = "test-search-2",
            name = "Database Server",
            description = "Provides database access",
            command = "python",
            args = listOf("db-server.py"),
            scope = McpScope.PROJECT
        )

        mcpServerManager.addServer(server1)
        mcpServerManager.addServer(server2)

        val fileResults = mcpServerManager.searchServers("file")
        assertEquals(1, fileResults.size)
        assertEquals("test-search-1", fileResults[0].id)

        val accessResults = mcpServerManager.searchServers("access")
        assertEquals(2, accessResults.size)
    }

    @Test
    fun `test exportServer - should return JSON representation`() {
        val server = McpServer(
            id = "test-export",
            name = "Export Server",
            command = "node",
            args = listOf("server.js"),
            scope = McpScope.PROJECT
        )

        mcpServerManager.addServer(server)

        val json = mcpServerManager.exportServer("test-export")
        assertNotNull(json)
        assertTrue(json.contains("\"id\""))
        assertTrue(json.contains("test-export"))
        assertTrue(json.contains("Export Server"))
    }

    @Test
    fun `test importServer - should import server from JSON`() {
        val json = """
            {
                "id": "test-import",
                "name": "Imported Server",
                "command": "python",
                "args": ["server.py"],
                "scope": "project",
                "enabled": true
            }
        """.trimIndent()

        val result = mcpServerManager.importServer(json)
        assertTrue(result)

        val imported = mcpServerManager.getServer("test-import")
        assertNotNull(imported)
        assertEquals("Imported Server", imported.name)
        assertEquals("python", imported.command)
    }

    @Test
    fun `test importServers - should import multiple servers`() {
        val json1 = """
            {
                "id": "test-import-1",
                "name": "Imported Server 1",
                "command": "node",
                "args": ["server1.js"],
                "scope": "project"
            }
        """.trimIndent()

        val json2 = """
            {
                "id": "test-import-2",
                "name": "Imported Server 2",
                "command": "python",
                "args": ["server2.py"],
                "scope": "project"
            }
        """.trimIndent()

        val count = mcpServerManager.importServers(listOf(json1, json2))
        assertEquals(2, count)

        assertNotNull(mcpServerManager.getServer("test-import-1"))
        assertNotNull(mcpServerManager.getServer("test-import-2"))
    }

    @Test
    fun `test getServersByStatus - should filter by status`() {
        val connectedServer = McpServer(
            id = "test-connected",
            name = "Connected Server",
            command = "node",
            args = listOf("server.js"),
            status = McpServerStatus.CONNECTED,
            scope = McpScope.PROJECT
        )

        val disconnectedServer = McpServer(
            id = "test-disconnected",
            name = "Disconnected Server",
            command = "python",
            args = listOf("server.py"),
            status = McpServerStatus.DISCONNECTED,
            scope = McpScope.PROJECT
        )

        mcpServerManager.addServer(connectedServer)
        mcpServerManager.addServer(disconnectedServer)

        val connectedServers = mcpServerManager.getServersByStatus(McpServerStatus.CONNECTED)
        assertEquals(1, connectedServers.size)
        assertEquals("test-connected", connectedServers[0].id)
    }

    @Test
    fun `test getAllServers - should return all servers`() {
        val server1 = McpServer(
            id = "test-all-1",
            name = "Server 1",
            command = "node",
            args = listOf("server1.js"),
            scope = McpScope.PROJECT
        )

        val server2 = McpServer(
            id = "test-all-2",
            name = "Server 2",
            command = "python",
            args = listOf("server2.py"),
            scope = McpScope.GLOBAL
        )

        mcpServerManager.addServer(server1)
        mcpServerManager.addServer(server2)

        val allServers = mcpServerManager.getAllServers()
        assertTrue(allServers.size >= 2) // 包括内置的文件服务器
        assertTrue(allServers.any { it.id == "test-all-1" })
        assertTrue(allServers.any { it.id == "test-all-2" })
    }
}
