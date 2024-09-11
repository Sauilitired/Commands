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
package org.incendo.cloud;

import io.leangen.geantyref.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apiguardian.api.API;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.component.DefaultValue;
import org.incendo.cloud.description.CommandDescription;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.execution.CommandExecutionHandler;
import org.incendo.cloud.key.CloudKey;
import org.incendo.cloud.meta.CommandMeta;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.parser.ParserRegistry;
import org.incendo.cloud.parser.aggregate.AggregateParser;
import org.incendo.cloud.parser.aggregate.AggregateParserPairBuilder;
import org.incendo.cloud.parser.aggregate.AggregateParserTripletBuilder;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.parser.flag.CommandFlagParser;
import org.incendo.cloud.parser.standard.LiteralParser;
import org.incendo.cloud.permission.Permission;
import org.incendo.cloud.permission.PredicatePermission;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.incendo.cloud.type.tuple.Pair;
import org.incendo.cloud.type.tuple.Triplet;

/**
 * A command is a chain of {@link CommandComponent command components} with an associated {@link #commandExecutionHandler()}.
 * <p>
 * The recommended way of creating a command is by using a {@link Command.Builder command builder}.
 * You may either create the command builder using {@link #newBuilder(String, CommandMeta, String...)} or
 * {@link CommandManager#commandBuilder(String, String...)}.
 * Getting a builder from the command manager means that the builder is linked to the manager.
 * When the command builder is linked to the manager, it is able to retrieve parsers from the associated
 * {@link ParserRegistry} in the case that only a parsed type is given to the builder,
 * and not a complete parser.
 * You may link any command builder to a command manager by using {@link Command.Builder#manager(CommandManager)}.
 * <p>
 * All command flags added to a command builder will be collected into a single component.
 * If there are flags added to the command, then they may be retrieved from the {@link #flagComponent()} or from the
 * {@link #flagParser()}.
 * <p>
 * Commands may have meta-data associated with them, which can be accessed using {@link #commandMeta()}.
 * A common way of using the command meta is by using it to filter out commands in post-processing.
 * <p>
 * A command may have a {@link #senderType()} that is different from the sender type of the command manager.
 * The command tree will enforce this type when parsing the command.
 *
 * @param <C> command sender type
 */
@SuppressWarnings("unused")
@API(status = API.Status.STABLE)
public class Command<C> {

    private final List<@NonNull CommandComponent<C>> components;
    private final @Nullable CommandComponent<C> flagComponent;
    private final CommandExecutionHandler<C> commandExecutionHandler;
    private final Type senderType;
    private final Permission permission;
    private final CommandMeta commandMeta;
    private final CommandDescription commandDescription;

    /**
     * Constructs a new command instance.
     *
     * @param commandComponents       command component argument and description
     * @param commandExecutionHandler execution handler
     * @param senderType              required sender type. May be {@code null}
     * @param permission       command permission
     * @param commandMeta             command meta instance
     * @param commandDescription      description of the command
     */
    @API(status = API.Status.INTERNAL)
    public Command(
            final @NonNull List<@NonNull CommandComponent<C>> commandComponents,
            final @NonNull CommandExecutionHandler<@NonNull C> commandExecutionHandler,
            final @Nullable Type senderType,
            final @NonNull Permission permission,
            final @NonNull CommandMeta commandMeta,
            final @NonNull CommandDescription commandDescription
    ) {
        this.components = Objects.requireNonNull(commandComponents, "Command components may not be null");
        if (this.components.isEmpty()) {
            throw new IllegalArgumentException("At least one command component is required");
        }

        this.flagComponent =
                this.components.stream()
                        .filter(ca -> ca.type() == CommandComponent.ComponentType.FLAG)
                        .findFirst()
                        .orElse(null);

        // Enforce ordering of command arguments
        boolean foundOptional = false;
        for (final CommandComponent<C> component : this.components) {
            if (component.name().isEmpty()) {
                throw new IllegalArgumentException("Component names may not be empty");
            }
            if (foundOptional && component.required()) {
                throw new IllegalArgumentException(
                        String.format(
                                "Command component '%s' cannot be placed after an optional argument",
                                component.name()
                        ));
            } else if (!component.required()) {
                foundOptional = true;
            }
        }
        this.commandExecutionHandler = commandExecutionHandler;
        this.senderType = senderType;
        this.permission = permission;
        this.commandMeta = commandMeta;
        this.commandDescription = commandDescription;
    }

    /**
     * Creates a new command builder.
     * <p>
     * Is recommended to use the builder methods in {@link CommandManager} rather than invoking this method directly.
     *
     * @param commandName base command argument
     * @param commandMeta command meta instance
     * @param description description of the root literal
     * @param aliases     command aliases
     * @param <C>         command sender type
     * @return command builder
     */
    @API(status = API.Status.STABLE)
    public static <C> @NonNull Builder<C> newBuilder(
            final @NonNull String commandName,
            final @NonNull CommandMeta commandMeta,
            final @NonNull Description description,
            final @NonNull String @NonNull... aliases
    ) {
        final List<CommandComponent<C>> commands = new ArrayList<>();
        final ParserDescriptor<C, String> staticParser = LiteralParser.literal(commandName, aliases);
        commands.add(
                CommandComponent.builder(commandName, staticParser)
                        .description(description)
                        .build()
        );
        return new Builder<>(
                null,
                commandMeta,
                null,
                commands,
                CommandExecutionHandler.noOpCommandExecutionHandler(),
                Permission.empty(),
                Collections.emptyList(),
                CommandDescription.empty()
        );
    }

    /**
     * Creates a new command builder.
     * <p>
     * Is recommended to use the builder methods in {@link CommandManager} rather than invoking this method directly.
     *
     * @param commandName base command argument
     * @param commandMeta command meta instance
     * @param aliases     command aliases
     * @param <C>         command sender type
     * @return command builder
     */
    public static <C> @NonNull Builder<C> newBuilder(
            final @NonNull String commandName,
            final @NonNull CommandMeta commandMeta,
            final @NonNull String @NonNull... aliases
    ) {
        final List<CommandComponent<C>> commands = new ArrayList<>();
        final ParserDescriptor<C, String> staticParser = LiteralParser.literal(commandName, aliases);
        commands.add(
                CommandComponent.<C, String>builder()
                        .name(commandName)
                        .parser(staticParser)
                        .build()
        );
        return new Builder<>(
                null,
                commandMeta,
                null,
                commands,
                CommandExecutionHandler.noOpCommandExecutionHandler(),
                Permission.empty(),
                Collections.emptyList(),
                CommandDescription.empty()
        );
    }

    /**
     * Returns a copy of the list of the components that make up this command.
     *
     * @return modifiable copy of the component list
     */
    @API(status = API.Status.STABLE)
    public @NonNull List<CommandComponent<C>> components() {
        return new ArrayList<>(this.components);
    }

    /**
     * Returns the first command component.
     *
     * @return the root component
     */
    @API(status = API.Status.STABLE)
    public @NonNull CommandComponent<C> rootComponent() {
        return this.components.get(0);
    }

