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
package cloud.commandframework.jda.parsers;

import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.NoInputProvidedException;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Command Argument for {@link Emote}
 *
 * @param <C> Command sender type
 * @since 1.1.0
 */
@SuppressWarnings("unused")
public final class EmoteArgument<C> extends CommandArgument<C, Emote> {

    private final Set<ParserMode> modes;

    private EmoteArgument(
            final boolean required,
            final @NonNull String name,
            final @NonNull String defaultValue,
            final @Nullable BiFunction<@NonNull CommandContext<C>,
                    @NonNull String, @NonNull List<@NonNull String>> suggestionsProvider,
            final @NonNull ArgumentDescription defaultDescription,
            final @NonNull Set<ParserMode> modes
    ) {
        super(
                required,
                name,
                new EmoteParser<>(modes),
                defaultValue,
                Emote.class,
                suggestionsProvider,
                defaultDescription
        );
        this.modes = modes;
    }

    /**
     * Create a new builder
     *
     * @param name Name of the component
     * @param <C>  Command sender type
     * @return Created builder
     */
    public static <C> @NonNull Builder<C> newBuilder(final @NonNull String name) {
        return new Builder<>(name);
    }

    /**
     * Create a new required command component
     *
     * @param name Component name
     * @param <C>  Command sender type
     * @return Created component
     */
    public static <C> @NonNull CommandArgument<C, Emote> of(final @NonNull String name) {
        return EmoteArgument.<C>newBuilder(name).withParserMode(ParserMode.REFERENCE).asRequired().build();
    }

    /**
     * Create a new optional command component
     *
     * @param name Component name
     * @param <C>  Command sender type
     * @return Created component
     */
    public static <C> @NonNull CommandArgument<C, Emote> optional(final @NonNull String name) {
        return EmoteArgument.<C>newBuilder(name).withParserMode(ParserMode.REFERENCE).asOptional().build();
    }

    /**
     * Get the modes enabled on the parser
     *
     * @return List of Modes
     */
    public @NotNull Set<ParserMode> getModes() {
        return this.modes;
    }


    public enum ParserMode {
        REFERENCE,
        ID,
        NAME
    }


    public static final class Builder<C> extends CommandArgument.Builder<C, Emote> {

        private Set<ParserMode> modes = EnumSet.noneOf(ParserMode.class);

        private Builder(final @NonNull String name) {
            super(Emote.class, name);
        }

        /**
         * Builder a new example component
         *
         * @return Constructed component
         */
        @Override
        public @NonNull EmoteArgument<C> build() {
            return new EmoteArgument<>(
                    this.isRequired(),
                    this.getName(),
                    this.getDefaultValue(),
                    this.getSuggestionsProvider(),
                    this.getDefaultDescription(),
                    this.modes
            );
        }

        /**
         * Add a parser mode to use
         *
         * @param mode Parser mode to add
         * @return Builder instance
         */
        public @NonNull Builder<C> withParserMode(final @NonNull ParserMode mode) {
            this.modes.add(mode);
            return this;
        }

        /**
         * Set the modes for the parsers to use
         *
         * @param modes List of Modes
         * @return Builder instance
         */
        public @NonNull Builder<C> withParsers(final @NonNull Set<ParserMode> modes) {
            this.modes = modes;
            return this;
        }

    }


    public static final class EmoteParser<C> implements ArgumentParser<C, Emote> {

        private final Set<ParserMode> modes;

        /**
         * Construct a new argument parser for {@link Emote}
         *
         * @param modes List of parsing modes to use when parsing
         * @throws java.lang.IllegalArgumentException If no parsing modes were provided
         */
        public EmoteParser(final @NonNull Set<ParserMode> modes) {
            if (modes.isEmpty()) {
                throw new IllegalArgumentException("At least one parsing mode is required");
            }
            this.modes = modes;
        }

