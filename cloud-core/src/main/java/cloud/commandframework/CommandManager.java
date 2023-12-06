//
// MIT License
//
// Copyright (c) 2022 Alexander Söderberg & Contributors
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
import cloud.commandframework.arguments.CommandSyntaxFormatter;
import cloud.commandframework.arguments.StandardCommandSyntaxFormatter;
import cloud.commandframework.arguments.flags.CommandFlag;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.arguments.parser.ParserParameter;
import cloud.commandframework.arguments.parser.ParserRegistry;
import cloud.commandframework.arguments.parser.StandardParserRegistry;
import cloud.commandframework.arguments.suggestion.Suggestion;
import cloud.commandframework.arguments.suggestion.SuggestionFactory;
import cloud.commandframework.arguments.suggestion.SuggestionMapper;
import cloud.commandframework.captions.CaptionRegistry;
import cloud.commandframework.captions.CaptionVariableReplacementHandler;
import cloud.commandframework.captions.SimpleCaptionRegistryFactory;
import cloud.commandframework.captions.SimpleCaptionVariableReplacementHandler;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.context.CommandContextFactory;
import cloud.commandframework.context.CommandInput;
import cloud.commandframework.context.StandardCommandContextFactory;
import cloud.commandframework.exceptions.handling.ExceptionController;
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
import cloud.commandframework.internal.CommandNode;
import cloud.commandframework.internal.CommandRegistrationHandler;
import cloud.commandframework.meta.CommandMeta;
import cloud.commandframework.permission.AndPermission;
import cloud.commandframework.permission.CommandPermission;
import cloud.commandframework.permission.OrPermission;
import cloud.commandframework.permission.Permission;
import cloud.commandframework.permission.PermissionResult;
import cloud.commandframework.permission.PredicatePermission;
import cloud.commandframework.services.ServicePipeline;
import cloud.commandframework.services.State;
import io.leangen.geantyref.TypeToken;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.apiguardian.api.API;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.returnsreceiver.qual.This;

/**
 * The manager is responsible for command registration, parsing delegation, etc.
 *
 * @param <C> the command sender type used to execute commands
 */