    /**
     * Returns a mutable copy of the command components, ignoring flag arguments.
     *
     * @return argument list
     */
    @API(status = API.Status.EXPERIMENTAL)
    public @NonNull List<CommandComponent<C>> nonFlagArguments() {
        final List<CommandComponent<C>> components = new ArrayList<>(this.components);
        if (this.flagComponent() != null) {
            components.remove(this.flagComponent());
        }
        return components;
    }

    /**
     * Returns the component that contains the flags belonging to the command.
     *
     * @return the flag component, or {@code null} if no flags have been registered
     */
    @API(status = API.Status.STABLE)
    public @Nullable CommandComponent<C> flagComponent() {
        return this.flagComponent;
    }

    /**
     * Returns the flag parser for this command, of {@code null} if the command has no flags.
     *
     * @return flag parser, or {@code null} if no flags have been registered
     */
    @SuppressWarnings("unchecked")
    @API(status = API.Status.STABLE)
    public @Nullable CommandFlagParser<@NonNull C> flagParser() {
        final CommandComponent<C> flagComponent = this.flagComponent();
        if (flagComponent == null) {
            return null;
        }
        return (CommandFlagParser<C>) flagComponent.parser();
    }

    /**
     * Returns the command execution handler.
     * <p>
     * The command execution handler is invoked after a parsing a command.
     * It has access to the {@link org.incendo.cloud.context.CommandContext} which contains
     * the parsed component values.
     *
     * @return the command execution handler
     */
    @API(status = API.Status.STABLE)
    public @NonNull CommandExecutionHandler<@NonNull C> commandExecutionHandler() {
        return this.commandExecutionHandler;
    }

    /**
     * Returns the specific command sender type for the command if one has been defined.
     * <p>
     * A command may have a sender that is different from the sender type of the command manager.
     * The command tree will enforce this type when parsing the command.
     *
     * @return the special sender type for the command, or {@link Optional#empty()} if the command uses the same sender type
     * as the command manager
     */
    @API(status = API.Status.STABLE)
    @SuppressWarnings("unchecked")
    public @NonNull Optional<TypeToken<? extends C>> senderType() {
        if (this.senderType == null) {
            return Optional.empty();
        }
        return Optional.of((TypeToken<? extends C>) TypeToken.get(this.senderType));
    }

    /**
     * Returns the permission required to execute the command.
     * <p>
     * If the sender does not have the required permission a {@link org.incendo.cloud.exception.NoPermissionException}
     * will be thrown when parsing the command.
     *
     * @return the command permission
     */
    @API(status = API.Status.STABLE)
    public @NonNull Permission commandPermission() {
        return this.permission;
    }

    /**
     * Returns the meta-data associated with the command.
     * <p>
     * A common way of using the command meta is by using it to filter out commands in post-processing.
     *
     * @return Command meta
     */
    @API(status = API.Status.STABLE)
    public @NonNull CommandMeta commandMeta() {
        return this.commandMeta;
    }

    /**
     * Returns the description of the command.
     * <p>
     * This is not the same as the description of the root component.
     * The command description used to be configured through the {@link #commandMeta()}.
     *
     * @return the command description
     */
    @API(status = API.Status.STABLE)
    public @NonNull CommandDescription commandDescription() {
        return this.commandDescription;
    }

    @Override
    public final String toString() {
        final StringBuilder stringBuilder = new StringBuilder();
        for (final CommandComponent<C> component : this.components()) {
            stringBuilder.append(component.name()).append(' ');
        }
        final String build = stringBuilder.toString();
        return build.substring(0, build.length() - 1);
    }


    /**
     * Builder for {@link Command} instances. The builder is immutable, and each
     * setter method will return a new builder instance.
     *
     * @param <C> command sender type
     */
    @API(status = API.Status.STABLE)
    public static final class Builder<C> {

        private final CommandMeta commandMeta;
        private final List<CommandComponent<C>> commandComponents;
        private final CommandExecutionHandler<C> commandExecutionHandler;
        private final Type senderType;
        private final Permission permission;
        private final CommandManager<C> commandManager;
        private final Collection<CommandFlag<?>> flags;
        private final CommandDescription commandDescription;

        private Builder(
                final @Nullable CommandManager<C> commandManager,
                final @NonNull CommandMeta commandMeta,
                final @Nullable Type senderType,
                final @NonNull List<@NonNull CommandComponent<C>> commandComponents,
                final @NonNull CommandExecutionHandler<@NonNull C> commandExecutionHandler,
                final @NonNull Permission permission,
                final @NonNull Collection<CommandFlag<?>> flags,
                final @NonNull CommandDescription commandDescription
        ) {
            this.commandManager = commandManager;
            this.senderType = senderType;
            this.commandComponents = Objects.requireNonNull(commandComponents, "Components may not be null");
            this.commandExecutionHandler = Objects.requireNonNull(commandExecutionHandler, "Execution handler may not be null");
            this.permission = Objects.requireNonNull(permission, "Permission may not be null");
            this.commandMeta = Objects.requireNonNull(commandMeta, "Meta may not be null");
            this.flags = Objects.requireNonNull(flags, "Flags may not be null");
            this.commandDescription = Objects.requireNonNull(commandDescription, "Command description may not be null");
        }

        /**
         * Returns the required sender type for this builder.
         * <p>
         * Returns {@code null} when there is not a specific required sender type.
         *
         * @return required sender type
         */
        @API(status = API.Status.STABLE)
        @SuppressWarnings("unchecked")
        public @Nullable TypeToken<? extends C> senderType() {
            if (this.senderType == null) {
                return null;
            }
            return (TypeToken<? extends C>) TypeToken.get(this.senderType);
        }

        /**
         * Returns the required command permission for this builder.
         * <p>
         * Will return {@link Permission#empty()} if there is no required permission.
         *
         * @return required permission
         */
        @API(status = API.Status.STABLE)
        public @NonNull Permission commandPermission() {
            return this.permission;
        }

        /**
         * Returns the current {@link CommandMeta command meta} value.
         *
         * @return current command meta
         */
        @API(status = API.Status.STABLE)
        public @NonNull CommandMeta meta() {
            return this.commandMeta;
        }

        /**
         * Applies the provided {@link Applicable} to this {@link Builder}, and returns the result.
         *
         * @param applicable operation
         * @return operation result
         */
        @API(status = API.Status.STABLE)
        public @NonNull Builder<@NonNull C> apply(
                final @NonNull Applicable<@NonNull C> applicable
        ) {
            return applicable.applyToCommandBuilder(this);
        }

        /**
         * Adds command meta to the internal command meta-map.
         *
         * @param <V>   meta value type
         * @param key   meta key
         * @param value meta value
         * @return new builder instance using the inserted meta key-value pair
         */
        @API(status = API.Status.STABLE)
        public <V> @NonNull Builder<C> meta(final @NonNull CloudKey<V> key, final @NonNull V value) {
            final CommandMeta commandMeta = CommandMeta.builder().with(this.commandMeta).with(key, value).build();
            return new Builder<>(
                    this.commandManager,
                    commandMeta,
                    this.senderType,
                    this.commandComponents,
                    this.commandExecutionHandler,
                    this.permission,
                    this.flags,
                    this.commandDescription
            );
        }

