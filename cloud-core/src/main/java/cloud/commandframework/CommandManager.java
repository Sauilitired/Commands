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
package cloud.commandframework;

import cloud.commandframework.annotations.injection.ParameterInjectorRegistry;
import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.CommandSuggestionEngine;
import cloud.commandframework.arguments.CommandSyntaxFormatter;
import cloud.commandframework.arguments.DelegatingCommandSuggestionEngineFactory;
import cloud.commandframework.arguments.StandardCommandSyntaxFormatter;
import cloud.commandframework.arguments.flags.CommandFlag;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.arguments.parser.ParserParameter;
import cloud.commandframework.arguments.parser.ParserRegistry;
import cloud.commandframework.arguments.parser.StandardParserRegistry;
import cloud.commandframework.captions.CaptionRegistry;
import cloud.commandframework.captions.SimpleCaptionRegistryFactory;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.context.CommandContextFactory;
import cloud.commandframework.context.StandardCommandContextFactory;
import cloud.commandframework.execution.CommandExecutionCoordinator;
import cloud.commandframework.execution.CommandResult;
import cloud.commandframework.execution.CommandSuggestionProcessor;
import cloud.commandframework.execution.FilteringCommandSuggestionProcessor;
import cloud.commandframework.execution.postprocessor.AcceptingCommandPostprocessor;
import cloud.commandframework.execution.postprocessor.CommandPostprocessingContext;
import cloud.commandframework.execution.postprocessor.CommandPostprocessor;
import cloud.commandframework.execution.preprocessor.AcceptingCommandPreprocessor;
import cloud.commandframework.execution.preprocessor.CommandPreprocessingContext;
import cloud.commandframework.execution.preprocessor.CommandPreprocessor;
import cloud.commandframework.internal.CommandInputTokenizer;
import cloud.commandframework.internal.CommandRegistrationHandler;
import cloud.commandframework.meta.CommandMeta;
import cloud.commandframework.permission.AndPermission;
import cloud.commandframework.permission.CommandPermission;
import cloud.commandframework.permission.OrPermission;
import cloud.commandframework.permission.Permission;
import cloud.commandframework.permission.PredicatePermission;
import cloud.commandframework.services.ServicePipeline;
import cloud.commandframework.services.State;
import io.leangen.geantyref.TypeToken;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The manager is responsible for command registration, parsing delegation, etc.
 *
 * @param <C> Command sender type
 */
public abstract class CommandManager<C> {

    private final Map<Class<? extends Exception>, BiConsumer<C, ? extends Exception>> exceptionHandlers = new HashMap<>();
    private final EnumSet<ManagerSettings> managerSettings = EnumSet.of(
            ManagerSettings.ENFORCE_INTERMEDIARY_PERMISSIONS);

    private final CommandContextFactory<C> commandContextFactory = new StandardCommandContextFactory<>();
    private final ServicePipeline servicePipeline = ServicePipeline.builder().build();
    private final ParserRegistry<C> parserRegistry = new StandardParserRegistry<>();
    private final Collection<Command<C>> commands = new LinkedList<>();
    private final ParameterInjectorRegistry<C> parameterInjectorRegistry = new ParameterInjectorRegistry<>();
    private final CommandExecutionCoordinator<C> commandExecutionCoordinator;
    private final CommandTree<C> commandTree;
    private final CommandSuggestionEngine<C> commandSuggestionEngine;

    private CommandSyntaxFormatter<C> commandSyntaxFormatter = new StandardCommandSyntaxFormatter<>();
    private CommandSuggestionProcessor<C> commandSuggestionProcessor = new FilteringCommandSuggestionProcessor<>();
    private CommandRegistrationHandler commandRegistrationHandler;
    private CaptionRegistry<C> captionRegistry;
    private final AtomicReference<RegistrationState> state = new AtomicReference<>(RegistrationState.BEFORE_REGISTRATION);

