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
package org.incendo.cloud.annotations.feature;

import io.leangen.geantyref.TypeToken;
import java.util.concurrent.CompletionException;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.annotations.AnnotationParser;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.execution.ExecutionCoordinator;
import org.incendo.cloud.internal.CommandRegistrationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class RequiredSenderTest {

    private CommandManager<SuperSender<?>> commandManager;
    private AnnotationParser<SuperSender<?>> annotationParser;

    @BeforeEach
    void setup() {
        this.commandManager = new CommandManager<SuperSender<?>>(
                ExecutionCoordinator.simpleCoordinator(),
                CommandRegistrationHandler.nullCommandRegistrationHandler()
        ) {
            @Override
            public boolean hasPermission(
                    final @NonNull SuperSender<?> sender,
                    final @NonNull String permission
            ) {
                return true;
            }
        };
        this.annotationParser = new AnnotationParser<>(
                this.commandManager,
                new TypeToken<SuperSender<?>>() {
                }
        );
        this.annotationParser.parse(new TestClassA());
    }

    @Test
    void testCorrectSender() {
        // Arrange
        final SuperSender<?> sender = new StringSender();

        // Act
        this.commandManager.commandExecutor().executeCommand(sender, "teststring").join();
    }

    @Test
    void testIncorrectSender() {
        // Arrange
        final SuperSender<?> sender = new IntegerSender();

        // Act
        assertThrows(
                CompletionException.class,
                () -> this.commandManager.commandExecutor().executeCommand(sender, "teststring").join()
        );
    }

    @Test
    void testInvalidRequirement() {
        assertThrows(
                IllegalArgumentException.class,
                () -> this.annotationParser.parse(new InvalidSenderRequirementTest())
        );
    }


    static class TestClassA {

        @Command("teststring")
        public void command(final StringSender sender) {
        }
    }


    public interface SuperSender<A> {
    }


    public static class StringSender implements SuperSender<String> {
    }


    public static class IntegerSender implements SuperSender<Integer> {
    }

    static class InvalidSenderRequirementTest {
        @Command(value = "invalid_command", requiredSender = String.class)
        public void badSenderTypeNoParams() {
        }
    }
}