        /**
         * Adds command meta with no value to the internal command meta-map
         *
         * @param key   meta key
         * @return new builder instance using the inserted meta key
         */
        @API(status = API.Status.STABLE)
        public @NonNull Builder<C> meta(final @NonNull CloudKey<Void> key) {
            final CommandMeta commandMeta = CommandMeta.builder().with(this.commandMeta).with(key).build();
            return new Builder<>(
                    this.commandManager,
                    commandMeta,
                    this.senderType,
                    this.commandComponents,
                    this.commandExecutionHandler,
                    this.permission,
                    this.flags,
                    this.commandDescription
            );
        }

        /**
         * Supplies a command manager instance to the builder.
         * <p>
         * This will be used when attempting to
         * retrieve command argument parsers, in the case that they're needed.
         * <p>
         * This is optional.
         *
         * @param commandManager Command manager
         * @return new builder instance using the provided command manager
         */
        public @NonNull Builder<C> manager(final @Nullable CommandManager<C> commandManager) {
            return new Builder<>(
                    commandManager,
                    this.commandMeta,
                    this.senderType,
                    this.commandComponents,
                    this.commandExecutionHandler,
                    this.permission,
                    this.flags,
                    this.commandDescription
            );
        }

        /**
         * Returns a new builder with the given {@code commandDescription}.
         * <p>
         * See {@link Command#commandDescription()} for information about the description.
         *
         * @param commandDescription the new command description
         * @return new builder instance using the provided command description
         */
        @API(status = API.Status.STABLE)
        public @NonNull Builder<C> commandDescription(final @NonNull CommandDescription commandDescription) {
            return new Builder<>(
                    this.commandManager,
                    this.commandMeta,
                    this.senderType,
                    this.commandComponents,
                    this.commandExecutionHandler,
                    this.permission,
                    this.flags,
                    commandDescription
            );
        }

        /**
         * Returns the current command description of this command builder.
         *
         * @return the current description
         */
        public @NonNull CommandDescription commandDescription() {
            return this.commandDescription;
        }

        /**
         * Returns the result of invoking {@link #commandDescription(CommandDescription)} with the result of
         * {@link CommandDescription#commandDescription(Description)}.
         *
         * @param commandDescription the new command description
         * @return new builder instance using the provided command description
         */
        public @NonNull Builder<C> commandDescription(final @NonNull Description commandDescription) {
            return this.commandDescription(CommandDescription.commandDescription(commandDescription));
        }

        /**
         * Returns the result of invoking {@link #commandDescription(CommandDescription)} with the result of
         * {@link CommandDescription#commandDescription(Description, Description)}.
         *
         * @param commandDescription        the new command description
         * @param verboseCommandDescription the new verbose command description
         * @return new builder instance using the provided command description
         */
        public @NonNull Builder<C> commandDescription(
                final @NonNull Description commandDescription,
                final @NonNull Description verboseCommandDescription
        ) {
            return this.commandDescription(CommandDescription.commandDescription(commandDescription, verboseCommandDescription));
        }

        /**
         * Inserts a required literal into the command chain.
         *
         * @param main    main argument name
         * @param aliases argument aliases
         * @return new builder instance with the modified command chain
         */
        public @NonNull Builder<C> literal(
                final @NonNull String main,
                final @NonNull String... aliases
        ) {
            return this.required(main, LiteralParser.literal(main, aliases));
        }

        /**
         * Inserts a required literal into the command chain.
         *
         * @param main        main argument name
         * @param description literal description
         * @param aliases     argument aliases
         * @return new builder instance with the modified command chain
         */
        @API(status = API.Status.STABLE)
        public @NonNull Builder<C> literal(
                final @NonNull String main,
                final @NonNull Description description,
                final @NonNull String... aliases
        ) {
            return this.required(main, LiteralParser.literal(main, aliases), description);
        }

