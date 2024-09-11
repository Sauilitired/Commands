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
package org.incendo.cloud.syntax;

import java.util.Collections;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.Command;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.TestCommandSender;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandNode;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.google.common.truth.Truth.assertThat;

class StandardCommandSyntaxFormatterTest {

    private CommandManager<TestCommandSender> manager;
    private CommandSyntaxFormatter<TestCommandSender> formatter;

    @BeforeEach
    void setup() {
        this.manager = new CommandManager<TestCommandSender>(
                ExecutionCoordinator.simpleCoordinator(),
                CommandRegistrationHandler.nullCommandRegistrationHandler()
        ) {
            @Override
            public boolean hasPermission(
                    final @NonNull TestCommandSender sender,
                    final @NonNull String permission
            ) {
                return sender.hasPermisison(permission);
            }
        };
        this.formatter = new StandardCommandSyntaxFormatter<>(this.manager);
    }

    @Test
    void accountsForSenderType() {
        final Command.Builder<TestCommandSender> root = this.manager.commandBuilder("root");
        this.manager.command(
                root.literal("all")
                        .handler(ctx -> {})
        );
        this.manager.command(
                root.literal("specific_only")
                        .senderType(SpecificTestCommandSender.class)
                        .handler(ctx -> {})
        );

        final CommandNode<TestCommandSender> rootNode = this.manager.commandTree().getNamedNode("root");

        final String specificFormatted = this.formatter.apply(
                new SpecificTestCommandSender(), Collections.emptyList(), rootNode);
        assertThat(specificFormatted).isEqualTo(" all|specific_only");

        final String formatted = this.formatter.apply(
                new TestCommandSender(), Collections.emptyList(), rootNode);
        assertThat(formatted).isEqualTo(" all");
    }

    @Test
    void respectsPermissions() {
        final Command.Builder<TestCommandSender> root = this.manager.commandBuilder("root");
        this.manager.command(
                root.literal("all")
                        .handler(ctx -> {})
        );
        this.manager.command(
                root.literal("permitted_only")
                        .permission("some_permission")
                        .handler(ctx -> {})
        );

        final CommandNode<TestCommandSender> rootNode = this.manager.commandTree().getNamedNode("root");

        final String permittedFormatted = this.formatter.apply(
                new TestCommandSender("some_permission"), Collections.emptyList(), rootNode);
        assertThat(permittedFormatted).isEqualTo(" all|permitted_only");

        final String formatted = this.formatter.apply(
                new TestCommandSender(), Collections.emptyList(), rootNode);
        assertThat(formatted).isEqualTo(" all");
    }

    @Test
    void respectsFlagPermissions() {
        final Command.Builder<TestCommandSender> root = this.manager.commandBuilder("root");
        this.manager.command(
                root.flag(CommandFlag.builder("some_flag").withPermission("some_permission").build())
                        .handler(ctx -> {})
        );

        final CommandNode<TestCommandSender> rootNode = this.manager.commandTree().getNamedNode("root");

        final String permittedFormatted = this.formatter.apply(
                new TestCommandSender("some_permission"), Collections.emptyList(), rootNode);
        assertThat(permittedFormatted).isEqualTo(" [--some_flag]");

        final String formatted = this.formatter.apply(
                new TestCommandSender(), Collections.emptyList(), rootNode);
        assertThat(formatted).isEqualTo("");
    }

    static final class SpecificTestCommandSender extends TestCommandSender {

    }
}
