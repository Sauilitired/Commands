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
package org.incendo.cloud.parser.standard;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apiguardian.api.API;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.caption.CaptionVariable;
import org.incendo.cloud.caption.StandardCaptionKeys;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.exception.parsing.ParserException;
import org.incendo.cloud.parser.ArgumentParseResult;
import org.incendo.cloud.parser.ArgumentParser;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.suggestion.BlockingSuggestionProvider;

@API(status = API.Status.STABLE)
public final class EnumParser<C, E extends Enum<E>> implements ArgumentParser<C, E>,
        BlockingSuggestionProvider.Strings<C> {

    /**
     * Creates a new enum parser.
     *
     * @param <C>       command sender type
     * @param <E>       the enum type
     * @param enumClass the enum class
     * @return the created parser
     */
    @API(status = API.Status.STABLE)
    public static <C, E extends Enum<E>> @NonNull ParserDescriptor<C, E> enumParser(final @NonNull Class<E> enumClass) {
        return ParserDescriptor.of(new EnumParser<>(enumClass), enumClass);
    }

    /**
     * Returns a {@link CommandComponent.Builder} using {@link #enumParser(Class)} as the parser.
     *
     * @param <C>       command sender type
     * @param <E>       the enum type
     * @param enumClass the enum class
     * @return the component builder
     */
    @API(status = API.Status.STABLE)
    public static <C, E extends Enum<E>> CommandComponent.@NonNull Builder<C, E> enumComponent(
            final @NonNull Class<E> enumClass
    ) {
        return CommandComponent.<C, E>builder().parser(enumParser(enumClass));
    }

    private final Class<E> enumClass;
    private final EnumSet<E> acceptedValues;

    /**
     * Construct a new enum parser
     *
     * @param enumClass Enum class
     */
    public EnumParser(final @NonNull Class<E> enumClass) {
        this.enumClass = enumClass;
        this.acceptedValues = EnumSet.allOf(enumClass);
    }

    /**
     * Returns the enum class that was used to create this parser.
     *
     * @return the enum class
     */
    public @NonNull Class<E> enumClass() {
        return this.enumClass;
    }

    /**
     * Returns a collection containing all accepted values.
     *
     * @return the accepted values
     */
    public @NonNull Collection<@NonNull E> acceptedValues() {
        return Collections.unmodifiableSet(this.acceptedValues);
    }

    @Override
    public @NonNull ArgumentParseResult<E> parse(
            final @NonNull CommandContext<C> commandContext,
            final @NonNull CommandInput commandInput
    ) {
        final String input = commandInput.readString();

        for (final E value : this.acceptedValues) {
            final EnumParseable<E> enumParseable = EnumParseable.of(value);
            if (enumParseable.displayName().equalsIgnoreCase(input)) {
                return ArgumentParseResult.success(value);
            }
        }

        return ArgumentParseResult.failure(new EnumParseException(input, this.enumClass, commandContext));
    }

    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(final @NonNull CommandContext<C> commandContext,
                                                                final @NonNull CommandInput input) {
        return EnumSet.allOf(this.enumClass)
                .stream()
                .map(EnumParseable::of)
                .map(EnumParseable::displayName)
                .collect(Collectors.toList());
    }


    @API(status = API.Status.STABLE)
    public static final class EnumParseException extends ParserException {

        private final String input;
        private final Class<? extends Enum<?>> enumClass;

        /**
         * Construct a new enum parse exception
         *
         * @param input     Input
         * @param enumClass Enum class
         * @param context   Command context
         */
        public EnumParseException(
                final @NonNull String input,
                final @NonNull Class<? extends Enum<?>> enumClass,
                final @NonNull CommandContext<?> context
        ) {
            super(
                    EnumParser.class,
                    context,
                    StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_ENUM,
                    CaptionVariable.of("input", input),
                    CaptionVariable.of("acceptableValues", join(enumClass))
            );
            this.input = input;
            this.enumClass = enumClass;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private static @NonNull String join(final @NonNull Class<? extends Enum> clazz) {
            final EnumSet<? extends Enum<?>> enumSet = EnumSet.allOf(clazz);
            return enumSet.stream()
                    .map(o -> EnumParseable.of((Enum) o))
                    .map(EnumParseable::displayName)
                    .collect(Collectors.joining(", "));
        }

        /**
         * Returns the input provided by the sender.
         *
         * @return input
         */
        public @NonNull String input() {
            return this.input;
        }

        /**
         * Returns the enum class that was attempted to be parsed.
         *
         * @return enum class
         */
        public @NonNull Class<? extends Enum<?>> enumClass() {
            return this.enumClass;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || this.getClass() != o.getClass()) {
                return false;
            }
            final EnumParseException that = (EnumParseException) o;
            return this.input.equals(that.input) && this.enumClass.equals(that.enumClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.input, this.enumClass);
        }
    }


    /**
     * Interface that allows for the customization of the parsing of an enum when using a {@link EnumParser}.
     *
     * @param <E> enum type
     */
    public interface EnumParseable<E extends Enum<E>> {

        /**
         * Returns a wrapping {@link EnumParseable} for the given {@code value}.
         *
         * <p>If the {@code value} is an {@link EnumParseable} then {@code value} will be returned.</p>
         *
         * @param <E>   enum type
         * @param value enum value
         * @return the enum parseable instance
         */
        @SuppressWarnings("unchecked")
        static <E extends Enum<E>> EnumParseable<E> of(final @NonNull E value) {
            if (value instanceof EnumParseable<?>) {
                return (EnumParseable<E>) value;
            }
            return new DummyEnumParseable<>(value);
        }

        /**
         * Returns the enum display name. This will be used in suggestions and is the value that will be accepted by the parser.
         *
         * @return display name
         */
        @NonNull String displayName();

        @SuppressWarnings("unchecked")
        default @NonNull E value() {
            if (this instanceof Enum<?>) {
                return (E) this;
            }
            throw new UnsupportedOperationException("Cannot retrieve value for non-enum, #value() needs to be overridden");
        }


        final class DummyEnumParseable<E extends Enum<E>> implements EnumParseable<E> {

            private final E value;

            private DummyEnumParseable(final @NonNull E value) {
                this.value = Objects.requireNonNull(value);
            }

            @Override
            public @NonNull String displayName() {
                return this.value.name().toLowerCase(Locale.ROOT);
            }

            @Override
            public @NonNull E value() {
                return this.value;
            }
        }
    }
}