        /**
         * Marks the {@code builder} as required and adds it to the command.
         *
         * @param <T> value type
         * @param name    the name that will be inserted into the builder
         * @param builder the component builder
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> required(
                final @NonNull String name,
                final CommandComponent.@NonNull Builder<? super C, T> builder
        ) {
            return this.argument(builder.name(name).required());
        }

        /**
         * Marks the {@code builder} as optional and adds it to the command.
         *
         * @param <T> value type
         * @param name    the name that will be inserted into the builder
         * @param builder the component builder
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> optional(
                final @NonNull String name,
                final CommandComponent.@NonNull Builder<? super C, T> builder
        ) {
            return this.argument(builder.name(name).optional());
        }

        /**
         * Marks the {@code builder} as required and adds it to the command.
         *
         * @param <T> value type
         * @param builder the component builder
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> required(
                final CommandComponent.@NonNull Builder<? super C, T> builder
        ) {
            return this.argument(builder.required());
        }

        /**
         * Marks the {@code builder} as optional and adds it to the command.
         *
         * @param <T> value type
         * @param builder the component builder
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> optional(
                final CommandComponent.@NonNull Builder<? super C, T> builder
        ) {
            return this.argument(builder.optional());
        }

        /**
         * Adds the given required argument to the command.
         *
         * @param name   the name of the argument
         * @param parser the parser
         * @param <T>    the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> required(
                final @NonNull String name,
                final @NonNull ParserDescriptor<? super C, T> parser
        ) {
            return this.argument(CommandComponent.<C, T>builder(name, parser).build());
        }

        /**
         * Adds the given required argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param suggestions the suggestion provider
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> required(
                final @NonNull String name,
                final @NonNull ParserDescriptor<? super C, T> parser,
                final @NonNull SuggestionProvider<? super C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder(name, parser)
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given required argument to the command.
         *
         * @param name   the name of the argument
         * @param parser the parser
         * @param <T>    the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> required(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<? super C, T> parser
        ) {
            return this.argument(CommandComponent.<C, T>builder(name, parser).build());
        }

        /**
         * Adds the given required argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param suggestions the suggestion provider
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> required(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<? super C, T> parser,
                final @NonNull SuggestionProvider<? super C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder(name, parser)
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given required argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param description the description of the argument
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> required(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<? super C, T> parser,
                final @NonNull Description description
        ) {
            return this.argument(CommandComponent.<C, T>builder(name, parser).description(description).build());
        }

        /**
         * Adds the given required argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param description the description of the argument
         * @param suggestions the suggestion provider
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> required(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<? super C, T> parser,
                final @NonNull Description description,
                final @NonNull SuggestionProvider<? super C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder(name, parser)
                            .description(description)
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given required argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param description the description of the argument
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> required(
                final @NonNull String name,
                final @NonNull ParserDescriptor<? super C, T> parser,
                final @NonNull Description description
        ) {
            return this.argument(CommandComponent.<C, T>builder(name, parser).description(description).build());
        }

        /**
         * Adds the given required argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param description the description of the argument
         * @param suggestions the suggestion provider
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> required(
                final @NonNull String name,
                final @NonNull ParserDescriptor<? super C, T> parser,
                final @NonNull Description description,
                final @NonNull SuggestionProvider<? super C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder(name, parser)
                            .description(description)
                            .suggestionProvider(suggestions)
                            .build()
            );
        }


        /**
         * Adds the given optional argument to the command.
         *
         * @param name   the name of the argument
         * @param parser the parser
         * @param <T>    the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> optional(
                final @NonNull String name,
                final @NonNull ParserDescriptor<? super C, T> parser
        ) {
            return this.argument(CommandComponent.<C, T>builder(name, parser).optional().build());
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param suggestions the suggestion provider
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> optional(
                final @NonNull String name,
                final @NonNull ParserDescriptor<? super C, T> parser,
                final @NonNull SuggestionProvider<? super C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder(name, parser)
                            .optional()
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name   the name of the argument
         * @param parser the parser
         * @param <T>    the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> optional(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<? super C, T> parser
        ) {
            return this.argument(CommandComponent.<C, T>builder(name, parser).optional().build());
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param suggestions the suggestion provider
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> optional(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<? super C, T> parser,
                final @NonNull SuggestionProvider<? super C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder(name, parser)
                            .optional()
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param description the description of the argument
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> optional(
                final @NonNull String name,
                final @NonNull ParserDescriptor<? super C, T> parser,
                final @NonNull Description description
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder(name, parser)
                            .description(description)
                            .optional()
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param description the description of the argument
         * @param suggestions the suggestion provider
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> optional(
                final @NonNull String name,
                final @NonNull ParserDescriptor<? super C, T> parser,
                final @NonNull Description description,
                final @NonNull SuggestionProvider<? super C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder(name, parser)
                            .description(description)
                            .optional()
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param description the description of the argument
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> optional(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<? super C, T> parser,
                final @NonNull Description description
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder(name, parser)
                            .description(description)
                            .optional()
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name        the name of the argument
         * @param parser      the parser
         * @param description the description of the argument
         * @param suggestions the suggestion provider
         * @param <T>         the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> optional(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<? super C, T> parser,
                final @NonNull Description description,
                final @NonNull SuggestionProvider<? super C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder(name, parser)
                            .description(description)
                            .optional()
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name         the name of the argument
         * @param parser       the parser
         * @param defaultValue the default value
         * @param <T>          the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> optional(
                final @NonNull String name,
                final @NonNull ParserDescriptor<? super C, T> parser,
                final @NonNull DefaultValue<? super C, T> defaultValue
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder(name, parser)
                            .optional(defaultValue)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name         the name of the argument
         * @param parser       the parser
         * @param defaultValue the default value
         * @param suggestions  the suggestion provider
         * @param <T>          the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> optional(
                final @NonNull String name,
                final @NonNull ParserDescriptor<? super C, T> parser,
                final @NonNull DefaultValue<? super C, T> defaultValue,
                final @NonNull SuggestionProvider<? super C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder(name, parser)
                            .optional(defaultValue)
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name         the name of the argument
         * @param parser       the parser
         * @param defaultValue the default value
         * @param <T>          the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> optional(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<? super C, T> parser,
                final @NonNull DefaultValue<? super C, T> defaultValue
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder(name, parser)
                            .optional(defaultValue)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name         the name of the argument
         * @param parser       the parser
         * @param defaultValue the default value
         * @param suggestions  the suggestion provider
         * @param <T>          the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> optional(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<? super C, T> parser,
                final @NonNull DefaultValue<? super C, T> defaultValue,
                final @NonNull SuggestionProvider<? super C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder(name, parser)
                            .optional(defaultValue)
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name         the name of the argument
         * @param parser       the parser
         * @param defaultValue the default value
         * @param description  the description of the argument
         * @param <T>          the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> optional(
                final @NonNull String name,
                final @NonNull ParserDescriptor<? super C, T> parser,
                final @NonNull DefaultValue<? super C, T> defaultValue,
                final @NonNull Description description
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder(name, parser)
                            .optional(defaultValue)
                            .description(description)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name         the name of the argument
         * @param parser       the parser
         * @param defaultValue the default value
         * @param description  the description of the argument
         * @param suggestions  the suggestion provider
         * @param <T>          the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> optional(
                final @NonNull String name,
                final @NonNull ParserDescriptor<? super C, T> parser,
                final @NonNull DefaultValue<? super C, T> defaultValue,
                final @NonNull Description description,
                final @NonNull SuggestionProvider<? super C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder(name, parser)
                            .optional(defaultValue)
                            .description(description)
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name         the name of the argument
         * @param parser       the parser
         * @param defaultValue the default value
         * @param description  the description of the argument
         * @param <T>          the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> optional(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<? super C, T> parser,
                final @NonNull DefaultValue<? super C, T> defaultValue,
                final @NonNull Description description
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder(name, parser)
                            .optional(defaultValue)
                            .description(description)
                            .build()
            );
        }

        /**
         * Adds the given optional argument to the command.
         *
         * @param name         the name of the argument
         * @param parser       the parser
         * @param defaultValue the default value
         * @param description  the description of the argument
         * @param suggestions  the suggestion provider
         * @param <T>          the type produced by the parser
         * @return new builder instance with the command argument inserted into the argument list
         */
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> optional(
                final @NonNull CloudKey<T> name,
                final @NonNull ParserDescriptor<? super C, T> parser,
                final @NonNull DefaultValue<? super C, T> defaultValue,
                final @NonNull Description description,
                final @NonNull SuggestionProvider<? super C> suggestions
        ) {
            return this.argument(
                    CommandComponent.<C, T>builder(name, parser)
                            .optional(defaultValue)
                            .description(description)
                            .suggestionProvider(suggestions)
                            .build()
            );
        }

        /**
         * Adds the given {@code argument} to the command.
         *
         * @param argument argument to add
         * @return new builder instance with the command argument inserted into the argument list
         */
        @SuppressWarnings("unchecked")
        @API(status = API.Status.STABLE)
        public @NonNull Builder<C> argument(
                final @NonNull CommandComponent<? super C> argument
        ) {
            final List<CommandComponent<C>> commandComponents = new ArrayList<>(this.commandComponents);
            commandComponents.add((CommandComponent<C>) argument);
            return new Builder<>(
                    this.commandManager,
                    this.commandMeta,
                    this.senderType,
                    commandComponents,
                    this.commandExecutionHandler,
                    this.permission,
                    this.flags,
                    this.commandDescription
            );
        }

