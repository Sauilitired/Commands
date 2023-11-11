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
package cloud.commandframework.arguments.standard;

import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.NoInputProvidedException;
import cloud.commandframework.exceptions.parsing.NumberParseException;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.function.BiFunction;
import org.apiguardian.api.API;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.common.returnsreceiver.qual.This;

@SuppressWarnings("unused")
@API(status = API.Status.STABLE, since = "1.5.0")
public final class ShortArgument<C> extends CommandArgument<C, Short> {

    private final short min;
    private final short max;

    private ShortArgument(
            final @NonNull String name,
            final short min,
            final short max,
            final @Nullable BiFunction<@NonNull CommandContext<C>, @NonNull String,
                    @NonNull List<String>> suggestionsProvider,
            final @NonNull ArgumentDescription defaultDescription
    ) {
        super(name, new ShortParser<>(min, max), Short.class, suggestionsProvider, defaultDescription);
        this.min = min;
        this.max = max;
    }

    /**
     * Create a new {@link Builder}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return new {@link Builder}
     * @since 1.8.0
     */
    @API(status = API.Status.STABLE, since = "1.8.0")
    public static <C> @NonNull Builder<C> builder(final @NonNull String name) {
        return new Builder<>(name);
    }

    /**
     * Create a new builder
     *
     * @param name Name of the argument
     * @param <C>  Command sender type
     * @return Created builder
     * @deprecated prefer {@link #builder(String)}
     */
    @API(status = API.Status.DEPRECATED, since = "1.8.0")
    @Deprecated
    public static <C> @NonNull Builder<C> newBuilder(final @NonNull String name) {
        return builder(name);
    }

    /**
     * Create a new required {@link ShortArgument}.
     *
     * @param name Argument name
     * @param <C>  Command sender type
     * @return Created argument
     */
    public static <C> @NonNull CommandArgument<C, Short> of(final @NonNull String name) {
        return ShortArgument.<C>builder(name).build();
    }

    /**
     * Get the minimum accepted short that could have been parsed
     *
     * @return Minimum short
     */
    public short getMin() {
        return this.min;
    }

    /**
     * Get the maximum accepted short that could have been parsed
     *
     * @return Maximum short
     */
    public short getMax() {
        return this.max;
    }


    @API(status = API.Status.STABLE)
    public static final class Builder<C> extends CommandArgument.Builder<C, Short> {

        private short min = ShortParser.DEFAULT_MINIMUM;
        private short max = ShortParser.DEFAULT_MAXIMUM;

        private Builder(final @NonNull String name) {
            super(Short.class, name);
        }

        /**
         * Set a minimum value
         *
         * @param min Minimum value
         * @return Builder instance
         */
        public @NonNull @This Builder<C> withMin(final short min) {
            this.min = min;
            return this;
        }

        /**
         * Set a maximum value
         *
         * @param max Maximum value
         * @return Builder instance
         */
        public @NonNull @This Builder<C> withMax(final short max) {
            this.max = max;
            return this;
        }

        @Override
        public @NonNull ShortArgument<C> build() {
            return new ShortArgument<>(this.getName(), this.min, this.max,
                    this.getSuggestionsProvider(), this.getDefaultDescription()
            );
        }
    }


    @API(status = API.Status.STABLE)
    public static final class ShortParser<C> implements ArgumentParser<C, Short> {

        /**
         * Constant for the default/unset minimum value.
         *
         * @since 1.5.0
         */
        @API(status = API.Status.STABLE, since = "1.5.0")
        public static final short DEFAULT_MINIMUM = Short.MIN_VALUE;

        /**
         * Constant for the default/unset maximum value.
         *
         * @since 1.5.0
         */
        @API(status = API.Status.STABLE, since = "1.5.0")
        public static final short DEFAULT_MAXIMUM = Short.MAX_VALUE;

        private final short min;
        private final short max;

        /**
         * Construct a new short parser
         *
         * @param min Minimum value
         * @param max Maximum value
         */
        public ShortParser(final short min, final short max) {
            this.min = min;
            this.max = max;
        }