        @Override
        public @NonNull ArgumentParseResult<Emote> parse(
                final @NonNull CommandContext<C> commandContext,
                final @NonNull Queue<@NonNull String> inputQueue
        ) {
            final String input = inputQueue.peek();
            if (input == null) {
                return ArgumentParseResult.failure(new NoInputProvidedException(
                        EmoteParser.class,
                        commandContext
                ));
            }

            if (!commandContext.contains("MessageReceivedEvent")) {
                return ArgumentParseResult.failure(new IllegalStateException(
                        "MessageReceivedEvent was not in the command context."
                ));
            }

            final MessageReceivedEvent event = commandContext.get("MessageReceivedEvent");

            if (!event.isFromGuild()) {
                return ArgumentParseResult.failure(new CommandNotFromGuildException());
            }

            Exception exception = null;

            if (this.modes.contains(ParserMode.REFERENCE)) {
                if (input.startsWith("<:") && input.endsWith(">")) {
                    final String id;
                    id = input.substring(input.indexOf(':', 3), input.length() - 1);

                    try {
                        final ArgumentParseResult<Emote> result = this.memberFromId(event, input, Long.parseLong(id));
                        inputQueue.remove();
                        return result;
                    } catch (final EmoteNotFoundParseException | NumberFormatException e) {
                        exception = e;
                    } catch (final CompletionException e) {
                        if (e.getCause().getClass().equals(ErrorResponseException.class)
                                && ((ErrorResponseException) e.getCause()).getErrorResponse() == ErrorResponse.UNKNOWN_EMOJI) {
                            exception = new EmoteNotFoundParseException(input);
                        } else {
                            exception = e;
                        }
                    }
                } else {
                    exception = new IllegalArgumentException(
                            String.format("Input '%s' is not a member mention.", input)
                    );
                }
            }

            if (this.modes.contains(ParserMode.ID)) {
                try {
                    final ArgumentParseResult<Emote> result = this.memberFromId(event, input, Long.parseLong(input));
                    inputQueue.remove();
                    return result;
                } catch (final EmoteNotFoundParseException | NumberFormatException e) {
                    exception = e;
                }
            }

            if (this.modes.contains(ParserMode.NAME)) {
                final List<Emote> members;

                members = event.getGuild().getEmotes()
                        .stream()
                        .filter(emote -> emote.getName().toLowerCase().startsWith(input))
                        .collect(Collectors.toList());

                if (members.isEmpty()) {
                    exception = new EmoteNotFoundParseException(input);
                } else if (members.size() > 1) {
                    exception = new TooManyEmotesFoundParseException(input);
                } else {
                    inputQueue.remove();
                    return ArgumentParseResult.success(members.get(0));
                }
            }

            assert exception != null;
            return ArgumentParseResult.failure(exception);
        }

        private @NonNull ArgumentParseResult<Emote> memberFromId(
                final @NonNull MessageReceivedEvent event,
                final @NonNull String input,
                final @NonNull Long id
        ) throws EmoteNotFoundParseException, NumberFormatException {

            try {
                final Guild guild = event.getGuild();

                final Emote cachedEmote = guild.getEmoteById(id);
                final Emote emote = cachedEmote != null ? cachedEmote : guild.retrieveEmoteById(id).submit().join();

                if (emote == null) {
                    throw new EmoteNotFoundParseException(input);
                } else {
                    return ArgumentParseResult.success(emote);
                }
            } catch (final CompletionException e) {
                if (e.getCause().getClass().equals(ErrorResponseException.class)
                        && ((ErrorResponseException) e.getCause()).getErrorResponse() == ErrorResponse.UNKNOWN_EMOJI) {
                    //noinspection ThrowInsideCatchBlockWhichIgnoresCaughtException
                    throw new EmoteNotFoundParseException(input);
                }
                throw e;
            }
        }

        @Override
        public boolean isContextFree() {
            return true;
        }

    }


    public static class EmoteParseException extends IllegalArgumentException {

        private static final long serialVersionUID = -6728909884195850077L;
        private final String input;

        /**
         * Construct a new user parse exception
         *
         * @param input String input
         */
        public EmoteParseException(final @NonNull String input) {
            this.input = input;
        }

        /**
         * Get the users input
         *
         * @return Users input
         */
        public final @NonNull String getInput() {
            return this.input;
        }

    }


    public static final class TooManyEmotesFoundParseException extends EmoteParseException {

        private static final long serialVersionUID = 7222089412615886672L;

        /**
         * Construct a new user parse exception
         *
         * @param input String input
         */
        public TooManyEmotesFoundParseException(final @NonNull String input) {
            super(input);
        }

        @Override
        public @NonNull String getMessage() {
            return String.format("Too many users found for '%s'.", getInput());
        }

    }


    public static final class EmoteNotFoundParseException extends EmoteParseException {

        private static final long serialVersionUID = 3689949065073643826L;

        /**
         * Construct a new user parse exception
         *
         * @param input String input
         */
        public EmoteNotFoundParseException(final @NonNull String input) {
            super(input);
        }

        @Override
        public @NonNull String getMessage() {
            return String.format("User not found for '%s'.", getInput());
        }

    }

    public static final class CommandNotFromGuildException extends IllegalArgumentException {

        private static final long serialVersionUID = -6023489381287831501L;

        /**
         * Constructs a new command not from guild exception.
         */
        public CommandNotFromGuildException() {
            super("Command must be executed in a guild.");
        }

    }

}