        /**
         * Adds the given {@code argument} to the command.
         *
         * @param <T> value type
         * @param builder builder that builds the component to add
         * @return new builder instance with the command argument inserted into the argument list
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        @API(status = API.Status.STABLE)
        public <T> @NonNull Builder<C> argument(
                final CommandComponent.Builder<? super C, T> builder
        ) {
            if (this.commandManager != null) {
                return this.argument(((CommandComponent.Builder) builder).commandManager(this.commandManager).build());
            } else {
                return this.argument(builder.build());
            }
        }

        // Aggregate helper methods

        /**
         * Creates a new argument pair that maps to {@link Pair}.
         *
         * @param name         name of the argument
         * @param firstName    name of the first subcomponent
         * @param firstParser  parser for the first subcomponent
         * @param secondName   name of the second subcomponent
         * @param secondParser parser for the second subcomponent
         * @param description  description of the argument
         * @param <U>          first type
         * @param <V>          second type
         * @return new builder instance with the argument inserted
         */
        @API(status = API.Status.STABLE)
        public <U, V> @NonNull Builder<C> requiredArgumentPair(
                final @NonNull String name,
                final @NonNull String firstName,
                final @NonNull ParserDescriptor<C, U> firstParser,
                final @NonNull String secondName,
                final @NonNull ParserDescriptor<C, V> secondParser,
                final @NonNull Description description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.required(
                    name,
                    AggregateParser.pairBuilder(firstName, firstParser, secondName, secondParser).build(),
                    description
            );
        }

        /**
         * Creates a new argument pair that maps to {@link Pair}.
         *
         * @param name         name of the argument
         * @param firstName    name of the first subcomponent
         * @param firstParser  parser for the first subcomponent
         * @param secondName   name of the second subcomponent
         * @param secondParser parser for the second subcomponent
         * @param description  description of the argument
         * @param <U>          first type
         * @param <V>          second type
         * @return new builder instance with the argument inserted
         */
        @API(status = API.Status.STABLE)
        public <U, V> @NonNull Builder<C> requiredArgumentPair(
                final @NonNull CloudKey<Pair<U, V>> name,
                final @NonNull String firstName,
                final @NonNull ParserDescriptor<C, U> firstParser,
                final @NonNull String secondName,
                final @NonNull ParserDescriptor<C, V> secondParser,
                final @NonNull Description description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.required(
                    name,
                    AggregateParser.pairBuilder(firstName, firstParser, secondName, secondParser).build(),
                    description
            );
        }

        /**
         * Creates a new argument pair that maps to {@link Pair}.
         *
         * @param name         name of the argument
         * @param firstName    name of the first subcomponent
         * @param firstParser  parser for the first subcomponent
         * @param secondName   name of the second subcomponent
         * @param secondParser parser for the second subcomponent
         * @param description  description of the argument
         * @param <U>          first type
         * @param <V>          second type
         * @return new builder instance with the argument inserted
         */
        @API(status = API.Status.STABLE)
        public <U, V> @NonNull Builder<C> optionalArgumentPair(
                final @NonNull String name,
                final @NonNull String firstName,
                final @NonNull ParserDescriptor<C, U> firstParser,
                final @NonNull String secondName,
                final @NonNull ParserDescriptor<C, V> secondParser,
                final @NonNull Description description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.optional(
                    name,
                    AggregateParser.pairBuilder(firstName, firstParser, secondName, secondParser).build(),
                    description
            );
        }

        /**
         * Creates a new argument pair that maps to {@link Pair}.
         *
         * @param name         name of the argument
         * @param firstName    name of the first subcomponent
         * @param firstParser  parser for the first subcomponent
         * @param secondName   name of the second subcomponent
         * @param secondParser parser for the second subcomponent
         * @param description  description of the argument
         * @param <U>          first type
         * @param <V>          second type
         * @return new builder instance with the argument inserted
         */
        @API(status = API.Status.STABLE)
        public <U, V> @NonNull Builder<C> optionalArgumentPair(
                final @NonNull CloudKey<Pair<U, V>> name,
                final @NonNull String firstName,
                final @NonNull ParserDescriptor<C, U> firstParser,
                final @NonNull String secondName,
                final @NonNull ParserDescriptor<C, V> secondParser,
                final @NonNull Description description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.optional(
                    name,
                    AggregateParser.pairBuilder(firstName, firstParser, secondName, secondParser).build(),
                    description
            );
        }

        /**
         * Creates a new argument pair that maps to a custom type.
         *
         * @param name         name of the argument
         * @param outputType   the output type
         * @param firstName    name of the first subcomponent
         * @param firstParser  parser for the first subcomponent
         * @param secondName   name of the second subcomponent
         * @param secondParser parser for the second subcomponent
         * @param mapper       mapper that maps from {@link Pair} to the custom type
         * @param description  description of the argument
         * @param <U>          first type
         * @param <V>          second type
         * @param <O>          output type
         * @return new builder instance with the argument inserted
         */
        @API(status = API.Status.STABLE)
        public <U, V, O> @NonNull Builder<C> requiredArgumentPair(
                final @NonNull String name,
                final @NonNull TypeToken<O> outputType,
                final @NonNull String firstName,
                final @NonNull ParserDescriptor<C, U> firstParser,
                final @NonNull String secondName,
                final @NonNull ParserDescriptor<C, V> secondParser,
                final AggregateParserPairBuilder.@NonNull Mapper<C, U, V, O> mapper,
                final @NonNull Description description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.required(
                    name,
                    AggregateParser.pairBuilder(firstName, firstParser, secondName, secondParser)
                            .withMapper(outputType, mapper)
                            .build(),
                    description
            );
        }

        /**
         * Creates a new argument pair that maps to a custom type.
         *
         * @param name         name of the argument
         * @param outputType   the output type
         * @param firstName    name of the first subcomponent
         * @param firstParser  parser for the first subcomponent
         * @param secondName   name of the second subcomponent
         * @param secondParser parser for the second subcomponent
         * @param mapper       mapper that maps from {@link Pair} to the custom type
         * @param description  description of the argument
         * @param <U>          first type
         * @param <V>          second type
         * @param <O>          output type
         * @return new builder instance with the argument inserted
         */
        @API(status = API.Status.STABLE)
        public <U, V, O> @NonNull Builder<C> requiredArgumentPair(
                final @NonNull CloudKey<O> name,
                final @NonNull TypeToken<O> outputType,
                final @NonNull String firstName,
                final @NonNull ParserDescriptor<C, U> firstParser,
                final @NonNull String secondName,
                final @NonNull ParserDescriptor<C, V> secondParser,
                final AggregateParserPairBuilder.@NonNull Mapper<C, U, V, O> mapper,
                final @NonNull Description description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.required(
                    name,
                    AggregateParser.pairBuilder(firstName, firstParser, secondName, secondParser)
                            .withMapper(outputType, mapper)
                            .build(),
                    description
            );
        }

        /**
         * Creates a new argument pair that maps to a custom type.
         *
         * @param name         name of the argument
         * @param outputType   the output type
         * @param firstName    name of the first subcomponent
         * @param firstParser  parser for the first subcomponent
         * @param secondName   name of the second subcomponent
         * @param secondParser parser for the second subcomponent
         * @param mapper       mapper that maps from {@link Pair} to the custom type
         * @param description  description of the argument
         * @param <U>          first type
         * @param <V>          second type
         * @param <O>          output type
         * @return new builder instance with the argument inserted
         */
        @API(status = API.Status.STABLE)
        public <U, V, O> @NonNull Builder<C> optionalArgumentPair(
                final @NonNull String name,
                final @NonNull TypeToken<O> outputType,
                final @NonNull String firstName,
                final @NonNull ParserDescriptor<C, U> firstParser,
                final @NonNull String secondName,
                final @NonNull ParserDescriptor<C, V> secondParser,
                final AggregateParserPairBuilder.@NonNull Mapper<C, U, V, O> mapper,
                final @NonNull Description description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.optional(
                    name,
                    AggregateParser.pairBuilder(firstName, firstParser, secondName, secondParser)
                            .withMapper(outputType, mapper)
                            .build(),
                    description
            );
        }

