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
package cloud.commandframework.sponge;

import cloud.commandframework.arguments.CommandArgument;
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
import cloud.commandframework.arguments.standard.UUIDArgument;
import io.leangen.geantyref.GenericTypeReflector;
import io.leangen.geantyref.TypeToken;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.api.command.registrar.tree.CommandTreeNode;
import org.spongepowered.api.command.registrar.tree.CommandTreeNodeTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

/**
 * Class responsible for mapping Cloud {@link ArgumentParser ArgumentParsers} to Sponge
 * {@link CommandTreeNode.Argument CommandTreeNode.Arguments}.
 *
 * @param <C> sender type
 */
public final class SpongeParserMapper<C> {

    private static final Class<?> DELEGATING_SUGGESTIONS_PROVIDER; // todo - ugly

    static {
        try {
            DELEGATING_SUGGESTIONS_PROVIDER = Class.forName("cloud.commandframework.arguments.DelegatingSuggestionsProvider");
        } catch (final ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private final Map<Class<?>, Mapping<C, ?>> mappers = new HashMap<>();

    SpongeParserMapper() {
        this.initStandardMappers();
    }

    CommandTreeNode.Argument<? extends CommandTreeNode.Argument<?>> mapArgument(final CommandArgument<C, ?> value) {
        final CommandTreeNode.Argument<? extends CommandTreeNode.Argument<?>> result = this.mapParser(value.getParser());
        final boolean customSuggestionsProvider = !DELEGATING_SUGGESTIONS_PROVIDER.isInstance(value.getSuggestionsProvider());
        if (customSuggestionsProvider) {
            result.customCompletions();
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    CommandTreeNode.Argument<? extends CommandTreeNode.Argument<?>> mapParser(final ArgumentParser<C, ?> argumentParser) {
        final CommandTreeNode.Argument<? extends CommandTreeNode.Argument<?>> result;
        ArgumentParser<C, ?> parser = argumentParser;
        while (parser instanceof MappedArgumentParser<?, ?, ?>) {
            parser = ((MappedArgumentParser<C, ?, ?>) parser).getBaseParser();
        }
        final Mapping<C, ?> mapper = this.mappers.get(parser.getClass());
        if (mapper != null) {
            final CommandTreeNode.Argument<? extends CommandTreeNode.Argument<?>> apply =
                    (CommandTreeNode.Argument<? extends CommandTreeNode.Argument<?>>) ((Function) mapper.mapper).apply(parser);
            if (mapper.cloudSuggestions) {
                apply.customCompletions();
                return apply;
            }
            result = apply;
        } else if (parser instanceof NodeSupplyingArgumentParser) {
            result = ((NodeSupplyingArgumentParser<C, ?>) parser).node();
        } else {
            result = CommandTreeNodeTypes.STRING.get().createNode().customCompletions().word();
        }
        return result;
    }

    private void initStandardMappers() {
        this.registerMapping(new TypeToken<StringArgument.StringParser<C>>() {
        }, builder -> builder.to(stringParser -> {
            final StringArgument.StringMode mode = stringParser.getStringMode();
            if (mode == StringArgument.StringMode.SINGLE) {
                return CommandTreeNodeTypes.STRING.get().createNode().customCompletions().word();
            } else if (mode == StringArgument.StringMode.QUOTED) {
                return CommandTreeNodeTypes.STRING.get().createNode().customCompletions();
            } else if (mode == StringArgument.StringMode.GREEDY) {
                return CommandTreeNodeTypes.STRING.get().createNode().customCompletions().greedy();
            }
            throw new IllegalArgumentException("Unknown string mode '" + mode + "'!");
        }));
        this.registerMapping(new TypeToken<ByteArgument.ByteParser<C>>() {
        }, builder -> builder.to(byteParser -> {
            final CommandTreeNode.Range<Integer> node = CommandTreeNodeTypes.INTEGER.get().createNode();
            node.min((int) byteParser.getMin());
            node.max((int) byteParser.getMax());
            return node;
        }));
        this.registerMapping(new TypeToken<ShortArgument.ShortParser<C>>() {
        }, builder -> builder.to(shortParser -> {
            final CommandTreeNode.Range<Integer> node = CommandTreeNodeTypes.INTEGER.get().createNode();
            node.min((int) shortParser.getMin());
            node.max((int) shortParser.getMax());
            return node;
        }));
        this.registerMapping(new TypeToken<IntegerArgument.IntegerParser<C>>() {
        }, builder -> builder.to(integerParser -> {
            final CommandTreeNode.Range<Integer> node = CommandTreeNodeTypes.INTEGER.get().createNode();
            if (integerParser.hasMin()) {
                node.min(integerParser.getMin());
            }
            if (integerParser.hasMax()) {
                node.max(integerParser.getMax());
            }
            return node;
        }));
        this.registerMapping(new TypeToken<FloatArgument.FloatParser<C>>() {
        }, builder -> builder.to(floatParser -> {
            final CommandTreeNode.Range<Float> node = CommandTreeNodeTypes.FLOAT.get().createNode();
            if (floatParser.hasMin()) {
                node.min(floatParser.getMin());
            }
            if (floatParser.hasMax()) {
                node.max(floatParser.getMax());
            }
            return node;
        }));
        this.registerMapping(new TypeToken<DoubleArgument.DoubleParser<C>>() {
        }, builder -> builder.to(doubleParser -> {
            final CommandTreeNode.Range<Double> node = CommandTreeNodeTypes.DOUBLE.get().createNode();
            if (doubleParser.hasMin()) {
                node.min(doubleParser.getMin());
            }
            if (doubleParser.hasMax()) {
                node.max(doubleParser.getMax());
            }
            return node;
        }));
        this.registerMapping(new TypeToken<LongArgument.LongParser<C>>() {
        }, builder -> builder.to(longParser -> {
            final CommandTreeNode.Range<Long> node = CommandTreeNodeTypes.LONG.get().createNode();
            if (longParser.hasMin()) {
                node.min(longParser.getMin());
            }
            if (longParser.hasMax()) {
                node.max(longParser.getMax());
            }
            return node;
        }));
        this.registerMapping(new TypeToken<BooleanArgument.BooleanParser<C>>() {
        }, builder -> builder.to(booleanParser -> {
            return CommandTreeNodeTypes.BOOL.get().createNode();
        }));
        this.registerMapping(new TypeToken<FlagArgument.FlagArgumentParser<C>>() {
        }, builder -> builder.to(flagArgumentParser -> {
            return CommandTreeNodeTypes.STRING.get().createNode().customCompletions().greedy();
        }));
        this.registerMapping(new TypeToken<StringArrayArgument.StringArrayParser<C>>() {
        }, builder -> builder.to(stringArrayParser -> {
            return CommandTreeNodeTypes.STRING.get().createNode().customCompletions().greedy();
        }));
        this.registerMapping(new TypeToken<UUIDArgument.UUIDParser<C>>() {
        }, builder -> builder.to(uuidParser -> {
            return CommandTreeNodeTypes.UUID.get().createNode();
        }));
    }

    /**
     * Register a mapping from a Cloud {@link ArgumentParser} type to a Sponge {@link CommandTreeNode.Argument}. This will
     * replace any existing mapping.
     *
     * @param cloudType  cloud argument parser type
     * @param configurer builder configurer
     * @param <A>        cloud argument parser type
     */
    public <A extends ArgumentParser<C, ?>> void registerMapping(
            final @NonNull TypeToken<A> cloudType,
            final @NonNull Consumer<MappingBuilder<C, A>> configurer
    ) {
        final MappingBuilderImpl<C, A> builder = new MappingBuilderImpl<>();
        configurer.accept(builder);
        this.mappers.put(GenericTypeReflector.erase(cloudType.getType()), builder.build());
    }

    /**
     * Set whether to use Cloud suggestions, or to fall back on the suggestions provided
     * by the {@link CommandTreeNode.Argument} for an already registered mapping. This is effectively {@code false} by default.
     *
     * @param parserType       cloud argument parser type
     * @param cloudSuggestions Whether or not Cloud suggestions should be used
     * @param <T>              argument value type
     * @param <A>              cloud argument parser type
     * @throws IllegalArgumentException when there is no mapper registered for the provided argument type
     */
    public <T, A extends ArgumentParser<C, T>> void cloudSuggestions(
            final @NonNull TypeToken<A> parserType,
            final boolean cloudSuggestions
    ) throws IllegalArgumentException {
        final Mapping<C, ?> mapping = this.mappers.get(GenericTypeReflector.erase(parserType.getType()));
        if (mapping == null) {
            throw new IllegalArgumentException(
                    "No mapper registered for type: " + GenericTypeReflector
                            .erase(parserType.getType())
                            .toGenericString()
            );
        }
        this.mappers.put(
                GenericTypeReflector.erase(parserType.getType()),
                new Mapping<>(mapping.mapper, cloudSuggestions)
        );
    }

    /**
     * Set whether to use Cloud's custom suggestions for number argument types. If {@code false}, the default Brigadier number
     * completions will be used.
     *
     * @param cloudNumberSuggestions whether to use Cloud number suggestions
     */
    public void cloudNumberSuggestions(final boolean cloudNumberSuggestions) {
        this.cloudSuggestions(new TypeToken<ByteArgument.ByteParser<C>>() {
        }, cloudNumberSuggestions);
        this.cloudSuggestions(new TypeToken<ShortArgument.ShortParser<C>>() {
        }, cloudNumberSuggestions);
        this.cloudSuggestions(new TypeToken<IntegerArgument.IntegerParser<C>>() {
        }, cloudNumberSuggestions);
        this.cloudSuggestions(new TypeToken<FloatArgument.FloatParser<C>>() {
        }, cloudNumberSuggestions);
        this.cloudSuggestions(new TypeToken<DoubleArgument.DoubleParser<C>>() {
        }, cloudNumberSuggestions);
        this.cloudSuggestions(new TypeToken<LongArgument.LongParser<C>>() {
        }, cloudNumberSuggestions);
    }

    /**
     * Builder for mappings from Cloud {@link ArgumentParser ArgumentParsers} to Sponge
     * {@link CommandTreeNode.Argument CommandTreeNode.Arguments}
     *
     * @param <C> sender type
     * @param <A> parser type
     */
    public interface MappingBuilder<C, A extends ArgumentParser<C, ?>> {

        /**
         * Set whether to use cloud suggestions, or to fall back onto {@link CommandTreeNodeTypes}. By default, this is set to
         * {@code false}.
         *
         * @param cloudSuggestions whether to use cloud suggestions
         * @return this builder
         */
        @NonNull MappingBuilder<C, A> cloudSuggestions(boolean cloudSuggestions);

        /**
         * Set the mapper function from {@link A} to {@link CommandTreeNode.Argument}.
         *
         * @param mapper mapper function
         * @return this builder
         */
        @NonNull MappingBuilder<C, A> to(@NonNull Function<A, CommandTreeNode.Argument<? extends CommandTreeNode.Argument<?>>> mapper);

    }

    private static final class MappingBuilderImpl<C, A extends ArgumentParser<C, ?>> implements MappingBuilder<C, A> {

        private Function<A, CommandTreeNode.Argument<? extends CommandTreeNode.Argument<?>>> mapper;
        private boolean cloudSuggestions;

        @Override
        public @NonNull MappingBuilder<C, A> cloudSuggestions(final boolean cloudSuggestions) {
            this.cloudSuggestions = cloudSuggestions;
            return this;
        }

        @Override
        public @NonNull MappingBuilder<C, A> to(
                final @NonNull Function<A, CommandTreeNode.Argument<? extends CommandTreeNode.Argument<?>>> mapper
        ) {
            this.mapper = mapper;
            return this;
        }

        private SpongeParserMapper.@NonNull Mapping<C, A> build() {
            requireNonNull(this.mapper, "Must provide a mapper function!");
            return new Mapping<>(this.mapper, this.cloudSuggestions);
        }

    }

    private static final class Mapping<C, A extends ArgumentParser<C, ?>> {

        private final Function<A, CommandTreeNode.Argument<? extends CommandTreeNode.Argument<?>>> mapper;
        private final boolean cloudSuggestions;

        private Mapping(
                final Function<A, CommandTreeNode.Argument<? extends CommandTreeNode.Argument<?>>> mapper,
                final boolean cloudSuggestions
        ) {
            this.mapper = mapper;
            this.cloudSuggestions = cloudSuggestions;
        }

    }

}
