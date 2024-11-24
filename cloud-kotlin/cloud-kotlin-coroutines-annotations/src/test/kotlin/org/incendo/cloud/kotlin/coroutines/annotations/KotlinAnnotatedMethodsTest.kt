//
// MIT License
//
// Copyright (c) 2024 Incendo
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
package org.incendo.cloud.kotlin.coroutines.annotations

import com.google.common.primitives.UnsignedBytes.toInt
import com.google.common.truth.Truth.assertThat
import io.leangen.geantyref.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.incendo.cloud.CommandManager
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.parser.Parser
import org.incendo.cloud.annotations.suggestion.Suggestions
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.context.CommandInput
import org.incendo.cloud.context.StandardCommandContextFactory
import org.incendo.cloud.exception.CommandExecutionException
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.internal.CommandRegistrationHandler
import org.incendo.cloud.parser.ArgumentParseResult
import org.incendo.cloud.parser.ParserParameters
import org.incendo.cloud.suggestion.Suggestion
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class KotlinAnnotatedMethodsTest {

    companion object {
        val executorService: ExecutorService = Executors.newSingleThreadExecutor()
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
        AnnotationParser(commandManager, TestCommandSender::class.java)
            .installCoroutineSupport()
            .parse(CommandMethods())

        commandManager.commandExecutor().executeCommand(TestCommandSender(), "test").await()
    }

    @Test
    fun `test suspending command methods with exception`(): Unit = runBlocking {
        AnnotationParser(commandManager, TestCommandSender::class.java)
            .installCoroutineSupport()
            .parse(CommandMethods())

        assertThrows<CommandExecutionException> {
            commandManager.commandExecutor().executeCommand(TestCommandSender(), "test-exception").await()
        }
    }

    @Test
    fun `test method with default value`(): Unit = runBlocking {
        AnnotationParser(commandManager, TestCommandSender::class.java)
            .installCoroutineSupport()
            .parse(CommandMethods())

        val result = commandManager.commandExecutor().executeCommand(TestCommandSender(), "with-default").await()
        assertThat(result.commandContext().get<Int>("the-value")).isEqualTo(5)
    }

    @Test
    fun `test suspending suggestion method`(): Unit = runBlocking {
        AnnotationParser(commandManager, TestCommandSender::class.java)
            .installCoroutineSupport()
            .parse(SuggestionMethods())

        val commandContext = StandardCommandContextFactory<TestCommandSender>(commandManager).create(
            true,
            TestCommandSender()
        )
        val suggestions = commandManager.parserRegistry().getSuggestionProvider("suspending-suggestions").get()
            .suggestionsFuture(commandContext, CommandInput.empty())
            .await()
            .map(Suggestion::suggestion)
            .map(String::toInt)
        assertThat(suggestions).containsExactlyElementsIn(1..10)
    }

    @Test
    fun `test non-suspending suggestion method`(): Unit = runBlocking {
        AnnotationParser(commandManager, TestCommandSender::class.java)
            .installCoroutineSupport()
            .parse(SuggestionMethods())

        val commandContext = StandardCommandContextFactory<TestCommandSender>(commandManager).create(
            true,
            TestCommandSender()
        )
        val suggestions = commandManager.parserRegistry().getSuggestionProvider("non-suspending-suggestions").get()
            .suggestionsFuture(commandContext, CommandInput.empty())
            .await()
            .map(Suggestion::suggestion)
            .map(String::toInt)
        assertThat(suggestions).containsExactlyElementsIn(1..10)
    }

    @Test
    fun `test suspending parser method`(): Unit = runBlocking {
        AnnotationParser(commandManager, TestCommandSender::class.java)
            .installCoroutineSupport()
            .parse(ParserMethods())

        val commandContext = StandardCommandContextFactory(commandManager).create(
            true,
            TestCommandSender()
        )

        val parser = commandManager.parserRegistry().createParser(
            TypeToken.get(ParserResult::class.java),
            ParserParameters.empty()
        )

        assert(parser.isPresent) {
            "Suspending parser cannot be found!"
        }

        val parsedValue = parser.get().parseFuture(commandContext, CommandInput.of("5")).await().parsedValue()

        assert(parsedValue.isPresent) {
            "Suspending parser cannot parsed the value!"
        }
    }

    @Test
    fun `test suspending parser method with result`(): Unit = runBlocking {
        AnnotationParser(commandManager, TestCommandSender::class.java)
            .installCoroutineSupport()
            .parse(ParserMethods())

        val commandContext = StandardCommandContextFactory(commandManager).create(
            true,
            TestCommandSender()
        )

        val parser = commandManager.parserRegistry().createParser(
            TypeToken.get(ParserResult2::class.java),
            ParserParameters.empty()
        )

        assert(parser.isPresent) {
            "Suspending parser cannot be found!"
        }

        val parsedValue = parser.get().parseFuture(commandContext, CommandInput.of("5")).await().parsedValue()

        assert(parsedValue.isPresent) {
            "Suspending parser cannot parsed the value!"
        }
    }

    @Test
    fun `test suspending parser method with exception`(): Unit = runBlocking {
        AnnotationParser(commandManager, TestCommandSender::class.java)
            .installCoroutineSupport()
            .parse(ParserMethods())

        val commandContext = StandardCommandContextFactory(commandManager).create(
            true,
            TestCommandSender()
        )

        val parser = commandManager.parserRegistry().createParser(
            TypeToken.get(ParserResult3::class.java),
            ParserParameters.empty()
        )

        assert(parser.isPresent) {
            "Suspending parser cannot be found!"
        }

        val result: ArgumentParseResult<*> = parser.get().parseFuture(commandContext, CommandInput.of("5")).await()

        assert(result.failure().orElse(null) is IllegalStateException) {
            "Suspending parser should fail with IllegalStateException!"
        }
    }

    public class TestCommandSender

    private class TestCommandManager : CommandManager<TestCommandSender>(
        ExecutionCoordinator.builder<TestCommandSender>()
            .executor(executorService)
            .build(),
        CommandRegistrationHandler.nullCommandRegistrationHandler()
    ) {

        override fun hasPermission(sender: TestCommandSender, permission: String): Boolean = true
    }

    public class CommandMethods {

        @Command("test")
        public suspend fun suspendingCommand(): Unit =
            withContext(Dispatchers.Default) {
                println("called from thread: ${Thread.currentThread().name}")
            }

        @Command("test-exception")
        public suspend fun suspendingCommandWithException(): Unit = throw IllegalStateException()

        @Command("with-default [value]")
        public fun commandWithDefault(@Argument("value") value: Int = 5, context: CommandContext<TestCommandSender>) {
            context["the-value"] = value
        }
    }

    class SuggestionMethods {

        @Suggestions("suspending-suggestions")
        suspend fun suspendingSuggestions(ctx: CommandContext<TestCommandSender>, input: String): Sequence<Suggestion> =
            withContext(Dispatchers.Default) {
                (1..10).asSequence().map(Int::toString).map(Suggestion::suggestion)
            }

        @Suggestions("non-suspending-suggestions")
        fun suggestions(ctx: CommandContext<TestCommandSender>, input: String): Sequence<Suggestion> =
            (1..10).asSequence().map(Int::toString).map(Suggestion::suggestion)
    }

    data class ParserResult(val test: Int)

    data class ParserResult2(val test: Int)

    data class ParserResult3(val test: Int)

    class ParserMethods {

        @Parser
        suspend fun suspendingParser(input: CommandInput): ParserResult =
            withContext(Dispatchers.Default) {
                ParserResult(input.lastRemainingToken().toInt())
            }

        @Parser
        suspend fun suspendingParser2(input: CommandInput): ArgumentParseResult<ParserResult2> =
            withContext(Dispatchers.Default) {
                ArgumentParseResult.success(ParserResult2(input.lastRemainingToken().toInt()))
            }

        @Parser
        suspend fun exceptionParser(input: CommandInput): ParserResult3 =
            withContext(Dispatchers.Default) {
                throw IllegalStateException()
            }
    }
}
