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

import java.time.Duration;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apiguardian.api.API;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
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

/**
 * Parser for {@link Duration}.
 *
 * <p>Matches durations in the format of: <code>2d15h7m12s</code>.</p>
 *
 * @param <C> command sender type
 */
@API(status = API.Status.STABLE)
public final class DurationParser<C> implements ArgumentParser<C, Duration>, BlockingSuggestionProvider.Strings<C> {

    /**
     * Creates a new duration parser.
     *
     * @param <C> command sender type
     * @return the created parser
     */
    @API(status = API.Status.STABLE)
    public static <C> @NonNull ParserDescriptor<C, Duration> durationParser() {
        return ParserDescriptor.of(new DurationParser<>(), Duration.class);
    }

    /**
     * Returns a {@link CommandComponent.Builder} using {@link #durationParser()} as the parser.
     *
     * @param <C> the command sender type
     * @return the component builder
     */
    @API(status = API.Status.STABLE)
    public static <C> CommandComponent.@NonNull Builder<C, Duration> durationComponent() {
        return CommandComponent.<C, Duration>builder().parser(durationParser());
    }

    @Override
    public @NonNull ArgumentParseResult<Duration> parse(
            final @NonNull CommandContext<C> commandContext,
            final @NonNull CommandInput commandInput
    ) {
        final String input = commandInput.readString();

        Duration duration = Duration.ofNanos(0);

        // substring range enclosing digits and unit (single char)
        int rangeStart = 0;
        int cursor = 0;

        while (cursor < input.length()) {
            // advance cursor until time unit or we reach end of input (in which case it's invalid anyway)
            while (cursor < input.length() && Character.isDigit(input.charAt(cursor))) {
                cursor += 1;
            }

            // reached end of input with no time unit
            if (cursor == input.length()) {
                return ArgumentParseResult.failure(new DurationParseException(input, commandContext));
            }

            final long timeValue;
            try {
                timeValue = Long.parseLong(input.substring(rangeStart, cursor));
            } catch (final NumberFormatException ex) {
                return ArgumentParseResult.failure(new DurationParseException(ex, input, commandContext));
            }

            final char timeUnit = input.charAt(cursor);
            try {
                switch (timeUnit) {
                    case 'd':
                        duration = duration.plusDays(timeValue);
                        break;
                    case 'h':
                        duration = duration.plusHours(timeValue);
                        break;
                    case 'm':
                        duration = duration.plusMinutes(timeValue);
                        break;
                    case 's':
                        duration = duration.plusSeconds(timeValue);
                        break;
                    default:
                        return ArgumentParseResult.failure(new DurationParseException(input, commandContext));
                }
            } catch (final ArithmeticException ex) {
                return ArgumentParseResult.failure(new DurationParseException(ex, input, commandContext));
            }

            // skip unit, reset rangeStart to start of next segment
            cursor += 1;
            rangeStart = cursor;
        }

        if (duration.isZero()) {
            return ArgumentParseResult.failure(new DurationParseException(input, commandContext));
        }

        return ArgumentParseResult.success(duration);
    }

    @Override
    public @NonNull Iterable<@NonNull String> stringSuggestions(
            final @NonNull CommandContext<C> commandContext,
            final @NonNull CommandInput input
    ) {
        if (input.isEmpty(true)) {
            return IntStream.range(1, 10).boxed()
                    .sorted()
                    .map(String::valueOf)
                    .collect(Collectors.toList());
        }

        // 1d_, 5d4m_, etc
        if (Character.isLetter(input.lastRemainingCharacter())) {
            return Collections.emptyList();
        }

        // 1d5_, 5d4m2_, etc
        final String string = input.readString();
        return Stream.of("d", "h", "m", "s")
                .filter(unit -> !string.contains(unit))
                .map(unit -> string + unit)
                .collect(Collectors.toList());
    }


    /**
     * Failure exception for {@link DurationParser}.
     *
     */
    @API(status = API.Status.STABLE)
    public static final class DurationParseException extends ParserException {

        private final String input;

        /**
         * Construct a new {@link DurationParseException}.
         *
         * @param input   input string
         * @param context command context
         */
        public DurationParseException(
                final @NonNull String input,
                final @NonNull CommandContext<?> context
        ) {
            super(
                    DurationParser.class,
                    context,
                    StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_DURATION,
                    CaptionVariable.of("input", input)
            );
            this.input = input;
        }

        /**
         * Construct a new {@link DurationParseException} with a causing exception.
         *
         * @param cause   cause of exception
         * @param input   input string
         * @param context command context
         */
        public DurationParseException(
                final @Nullable Throwable cause,
                final @NonNull String input,
                final @NonNull CommandContext<?> context
        ) {
            super(
                    cause,
                    DurationParser.class,
                    context,
                    StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_DURATION,
                    CaptionVariable.of("input", input)
            );
            this.input = input;
        }

        /**
         * Returns the supplied input string.
         *
         * @return input string
         */
        public @NonNull String input() {
            return this.input;
        }
    }
}
