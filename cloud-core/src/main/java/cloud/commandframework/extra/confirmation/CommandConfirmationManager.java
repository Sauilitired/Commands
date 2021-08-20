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
package cloud.commandframework.extra.confirmation;

import cloud.commandframework.CommandManager;
import cloud.commandframework.execution.CommandExecutionHandler;
import cloud.commandframework.execution.postprocessor.CommandPostprocessingContext;
import cloud.commandframework.execution.postprocessor.CommandPostprocessor;
import cloud.commandframework.meta.CommandMeta;
import cloud.commandframework.meta.SimpleCommandMeta;
import cloud.commandframework.services.types.ConsumerService;
import cloud.commandframework.types.tuples.Pair;

import java.util.concurrent.CompletableFuture;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Manager for the command confirmation system that enables the ability to add "confirmation" requirements to commands,
 * such that they need to be confirmed in order to be executed.
 * <p>
 * To use the confirmation system, the confirmation post processor needs to be added. To to this, use
 * {@link #registerConfirmationProcessor(CommandManager)}. After this is done, a confirmation command has
 * been added. To do this, create a command builder and attach {@link #createConfirmationExecutionHandler()}.
 * <p>
 * To require a command to be confirmed, use {@link #decorate(SimpleCommandMeta.Builder)} on the command meta builder.
 *
 * @param <C> Command sender type
 */
public class CommandConfirmationManager<C> {

    /**
     * Meta data stored for commands that require confirmation
     *
     * @deprecated for removal since 1.3.0. Use {@link #META_CONFIRMATION_REQUIRED} instead.
     */
    @Deprecated
    public static final String CONFIRMATION_REQUIRED_META = "__REQUIRE_CONFIRMATION__";

    private static final CommandMeta.Key<String> LEGACY_CONFIRMATION_META = CommandMeta.Key.of(
            String.class,
            CONFIRMATION_REQUIRED_META
    );

    /**
     * Meta data stored for commands that require confirmation
     *
     * @since 1.3.0
     */
    public static final CommandMeta.Key<Boolean> META_CONFIRMATION_REQUIRED = CommandMeta.Key.of(
            Boolean.class,
            "cloud:require_confirmation",
            meta -> meta.get(LEGACY_CONFIRMATION_META).map(Boolean::valueOf).orElse(null)
    );
    private static final int MAXIMUM_PENDING_SIZE = 100;

    private final Consumer<CommandPostprocessingContext<C>> notifier;
    private final Consumer<C> errorNotifier;
    private final Map<C, Pair<CommandPostprocessingContext<C>, Long>> pendingCommands;

    private final long timeoutMillis;

    /**
     * Create a new confirmation manager instance
     *
     * @param timeout         Timeout value
     * @param timeoutTimeUnit Timeout time unit
     * @param notifier        Notifier that gets called when a command gets added to the queue
     * @param errorNotifier   Notifier that gets called when someone tries to confirm a command with nothing in the queue
     */
    public CommandConfirmationManager(
            final long timeout,
            final @NonNull TimeUnit timeoutTimeUnit,
            final @NonNull Consumer<@NonNull CommandPostprocessingContext<C>> notifier,
            final @NonNull Consumer<@NonNull C> errorNotifier
    ) {
        this.notifier = notifier;
        this.errorNotifier = errorNotifier;
        this.pendingCommands = new LinkedHashMap<C, Pair<CommandPostprocessingContext<C>, Long>>() {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<C, Pair<CommandPostprocessingContext<C>, Long>> eldest) {
                return this.size() > MAXIMUM_PENDING_SIZE;
            }
        };
        this.timeoutMillis = timeoutTimeUnit.toMillis(timeout);
    }

    private void notifyConsumer(final @NonNull CommandPostprocessingContext<C> context) {
        this.notifier.accept(context);
    }

    private void addPending(final @NonNull CommandPostprocessingContext<C> context) {
        this.pendingCommands.put(context.getCommandContext().getSender(), Pair.of(context, System.currentTimeMillis()));
    }

    /**
     * Get a pending context if one is stored for the sender
     *
     * @param sender Sender
     * @return Optional containing the post processing context if one has been stored, else {@link Optional#empty()}
     */
    public @NonNull Optional<CommandPostprocessingContext<C>> getPending(final @NonNull C sender) {
        final Pair<CommandPostprocessingContext<C>, Long> pair = this.pendingCommands.remove(sender);
        if (pair != null) {
            if (System.currentTimeMillis() < pair.getSecond() + this.timeoutMillis) {
                return Optional.of(pair.getFirst());
            }
        }
        return Optional.empty();
    }

    /**
     * Decorate a simple command meta builder, to require confirmation for a command
     *
     * @param builder Command meta builder
     * @return Builder instance
     */
    public SimpleCommandMeta.@NonNull Builder decorate(final SimpleCommandMeta.@NonNull Builder builder) {
        return builder.with(META_CONFIRMATION_REQUIRED, true);
    }

    /**
     * Register the confirmation processor in the command manager
     *
     * @param manager Command manager
     */
    public void registerConfirmationProcessor(final @NonNull CommandManager<C> manager) {
        manager.registerCommandPostProcessor(new CommandConfirmationPostProcessor());
    }

    /**
     * Create an execution handler for a confirmation command
     *
     * @return Handler for a confirmation command
     */
    public @NonNull CommandExecutionHandler<C> createConfirmationExecutionHandler() {
        return (CommandExecutionHandler.FutureCommandExecutionHandler<C>) context -> {
            final Optional<CommandPostprocessingContext<C>> pending = this.getPending(context.getSender());
            if (pending.isPresent()) {
                final CommandPostprocessingContext<C> postprocessingContext = pending.get();
                return postprocessingContext.getCommand()
                        .getCommandExecutionHandler()
                        .executeFuture(postprocessingContext.getCommandContext());
            } else {
                this.errorNotifier.accept(context.getSender());
            }
            return CompletableFuture.completedFuture(null);
        };
    }

    private final class CommandConfirmationPostProcessor implements CommandPostprocessor<C> {

        @Override
        public void accept(final @NonNull CommandPostprocessingContext<C> context) {
            if (!context.getCommand()
                    .getCommandMeta()
                    .getOrDefault(META_CONFIRMATION_REQUIRED, false)) {
                return;
            }
            /* Add it to the "queue" */
            CommandConfirmationManager.this.addPending(context);
            /* Notify the consumer that a confirmation is required */
            CommandConfirmationManager.this.notifyConsumer(context);
            /* Interrupt */
            ConsumerService.interrupt();
        }

    }

}
