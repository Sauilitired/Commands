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
package cloud.commandframework.brigadier;

import cloud.commandframework.Command;
import cloud.commandframework.CommandManager;
import cloud.commandframework.CommandTree;
import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.StaticArgument;
import cloud.commandframework.arguments.compound.CompoundArgument;
import cloud.commandframework.arguments.compound.FlagArgument;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.arguments.parser.MappedArgumentParser;
import cloud.commandframework.arguments.standard.BooleanArgument;
import cloud.commandframework.arguments.standard.ByteArgument;
import cloud.commandframework.arguments.standard.DoubleArgument;
import cloud.commandframework.arguments.standard.FloatArgument;
import cloud.commandframework.arguments.standard.IntegerArgument;
import cloud.commandframework.arguments.standard.LongArgument;
import cloud.commandframework.arguments.standard.ShortArgument;
import cloud.commandframework.arguments.standard.StringArgument;
import cloud.commandframework.arguments.standard.StringArrayArgument;
import cloud.commandframework.brigadier.argument.WrappedBrigadierParser;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.permission.CommandPermission;
import cloud.commandframework.permission.Permission;
import cloud.commandframework.types.tuples.Pair;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.leangen.geantyref.GenericTypeReflector;
import io.leangen.geantyref.TypeToken;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manager used to map cloud {@link Command}
 * <p>
 * The structure of this class is largely inspired by
 * <a href="https://github.com/aikar/commands/blob/master/brigadier/src/main/java/co.aikar.commands/ACFBrigadierManager.java">
 * ACFBrigadiermanager</a> in the ACF project, which was originally written by MiniDigger and licensed under the MIT license.
 *
 * @param <C> Command sender type
 * @param <S> Brigadier sender type
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public final class CloudBrigadierManager<C, S> {

    private static final SuggestionProvider<?> DELEGATE_TO_CLOUD = (c, b) -> b.buildFuture();

    private final Map<Class<?>, BrigadierMapping<C, ?, S>> mappers;
    private final Map<@NonNull Class<?>, @NonNull Supplier<@Nullable ArgumentType<?>>> defaultArgumentTypeSuppliers;
    private final Supplier<CommandContext<C>> dummyContextProvider;
    private final CommandManager<C> commandManager;
    private Function<S, C> brigadierCommandSenderMapper;
    private Function<C, S> backwardsBrigadierCommandSenderMapper;

    /**
     * A sentinel value for declaring that suggestions should be delegated to cloud.
     *
     * @param <T> the sender type
     * @return a singleton sentinel suggestion provider
     */
    static <T> SuggestionProvider<T> delegateSuggestions() {
        return (SuggestionProvider<T>) DELEGATE_TO_CLOUD;
    }

    /**
     * Create a new cloud brigadier manager
     *
     * @param commandManager       Command manager
     * @param dummyContextProvider Provider of dummy context for completions
     */
    public CloudBrigadierManager(
            final @NonNull CommandManager<C> commandManager,
            final @NonNull Supplier<@NonNull CommandContext<C>> dummyContextProvider
    ) {
        this.mappers = new HashMap<>();
        this.defaultArgumentTypeSuppliers = new HashMap<>();
        this.commandManager = commandManager;
        this.dummyContextProvider = dummyContextProvider;
        this.registerInternalMappings();
        commandManager.registerCommandPreProcessor(ctx -> {
            if (this.backwardsBrigadierCommandSenderMapper != null) {
                ctx.getCommandContext().store(
                        WrappedBrigadierParser.COMMAND_CONTEXT_BRIGADIER_NATIVE_SENDER,
                        this.backwardsBrigadierCommandSenderMapper.apply(ctx.getCommandContext().getSender())
                );
            }
        });
    }

    private void registerInternalMappings() {
        /* Map byte, short and int to IntegerArgumentType */
        this.registerMapping(new TypeToken<ByteArgument.ByteParser<C>>() {
        }, builder -> builder.to(argument -> {
            return IntegerArgumentType.integer(argument.getMin(), argument.getMax());
        }));
        this.registerMapping(new TypeToken<ShortArgument.ShortParser<C>>() {
        }, builder -> builder.to(argument -> {
            return IntegerArgumentType.integer(argument.getMin(), argument.getMax());
        }));
        this.registerMapping(new TypeToken<IntegerArgument.IntegerParser<C>>() {
        }, builder -> builder.to(argument -> {
            if (!argument.hasMin() && !argument.hasMax()) {
                return IntegerArgumentType.integer();
            }
            if (argument.hasMin() && !argument.hasMax()) {
                return IntegerArgumentType.integer(argument.getMin());
            } else if (!argument.hasMin()) {
                // Brig uses Integer.MIN_VALUE and Integer.MAX_VALUE for default min/max
                return IntegerArgumentType.integer(Integer.MIN_VALUE, argument.getMax());
            }
            return IntegerArgumentType.integer(argument.getMin(), argument.getMax());
        }));
        /* Map float to FloatArgumentType */
        this.registerMapping(new TypeToken<FloatArgument.FloatParser<C>>() {
        }, builder -> builder.to(argument -> {
            if (!argument.hasMin() && !argument.hasMax()) {
                return FloatArgumentType.floatArg();
            }
            if (argument.hasMin() && !argument.hasMax()) {
                return FloatArgumentType.floatArg(argument.getMin());
            } else if (!argument.hasMin()) {
                // Brig uses -Float.MAX_VALUE and Float.MAX_VALUE for default min/max
                return FloatArgumentType.floatArg(-Float.MAX_VALUE, argument.getMax());
            }
            return FloatArgumentType.floatArg(argument.getMin(), argument.getMax());
        }));
        /* Map double to DoubleArgumentType */
        this.registerMapping(new TypeToken<DoubleArgument.DoubleParser<C>>() {
        }, builder -> builder.to(argument -> {
            if (!argument.hasMin() && !argument.hasMax()) {
                return DoubleArgumentType.doubleArg();
            }
            if (argument.hasMin() && !argument.hasMax()) {
                return DoubleArgumentType.doubleArg(argument.getMin());
            } else if (!argument.hasMin()) {
                // Brig uses -Double.MAX_VALUE and Double.MAX_VALUE for default min/max
                return DoubleArgumentType.doubleArg(-Double.MAX_VALUE, argument.getMax());
            }
            return DoubleArgumentType.doubleArg(argument.getMin(), argument.getMax());
        }));
        /* Map long parser to LongArgumentType */
        this.registerMapping(new TypeToken<LongArgument.LongParser<C>>() {
        }, builder -> builder.to(longParser -> {
            if (!longParser.hasMin() && !longParser.hasMax()) {
                return LongArgumentType.longArg();
            }
            if (longParser.hasMin() && !longParser.hasMax()) {
                return LongArgumentType.longArg(longParser.getMin());
            } else if (!longParser.hasMin()) {
                // Brig uses Long.MIN_VALUE and Long.MAX_VALUE for default min/max
                return LongArgumentType.longArg(Long.MIN_VALUE, longParser.getMax());
            }
            return LongArgumentType.longArg(longParser.getMin(), longParser.getMax());
        }));
        /* Map boolean to BoolArgumentType */
        this.registerMapping(new TypeToken<BooleanArgument.BooleanParser<C>>() {
        }, builder -> builder.toConstant(BoolArgumentType.bool()));
        /* Map String properly to StringArgumentType */
        this.registerMapping(new TypeToken<StringArgument.StringParser<C>>() {
        }, builder -> builder.cloudSuggestions().to(argument -> {
            switch (argument.getStringMode()) {
                case QUOTED:
                    return StringArgumentType.string();
                case GREEDY:
                    return StringArgumentType.greedyString();
                default:
                    return StringArgumentType.word();
            }
        }));
        /* Map flags to a greedy string */
        this.registerMapping(new TypeToken<FlagArgument.FlagArgumentParser<C>>() {
        }, builder -> builder.cloudSuggestions().toConstant(StringArgumentType.greedyString()));
        /* Map String[] to a greedy string */
        this.registerMapping(new TypeToken<StringArrayArgument.StringArrayParser<C>>() {
        }, builder -> builder.cloudSuggestions().toConstant(StringArgumentType.greedyString()));
        /* Map wrapped parsers to their native types */
        this.registerMapping(new TypeToken<WrappedBrigadierParser<C, ?>>() {
        }, builder -> builder.to(WrappedBrigadierParser::getNativeArgument)); // todo : way to  specify custom suggestions
    }

    /**
     * Set the mapper between the Brigadier command sender type and the Cloud command sender type
     *
     * @param mapper Mapper
     * @since 1.2.0
     */
    public void brigadierSenderMapper(
            final @NonNull Function<@NonNull S, @Nullable C> mapper
    ) {
        this.brigadierCommandSenderMapper = mapper;
    }

    /**
     * Get the mapper between Brigadier and Cloud command senders, if one exists
     *
     * @return Mapper
     * @since 1.2.0
     */
    public @Nullable Function<@NonNull S, @Nullable C> brigadierSenderMapper() {
        return this.brigadierCommandSenderMapper;
    }

    /**
     * Set the backwards mapper from Cloud to Brigadier command senders.
     *
     * <p>This is passed to completion requests for mapped argument types.</p>
     *
     * @param mapper the reverse brigadier sender mapper
     * @since 1.5.0
     */
    public void backwardsBrigadierSenderMapper(final @NonNull Function<@NonNull C, @Nullable S> mapper) {
        this.backwardsBrigadierCommandSenderMapper = mapper;
    }

    /**
     * Set whether to use Brigadier's native suggestions for number argument types.
     * <p>
     * If Brigadier's suggestions are not used, cloud's default number suggestion provider will be used.
     *
     * @param nativeNumberSuggestions Whether or not Brigadier suggestions should be used for numbers
     * @since 1.2.0
     */
    public void setNativeNumberSuggestions(final boolean nativeNumberSuggestions) {
        this.setNativeSuggestions(new TypeToken<ByteArgument.ByteParser<C>>() {
        }, nativeNumberSuggestions);
        this.setNativeSuggestions(new TypeToken<ShortArgument.ShortParser<C>>() {
        }, nativeNumberSuggestions);
        this.setNativeSuggestions(new TypeToken<IntegerArgument.IntegerParser<C>>() {
        }, nativeNumberSuggestions);
        this.setNativeSuggestions(new TypeToken<FloatArgument.FloatParser<C>>() {
        }, nativeNumberSuggestions);
        this.setNativeSuggestions(new TypeToken<DoubleArgument.DoubleParser<C>>() {
        }, nativeNumberSuggestions);
        this.setNativeSuggestions(new TypeToken<LongArgument.LongParser<C>>() {
        }, nativeNumberSuggestions);
    }

    /**
     * Set whether to use Brigadier's native suggestions for an argument type with an already registered mapper.
     * <p>
     * If Brigadier's suggestions are not used, suggestions will fall back to the cloud suggestion provider.
     *
     * @param argumentType      cloud argument parser type
     * @param nativeSuggestions Whether or not Brigadier suggestions should be used
     * @param <T>               argument type
     * @param <K>               cloud argument parser type
     * @throws IllegalArgumentException when there is no mapper registered for the provided argument type
     * @since 1.2.0
     */
    public <T, K extends ArgumentParser<C, T>> void setNativeSuggestions(
            final @NonNull TypeToken<K> argumentType,
            final boolean nativeSuggestions
    ) throws IllegalArgumentException {
        final BrigadierMapping<C, ?, S> pair = this.mappers.get(
                GenericTypeReflector.erase(argumentType.getType())
        );
        if (pair == null) {
            throw new IllegalArgumentException(
                    "No mapper registered for type: " + GenericTypeReflector
                            .erase(argumentType.getType())
                            .toGenericString()
            );
        }
        this.mappers.put(
                GenericTypeReflector.erase(argumentType.getType()),
                pair.withNativeSuggestions(nativeSuggestions)
        );
    }

    /**
     * Register a cloud-Brigadier mapping
     *
     * @param argumentType      cloud argument parser type
     * @param nativeSuggestions Whether or not Brigadier suggestions should be used
     * @param mapper            mapper function
     * @param <T>               cloud argument value type
     * @param <K>               cloud argument type
     * @param <O>               Brigadier argument type value
     * @deprecated for removal since 1.5.0, use {@link #registerMapping(TypeToken, Consumer)} instead.
     */
    @Deprecated
    @SuppressWarnings("unchecked")
    public <T, K extends ArgumentParser<C, T>, O> void registerMapping(
            final @NonNull TypeToken<K> argumentType,
            final boolean nativeSuggestions,
            final @NonNull Function<@NonNull ? extends K,
                    @NonNull ? extends ArgumentType<O>> mapper
    ) {
        this.registerMapping(argumentType, builder -> {
            builder.to((Function<K, ? extends ArgumentType<?>>) mapper);
            if (!nativeSuggestions) {
                builder.cloudSuggestions();
            }
        });
    }

    /**
     * Register a cloud-Brigadier mapping.
     *
     * @param parserType The cloud argument parser type
     * @param configurer a callback that will configure the mapping attributes
     * @param <K>        cloud argument parser type
     * @since 1.5.0
     */
    public <K extends ArgumentParser<C, ?>> void registerMapping(
            final @NonNull TypeToken<K> parserType,
            final Consumer<BrigadierMappingBuilder<K, S>> configurer
    ) {
        final BrigadierMapping.BuilderImpl<C, K, S> builder = new BrigadierMapping.BuilderImpl<>();
        configurer.accept(builder);
        this.mappers.put(GenericTypeReflector.erase(parserType.getType()), builder.build());

    }

    /**
     * Register a default mapping to between a class and a Brigadier argument type
     *
     * @param clazz    Type to map
     * @param supplier Supplier that supplies the argument type
     */
    public void registerDefaultArgumentTypeSupplier(
            final @NonNull Class<?> clazz,
            final @NonNull Supplier<@Nullable ArgumentType<?>> supplier
    ) {
        this.defaultArgumentTypeSuppliers.put(clazz, supplier);
    }

    @SuppressWarnings("all")
    private <T, K extends ArgumentParser<C, ?>> @Nullable Pair<@NonNull ArgumentType<?>, @Nullable SuggestionProvider<S>> getArgument(
            final @NonNull TypeToken<?> valueType,
            final @NonNull K argumentParser
    ) {
        /* Unwrap mapped arguments */
        ArgumentParser<C, ?> commandArgument = (ArgumentParser<C, ?>) argumentParser;
        while (commandArgument instanceof MappedArgumentParser<?, ?, ?>) {
            commandArgument = ((MappedArgumentParser<C, ?, ?>) commandArgument).getBaseParser();
        }

        final BrigadierMapping<C, K, S> mapping = (BrigadierMapping<C, K, S>) this.mappers
                .get(commandArgument.getClass());
        if (mapping == null || mapping.getMapper() == null) {
            return this.createDefaultMapper(valueType);
        }
        return Pair.of(
                (ArgumentType<?>) ((Function) mapping.getMapper()).apply(commandArgument),
                mapping.makeSuggestionProvider(argumentParser) // todo: should we use unwrapped here?
        );
    }

    private @NonNull Pair<@NonNull ArgumentType<?>, @Nullable SuggestionProvider<S>> createDefaultMapper(
            final @NonNull TypeToken<?> clazz
    ) {
        final Supplier<ArgumentType<?>> argumentTypeSupplier = this.defaultArgumentTypeSuppliers
                .get(GenericTypeReflector.erase(clazz.getType()));
        final @Nullable ArgumentType<?> defaultType;
        if (argumentTypeSupplier != null) {
            defaultType = argumentTypeSupplier.get();
        } else {
            defaultType = null;
        }
        if (defaultType != null) {
            return Pair.of(argumentTypeSupplier.get(), null);
        }
        return Pair.of(StringArgumentType.word(), delegateSuggestions());
    }

    /**
     * Create a new literal command node
     *
     * @param label             Command label
     * @param cloudCommand      Cloud command instance
     * @param permissionChecker Permission checker
     * @param forceRegister     Whether or not to force register an executor at every node
     * @param executor          Command executor
     * @return Literal command node
     */
    public @NonNull LiteralCommandNode<S> createLiteralCommandNode(
            final @NonNull String label,
            final @NonNull Command<C> cloudCommand,
            final @NonNull BiPredicate<@NonNull S,
                    @NonNull CommandPermission> permissionChecker,
            final boolean forceRegister,
            final com.mojang.brigadier.@NonNull Command<S> executor
    ) {
        final CommandTree.Node<CommandArgument<C, ?>> node = this.commandManager
                .getCommandTree().getNamedNode(cloudCommand.getArguments().get(0).getName());
        final SuggestionProvider<S> provider = (context, builder) -> this.buildSuggestions(
                context,
                null, /* parent node, null for the literal command node root */
                node.getValue(),
                builder
        );

        final LiteralArgumentBuilder<S> literalArgumentBuilder = LiteralArgumentBuilder
                .<S>literal(label)
                .requires(sender -> permissionChecker.test(sender, (CommandPermission) node.getNodeMeta()
                        .getOrDefault(
                                "permission",
                                Permission.empty()
                        )));
        if (forceRegister || (node.getValue() != null && node.getValue().getOwningCommand() != null)) {
            literalArgumentBuilder.executes(executor);
        }
        literalArgumentBuilder.executes(executor);
        final LiteralCommandNode<S> constructedRoot = literalArgumentBuilder.build();
        for (final CommandTree.Node<CommandArgument<C, ?>> child : node.getChildren()) {
            constructedRoot.addChild(this.constructCommandNode(forceRegister, child,
                    permissionChecker, executor, provider
            ).build());
        }
        return constructedRoot;
    }

    /**
     * Create a literal command from Brigadier command info, and a cloud command instance
     *
     * @param cloudCommand       Cloud root command
     * @param root               Brigadier root command
     * @param suggestionProvider Brigadier suggestions provider
     * @param executor           Brigadier command executor
     * @param permissionChecker  Permission checker
     * @return Constructed literal command node
     */
    public @NonNull LiteralCommandNode<S> createLiteralCommandNode(
            final CommandTree.@NonNull Node<@NonNull CommandArgument<C, ?>> cloudCommand,
            final @NonNull LiteralCommandNode<S> root,
            final @NonNull SuggestionProvider<S> suggestionProvider,
            final com.mojang.brigadier.@NonNull Command<S> executor,
            final @NonNull BiPredicate<@NonNull S, @NonNull CommandPermission> permissionChecker
    ) {
        final LiteralArgumentBuilder<S> literalArgumentBuilder = LiteralArgumentBuilder.<S>literal(root.getLiteral())
                .requires(sender -> permissionChecker.test(
                        sender,
                        (CommandPermission) cloudCommand.getNodeMeta()
                                .getOrDefault(
                                        "permission",
                                        Permission.empty()
                                )
                ));
        if (cloudCommand.getValue() != null && cloudCommand.getValue().getOwningCommand() != null) {
            literalArgumentBuilder.executes(executor);
        }
        final LiteralCommandNode<S> constructedRoot = literalArgumentBuilder.build();
        for (final CommandTree.Node<CommandArgument<C, ?>> child : cloudCommand.getChildren()) {
            constructedRoot.addChild(this.constructCommandNode(false, child, permissionChecker,
                    executor, suggestionProvider
            ).build());
        }
        return constructedRoot;
    }

    private @NonNull ArgumentBuilder<S, ?> constructCommandNode(
            final boolean forceExecutor,
            final CommandTree.@NonNull Node<CommandArgument<C, ?>> root,
            final @NonNull BiPredicate<@NonNull S, @NonNull CommandPermission> permissionChecker,
            final com.mojang.brigadier.@NonNull Command<S> executor,
            final SuggestionProvider<S> suggestionProvider
    ) {
        if (root.getValue() instanceof CompoundArgument) {
            @SuppressWarnings("unchecked") final CompoundArgument<?, C, ?> compoundArgument = (CompoundArgument<?, C, ?>) root.getValue();
            final Object[] parsers = compoundArgument.getParserTuple().toArray();
            final Object[] types = compoundArgument.getTypes().toArray();
            final Object[] names = compoundArgument.getNames().toArray();

            /* Build nodes backwards */
            final ArgumentBuilder<S, ?>[] argumentBuilders = new ArgumentBuilder[parsers.length];

            for (int i = parsers.length - 1; i >= 0; i--) {
                @SuppressWarnings("unchecked") final ArgumentParser<C, ?> parser = (ArgumentParser<C, ?>) parsers[i];
                final Pair<ArgumentType<?>, SuggestionProvider<S>> pair = this.getArgument(
                        TypeToken.get((Class<?>) types[i]),
                        parser
                );
                final SuggestionProvider<S> provider = pair.getSecond() == delegateSuggestions() ? suggestionProvider
                        : pair.getSecond();

                final ArgumentBuilder<S, ?> fragmentBuilder = RequiredArgumentBuilder
                        .<S, Object>argument((String) names[i], (ArgumentType<Object>) pair.getFirst())
                        .suggests(provider)
                        .requires(sender -> permissionChecker.test(
                                sender,
                                (CommandPermission) root.getNodeMeta()
                                        .getOrDefault(
                                                "permission",
                                                Permission.empty()
                                        )
                        ));
                argumentBuilders[i] = fragmentBuilder;

                if (forceExecutor || ((i == parsers.length - 1) && (root.isLeaf() || !root.getValue().isRequired()))) {
                    fragmentBuilder.executes(executor);
                }

                /* Link all previous builder to this one */
                if ((i + 1) < parsers.length) {
                    fragmentBuilder.then(argumentBuilders[i + 1]);
                }
            }

            for (final CommandTree.Node<CommandArgument<C, ?>> node : root.getChildren()) {
                argumentBuilders[parsers.length - 1]
                        .then(this.constructCommandNode(forceExecutor, node, permissionChecker, executor, suggestionProvider));
            }

            return argumentBuilders[0];
        }
        final ArgumentBuilder<S, ?> argumentBuilder;
        if (root.getValue() instanceof StaticArgument) {
            argumentBuilder = LiteralArgumentBuilder.<S>literal(root.getValue().getName())
                    .requires(sender -> permissionChecker.test(sender, (CommandPermission) root.getNodeMeta()
                            .getOrDefault(
                                    "permission",
                                    Permission.empty()
                            )))
                    .executes(executor);
        } else {
            // Register argument
            final Pair<ArgumentType<?>, SuggestionProvider<S>> pair = this.getArgument(
                    root.getValue().getValueType(),
                    root.getValue().getParser()
            );
            final SuggestionProvider<S> provider = pair.getSecond() == delegateSuggestions()
                    // todo: nodesupplyingparser equivalent
                    //|| !(root.getValue().getSuggestionsProvider() instanceof DelegatingSuggestionsProvider) // todo
                    ? (context, builder) -> this.buildSuggestions(
                            context,
                            root.getParent(),
                            root.getValue(),
                            builder
                    ) : pair.getSecond();
            argumentBuilder = RequiredArgumentBuilder
                    .<S, Object>argument(root.getValue().getName(), (ArgumentType<Object>) pair.getFirst())
                    .suggests(provider)
                    .requires(sender -> permissionChecker.test(
                            sender,
                            (CommandPermission) root.getNodeMeta()
                                    .getOrDefault(
                                            "permission",
                                            Permission.empty()
                                    )
                    ));
        }
        if (forceExecutor || root.isLeaf() || !root.getValue().isRequired()) {
            argumentBuilder.executes(executor);
        }
        if (root.getChildren().stream().noneMatch(node -> node.getValue().isRequired())) {
            argumentBuilder.executes(executor);
        }
        for (final CommandTree.Node<CommandArgument<C, ?>> node : root.getChildren()) {
            argumentBuilder.then(this.constructCommandNode(forceExecutor, node, permissionChecker, executor, suggestionProvider));
        }
        return argumentBuilder;
    }

    private @NonNull CompletableFuture<Suggestions> buildSuggestions(
            final com.mojang.brigadier.context.@Nullable CommandContext<S> senderContext,
            final CommandTree.@Nullable Node<CommandArgument<C, ?>> parentNode,
            final @NonNull CommandArgument<C, ?> argument,
            final @NonNull SuggestionsBuilder builder
    ) {
        final CommandContext<C> commandContext;
        String command = builder.getInput();
        if (this.brigadierCommandSenderMapper == null || senderContext == null) {
            commandContext = this.dummyContextProvider.get();
            if (command.startsWith("/") /* Minecraft specific */) {
                command = command.substring(1);
            }
        } else {
            final C cloudSender = this.brigadierCommandSenderMapper.apply(senderContext.getSource());
            commandContext = new CommandContext<>(
                    true,
                    cloudSender,
                    this.commandManager
            );
            command = command.substring(senderContext.getLastChild().getNodes().get(0).getRange().getStart());
        }

        /* Remove namespace */
        final String leading = command.split(" ")[0];
        if (leading.contains(":")) {
            command = command.substring(leading.split(":")[0].length() + 1);
        }

        final List<String> suggestionsUnfiltered = this.commandManager.suggest(
                commandContext.getSender(),
                command
        );

        /* Filter suggestions that are literal arguments to avoid duplicates, except for root arguments */
        final List<String> suggestions = new ArrayList<>(suggestionsUnfiltered);
        if (parentNode != null) {
            final Set<String> siblingLiterals = parentNode.getChildren().stream()
                    .map(CommandTree.Node::getValue)
                    .flatMap(arg -> (arg instanceof StaticArgument)
                            ? ((StaticArgument<C>) arg).getAliases().stream() : Stream.empty())
                    .collect(Collectors.toSet());

            suggestions.removeIf(siblingLiterals::contains);
        }

        SuggestionsBuilder suggestionsBuilder = builder;

        final int lastIndexOfSpaceInRemainingString = builder.getRemaining().lastIndexOf(' ');
        if (lastIndexOfSpaceInRemainingString != -1) {
            suggestionsBuilder = builder.createOffset(builder.getStart() + lastIndexOfSpaceInRemainingString + 1);
        }

        for (final String suggestion : suggestions) {
            String tooltip = argument.getName();
            if (!(argument instanceof StaticArgument)) {
                if (argument.isRequired()) {
                    tooltip = '<' + tooltip + '>';
                } else {
                    tooltip = '[' + tooltip + ']';
                }
            }
            suggestionsBuilder = suggestionsBuilder.suggest(suggestion, new LiteralMessage(tooltip));
        }

        return suggestionsBuilder.buildFuture();
    }

}
