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
package cloud.commandframework.execution;

import cloud.commandframework.Command;
import cloud.commandframework.context.CommandContext;

import java.util.concurrent.CompletableFuture;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Handler that is invoked whenever a {@link Command} is executed
 * by a command sender
 *
 * @param <C> Command sender type
 */
@FunctionalInterface
public interface CommandExecutionHandler<C> {

    /**
     * Handle command execution
     *
     * @param commandContext Command context
     */
    void execute(@NonNull CommandContext<C> commandContext);

    /**
     * Handle command execution
     *
     * @param commandContext Command context
     * @return future that completes when the command has finished execution
     * @since 1.6.0
     */
    default CompletableFuture<@Nullable Object> executeFuture(@NonNull CommandContext<C> commandContext) {
        final CompletableFuture<Object> future = new CompletableFuture<>();
        try {
            this.execute(commandContext);
            /* The command executed successfully */
            future.complete(null);
        } catch (final Throwable throwable) {
            future.completeExceptionally(throwable);
        }
        return future;
    }

    /**
     * Command execution handler that does nothing
     *
     * @param <C> Command sender type
     */
    class NullCommandExecutionHandler<C> implements CommandExecutionHandler<C> {

        @Override
        public void execute(final @NonNull CommandContext<C> commandContext) {
        }

    }

    /**
     * Handler that is invoked whenever a {@link Command} is executed
     * by a command sender
     *
     * @param <C> Command sender type
     * @since 1.6.0
     */
    @FunctionalInterface
    interface FutureCommandExecutionHandler<C> extends CommandExecutionHandler<C> {

        @Override
        @SuppressWarnings("FunctionalInterfaceMethodChanged")
        default void execute(
                @NonNull CommandContext<C> commandContext
        ) {
        }

        @Override
        CompletableFuture<@Nullable Object> executeFuture(
                @NonNull CommandContext<C> commandContext
        );

    }

}
