//
// MIT License
//
// Copyright (c) 2021 Alexander Söderberg & Contributors
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.
//
package cloud.commandframework.kotlin.coroutines

import cloud.commandframework.CommandManager
import cloud.commandframework.annotations.AnnotationParser
import cloud.commandframework.annotations.CommandMethod
import cloud.commandframework.execution.AsynchronousCommandExecutionCoordinator
import cloud.commandframework.internal.CommandRegistrationHandler
import cloud.commandframework.meta.CommandMeta
import cloud.commandframework.meta.SimpleCommandMeta
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KotlinAnnotatedMethodsTest {

    companion object {
        val executorService = Executors.newSingleThreadExecutor()
    }

    private lateinit var commandManager: CommandManager<TestCommandSender>

    @BeforeEach
    fun setUp() {
        commandManager = TestCommandManager()
    }

    private fun awaitCommands() {
        executorService.shutdown()
        executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS)
    }

    @Test
    fun `test suspending command methods`(): Unit = runBlocking {
        AnnotationParser(commandManager, TestCommandSender::class.java) {
                SimpleCommandMeta.empty()
            }
            .also { it.installCoroutineSupport() }
            .parse(CommandMethods())

        commandManager.executeCommand(TestCommandSender(), "test").await()
    }

    private class TestCommandSender {}

    private class TestCommandManager :
        CommandManager<TestCommandSender>(
            AsynchronousCommandExecutionCoordinator.newBuilder<TestCommandSender>()
                .withExecutor(executorService)
                .build(),
            CommandRegistrationHandler.nullCommandRegistrationHandler()) {

        override fun hasPermission(sender: TestCommandSender, permission: String): Boolean = true

        override fun createDefaultCommandMeta(): CommandMeta = SimpleCommandMeta.empty()
    }

    public class CommandMethods {

        @CommandMethod("test")
        public suspend fun suspendingCommand(): Unit =
            withContext(Dispatchers.Default) {
                println("called from thread: ${Thread.currentThread().name}")
            }
    }
}