    /**
     * Create a new command manager instance
     *
     * @param commandExecutionCoordinator Execution coordinator instance. The coordinator is in charge of executing incoming
     *                                    commands. Some considerations must be made when picking a suitable execution coordinator
     *                                    for your platform. For example, an entirely asynchronous coordinator is not suitable
     *                                    when the parsers used in that particular platform are not thread safe. If you have
     *                                    commands that perform blocking operations, however, it might not be a good idea to
     *                                    use a synchronous execution coordinator. In most cases you will want to pick between
     *                                    {@link CommandExecutionCoordinator#simpleCoordinator()} and
     *                                    {@link cloud.commandframework.execution.AsynchronousCommandExecutionCoordinator}
     * @param commandRegistrationHandler  Command registration handler. This will get called every time a new command is
     *                                    registered to the command manager. This may be used to forward command registration
     *                                    to the platform.
     */
    protected CommandManager(
            final @NonNull Function<@NonNull CommandTree<C>, @NonNull CommandExecutionCoordinator<C>> commandExecutionCoordinator,
            final @NonNull CommandRegistrationHandler commandRegistrationHandler
    ) {
        this.commandTree = CommandTree.newTree(this);
        this.commandExecutionCoordinator = commandExecutionCoordinator.apply(this.commandTree);
        this.commandRegistrationHandler = commandRegistrationHandler;
        this.commandSuggestionEngine = new DelegatingCommandSuggestionEngineFactory<>(this).create();
        /* Register service types */
        this.servicePipeline.registerServiceType(new TypeToken<CommandPreprocessor<C>>() {
        }, new AcceptingCommandPreprocessor<>());
        this.servicePipeline.registerServiceType(new TypeToken<CommandPostprocessor<C>>() {
        }, new AcceptingCommandPostprocessor<>());
        /* Create the caption registry */
        this.captionRegistry = new SimpleCaptionRegistryFactory<C>().create();
        /* Register default injectors */
        this.parameterInjectorRegistry().registerInjector(
                CommandContext.class,
                (context, annotationAccessor) -> context
        );
    }