        /**
         * Creates a new argument pair that maps to a custom type.
         *
         * @param name         name of the argument
         * @param outputType   the output type
         * @param firstName    name of the first subcomponent
         * @param firstParser  parser for the first subcomponent
         * @param secondName   name of the second subcomponent
         * @param secondParser parser for the second subcomponent
         * @param mapper       mapper that maps from {@link Pair} to the custom type
         * @param description  description of the argument
         * @param <U>          first type
         * @param <V>          second type
         * @param <O>          output type
         * @return new builder instance with the argument inserted
         */
        @API(status = API.Status.STABLE)
        public <U, V, O> @NonNull Builder<C> optionalArgumentPair(
                final @NonNull CloudKey<O> name,
                final @NonNull TypeToken<O> outputType,
                final @NonNull String firstName,
                final @NonNull ParserDescriptor<C, U> firstParser,
                final @NonNull String secondName,
                final @NonNull ParserDescriptor<C, V> secondParser,
                final AggregateParserPairBuilder.@NonNull Mapper<C, U, V, O> mapper,
                final @NonNull Description description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.optional(
                    name,
                    AggregateParser.pairBuilder(firstName, firstParser, secondName, secondParser)
                            .withMapper(outputType, mapper)
                            .build(),
                    description
            );
        }

        /**
         * Create a new argument pair that maps to {@link org.incendo.cloud.type.tuple.Triplet}
         *
         * @param name         name of the argument
         * @param firstName    name of the first subcomponent
         * @param firstParser  parser for the first subcomponent
         * @param secondName   name of the second subcomponent
         * @param secondParser parser for the second subcomponent
         * @param thirdName    name of the third subcomponent
         * @param thirdParser  parser for the third subcomponent
         * @param description  description of the argument
         * @param <U>          first type
         * @param <V>          second type
         * @param <W>          third type
         * @return new builder instance with the argument inserted
         */
        @API(status = API.Status.STABLE)
        public <U, V, W> @NonNull Builder<C> requiredArgumentTriplet(
                final @NonNull String name,
                final @NonNull String firstName,
                final @NonNull ParserDescriptor<C, U> firstParser,
                final @NonNull String secondName,
                final @NonNull ParserDescriptor<C, V> secondParser,
                final @NonNull String thirdName,
                final @NonNull ParserDescriptor<C, W> thirdParser,
                final @NonNull Description description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.required(
                    name,
                    AggregateParser.tripletBuilder(firstName, firstParser, secondName, secondParser, thirdName, thirdParser)
                            .build(),
                    description
            );
        }

        /**
         * Create a new argument pair that maps to {@link org.incendo.cloud.type.tuple.Triplet}
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}.
         *
         * @param name         name of the argument
         * @param firstName    name of the first subcomponent
         * @param firstParser  parser for the first subcomponent
         * @param secondName   name of the second subcomponent
         * @param secondParser parser for the second subcomponent
         * @param thirdName    name of the third subcomponent
         * @param thirdParser  parser for the third subcomponent
         * @param description  description of the argument
         * @param <U>          first type
         * @param <V>          second type
         * @param <W>          third type
         * @return new builder instance with the argument inserted
         */
        @API(status = API.Status.STABLE)
        public <U, V, W> @NonNull Builder<C> requiredArgumentTriplet(
                final @NonNull CloudKey<Triplet<U, V, W>> name,
                final @NonNull String firstName,
                final @NonNull ParserDescriptor<C, U> firstParser,
                final @NonNull String secondName,
                final @NonNull ParserDescriptor<C, V> secondParser,
                final @NonNull String thirdName,
                final @NonNull ParserDescriptor<C, W> thirdParser,
                final @NonNull Description description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.required(
                    name,
                    AggregateParser.tripletBuilder(firstName, firstParser, secondName, secondParser, thirdName, thirdParser)
                            .build(),
                    description
            );
        }

        /**
         * Create a new argument pair that maps to {@link org.incendo.cloud.type.tuple.Triplet}
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}.
         *
         * @param name         name of the argument
         * @param firstName    name of the first subcomponent
         * @param firstParser  parser for the first subcomponent
         * @param secondName   name of the second subcomponent
         * @param secondParser parser for the second subcomponent
         * @param thirdName    name of the third subcomponent
         * @param thirdParser  parser for the third subcomponent
         * @param description  description of the argument
         * @param <U>          first type
         * @param <V>          second type
         * @param <W>          third type
         * @return new builder instance with the argument inserted
         */
        @API(status = API.Status.STABLE)
        public <U, V, W> @NonNull Builder<C> optionalArgumentTriplet(
                final @NonNull String name,
                final @NonNull String firstName,
                final @NonNull ParserDescriptor<C, U> firstParser,
                final @NonNull String secondName,
                final @NonNull ParserDescriptor<C, V> secondParser,
                final @NonNull String thirdName,
                final @NonNull ParserDescriptor<C, W> thirdParser,
                final @NonNull Description description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.optional(
                    name,
                    AggregateParser
                            .tripletBuilder(firstName, firstParser, secondName, secondParser, thirdName, thirdParser)
                            .build(),
                    description
            );
        }

        /**
         * Create a new argument pair that maps to {@link org.incendo.cloud.type.tuple.Triplet}
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}.
         *
         * @param name         name of the argument
         * @param firstName    name of the first subcomponent
         * @param firstParser  parser for the first subcomponent
         * @param secondName   name of the second subcomponent
         * @param secondParser parser for the second subcomponent
         * @param thirdName    name of the third subcomponent
         * @param thirdParser  parser for the third subcomponent
         * @param description  description of the argument
         * @param <U>          first type
         * @param <V>          second type
         * @param <W>          third type
         * @return new builder instance with the argument inserted
         */
        @API(status = API.Status.STABLE)
        public <U, V, W> @NonNull Builder<C> optionalArgumentTriplet(
                final @NonNull CloudKey<Triplet<U, V, W>> name,
                final @NonNull String firstName,
                final @NonNull ParserDescriptor<C, U> firstParser,
                final @NonNull String secondName,
                final @NonNull ParserDescriptor<C, V> secondParser,
                final @NonNull String thirdName,
                final @NonNull ParserDescriptor<C, W> thirdParser,
                final @NonNull Description description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.optional(
                    name,
                    AggregateParser
                            .tripletBuilder(firstName, firstParser, secondName, secondParser, thirdName, thirdParser)
                            .build(),
                    description
            );
        }

