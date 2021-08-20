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
package cloud.commandframework.kotlin

import cloud.commandframework.CommandManager
import cloud.commandframework.arguments.standard.StringArgument
import cloud.commandframework.execution.CommandExecutionCoordinator
import cloud.commandframework.internal.CommandRegistrationHandler
import cloud.commandframework.kotlin.extension.*
import cloud.commandframework.meta.CommandMeta
import cloud.commandframework.meta.SimpleCommandMeta
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class CommandBuildingDSLTest {

    @Test
    fun testCommandDSL() {
        val manager = TestCommandManager()

        manager.command(
            manager.commandBuilder("kotlin", aliases = arrayOf("alias")) {
                permission = "permission"
                senderType<SpecificCommandSender>()

                literal("dsl")
                argument(argumentDescription("An amazing command argument")) {
                    StringArgument.of("moment")
                }
                handler {
                    // ...
                }

                manager.command(
                    copy {
                        literal("bruh_moment")
                        handler {
                            // ...
                        }
                    })
            })

        manager.buildAndRegister("is") {
            commandDescription("Command description")

            registerCopy {
                literal("this")
                CommandMeta.DESCRIPTION to "Command description"

                registerCopy {
                    literal("going")
                    meta(CommandMeta.DESCRIPTION, "Command Description")

                    registerCopy("too_far") {
                        // ?
                    }
                }
            }
        }

        manager.executeCommand(SpecificCommandSender(), "kotlin dsl time")
        manager.executeCommand(SpecificCommandSender(), "kotlin dsl time bruh_moment")

        Assertions.assertEquals(
            manager.commandHelpHandler.allCommands.map { it.syntaxString }.sorted(),
            setOf(
                    "kotlin dsl <moment>",
                    "kotlin dsl <moment> bruh_moment",
                    "is",
                    "is this",
                    "is this going",
                    "is this going too_far",
                )
                .sorted())
    }

    class TestCommandManager :
        CommandManager<TestCommandSender>(
            CommandExecutionCoordinator.simpleCoordinator(),
            CommandRegistrationHandler.nullCommandRegistrationHandler()) {
        override fun createDefaultCommandMeta(): SimpleCommandMeta {
            return SimpleCommandMeta.empty()
        }

        override fun hasPermission(sender: TestCommandSender, permission: String): Boolean {
            return !permission.equals("no", ignoreCase = true)
        }
    }

    open class TestCommandSender
    class SpecificCommandSender : TestCommandSender()
}
