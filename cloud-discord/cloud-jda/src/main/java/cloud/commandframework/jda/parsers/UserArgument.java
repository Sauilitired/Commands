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
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Command Argument for {@link User}
 *
 * @param <C> Command sender type
 * @since 1.1.0
 */
@SuppressWarnings("unused")
public final class UserArgument<C> extends CommandArgument<C, User> {

    private final Set<ParserMode> modes;
    private final Isolation isolationLevel;

    private UserArgument(
            final boolean required,
            final @NonNull String name,
            final @NonNull String defaultValue,
            final @Nullable BiFunction<@NonNull CommandContext<C>,
                    @NonNull String, @NonNull List<@NonNull String>> suggestionsProvider,
            final @NonNull ArgumentDescription defaultDescription,
            final @NonNull Set<ParserMode> modes,
            final @NonNull Isolation isolationLevel
    ) {
        super(
                required,
                name,
                new UserParser<>(modes, isolationLevel),
                defaultValue,
                User.class,
                suggestionsProvider,
                defaultDescription
        );
        this.modes = modes;
        this.isolationLevel = isolationLevel;
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
    public static <C> @NonNull CommandArgument<C, User> of(final @NonNull String name) {
        return UserArgument.<C>newBuilder(name).withParserMode(ParserMode.MENTION).asRequired().build();
    }

    /**
     * Create a new optional command component
     *
     * @param name Component name
     * @param <C>  Command sender type
     * @return Created component
     */
    public static <C> @NonNull CommandArgument<C, User> optional(final @NonNull String name) {
        return UserArgument.<C>newBuilder(name).withParserMode(ParserMode.MENTION).asOptional().build();
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
        MENTION,
        ID,
        NAME
    }

    public enum Isolation {
        GLOBAL,
        GUILD
    }


    public static final class Builder<C> extends CommandArgument.Builder<C, User> {

        private Set<ParserMode> modes = EnumSet.noneOf(ParserMode.class);
        private Isolation isolationLevel = Isolation.GLOBAL;

        private Builder(final @NonNull String name) {
            super(User.class, name);
        }

        /**
         * Builder a new example component
         *
         * @return Constructed component
         */
        @Override
        public @NonNull UserArgument<C> build() {
            return new UserArgument<>(
                    this.isRequired(),
                    this.getName(),
                    this.getDefaultValue(),
                    this.getSuggestionsProvider(),
                    this.getDefaultDescription(),
                    this.modes,
                    this.isolationLevel
            );
        }

        /**
         * Set the isolation level of the parser
         *
         * @param isolation Isolation level
         * @return Builder instance
         */
        public @NonNull Builder<C> withIsolationLevel(final @NonNull Isolation isolation) {
            this.isolationLevel = isolation;
            return this;
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


    public static final class UserParser<C> implements ArgumentParser<C, User> {

        private final Set<ParserMode> modes;
        private final Isolation isolationLevel;

        /**
         * Construct a new argument parser for {@link User}
         *
         * @param modes List of parsing modes to use when parsing
         * @throws java.lang.IllegalArgumentException If no parsing modes were provided
         * @deprecated Use {@link #UserParser(Set, Isolation)} instead.
         */
        @Deprecated
        public UserParser(final @NonNull Set<ParserMode> modes) {
            this(modes, Isolation.GLOBAL);
        }

        /**
         * Construct a new argument parser for {@link User}
         *
         * @param modes          List of parsing modes to use when parsing
         * @param isolationLevel Level of isolation to maintain when parsing
         * @throws java.lang.IllegalArgumentException If no parsing modes were provided
         */
        public UserParser(final @NonNull Set<ParserMode> modes, final @NonNull Isolation isolationLevel) {
            if (modes.isEmpty()) {
                throw new IllegalArgumentException("At least one parsing mode is required");
            }

            this.modes = modes;
            this.isolationLevel = isolationLevel;
        }

        @Override
        public @NonNull ArgumentParseResult<User> parse(
                final @NonNull CommandContext<C> commandContext,
                final @NonNull Queue<@NonNull String> inputQueue
        ) {
            final String input = inputQueue.peek();
            if (input == null) {
                return ArgumentParseResult.failure(new NoInputProvidedException(
                        UserParser.class,
                        commandContext
                ));
            }

            if (!commandContext.contains("MessageReceivedEvent")) {
                return ArgumentParseResult.failure(new IllegalStateException(
                        "MessageReceivedEvent was not in the command context."
                ));
            }

            final MessageReceivedEvent event = commandContext.get("MessageReceivedEvent");
            Exception exception = null;

            if (this.modes.contains(ParserMode.MENTION)) {
                if (input.startsWith("<@") && input.endsWith(">")) {
                    final String id;
                    if (input.startsWith("<@!")) {
                        id = input.substring(3, input.length() - 1);
                    } else {
                        id = input.substring(2, input.length() - 1);
                    }

                    try {
                        final ArgumentParseResult<User> result = this.userFromId(event, input, Long.parseLong(id));
                        inputQueue.remove();
                        return result;
                    } catch (final UserNotFoundParseException | NumberFormatException e) {
                        exception = e;
                    }
                } else {
                    exception = new IllegalArgumentException(
                            String.format("Input '%s' is not a user mention.", input)
                    );
                }
            }

            if (this.modes.contains(ParserMode.ID)) {
                try {
                    final ArgumentParseResult<User> result = this.userFromId(event, input, Long.parseLong(input));
                    inputQueue.remove();
                    return result;
                } catch (final UserNotFoundParseException | NumberFormatException e) {
                    exception = e;
                }
            }

            if (this.modes.contains(ParserMode.NAME)) {
                final List<User> users;

                if (this.isolationLevel == Isolation.GLOBAL) {
                    users = event.getJDA().getUsersByName(input, true);
                } else if (event.isFromGuild()) {
                    users = event.getGuild().getMembers()
                            .stream()
                            .filter(member -> member.getEffectiveName().toLowerCase().startsWith(input))
                            .map(Member::getUser)
                            .collect(Collectors.toList());
                } else if (event.getAuthor().getName().equalsIgnoreCase(input)) {
                    users = Collections.singletonList(event.getAuthor());
                } else {
                    users = Collections.emptyList();
                }

                if (users.isEmpty()) {
                    exception = new UserNotFoundParseException(input);
                } else if (users.size() > 1) {
                    exception = new TooManyUsersFoundParseException(input);
                } else {
                    inputQueue.remove();
                    return ArgumentParseResult.success(users.get(0));
                }
            }

            assert exception != null;
            return ArgumentParseResult.failure(exception);
        }

        private @NonNull ArgumentParseResult<User> userFromId(
                final @NonNull MessageReceivedEvent event,
                final @NonNull String input,
                final @NonNull Long id
        ) throws UserNotFoundParseException, NumberFormatException {
            final User user;
            if (this.isolationLevel == Isolation.GLOBAL) {
                User globalUser = event.getJDA().getUserById(id);

                if (globalUser == null) { // fallback if user is not cached
                    globalUser = event.getJDA().retrieveUserById(id).complete();
                }

                user = globalUser;
            } else if (event.isFromGuild()) {
                try {
                    final Guild guild = event.getGuild();
                    Member member = guild.getMemberById(id);

                    if (member == null) { // fallback if user is not cached
                        member = guild.retrieveMemberById(id).complete();
                    }

                    user = member.getUser();
                } catch (final CompletionException e) {
                    if (e.getCause().getClass().equals(ErrorResponseException.class)
                            && ((ErrorResponseException) e.getCause()).getErrorResponse() == ErrorResponse.UNKNOWN_USER) {
                        //noinspection ThrowInsideCatchBlockWhichIgnoresCaughtException
                        throw new UserNotFoundParseException(input);
                    }
                    throw e;
                }
            } else if (event.getAuthor().getIdLong() == id) {
                user = event.getAuthor();
            } else {
                user = null;
            }

            if (user == null) {
                throw new UserNotFoundParseException(input);
            } else {
                return ArgumentParseResult.success(user);
            }
        }

        @Override
        public boolean isContextFree() {
            return true;
        }

    }


    public static class UserParseException extends IllegalArgumentException {

        private static final long serialVersionUID = -6728909884195850077L;
        private final String input;

        /**
         * Construct a new user parse exception
         *
         * @param input String input
         */
        public UserParseException(final @NonNull String input) {
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


    public static final class TooManyUsersFoundParseException extends UserParseException {

        private static final long serialVersionUID = 7222089412615886672L;

        /**
         * Construct a new user parse exception
         *
         * @param input String input
         */
        public TooManyUsersFoundParseException(final @NonNull String input) {
            super(input);
        }

        @Override
        public @NonNull String getMessage() {
            return String.format("Too many users found for '%s'.", getInput());
        }

    }


    public static final class UserNotFoundParseException extends UserParseException {

        private static final long serialVersionUID = 3689949065073643826L;

        /**
         * Construct a new user parse exception
         *
         * @param input String input
         */
        public UserNotFoundParseException(final @NonNull String input) {
            super(input);
        }

        @Override
        public @NonNull String getMessage() {
            return String.format(
                    "User not found for '%s'. Note: if a user is not cached, they cannot be loaded by name.",
                    getInput()
            );
        }

    }

}