@API(status = API.Status.STABLE)
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
    private final SuggestionFactory<C, ? extends Suggestion> suggestionFactory;
    private final Set<CloudCapability> capabilities = new HashSet<>();
    private final ExceptionController<C> exceptionController = new ExceptionController<>();

    private CaptionVariableReplacementHandler captionVariableReplacementHandler = new SimpleCaptionVariableReplacementHandler();
    private CommandSyntaxFormatter<C> commandSyntaxFormatter = new StandardCommandSyntaxFormatter<>();
    private CommandSuggestionProcessor<C> commandSuggestionProcessor =
            new FilteringCommandSuggestionProcessor<>(FilteringCommandSuggestionProcessor.Filter.startsWith(true));
    private CommandRegistrationHandler<C> commandRegistrationHandler;
    private CaptionRegistry<C> captionRegistry;
    private SuggestionMapper<? extends Suggestion> suggestionMapper = SuggestionMapper.identity();
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
            final @NonNull CommandRegistrationHandler<C> commandRegistrationHandler
    ) {
        this.commandTree = CommandTree.newTree(this);
        this.commandExecutionCoordinator = commandExecutionCoordinator.apply(this.commandTree);
        this.commandRegistrationHandler = commandRegistrationHandler;
        this.suggestionFactory = SuggestionFactory.delegating(this, (suggestion) -> this.suggestionMapper.map(suggestion));
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
     * The future may also complete exceptionally.
     * These exceptions may be handled using exception handlers registered in the {@link #exceptionController()}.
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
        final CommandInput commandInput = CommandInput.of(input);
        /* Store a copy of the input queue in the context */
        context.store("__raw_input__", commandInput.copy());
        try {
            if (this.preprocessContext(context, commandInput) == State.ACCEPTED) {
                return this.commandExecutionCoordinator.coordinateExecution(context, commandInput);
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
     * Returns the suggestion factory.
     * <p>
     * Platform implementations of command manager may override this method to make it easier to work with platform
     * suggestion types, for example:
     * <pre>{@code
     * @Override
     * public @NonNull SuggestionFactory<C, YourType> suggestionFactory() {
     *    return super.suggestionFactory().mapped(suggestion -> yourType);
     * }
     * }</pre>
     * @return the suggestion factory
     * @since 2.0.0
     */
    @API(status = API.Status.STABLE, since = "2.0.0")
    public @NonNull SuggestionFactory<C, ? extends Suggestion> suggestionFactory() {
        return this.suggestionFactory;
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
    @SuppressWarnings("unchecked")
    public @NonNull @This CommandManager<C> command(final @NonNull Command<? extends C> command) {
        if (!(this.transitionIfPossible(RegistrationState.BEFORE_REGISTRATION, RegistrationState.REGISTERING)
                || this.isCommandRegistrationAllowed())) {
            throw new IllegalStateException("Unable to register commands because the manager is no longer in a registration "
                    + "state. Your platform may allow unsafe registrations by enabling the appropriate manager setting.");
        }
        this.commandTree.insertCommand((Command<C>) command);
        this.commands.add((Command<C>) command);
        return this;
    }

    /**
     * Creates a command using the given {@code commandFactory} and inserts it into the underlying command tree. The command
     * will be forwarded to the {@link CommandRegistrationHandler} and will, depending on the platform, be forwarded to the
     * platform.
     * <p>
     * Different command manager implementations have different requirements for the command registration. It is possible
     * that a command manager may only allow registration during certain stages of the application lifetime. Read the platform
     * command manager documentation to find out more about your particular platform
     *
     * @param commandFactory the command factory to register
     * @return The command manager instance. This is returned so that these method calls may be chained. This will always
     *         return {@code this}
     * @since 2.0.0
     */
    @API(status = API.Status.STABLE, since = "2.0.0")
    public @NonNull @This CommandManager<C> command(final @NonNull CommandFactory<C> commandFactory) {
        commandFactory.createCommands(this).forEach(this::command);
        return this;
    }

    /**
     * Register a new command
     *
     * @param command Command to register. {@link Command.Builder#build()}} will be invoked.
     * @return The command manager instance
     */
    @SuppressWarnings("unchecked")
    public @NonNull CommandManager<C> command(final Command.@NonNull Builder<? extends C> command) {
        return this.command(((Command.Builder<C>) command).manager(this).build());
    }

    /**
     * Returns the factory used to create {@link CommandContext} instances.
     *
     * @return the command context factory
     * @since 2.0.0
     */
    @API(status = API.Status.STABLE, since = "2.0.0")
    public @NonNull CommandContextFactory<C> commandContextFactory() {
        return this.commandContextFactory;
    }

    /**
     * Get the caption variable replacement handler.
     *
     * @return the caption variable replacement handler
     * @since 1.7.0
     * @see #captionVariableReplacementHandler(CaptionVariableReplacementHandler)
     */
    @API(status = API.Status.STABLE, since = "1.7.0")
    public @NonNull CaptionVariableReplacementHandler captionVariableReplacementHandler() {
        return this.captionVariableReplacementHandler;
    }

    /**
     * Sets the caption variable replacement handler.
     *
     * @param captionVariableReplacementHandler new replacement handler
     * @since 1.7.0
     * @see #captionVariableReplacementHandler()
     */
    @API(status = API.Status.STABLE, since = "1.7.0")
    public void captionVariableReplacementHandler(
            final @NonNull CaptionVariableReplacementHandler captionVariableReplacementHandler
    ) {
        this.captionVariableReplacementHandler = captionVariableReplacementHandler;
    }

    /**
     * Returns the command syntax formatter.
     *
     * @return the syntax formatter
     * @since 1.7.0
     * @see #commandSyntaxFormatter(CommandSyntaxFormatter)
     */
    @API(status = API.Status.STABLE, since = "1.7.0")
    public @NonNull CommandSyntaxFormatter<C> commandSyntaxFormatter() {
        return this.commandSyntaxFormatter;
    }

    /**
     * Sets the command syntax formatter.
     * <p>
     * The command syntax formatter is used to format the command syntax hints that are used in help and error messages.
     *
     * @param commandSyntaxFormatter new formatter
     * @since 1.7.0
     * @see #commandSyntaxFormatter()
     */
    @API(status = API.Status.STABLE, since = "1.7.0")
    public void commandSyntaxFormatter(final @NonNull CommandSyntaxFormatter<C> commandSyntaxFormatter) {
        this.commandSyntaxFormatter = commandSyntaxFormatter;
    }

    /**
     * Returns the command registration handler.
     * <p>
     * The command registration handler is able to intercept newly created/deleted commands, in order to propagate
     * these changes to the native command handler of the platform.
     * <p>
     * In platforms without a native command concept, this is likely to return
     * {@link CommandRegistrationHandler#nullCommandRegistrationHandler()}.
     *
     * @return the command registration handler
     * @since 1.7.0
     */
    public @NonNull CommandRegistrationHandler<C> commandRegistrationHandler() {
        return this.commandRegistrationHandler;
    }

    @API(status = API.Status.STABLE, since = "1.7.0")
    protected final void commandRegistrationHandler(final @NonNull CommandRegistrationHandler<C> commandRegistrationHandler) {
        this.requireState(RegistrationState.BEFORE_REGISTRATION);
        this.commandRegistrationHandler = commandRegistrationHandler;
    }

    /**
     * Registers the given {@code capability}.
     *
     * @param capability the capability
     * @since 1.7.0
     * @see #hasCapability(CloudCapability)
     * @see #capabilities()
     */
    @API(status = API.Status.STABLE, since = "1.7.0")
    protected final void registerCapability(final @NonNull CloudCapability capability) {
        this.capabilities.add(capability);
    }

    /**
     * Checks whether the cloud implementation has the given {@code capability}.
     *
     * @param capability the capability
     * @return {@code true} if the implementation has the {@code capability}, {@code false} if not
     * @since 1.7.0
     * @see #capabilities()
     */
    @API(status = API.Status.STABLE, since = "1.7.0")
    public boolean hasCapability(final @NonNull CloudCapability capability) {
        return this.capabilities.contains(capability);
    }

    /**
     * Returns an unmodifiable snapshot of the currently registered {@link CloudCapability capabilities}.
     *
     * @return the currently registered capabilities
     * @since 1.7.0
     * @see #hasCapability(CloudCapability)
     */
    @API(status = API.Status.STABLE, since = "1.7.0")
    public @NonNull Collection<@NonNull CloudCapability> capabilities() {
        return Collections.unmodifiableSet(new HashSet<>(this.capabilities));
    }

    /**
     * Checks if the command sender has the required permission. If the permission node is
     * empty, this should return {@code true}.
     *
     * @param sender     Command sender
     * @param permission Permission node
     * @return {@code true} if the sender has the permission, else {@code false}
     */
    public boolean hasPermission(
            final @NonNull C sender,
            final @NonNull CommandPermission permission
    ) {
        return this.testPermission(sender, permission).toBoolean();
    }

    /**
     * Checks if the command sender has the required permission. If the permission node is
     * empty, this should return {@code true}.
     *
     * @param sender     Command sender
     * @param permission Permission node
     * @return a {@link PermissionResult} representing whether the sender has the permission
     * @since 2.0.0
     */
    @API(status = API.Status.STABLE, since = "2.0.0")
    @SuppressWarnings("unchecked")
    public @NonNull PermissionResult testPermission(
            final @NonNull C sender,
            final @NonNull CommandPermission permission
    ) {
        if (permission.isEmpty()) {
            return PermissionResult.TRUE;
        } else if (permission instanceof Permission) {
            return PermissionResult.of(this.hasPermission(sender, permission.toString()));
        } else if (permission instanceof PredicatePermission) {
            return ((PredicatePermission<C>) permission).testPermission(sender);
        }

        if (permission instanceof OrPermission) {
            for (final CommandPermission innerPermission : permission.getPermissions()) {
                final PermissionResult result = this.testPermission(sender, innerPermission);
                if (result.toBoolean()) {
                    return result; // short circuit the first true result
                }
            }
            return PermissionResult.FALSE; // none returned true
        } else if (permission instanceof AndPermission) {
            for (final CommandPermission innerPermission : permission.getPermissions()) {
                final PermissionResult result = this.testPermission(sender, innerPermission);
                if (!result.toBoolean()) {
                    return result; // short circuit the first false result
                }
            }
            return PermissionResult.TRUE; // all returned true
        }

        throw new IllegalArgumentException("Unknown permission type " + permission.getClass());
    }

    /**
     * Returns the caption registry.
     *
     * @return the caption registry
     * @since 1.7.0
     * @see #captionRegistry(CaptionRegistry)
     */
    @API(status = API.Status.STABLE, since = "1.7.0")
    public final @NonNull CaptionRegistry<C> captionRegistry() {
        return this.captionRegistry;
    }

    /**
     * Replaces the caption registry.
     * <p>
     * Some platforms may inject their own captions into the default caption registry,
     * and so you may need to insert these captions yourself, if you do decide to replace the caption registry.
     *
     * @param captionRegistry new caption registry.
     * @see #captionRegistry()
     * @since 1.7.0
     */
    @API(status = API.Status.STABLE, since = "1.7.0")
    public final void captionRegistry(final @NonNull CaptionRegistry<C> captionRegistry) {
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
     * Sets the suggestion mapper.
     * <p>
     * The suggestion mapper is invoked after the suggestions have been generated by the {@link CommandComponent command
     * components} in the {@link CommandTree command tree}, but before the platform mappers configured using
     * {@link #suggestionFactory()} gets to map the suggestions.
     * This means that you can perform custom mapping to the platform-native suggestion types using your suggestion mapper.
     *
     * @param <S>              the custom type
     * @param suggestionMapper the suggestion mapper
     * @since 2.0.0
     */
    @API(status = API.Status.STABLE, since = "2.0.0")
    public <S extends Suggestion> void suggestionMapper(final @NonNull SuggestionMapper<S> suggestionMapper) {
        this.suggestionMapper = suggestionMapper;
    }

    /**
     * Deletes the given {@code rootCommand}.
     * <p>
     * This will delete all chains that originate at the root command.
     *
     * @param rootCommand The root command to delete
     * @throws CloudCapability.CloudCapabilityMissingException If {@link CloudCapability.StandardCapabilities#ROOT_COMMAND_DELETION} is missing
     * @since 1.7.0
     */
    @API(status = API.Status.EXPERIMENTAL, since = "1.7.0")
    public void deleteRootCommand(final @NonNull String rootCommand) throws CloudCapability.CloudCapabilityMissingException {
        if (!this.hasCapability(CloudCapability.StandardCapabilities.ROOT_COMMAND_DELETION)) {
            throw new CloudCapability.CloudCapabilityMissingException(CloudCapability.StandardCapabilities.ROOT_COMMAND_DELETION);
        }

        // Mark the command for deletion.
        final CommandNode<C> node = this.commandTree.getNamedNode(rootCommand);
        if (node == null || node.component() == null) {
            // If the node doesn't exist, we don't really need to delete it...
            return;
        }

        // The registration handler gets to act before we destruct the command.
        this.commandRegistrationHandler.unregisterRootCommand(node.component());

        // We then delete it from the tree.
        this.commandTree.deleteRecursively(node, true, this.commands::remove);

        // And lastly we re-build the entire tree.
        this.commandTree.verifyAndRegister();
    }

    /**
     * Returns all root command names.
     *
     * @return Root command names.
     * @since 1.7.0
     */
    @SuppressWarnings("unchecked")
    @API(status = API.Status.STABLE, since = "1.7.0")
    public @NonNull Collection<@NonNull String> rootCommands() {
        return this.commandTree.rootNodes()
                .stream()
                .map(CommandNode::component)
                .filter(Objects::nonNull)
                .filter(component -> component.type() == CommandComponent.ComponentType.LITERAL)
                .map(CommandComponent::name)
                .collect(Collectors.toList());
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
    @API(status = API.Status.STABLE, since = "1.4.0")
    public Command.@NonNull Builder<C> commandBuilder(
            final @NonNull String name,
            final @NonNull Collection<String> aliases,
            final @NonNull Description description,
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
                Description.empty(),
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
     * @since 1.4.0
     */
    @API(status = API.Status.STABLE, since = "1.4.0")
    public Command.@NonNull Builder<C> commandBuilder(
            final @NonNull String name,
            final @NonNull CommandMeta meta,
            final @NonNull Description description,
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
                Description.empty(),
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
     * @since 1.4.0
     */
    @API(status = API.Status.STABLE, since = "1.4.0")
    public Command.@NonNull Builder<C> commandBuilder(
            final @NonNull String name,
            final @NonNull Description description,
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
                Description.empty(),
                aliases
        ).manager(this);
    }

    /**
     * Create a new command component builder.
     * <p>
     * This will also invoke {@link CommandArgument.Builder#manager(CommandManager)}
     * so that the argument is associated with the calling command manager. This allows for parser inference based on
     * the type, with the help of the {@link ParserRegistry parser registry}.
     *
     * @param type Argument type
     * @param name Argument name
     * @param <T>  Generic argument name
     * @return Component builder
     * @since 2.0.0
     */
    @API(status = API.Status.STABLE, since = "2.0.0")
    public <T> CommandComponent.@NonNull Builder<C, T> componentBuilder(
            final @NonNull Class<T> type,
            final @NonNull String name
    ) {
        return CommandComponent.<C, T>ofType(type, name).commandManager(this);
    }

    /**
     * Create a new command flag builder
     *
     * @param name Flag name
     * @return Flag builder
     */
    public CommandFlag.@NonNull Builder<Void> flagBuilder(final @NonNull String name) {
        return CommandFlag.builder(name);
    }

    /**
     * Returns the internal command tree.
     * <p>
     * Be careful when accessing the command tree. Do not interact with it, unless you
     * absolutely know what you're doing.
     *
     * @return the command tree
     * @since 1.7.0
     */
    @API(status = API.Status.STABLE, since = "1.7.0")
    public @NonNull CommandTree<C> commandTree() {
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
     * @see #preprocessContext(CommandContext, CommandInput) Preprocess a context
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
     * @see #preprocessContext(CommandContext, CommandInput) Preprocess a context
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
     * @param context      Command context
     * @param commandInput Command input as supplied by sender
     * @return {@link State#ACCEPTED} if the command should be parsed and executed, else {@link State#REJECTED}
     * @see #registerCommandPreProcessor(CommandPreprocessor) Register a command preprocessor
     * @since 2.0.0
     */
    @API(status = API.Status.STABLE, since = "2.0.0")
    public State preprocessContext(
            final @NonNull CommandContext<C> context,
            final @NonNull CommandInput commandInput
    ) {
        this.servicePipeline.pump(new CommandPreprocessingContext<>(context, commandInput))
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
     * Returns the command suggestion processor used in this command manager.
     *
     * @return the command suggestion processor
     * @since 1.7.0
     * @see #commandSuggestionProcessor(CommandSuggestionProcessor)
     */
    @API(status = API.Status.STABLE, since = "1.7.0")
    public @NonNull CommandSuggestionProcessor<C> commandSuggestionProcessor() {
        return this.commandSuggestionProcessor;
    }

    /**
     * Sets the command suggestion processor.
     * <p>
     * This will be called every time {@link SuggestionFactory#suggest(CommandContext, String)} is called, to process the list
     * of suggestions before it's returned to the caller.
     *
     * @param commandSuggestionProcessor the new command suggestion processor
     * @since 1.7.0
     * @see #commandSuggestionProcessor()
     */
    @API(status = API.Status.STABLE, since = "1.7.0")
    public void commandSuggestionProcessor(final @NonNull CommandSuggestionProcessor<C> commandSuggestionProcessor) {
        this.commandSuggestionProcessor = commandSuggestionProcessor;
    }

    /**
     * Returns the parser registry instance.
     * <p>
     * The parser registry contains default mappings to {@link ArgumentParser argument parsers} and
     * allows for the registration of custom mappings. The parser registry also contains mappings between
     * annotations and {@link ParserParameter}, which allows for the customization of parser settings by
     * using annotations.
     * <p>
     * When creating a new parser type, it is highly recommended to register it in the parser registry.
     * In particular, default parser types (shipped with cloud implementations) should be registered in the
     * constructor of the platform {@link CommandManager}.
     *
     * @return the parser registry instance
     * @since 1.7.0
     */
    @API(status = API.Status.STABLE, since = "1.7.0")
    public @NonNull ParserRegistry<C> parserRegistry() {
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
     * Returns the exception controller.
     * <p>
     * The exception controller is responsible for exception handler registration.
     *
     * @return the exception controller
     * @since 2.0.0
     */
    @API(status = API.Status.STABLE, since = "2.0.0")
    public final @NonNull ExceptionController<C> exceptionController() {
        return this.exceptionController;
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
     * Returns an unmodifiable view of all registered commands.
     *
     * @return unmodifiable view of all registered commands
     * @since 1.7.0
     */
    @API(status = API.Status.STABLE, since = "1.7.0")
    public final @NonNull Collection<@NonNull Command<C>> commands() {
        return Collections.unmodifiableCollection(this.commands);
    }

    /**
     * Creates a new command help handler instance.
     * <p>
     * The command helper handler can be used to assist in the production of commad help menus, etc.
     * <p>
     * This command help handler instance will display all commands registered in this command manager.
     *
     * @return a new command helper handler instance
     * @since 1.7.0
     */
    @API(status = API.Status.STABLE, since = "1.7.0")
    public final @NonNull CommandHelpHandler<C> createCommandHelpHandler() {
        return new CommandHelpHandler<>(this, cmd -> true);
    }

    /**
     * Creates a new command help handler instance.
     * <p>
     * The command helper handler can be used to assist in the production of commad help menus, etc.
     * <p>
     * A predicate can be specified to filter what commands
     * registered in this command manager are visible in the help menu.
     *
     * @param commandPredicate predicate that filters what commands are displayed in
     *                         the help menu.
     * @return a new command helper handler instance
     * @since 1.7.0
     */
    @API(status = API.Status.STABLE, since = "1.7.0")
    public final @NonNull CommandHelpHandler<C> createCommandHelpHandler(
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
    @API(status = API.Status.STABLE, since = "1.6.0")
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
    @API(status = API.Status.STABLE, since = "1.2.0")
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
    @API(status = API.Status.STABLE, since = "1.2.0")
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
    @API(status = API.Status.STABLE, since = "1.2.0")
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
    @API(status = API.Status.STABLE, since = "1.4.0")
    protected final void lockRegistration() {
        if (this.registrationState() == RegistrationState.BEFORE_REGISTRATION) {
            this.transitionOrThrow(RegistrationState.BEFORE_REGISTRATION, RegistrationState.AFTER_REGISTRATION);
            return;
        }
        this.transitionOrThrow(RegistrationState.REGISTERING, RegistrationState.AFTER_REGISTRATION);
    }

    /**
     * Returns the active registration state for this manager.
     * <p>
     * If the state is {@link RegistrationState#AFTER_REGISTRATION}, commands can no longer be registered.
     *
     * @return the current state
     * @since 1.7.0
     */
    @API(status = API.Status.STABLE, since = "1.7.0")
    public final @NonNull RegistrationState registrationState() {
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
    @API(status = API.Status.STABLE, since = "1.2.0")
    public boolean isCommandRegistrationAllowed() {
        return this.getSetting(ManagerSettings.ALLOW_UNSAFE_REGISTRATION) || this.state.get() != RegistrationState.AFTER_REGISTRATION;
    }


    /**
     * Configurable command related settings
     *
     * @see CommandManager#setSetting(ManagerSettings, boolean) Set a manager setting
     * @see CommandManager#getSetting(ManagerSettings) Get a manager setting
     */
    @API(status = API.Status.STABLE)
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
        @API(status = API.Status.STABLE, since = "1.2.0")
        ALLOW_UNSAFE_REGISTRATION,

        /**
         * Enables overriding of existing commands on supported platforms.
         *
         * @since 1.2.0
         */
        @API(status = API.Status.STABLE, since = "1.2.0")
        OVERRIDE_EXISTING_COMMANDS,

        /**
         * Allows parsing flags at any position after the last literal by appending flag argument nodes between each command node.
         * It can have some conflicts when integrating with other command systems like Brigadier,
         * and code inspecting the command tree may need to be adjusted.
         *
         * @since 1.8.0
         */
        @API(status = API.Status.EXPERIMENTAL, since = "1.8.0")
        LIBERAL_FLAG_PARSING
    }


    /**
     * The point in the registration lifecycle for this commands manager
     *
     * @since 1.2.0
     */
    @API(status = API.Status.STABLE, since = "1.2.0")
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