        /**
         * Creates a new argument triplet that maps to a custom type.
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}.
         *
         * @param name         name of the argument
         * @param outputType   the output type
         * @param firstName    name of the first subcomponent
         * @param firstParser  parser for the first subcomponent
         * @param secondName   name of the second subcomponent
         * @param secondParser parser for the second subcomponent
         * @param thirdName    name of the third subcomponent
         * @param thirdParser  parser for the third subcomponent
         * @param mapper       mapper that maps from {@link Triplet} to the custom type
         * @param description  description of the argument
         * @param <U>          first type
         * @param <V>          second type
         * @param <W>          third type
         * @param <O>          output type
         * @return new builder instance with the argument inserted
         */
        @API(status = API.Status.STABLE)
        public <U, V, W, O> @NonNull Builder<C> requiredArgumentTriplet(
                final @NonNull String name,
                final @NonNull TypeToken<O> outputType,
                final @NonNull String firstName,
                final @NonNull ParserDescriptor<C, U> firstParser,
                final @NonNull String secondName,
                final @NonNull ParserDescriptor<C, V> secondParser,
                final @NonNull String thirdName,
                final @NonNull ParserDescriptor<C, W> thirdParser,
                final AggregateParserTripletBuilder.@NonNull Mapper<C, U, V, W, O> mapper,
                final @NonNull Description description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.required(
                    name,
                    AggregateParser.tripletBuilder(firstName, firstParser, secondName, secondParser, thirdName, thirdParser)
                            .withMapper(outputType, mapper)
                            .build(),
                    description
            );
        }

        /**
         * Creates a new argument triplet that maps to a custom type.
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}.
         *
         * @param name         name of the argument
         * @param outputType   the output type
         * @param firstName    name of the first subcomponent
         * @param firstParser  parser for the first subcomponent
         * @param secondName   name of the second subcomponent
         * @param secondParser parser for the second subcomponent
         * @param thirdName    name of the third subcomponent
         * @param thirdParser  parser for the third subcomponent
         * @param mapper       Mapper that maps from {@link Triplet} to the custom type
         * @param description  description of the argument
         * @param <U>          first type
         * @param <V>          second type
         * @param <W>          third type
         * @param <O>          output type
         * @return new builder instance with the argument inserted
         */
        @API(status = API.Status.STABLE)
        public <U, V, W, O> @NonNull Builder<C> requiredArgumentTriplet(
                final @NonNull CloudKey<O> name,
                final @NonNull TypeToken<O> outputType,
                final @NonNull String firstName,
                final @NonNull ParserDescriptor<C, U> firstParser,
                final @NonNull String secondName,
                final @NonNull ParserDescriptor<C, V> secondParser,
                final @NonNull String thirdName,
                final @NonNull ParserDescriptor<C, W> thirdParser,
                final AggregateParserTripletBuilder.@NonNull Mapper<C, U, V, W, O> mapper,
                final @NonNull Description description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.required(
                    name,
                    AggregateParser.tripletBuilder(firstName, firstParser, secondName, secondParser, thirdName, thirdParser)
                            .withMapper(outputType, mapper)
                            .build(),
                    description
            );
        }

        /**
         * Creates a new argument triplet that maps to a custom type.
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}.
         *
         * @param name         name of the argument
         * @param outputType   the output type
         * @param firstName    name of the first subcomponent
         * @param firstParser  parser for the first subcomponent
         * @param secondName   name of the second subcomponent
         * @param secondParser parser for the second subcomponent
         * @param thirdName    name of the third subcomponent
         * @param thirdParser  parser for the third subcomponent
         * @param mapper       mapper that maps from {@link Triplet} to the custom type
         * @param description  description of the argument
         * @param <U>          first type
         * @param <V>          second type
         * @param <W>          third type
         * @param <O>          output type
         * @return new builder instance with the argument inserted
         */
        @API(status = API.Status.STABLE)
        public <U, V, W, O> @NonNull Builder<C> optionalArgumentTriplet(
                final @NonNull String name,
                final @NonNull TypeToken<O> outputType,
                final @NonNull String firstName,
                final @NonNull ParserDescriptor<C, U> firstParser,
                final @NonNull String secondName,
                final @NonNull ParserDescriptor<C, V> secondParser,
                final @NonNull String thirdName,
                final @NonNull ParserDescriptor<C, W> thirdParser,
                final AggregateParserTripletBuilder.@NonNull Mapper<C, U, V, W, O> mapper,
                final @NonNull Description description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.optional(
                    name,
                    AggregateParser.tripletBuilder(firstName, firstParser, secondName, secondParser, thirdName, thirdParser)
                            .withMapper(outputType, mapper)
                            .build(),
                    description
            );
        }

        /**
         * Creates a new argument triplet that maps to a custom type.
         * <p>
         * For this to work, there must be a {@link CommandManager}
         * attached to the command builder. To guarantee this, it is recommended to get the command builder instance
         * using {@link CommandManager#commandBuilder(String, String...)}.
         *
         * @param name         name of the argument
         * @param outputType   the output type
         * @param firstName    name of the first subcomponent
         * @param firstParser  parser for the first subcomponent
         * @param secondName   name of the second subcomponent
         * @param secondParser parser for the second subcomponent
         * @param thirdName    name of the third subcomponent
         * @param thirdParser  parser for the third subcomponent
         * @param mapper       mapper that maps from {@link Triplet} to the custom type
         * @param description  description of the argument
         * @param <U>          first type
         * @param <V>          second type
         * @param <W>          third type
         * @param <O>          output type
         * @return new builder instance with the argument inserted
         */
        @API(status = API.Status.STABLE)
        public <U, V, W, O> @NonNull Builder<C> optionalArgumentTriplet(
                final @NonNull CloudKey<O> name,
                final @NonNull TypeToken<O> outputType,
                final @NonNull String firstName,
                final @NonNull ParserDescriptor<C, U> firstParser,
                final @NonNull String secondName,
                final @NonNull ParserDescriptor<C, V> secondParser,
                final @NonNull String thirdName,
                final @NonNull ParserDescriptor<C, W> thirdParser,
                final AggregateParserTripletBuilder.@NonNull Mapper<C, U, V, W, O> mapper,
                final @NonNull Description description
        ) {
            if (this.commandManager == null) {
                throw new IllegalStateException("This cannot be called from a command that has no command manager attached");
            }
            return this.optional(
                    name,
                    AggregateParser.tripletBuilder(firstName, firstParser, secondName, secondParser, thirdName, thirdParser)
                            .withMapper(outputType, mapper)
                            .build(),
                    description
            );
        }

        // End of compound helper methods

        /**
         * Specifies the command execution handler.
         *
         * @param commandExecutionHandler New execution handler
         * @return new builder instance using the command execution handler
         */
        public @NonNull Builder<C> handler(final @NonNull CommandExecutionHandler<C> commandExecutionHandler) {
            return new Builder<>(
                    this.commandManager,
                    this.commandMeta,
                    this.senderType,
                    this.commandComponents,
                    commandExecutionHandler,
                    this.permission,
                    this.flags,
                    this.commandDescription
            );
        }

        /**
         * Specifies the command execution handler.
         *
         * @param commandExecutionHandler New execution handler
         * @return new builder instance using the command execution handler
         */
        public @NonNull Builder<C> futureHandler(
                final CommandExecutionHandler.@NonNull FutureCommandExecutionHandler<C> commandExecutionHandler
        ) {
            return this.handler(commandExecutionHandler);
        }

