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
import cloud.commandframework.CommandTree;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.CommandExecutionException;
import cloud.commandframework.services.State;
import cloud.commandframework.types.tuples.Pair;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The command execution coordinator is responsible for
 * coordinating command execution. This includes determining
 * what thread the command should be executed on, whether or
 * not command may be executed in parallel, etc.
 *
 * @param <C> Command sender type
 */
public abstract class CommandExecutionCoordinator<C> {

    private final CommandTree<C> commandTree;

    /**
     * Construct a new command execution coordinator
     *
     * @param commandTree Command tree
     */
    protected CommandExecutionCoordinator(final @NonNull CommandTree<C> commandTree) {
        this.commandTree = commandTree;
    }

    /**
     * Returns a simple command execution coordinator that executes all commands immediately, on the calling thread
     *
     * @param <C> Command sender type
     * @return New coordinator instance
     */
    public static <C> @NonNull Function<@NonNull CommandTree<C>,
            @NonNull CommandExecutionCoordinator<C>> simpleCoordinator() {
        return SimpleCoordinator::new;
    }

    /**
     * Coordinate the execution of a command and return the result
     *
     * @param commandContext Command context
     * @param input          Command input
     * @return Future that completes with the result
     */
    public abstract @NonNull CompletableFuture<CommandResult<C>> coordinateExecution(
            @NonNull CommandContext<C> commandContext,
            @NonNull Queue<@NonNull String> input
    );

    /**
     * Get the command tree
     *
     * @return Command tree
     */
    protected @NonNull CommandTree<C> getCommandTree() {
        return this.commandTree;
    }


    /**
     * A simple command execution coordinator that executes all commands immediately, on the calling thread
     *
     * @param <C> Command sender type
     */
    public static final class SimpleCoordinator<C> extends
            CommandExecutionCoordinator<C> {

        private SimpleCoordinator(final @NonNull CommandTree<C> commandTree) {
            super(commandTree);
        }

        @Override
        public @NonNull CompletableFuture<CommandResult<C>> coordinateExecution(
                final @NonNull CommandContext<C> commandContext,
                final @NonNull Queue<@NonNull String> input
        ) {
            final CompletableFuture<CommandResult<C>> completableFuture = new CompletableFuture<>();
            try {
                final @NonNull Pair<@Nullable Command<C>, @Nullable Exception> pair =
                        this.getCommandTree().parse(commandContext, input);
                if (pair.getSecond() != null) {
                    completableFuture.completeExceptionally(pair.getSecond());
                } else {
                    final Command<C> command = Objects.requireNonNull(pair.getFirst());
                    if (this.getCommandTree().getCommandManager().postprocessContext(commandContext, command) == State.ACCEPTED) {
                        try {
                            command.getCommandExecutionHandler().executeFuture(commandContext).get();
                        } catch (final CommandExecutionException exception) {
                            completableFuture.completeExceptionally(exception);
                        } catch (final Exception exception) {
                            completableFuture.completeExceptionally(new CommandExecutionException(exception, commandContext));
                        }
                    }
                    completableFuture.complete(new CommandResult<>(commandContext));
                }
            } catch (final Exception e) {
                completableFuture.completeExceptionally(e);
            }
            return completableFuture;
        }

    }

}