    /**
     * Execute a command and get a future that completes with the result. The command may be executed immediately
     * or at some point in the future, depending on the {@link CommandExecutionCoordinator} used in the command manager.
     * <p>
     * The command may also be filtered out by preprocessors (see {@link CommandPreprocessor}) before they are parsed,
     * or by the {@link CommandArgument} command arguments during parsing. The execution may also be filtered out
     * after parsing by a {@link CommandPostprocessor}. In the case that a command was filtered out at any of the
     * execution stages, the future will complete with {@code null}.
     * <p>
     * The future may also complete exceptionally. The command manager contains some utilities that allow users to
     * register exception handlers ({@link #registerExceptionHandler(Class, BiConsumer)} and these can be retrieved using
     * {@link #getExceptionHandler(Class)}, or used with {@link #handleException(Object, Class, Exception, BiConsumer)}. It
     * is highly recommended that these methods are used in the command manager, as it allows users of the command manager
     * to override the exception handling as they wish.
     *
     * @param commandSender Sender of the command
     * @param input         Input provided by the sender. Prefixes should be removed before the method is being called, and
     *                      the input here will be passed directly to the command parsing pipeline, after having been tokenized.
     * @return future that completes with the command result, or {@code null} if the execution was cancelled at any of the
     *         processing stages.
     */
    public @NonNull CompletableFuture<CommandResult<C>> executeCommand(
            final @NonNull C commandSender,
            final @NonNull String input
    ) {
        final CommandContext<C> context = this.commandContextFactory.create(
                false,
                commandSender,
                this
        );
        final LinkedList<String> inputQueue = new CommandInputTokenizer(input).tokenize();
        /* Store a copy of the input queue in the context */
        context.store("__raw_input__", new LinkedList<>(inputQueue));
        try {
            if (this.preprocessContext(context, inputQueue) == State.ACCEPTED) {
                return this.commandExecutionCoordinator.coordinateExecution(context, inputQueue);
            }
        } catch (final Exception e) {
            final CompletableFuture<CommandResult<C>> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
        /* Wasn't allowed to execute the command */
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Get command suggestions for the "next" argument that would yield a correctly parsing command input. The command
     * suggestions provided by the command argument parsers will be filtered using the {@link CommandSuggestionProcessor}
     * before being returned.
     *
     * @param commandSender Sender of the command
     * @param input         Input provided by the sender. Prefixes should be removed before the method is being called, and
     *                      the input here will be passed directly to the command parsing pipeline, after having been tokenized.
     * @return List of suggestions
     */
    public @NonNull List<@NonNull String> suggest(
            final @NonNull C commandSender,
            final @NonNull String input
    ) {
        final CommandContext<C> context = this.commandContextFactory.create(
                true,
                commandSender,
                this
        );
        return this.commandSuggestionEngine.getSuggestions(context, input);
    }

    /**
     * Register a new command to the command manager and insert it into the underlying command tree. The command will be
     * forwarded to the {@link CommandRegistrationHandler} and will, depending on the platform, be forwarded to the platform.
     * <p>
     * Different command manager implementations have different requirements for the command registration. It is possible
     * that a command manager may only allow registration during certain stages of the application lifetime. Read the platform
     * command manager documentation to find out more about your particular platform
     *
     * @param command Command to register
     * @return The command manager instance. This is returned so that these method calls may be chained. This will always
     *         return {@code this}.
     */
    public @NonNull CommandManager<C> command(final @NonNull Command<C> command) {
        if (!(this.transitionIfPossible(RegistrationState.BEFORE_REGISTRATION, RegistrationState.REGISTERING)
                || this.isCommandRegistrationAllowed())) {
            throw new IllegalStateException("Unable to register commands because the manager is no longer in a registration "
                    + "state. Your platform may allow unsafe registrations by enabling the appropriate manager setting.");
        }
        this.commandTree.insertCommand(command);
        this.commands.add(command);
        return this;
    }

    /**
     * Register a new command
     *
     * @param command Command to register. {@link Command.Builder#build()}} will be invoked.
     * @return The command manager instance
     */
    public @NonNull CommandManager<C> command(final Command.@NonNull Builder<C> command) {
        return this.command(command.manager(this).build());
    }

    /**
     * Get the command syntax formatter
     *
     * @return Command syntax formatter
     */
    public @NonNull CommandSyntaxFormatter<C> getCommandSyntaxFormatter() {
        return this.commandSyntaxFormatter;
    }

    /**
     * Set the command syntax formatter
     *
     * @param commandSyntaxFormatter New formatter
     */
    public void setCommandSyntaxFormatter(final @NonNull CommandSyntaxFormatter<C> commandSyntaxFormatter) {
        this.commandSyntaxFormatter = commandSyntaxFormatter;
    }

    /**
     * Get the command registration handler
     *
     * @return Command registration handler
     */
    public @NonNull CommandRegistrationHandler getCommandRegistrationHandler() {
        return this.commandRegistrationHandler;
    }

    protected final void setCommandRegistrationHandler(final @NonNull CommandRegistrationHandler commandRegistrationHandler) {
        this.requireState(RegistrationState.BEFORE_REGISTRATION);
        this.commandRegistrationHandler = commandRegistrationHandler;
    }

    /**
     * Check if the command sender has the required permission. If the permission node is
     * empty, this should return {@code true}
     *
     * @param sender     Command sender
     * @param permission Permission node
     * @return {@code true} if the sender has the permission, else {@code false}
     */
    @SuppressWarnings("unchecked")
    public boolean hasPermission(
            final @NonNull C sender,
            final @NonNull CommandPermission permission
    ) {
        if (permission instanceof Permission) {
            if (permission.toString().isEmpty()) {
                return true;
            }
            return this.hasPermission(sender, permission.toString());
        } else if (permission instanceof PredicatePermission) {
            return ((PredicatePermission<C>) permission).hasPermission(sender);
        } else if (permission instanceof OrPermission) {
            for (final CommandPermission innerPermission : permission.getPermissions()) {
                if (this.hasPermission(sender, innerPermission)) {
                    return true;
                }
            }
            return false;
        } else if (permission instanceof AndPermission) {
            for (final CommandPermission innerPermission : permission.getPermissions()) {
                if (!this.hasPermission(sender, innerPermission)) {
                    return false;
                }
            }
            return true;
        }

        throw new IllegalArgumentException("Unknown permission type " + permission.getClass());
    }

    /**
     * Get the caption registry
     *
     * @return Caption registry
     */
    public final @NonNull CaptionRegistry<C> getCaptionRegistry() {
        return this.captionRegistry;
    }

    /**
     * Replace the caption registry. Some platforms may inject their own captions into the default registry,
     * and so you may need to insert these captions yourself if you do decide to replace the caption registry.
     *
     * @param captionRegistry New caption registry
     */
    public final void setCaptionRegistry(final @NonNull CaptionRegistry<C> captionRegistry) {
        this.captionRegistry = captionRegistry;
    }

    /**
     * Replace the default caption registry
     *
     * @param captionRegistry Caption registry to use
     * @deprecated Use {@link #setCaptionRegistry(CaptionRegistry)} These methods are identical.
     */
    @Deprecated
    public final void registerDefaultCaptions(final @NonNull CaptionRegistry<C> captionRegistry) {
        this.captionRegistry = captionRegistry;
    }

    /**
     * Check if the command sender has the required permission. If the permission node is
     * empty, this should return {@code true}
     *
     * @param sender     Command sender
     * @param permission Permission node
     * @return {@code true} if the sender has the permission, else {@code false}
     */
    public abstract boolean hasPermission(@NonNull C sender, @NonNull String permission);

    /**
     * Create a new command builder. This will also register the creating manager in the command
     * builder using {@link Command.Builder#manager(CommandManager)}, so that the command
     * builder is associated with the creating manager. This allows for parser inference based on
     * the type, with the help of the {@link ParserRegistry parser registry}
     * <p>
     * This method will not register the command in the manager. To do that, {@link #command(Command.Builder)}
     * or {@link #command(Command)} has to be invoked with either the {@link Command.Builder} instance, or the constructed
     * {@link Command command} instance
     *
     * @param name        Command name
     * @param aliases     Command aliases
     * @param description Description for the root literal
     * @param meta        Command meta
     * @return Builder instance
     * @deprecated for removal since 1.4.0. Use {@link #commandBuilder(String, Collection, Description, CommandMeta)} instead.
     */
    @Deprecated
    public Command.@NonNull Builder<C> commandBuilder(
            final @NonNull String name,
            final @NonNull Collection<String> aliases,
            final @NonNull Description description,
            final @NonNull CommandMeta meta
    ) {
        return this.commandBuilder(name, aliases, (ArgumentDescription) description, meta);
    }

    /**
     * Create a new command builder. This will also register the creating manager in the command
     * builder using {@link Command.Builder#manager(CommandManager)}, so that the command
     * builder is associated with the creating manager. This allows for parser inference based on
     * the type, with the help of the {@link ParserRegistry parser registry}
     * <p>
     * This method will not register the command in the manager. To do that, {@link #command(Command.Builder)}
     * or {@link #command(Command)} has to be invoked with either the {@link Command.Builder} instance, or the constructed
     * {@link Command command} instance
     *
     * @param name        Command name
     * @param aliases     Command aliases
     * @param description Description for the root literal
     * @param meta        Command meta
     * @return Builder instance
     * @since 1.4.0
     */
    public Command.@NonNull Builder<C> commandBuilder(
            final @NonNull String name,
            final @NonNull Collection<String> aliases,
            final @NonNull ArgumentDescription description,
            final @NonNull CommandMeta meta
    ) {
        return Command.<C>newBuilder(
                name,
                meta,
                description,
                aliases.toArray(new String[0])
        ).manager(this);
    }

    /**
     * Create a new command builder with an empty description.
     * <p>
     * This will also register the creating manager in the command
     * builder using {@link Command.Builder#manager(CommandManager)}, so that the command
     * builder is associated with the creating manager. This allows for parser inference based on
     * the type, with the help of the {@link ParserRegistry parser registry}
     * <p>
     * This method will not register the command in the manager. To do that, {@link #command(Command.Builder)}
     * or {@link #command(Command)} has to be invoked with either the {@link Command.Builder} instance, or the constructed
     * {@link Command command} instance
     *
     * @param name    Command name
     * @param aliases Command aliases
     * @param meta    Command meta
     * @return Builder instance
     */
    public Command.@NonNull Builder<C> commandBuilder(
            final @NonNull String name,
            final @NonNull Collection<String> aliases,
            final @NonNull CommandMeta meta
    ) {
        return Command.<C>newBuilder(
                name,
                meta,
                ArgumentDescription.empty(),
                aliases.toArray(new String[0])
        ).manager(this);
    }

    /**
     * Create a new command builder. This will also register the creating manager in the command
     * builder using {@link Command.Builder#manager(CommandManager)}, so that the command
     * builder is associated with the creating manager. This allows for parser inference based on
     * the type, with the help of the {@link ParserRegistry parser registry}
     * <p>
     * This method will not register the command in the manager. To do that, {@link #command(Command.Builder)}
     * or {@link #command(Command)} has to be invoked with either the {@link Command.Builder} instance, or the constructed
     * {@link Command command} instance
     *
     * @param name        Command name
     * @param meta        Command meta
     * @param description Description for the root literal
     * @param aliases     Command aliases
     * @return Builder instance
     * @deprecated for removal since 1.4.0. Use {@link #commandBuilder(String, CommandMeta, ArgumentDescription, String...)}
     *      instead.
     */
    @Deprecated
    public Command.@NonNull Builder<C> commandBuilder(
            final @NonNull String name,
            final @NonNull CommandMeta meta,
            final @NonNull Description description,
            final @NonNull String... aliases
    ) {
        return this.commandBuilder(name, meta, (ArgumentDescription) description, aliases);
    }

    /**
     * Create a new command builder. This will also register the creating manager in the command
     * builder using {@link Command.Builder#manager(CommandManager)}, so that the command
     * builder is associated with the creating manager. This allows for parser inference based on
     * the type, with the help of the {@link ParserRegistry parser registry}
     * <p>
     * This method will not register the command in the manager. To do that, {@link #command(Command.Builder)}
     * or {@link #command(Command)} has to be invoked with either the {@link Command.Builder} instance, or the constructed
     * {@link Command command} instance
     *
     * @param name        Command name
     * @param meta        Command meta
     * @param description Description for the root literal
     * @param aliases     Command aliases
     * @return Builder instance
     * @since 1.4.0
     */
    public Command.@NonNull Builder<C> commandBuilder(
            final @NonNull String name,
            final @NonNull CommandMeta meta,
            final @NonNull ArgumentDescription description,
            final @NonNull String... aliases
    ) {
        return Command.<C>newBuilder(
                name,
                meta,
                description,
                aliases
        ).manager(this);
    }

    /**
     * Create a new command builder with an empty description.
     * <p>
     * This will also register the creating manager in the command
     * builder using {@link Command.Builder#manager(CommandManager)}, so that the command
     * builder is associated with the creating manager. This allows for parser inference based on
     * the type, with the help of the {@link ParserRegistry parser registry}
     * <p>
     * This method will not register the command in the manager. To do that, {@link #command(Command.Builder)}
     * or {@link #command(Command)} has to be invoked with either the {@link Command.Builder} instance, or the constructed
     * {@link Command command} instance
     *
     * @param name    Command name
     * @param meta    Command meta
     * @param aliases Command aliases
     * @return Builder instance
     */
    public Command.@NonNull Builder<C> commandBuilder(
            final @NonNull String name,
            final @NonNull CommandMeta meta,
            final @NonNull String... aliases
    ) {
        return Command.<C>newBuilder(
                name,
                meta,
                ArgumentDescription.empty(),
                aliases
        ).manager(this);
    }

    /**
     * Create a new command builder using default command meta created by {@link #createDefaultCommandMeta()}.
     * <p>
     * This will also register the creating manager in the command
     * builder using {@link Command.Builder#manager(CommandManager)}, so that the command
     * builder is associated with the creating manager. This allows for parser inference based on
     * the type, with the help of the {@link ParserRegistry parser registry}
     * <p>
     * This method will not register the command in the manager. To do that, {@link #command(Command.Builder)}
     * or {@link #command(Command)} has to be invoked with either the {@link Command.Builder} instance, or the constructed
     * {@link Command command} instance
     *
     * @param name        Command name
     * @param description Description for the root literal
     * @param aliases     Command aliases
     * @return Builder instance
     * @throws UnsupportedOperationException If the command manager does not support default command meta creation
     * @see #createDefaultCommandMeta() Default command meta creation
     * @deprecated for removal since 1.4.0. Use {@link #commandBuilder(String, ArgumentDescription, String...)} instead.
     */
    @Deprecated
    public Command.@NonNull Builder<C> commandBuilder(
            final @NonNull String name,
            final @NonNull Description description,
            final @NonNull String... aliases
    ) {
        return this.commandBuilder(name, (ArgumentDescription) description, aliases);
    }

    /**
     * Create a new command builder using default command meta created by {@link #createDefaultCommandMeta()}.
     * <p>
     * This will also register the creating manager in the command
     * builder using {@link Command.Builder#manager(CommandManager)}, so that the command
     * builder is associated with the creating manager. This allows for parser inference based on
     * the type, with the help of the {@link ParserRegistry parser registry}
     * <p>
     * This method will not register the command in the manager. To do that, {@link #command(Command.Builder)}
     * or {@link #command(Command)} has to be invoked with either the {@link Command.Builder} instance, or the constructed
     * {@link Command command} instance
     *
     * @param name        Command name
     * @param description Description for the root literal
     * @param aliases     Command aliases
     * @return Builder instance
     * @throws UnsupportedOperationException If the command manager does not support default command meta creation
     * @see #createDefaultCommandMeta() Default command meta creation
     * @since 1.4.0
     */
    public Command.@NonNull Builder<C> commandBuilder(
            final @NonNull String name,
            final @NonNull ArgumentDescription description,
            final @NonNull String... aliases
    ) {
        return Command.<C>newBuilder(
                name,
                this.createDefaultCommandMeta(),
                description,
                aliases
        ).manager(this);
    }

    /**
     * Create a new command builder using default command meta created by {@link #createDefaultCommandMeta()}, and
     * an empty description.
     * <p>
     * This will also register the creating manager in the command
     * builder using {@link Command.Builder#manager(CommandManager)}, so that the command
     * builder is associated with the creating manager. This allows for parser inference based on
     * the type, with the help of the {@link ParserRegistry parser registry}
     * <p>
     * This method will not register the command in the manager. To do that, {@link #command(Command.Builder)}
     * or {@link #command(Command)} has to be invoked with either the {@link Command.Builder} instance, or the constructed
     * {@link Command command} instance
     *
     * @param name    Command name
     * @param aliases Command aliases
     * @return Builder instance
     * @throws UnsupportedOperationException If the command manager does not support default command meta creation
     * @see #createDefaultCommandMeta() Default command meta creation
     */
    public Command.@NonNull Builder<C> commandBuilder(
            final @NonNull String name,
            final @NonNull String... aliases
    ) {
        return Command.<C>newBuilder(
                name,
                this.createDefaultCommandMeta(),
                ArgumentDescription.empty(),
                aliases
        ).manager(this);
    }

    /**
     * Create a new command argument builder.
     * <p>
     * This will also invoke {@link CommandArgument.Builder#manager(CommandManager)}
     * so that the argument is associated with the calling command manager. This allows for parser inference based on
     * the type, with the help of the {@link ParserRegistry parser registry}.
     *
     * @param type Argument type
     * @param name Argument name
     * @param <T>  Generic argument name
     * @return Argument builder
     */
    public <T> CommandArgument.@NonNull Builder<C, T> argumentBuilder(
            final @NonNull Class<T> type,
            final @NonNull String name
    ) {
        return CommandArgument.<C, T>ofType(type, name).manager(this);
    }

    /**
     * Create a new command flag builder
     *
     * @param name Flag name
     * @return Flag builder
     */
    public CommandFlag.@NonNull Builder<Void> flagBuilder(final @NonNull String name) {
        return CommandFlag.newBuilder(name);
    }

    /**
     * Get the internal command tree. This should not be accessed unless you know what you
     * are doing
     *
     * @return Command tree
     */
    public @NonNull CommandTree<C> getCommandTree() {
        return this.commandTree;
    }

    /**
     * Construct a default command meta instance
     *
     * @return Default command meta
     * @throws UnsupportedOperationException If the command manager does not support this operation
     */
    public abstract @NonNull CommandMeta createDefaultCommandMeta();

    /**
     * Register a new command preprocessor. The order they are registered in is respected, and they
     * are called in LIFO order
     *
     * @param processor Processor to register
     * @see #preprocessContext(CommandContext, LinkedList) Preprocess a context
     */
    public void registerCommandPreProcessor(final @NonNull CommandPreprocessor<C> processor) {
        this.servicePipeline.registerServiceImplementation(
                new TypeToken<CommandPreprocessor<C>>() {
                },
                processor,
                Collections.emptyList()
        );
    }

    /**
     * Register a new command postprocessor. The order they are registered in is respected, and they
     * are called in LIFO order
     *
     * @param processor Processor to register
     * @see #preprocessContext(CommandContext, LinkedList) Preprocess a context
     */
    public void registerCommandPostProcessor(final @NonNull CommandPostprocessor<C> processor) {
        this.servicePipeline.registerServiceImplementation(new TypeToken<CommandPostprocessor<C>>() {
                                                           }, processor,
                Collections.emptyList()
        );
    }

    /**
     * Preprocess a command context instance
     *
     * @param context    Command context
     * @param inputQueue Command input as supplied by sender
     * @return {@link State#ACCEPTED} if the command should be parsed and executed, else {@link State#REJECTED}
     * @see #registerCommandPreProcessor(CommandPreprocessor) Register a command preprocessor
     */
    public State preprocessContext(
            final @NonNull CommandContext<C> context,
            final @NonNull LinkedList<@NonNull String> inputQueue
    ) {
        this.servicePipeline.pump(new CommandPreprocessingContext<>(context, inputQueue))
                .through(new TypeToken<CommandPreprocessor<C>>() {
                })
                .getResult();
        return context.<String>getOptional(AcceptingCommandPreprocessor.PROCESSED_INDICATOR_KEY).orElse("").isEmpty()
                ? State.REJECTED
                : State.ACCEPTED;
    }

    /**
     * Postprocess a command context instance
     *
     * @param context Command context
     * @param command Command instance
     * @return {@link State#ACCEPTED} if the command should be parsed and executed, else {@link State#REJECTED}
     * @see #registerCommandPostProcessor(CommandPostprocessor) Register a command postprocessor
     */
    public State postprocessContext(
            final @NonNull CommandContext<C> context,
            final @NonNull Command<C> command
    ) {
        this.servicePipeline.pump(new CommandPostprocessingContext<>(context, command))
                .through(new TypeToken<CommandPostprocessor<C>>() {
                })
                .getResult();
        return context.<String>getOptional(AcceptingCommandPostprocessor.PROCESSED_INDICATOR_KEY).orElse("").isEmpty()
                ? State.REJECTED
                : State.ACCEPTED;
    }

    /**
     * Get the command suggestions processor instance currently used in this command manager
     *
     * @return Command suggestions processor
     * @see #setCommandSuggestionProcessor(CommandSuggestionProcessor) Setting the suggestion processor
     */
    public @NonNull CommandSuggestionProcessor<C> getCommandSuggestionProcessor() {
        return this.commandSuggestionProcessor;
    }

    /**
     * Set the command suggestions processor for this command manager. This will be called every
     * time {@link #suggest(Object, String)} is called, to process the list of suggestions
     * before it's returned to the caller
     *
     * @param commandSuggestionProcessor New command suggestions processor
     */
    public void setCommandSuggestionProcessor(final @NonNull CommandSuggestionProcessor<C> commandSuggestionProcessor) {
        this.commandSuggestionProcessor = commandSuggestionProcessor;
    }

    /**
     * Get the parser registry instance. The parser registry contains default
     * mappings to {@link ArgumentParser}
     * and allows for the registration of custom mappings. The parser registry also
     * contains mappings of annotations to {@link ParserParameter}
     * which allows for annotations to be used to customize parser settings.
     * <p>
     * When creating a new parser type, it is recommended to register it in the parser
     * registry. In particular, default parser types (shipped with cloud implementations)
     * should be registered in the constructor of the platform {@link CommandManager}
     *
     * @return Parser registry instance
     */
    public ParserRegistry<C> getParserRegistry() {
        return this.parserRegistry;
    }

    /**
     * Get the parameter injector registry instance
     *
     * @return Parameter injector registry
     * @since 1.3.0
     */
    public final @NonNull ParameterInjectorRegistry<C> parameterInjectorRegistry() {
        return this.parameterInjectorRegistry;
    }


    /**
     * Get the exception handler for an exception type, if one has been registered
     *
     * @param clazz Exception class
     * @param <E>   Exception type
     * @return Exception handler, or {@code null}
     * @see #registerCommandPreProcessor(CommandPreprocessor) Registering an exception handler
     */
    @SuppressWarnings("unchecked")
    public final <E extends Exception> @Nullable BiConsumer<@NonNull C, @NonNull E>
    getExceptionHandler(final @NonNull Class<E> clazz) {
        final BiConsumer<C, ? extends Exception> consumer = this.exceptionHandlers.get(clazz);
        if (consumer == null) {
            return null;
        }
        return (BiConsumer<C, E>) consumer;
    }

    /**
     * Register an exception handler for an exception type. This will then be used
     * when {@link #handleException(Object, Class, Exception, BiConsumer)} is called
     * for the particular exception type
     *
     * @param clazz   Exception class
     * @param handler Exception handler
     * @param <E>     Exception type
     */
    public final <E extends Exception> void registerExceptionHandler(
            final @NonNull Class<E> clazz,
            final @NonNull BiConsumer<@NonNull C, @NonNull E> handler
    ) {
        this.exceptionHandlers.put(clazz, handler);
    }

    /**
     * Handle an exception using the registered exception handler for the exception type, or using the
     * provided default handler if no exception handler has been registered for the exception type
     *
     * @param sender         Executing command sender
     * @param clazz          Exception class
     * @param exception      Exception instance
     * @param defaultHandler Default exception handler. Will be called if there is no exception
     *                       handler stored for the exception type
     * @param <E>            Exception type
     */
    public final <E extends Exception> void handleException(
            final @NonNull C sender,
            final @NonNull Class<E> clazz,
            final @NonNull E exception,
            final @NonNull BiConsumer<C, E> defaultHandler
    ) {
        Optional.ofNullable(this.getExceptionHandler(clazz)).orElse(defaultHandler).accept(sender, exception);
    }

    /**
     * Get a collection containing all registered commands.
     *
     * @return Unmodifiable view of all registered commands
     */
    public final @NonNull Collection<@NonNull Command<C>> getCommands() {
        return Collections.unmodifiableCollection(this.commands);
    }

    /**
     * Get a command help handler instance. This can be used to assist in the production
     * of command help menus, etc. This command help handler instance will display
     * all commands registered in this command manager.
     *
     * @return Command help handler. A new instance will be created
     *         each time this method is called.
     */
    public final @NonNull CommandHelpHandler<C> getCommandHelpHandler() {
        return new CommandHelpHandler<>(this, cmd -> true);
    }

    /**
     * Get a command help handler instance. This can be used to assist in the production
     * of command help menus, etc. A predicate can be specified to filter what commands
     * registered in this command manager are visible in the help menu.
     *
     * @param commandPredicate Predicate that filters what commands are displayed in
     *                         the help menu.
     * @return Command help handler. A new instance will be created
     *         each time this method is called.
     */
    public final @NonNull CommandHelpHandler<C> getCommandHelpHandler(
            final @NonNull Predicate<Command<C>> commandPredicate
    ) {
        return new CommandHelpHandler<>(this, commandPredicate);
    }

    /**
     * Get a command manager setting
     *
     * @param setting Setting
     * @return {@code true} if the setting is activated or {@code false} if it's not
     * @see #setSetting(ManagerSettings, boolean) Update a manager setting
     */
    public boolean getSetting(final @NonNull ManagerSettings setting) {
        return this.managerSettings.contains(setting);
    }

    /**
     * Update a command manager setting
     *
     * @param setting Setting to update
     * @param value   Value. In most cases {@code true} will enable a feature, whereas {@code false} will disable it.
     *                The value passed to the method will be reflected in {@link #getSetting(ManagerSettings)}
     * @see #getSetting(ManagerSettings) Get a manager setting
     */
    @SuppressWarnings("unused")
    public void setSetting(
            final @NonNull ManagerSettings setting,
            final boolean value
    ) {
        if (value) {
            this.managerSettings.add(setting);
        } else {
            this.managerSettings.remove(setting);
        }
    }

    /**
     * Returns the command execution coordinator used in this manager
     *
     * @return Command execution coordinator
     * @since 1.6.0
     */
    public @NonNull CommandExecutionCoordinator<C> commandExecutionCoordinator() {
        return this.commandExecutionCoordinator;
    }

    /**
     * Transition from the {@code in} state to the {@code out} state, if the manager is not already in that state.
     *
     * @param in  The starting state
     * @param out The ending state
     * @throws IllegalStateException if the manager is in any state but {@code in} or {@code out}
     * @since 1.2.0
     */
    protected final void transitionOrThrow(final @NonNull RegistrationState in, final @NonNull RegistrationState out) {
        if (!this.transitionIfPossible(in, out)) {
            throw new IllegalStateException("Command manager was in state " + this.state.get() + ", while we were expecting a state "
                    + "of " + in + " or " + out + "!");
        }
    }

    /**
     * Transition from the {@code in} state to the {@code out} state, if the manager is not already in that state.
     *
     * @param in  The starting state
     * @param out The ending state
     * @return {@code true} if the state transition was successful, or the manager was already in the desired state
     * @since 1.2.0
     */
    protected final boolean transitionIfPossible(final @NonNull RegistrationState in, final @NonNull RegistrationState out) {
        return this.state.compareAndSet(in, out) || this.state.get() == out;
    }

    /**
     * Require that the commands manager is in a certain state.
     *
     * @param expected The required state
     * @throws IllegalStateException if the manager is not in the expected state
     * @since 1.2.0
     */
    protected final void requireState(final @NonNull RegistrationState expected) {
        if (this.state.get() != expected) {
            throw new IllegalStateException("This operation required the commands manager to be in state " + expected + ", but it "
                    + "was in " + this.state.get() + " instead!");
        }
    }

    /**
     * Transition the command manager from either {@link RegistrationState#BEFORE_REGISTRATION} or
     * {@link RegistrationState#REGISTERING} to {@link RegistrationState#AFTER_REGISTRATION}.
     *
     * @throws IllegalStateException if the manager is not in the expected state
     * @since 1.4.0
     */
    protected final void lockRegistration() {
        if (this.getRegistrationState() == RegistrationState.BEFORE_REGISTRATION) {
            this.transitionOrThrow(RegistrationState.BEFORE_REGISTRATION, RegistrationState.AFTER_REGISTRATION);
            return;
        }
        this.transitionOrThrow(RegistrationState.REGISTERING, RegistrationState.AFTER_REGISTRATION);
    }

    /**
     * Get the active registration state for this manager.
     * <p>
     * If this state is {@link RegistrationState#AFTER_REGISTRATION}, commands can no longer be registered
     *
     * @return The current state
     * @since 1.2.0
     */
    public final @NonNull RegistrationState getRegistrationState() {
        return this.state.get();
    }

    /**
     * Check if command registration is allowed.
     * <p>
     * On platforms where unsafe registration is possible, this can be overridden by enabling the
     * {@link ManagerSettings#ALLOW_UNSAFE_REGISTRATION} setting.
     *
     * @return {@code true} if the registration is allowed, else {@code false}
     * @since 1.2.0
     */
    public boolean isCommandRegistrationAllowed() {
        return this.getSetting(ManagerSettings.ALLOW_UNSAFE_REGISTRATION) || this.state.get() != RegistrationState.AFTER_REGISTRATION;
    }

    /**
     * Configurable command related settings
     *
     * @see CommandManager#setSetting(ManagerSettings, boolean) Set a manager setting
     * @see CommandManager#getSetting(ManagerSettings) Get a manager setting
     */
    public enum ManagerSettings {
        /**
         * Do not create a compound permission and do not look greedily
         * for child permission values, if a preceding command in the tree path
         * has a command handler attached
         */
        ENFORCE_INTERMEDIARY_PERMISSIONS,

        /**
         * Force sending of an empty suggestion (i.e. a singleton list containing an empty string)
         * when no suggestions are present
         */
        FORCE_SUGGESTION,

        /**
         * Allow registering commands even when doing so has the potential to produce inconsistent results.
         * <p>
         * For example, if a platform serializes the command tree and sends it to clients,
         * this will allow modifying the command tree after it has been sent, as long as these modifications are not blocked by
         * the underlying platform
         *
         * @since 1.2.0
         */
        ALLOW_UNSAFE_REGISTRATION,

        /**
         * Enables overriding of existing commands on supported platforms.
         *
         * @since 1.2.0
         */
        OVERRIDE_EXISTING_COMMANDS
    }

    /**
     * The point in the registration lifecycle for this commands manager
     *
     * @since 1.2.0
     */
    public enum RegistrationState {
        /**
         * The point when no commands have been registered yet.
         *
         * <p>At this point, all configuration options can be changed.</p>
         */
        BEFORE_REGISTRATION,

        /**
         * When at least one command has been registered, and more commands have been registered.
         *
         * <p>In this state, some options that affect how commands are registered with the platform are frozen. Some platforms
         * will remain in this state for their lifetime.</p>
         */
        REGISTERING,

        /**
         * Once registration has been completed.
         *
         * <p>At this point, the command manager is effectively immutable. On platforms where command registration happens via
         * callback, this state is achieved the first time the manager's callback is executed for registration.</p>
         */
        AFTER_REGISTRATION
    }

}