        /**
         * Returns the current command execution handler.
         *
         * @return the current handler
         */
        @API(status = API.Status.STABLE)
        public @NonNull CommandExecutionHandler<C> handler() {
            return this.commandExecutionHandler;
        }

        /**
         * Sets a new command execution handler that invokes the given {@code handler} before the current
         * {@link #handler() handler}.
         *
         * @param handler the handler to invoke before the current handler
         * @return new builder instance
         */
        @API(status = API.Status.STABLE)
        public @NonNull Builder<C> prependHandler(final @NonNull CommandExecutionHandler<C> handler) {
            return this.handler(CommandExecutionHandler.delegatingExecutionHandler(Arrays.asList(handler, this.handler())));
        }

        /**
         * Sets a new command execution handler that invokes the given {@code handler} after the current
         * {@link #handler() handler}.
         *
         * @param handler the handler to invoke after the current handler
         * @return new builder instance
         */
        @API(status = API.Status.STABLE)
        public @NonNull Builder<C> appendHandler(final @NonNull CommandExecutionHandler<C> handler) {
            return this.handler(CommandExecutionHandler.delegatingExecutionHandler(Arrays.asList(this.handler(), handler)));
        }

        /**
         * Specifies a required sender type.
         *
         * @param <N>        the new sender type or a superclass thereof
         * @param senderType required sender type
         * @return new builder instance using the required sender type
         */
        public <N extends C> @NonNull Builder<N> senderType(final @NonNull Class<? extends N> senderType) {
            return this.senderType(TypeToken.get(senderType));
        }

        /**
         * Specifies a required sender type.
         *
         * @param <N>        the new sender type or a superclass thereof
         * @param senderType required sender type
         * @return new builder instance using the required sender type
         */
        @SuppressWarnings("unchecked")
        public <N extends C> @NonNull Builder<N> senderType(final @NonNull TypeToken<? extends N> senderType) {
            return (Builder<N>) new Builder<>(
                    this.commandManager,
                    this.commandMeta,
                    senderType.getType(),
                    this.commandComponents,
                    this.commandExecutionHandler,
                    this.permission,
                    this.flags,
                    this.commandDescription
            );
        }

        /**
         * Specifies a command permission.
         *
         * @param permission the command permission
         * @return new builder instance using the command permission
         */
        public @NonNull Builder<C> permission(final @NonNull Permission permission) {
            return new Builder<>(
                    this.commandManager,
                    this.commandMeta,
                    this.senderType,
                    this.commandComponents,
                    this.commandExecutionHandler,
                    permission,
                    this.flags,
                    this.commandDescription
            );
        }

        /**
         * Specifies a command permission.
         *
         * @param permission the command permission
         * @return new builder instance using the command permission
         */
        public @NonNull Builder<C> permission(final @NonNull PredicatePermission<C> permission) {
            return new Builder<>(
                    this.commandManager,
                    this.commandMeta,
                    this.senderType,
                    this.commandComponents,
                    this.commandExecutionHandler,
                    permission,
                    this.flags,
                    this.commandDescription
            );
        }

        /**
         * Specifies a command permission.
         *
         * @param permission the command permission
         * @return new builder instance using the command permission
         */
        public @NonNull Builder<C> permission(final @NonNull String permission) {
            return new Builder<>(
                    this.commandManager,
                    this.commandMeta,
                    this.senderType,
                    this.commandComponents,
                    this.commandExecutionHandler,
                    Permission.of(permission),
                    this.flags,
                    this.commandDescription
            );
        }

        /**
         * Makes the current command be a proxy of the supplied command. T
         * <p>
         * This means that all the proxied command's variable command arguments will be inserted into this
         * builder instance, in the order they are declared in the proxied command. Furthermore,
         * the proxied command's command handler will be shown by the command that is currently
         * being built. If the current command builder does not have a permission node set, this
         * too will be copied.
         *
         * @param <N> new command sender type
         * @param command the command to proxy
         * @return new builder that proxies the given command
         */
        @SuppressWarnings("unchecked")
        public <N extends C> @NonNull Builder<N> proxies(final @NonNull Command<N> command) {
            Builder<N> builder;
            if (command.senderType().isPresent()) {
                builder = this.senderType(command.senderType().get());
            } else {
                builder = (Builder<N>) this;
            }
            for (final CommandComponent<N> component : command.components()) {
                if (component.type() == CommandComponent.ComponentType.LITERAL) {
                    continue;
                }
                builder = builder.argument(component);
            }
            if (this.permission.permissionString().isEmpty()) {
                builder = builder.permission(command.commandPermission());
            }
            return builder.handler(command.commandExecutionHandler);
        }

        /**
         * Registers a new command flag.
         *
         * @param flag flag
         * @param <T>  flag value type
         * @return new builder instance that uses the provided flag
         */
        public @NonNull <T> Builder<C> flag(final @NonNull CommandFlag<T> flag) {
            final List<CommandFlag<?>> flags = new ArrayList<>(this.flags);
            flags.add(flag);
            return new Builder<>(
                    this.commandManager,
                    this.commandMeta,
                    this.senderType,
                    this.commandComponents,
                    this.commandExecutionHandler,
                    this.permission,
                    Collections.unmodifiableList(flags),
                    this.commandDescription
            );
        }

        /**
         * Registers a new command flag.
         *
         * @param builder flag builder. {@link CommandFlag.Builder#build()} will be invoked.
         * @param <T>     flag value type
         * @return new builder instance that uses the provided flag
         */
        public @NonNull <T> Builder<C> flag(final CommandFlag.@NonNull Builder<C, T> builder) {
            return this.flag(builder.build());
        }

        /**
         * Builds a command using the builder instance.
         *
         * @return built command
         */
        public @NonNull Command<C> build() {
            final List<CommandComponent<C>> commandComponents = new ArrayList<>(this.commandComponents);
            /* Construct flag node */
            if (!this.flags.isEmpty()) {
                final CommandFlagParser<C> flagParser = new CommandFlagParser<>(this.flags);
                final CommandComponent<C> flagComponent =
                        CommandComponent.<C, Object>builder()
                                .name("flags")
                                .parser(flagParser)
                                .valueType(Object.class)
                                .description(Description.of("Command flags"))
                                .build();
                commandComponents.add(flagComponent);
            }
            return new Command<>(
                    Collections.unmodifiableList(commandComponents),
                    this.commandExecutionHandler,
                    this.senderType,
                    this.permission,
                    this.commandMeta,
                    this.commandDescription
            );
        }

        /**
         * Essentially a {@link java.util.function.UnaryOperator} for {@link Builder},
         * but as a separate interface to avoid conflicts.
         *
         * @param <C> command sender type
         */
        @API(status = API.Status.STABLE)
        @FunctionalInterface
        public interface Applicable<C> {

            /**
             * Accepts a {@link Builder} and returns either the same or a modified {@link Builder} instance.
             *
             * @param builder builder
             * @return possibly modified builder
             */
            @API(status = API.Status.STABLE)
            @NonNull Builder<C> applyToCommandBuilder(@NonNull Builder<C> builder);
        }
    }
}