        @Override
        public @NonNull ArgumentParseResult<Short> parse(
                final @NonNull CommandContext<C> commandContext,
                final @NonNull Queue<@NonNull String> inputQueue
        ) {
            final String input = inputQueue.peek();
            if (input == null) {
                return ArgumentParseResult.failure(new NoInputProvidedException(ShortParser.class, commandContext));
            }
            try {
                final short value = Short.parseShort(input);
                if (value < this.min || value > this.max) {
                    return ArgumentParseResult.failure(new ShortParseException(input, this, commandContext));
                }
                inputQueue.remove();
                return ArgumentParseResult.success(value);
            } catch (final Exception e) {
                return ArgumentParseResult.failure(new ShortParseException(input, this, commandContext));
            }
        }

        @Override
        public boolean isContextFree() {
            return true;
        }

        @Override
        public @NonNull List<@NonNull String> suggestions(
                final @NonNull CommandContext<C> commandContext,
                final @NonNull String input
        ) {
            return IntegerArgument.IntegerParser.getSuggestions(this.min, this.max, input);
        }

        /**
         * Get the max value
         *
         * @return Max value
         */
        public short getMax() {
            return this.max;
        }

        /**
         * Get the min value
         *
         * @return Min value
         */
        public short getMin() {
            return this.min;
        }

        /**
         * Get whether this parser has a maximum set.
         * This will compare the parser's maximum to {@link #DEFAULT_MAXIMUM}.
         *
         * @return whether the parser has a maximum set
         * @since 1.5.0
         */
        @API(status = API.Status.STABLE, since = "1.5.0")
        public boolean hasMax() {
            return this.max != DEFAULT_MAXIMUM;
        }

        /**
         * Get whether this parser has a minimum set.
         * This will compare the parser's minimum to {@link #DEFAULT_MINIMUM}.
         *
         * @return whether the parser has a maximum set
         * @since 1.5.0
         */
        @API(status = API.Status.STABLE, since = "1.5.0")
        public boolean hasMin() {
            return this.min != DEFAULT_MINIMUM;
        }
    }


    @SuppressWarnings("serial")
    @API(status = API.Status.STABLE, since = "1.5.0")
    public static final class ShortParseException extends NumberParseException {

        private static final long serialVersionUID = -478674263339091032L;

        private final ShortParser<?> parser;

        /**
         * Construct a new short parse exception
         *
         * @param input          String input
         * @param min            Minimum value
         * @param max            Maximum value
         * @param commandContext Command context
         * @deprecated use {@link #ShortParseException(String, ShortParser, CommandContext)} instead
         */
        @Deprecated
        @API(status = API.Status.DEPRECATED, since = "1.5.0")
        public ShortParseException(
                final @NonNull String input,
                final short min,
                final short max,
                final @NonNull CommandContext<?> commandContext
        ) {
            this(input, new ShortParser<>(min, max), commandContext);
        }

        /**
         * Create a new {@link ShortParseException}.
         *
         * @param input          input string
         * @param parser         short parser
         * @param commandContext command context
         * @since 1.5.0
         */
        @API(status = API.Status.STABLE, since = "1.5.0")
        public ShortParseException(
                final @NonNull String input,
                final @NonNull ShortParser<?> parser,
                final @NonNull CommandContext<?> commandContext
        ) {
            super(input, parser.min, parser.max, ShortParser.class, commandContext);
            this.parser = parser;
        }

        @Override
        public boolean hasMin() {
            return this.parser.hasMin();
        }

        @Override
        public boolean hasMax() {
            return this.parser.hasMax();
        }

        @Override
        public @NonNull String getNumberType() {
            return "short";
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || this.getClass() != o.getClass()) {
                return false;
            }
            final ShortParseException that = (ShortParseException) o;
            return this.parser.equals(that.parser);
        }

        @Override
        public int hashCode() {
            return Objects.hash(this.parser);
        }
    }
}
