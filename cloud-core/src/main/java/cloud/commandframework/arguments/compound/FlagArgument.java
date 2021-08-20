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
package cloud.commandframework.arguments.compound;

import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.flags.CommandFlag;
import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.captions.Caption;
import cloud.commandframework.captions.CaptionVariable;
import cloud.commandframework.captions.StandardCaptionKeys;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.ParserException;
import cloud.commandframework.keys.CloudKey;
import cloud.commandframework.keys.SimpleCloudKey;
import io.leangen.geantyref.TypeToken;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Container for flag parsing logic. This should not be be used directly.
 * Internally, a flag argument is a special case of a {@link CompoundArgument}.
 *
 * @param <C> Command sender type
 */
public final class FlagArgument<C> extends CommandArgument<C, Object> {

    private static final Pattern FLAG_ALIAS_PATTERN = Pattern.compile(" -(?<name>([A-Za-z]+))");
    private static final Pattern FLAG_PRIMARY_PATTERN = Pattern.compile(" --(?<name>([A-Za-z]+))");

    /**
     * Dummy object that indicates that flags were parsed successfully
     */
    public static final Object FLAG_PARSE_RESULT_OBJECT = new Object();
    /**
     * Meta data for the last argument that was suggested
     *
     * @deprecated Use {@link #FLAG_META_KEY} instead
     */
    @Deprecated
    public static final String FLAG_META = "__last_flag__";
    /**
     * Meta data for the last argument that was suggested
     */
    public static final CloudKey<String> FLAG_META_KEY = SimpleCloudKey.of("__last_flag__", TypeToken.get(String.class));

    private static final String FLAG_ARGUMENT_NAME = "flags";

    private final Collection<@NonNull CommandFlag<?>> flags;

    /**
     * Construct a new flag argument
     *
     * @param flags Flags
     */
    public FlagArgument(final Collection<CommandFlag<?>> flags) {
        super(
                false,
                FLAG_ARGUMENT_NAME,
                new FlagArgumentParser<>(flags.toArray(new CommandFlag<?>[0])),
                Object.class
        );
        this.flags = flags;
    }

    /**
     * Get the flags registered in the argument
     *
     * @return Unmodifiable view of flags
     */
    public @NonNull Collection<@NonNull CommandFlag<?>> getFlags() {
        return Collections.unmodifiableCollection(this.flags);
    }


    public static final class FlagArgumentParser<C> implements ArgumentParser<C, Object> {

        private final CommandFlag<?>[] flags;

        private FlagArgumentParser(final @NonNull CommandFlag<?>[] flags) {
            this.flags = flags;
        }

        @Override
        public @NonNull ArgumentParseResult<@NonNull Object> parse(
                final @NonNull CommandContext<@NonNull C> commandContext,
                final @NonNull Queue<@NonNull String> inputQueue
        ) {
            final FlagParser parser = new FlagParser();
            return parser.parse(commandContext, inputQueue);
        }

        /**
         * Parse command input to figure out what flag is currently being
         * typed at the end of the input queue. If no flag value is being
         * inputed, returns {@link Optional#empty()}.<br>
         * <br>
         * Will consume all but the last element from the input queue.
         *
         * @param commandContext Command context
         * @param inputQueue The input queue of arguments
         * @return current flag being typed, or <i>empty()</i> if none is
         */
        public @NonNull Optional<String> parseCurrentFlag(
                final @NonNull CommandContext<@NonNull C> commandContext,
                final @NonNull Queue<@NonNull String> inputQueue
        ) {
            /* If empty, nothing to do */
            if (inputQueue.isEmpty()) {
                return Optional.empty();
            }

            /* Before parsing, retrieve the last known input of the queue */
            String lastInputValue = "";
            for (String input : inputQueue) {
                lastInputValue = input;
            }

            /* Parse, but ignore the result of parsing */
            final FlagParser parser = new FlagParser();
            parser.parse(commandContext, inputQueue);

            /*
             * Remove all but the last element from the command input queue
             * If the parser parsed the entire queue, restore the last typed
             * input obtained earlier.
             */
            if (inputQueue.isEmpty()) {
                inputQueue.add(lastInputValue);
            } else {
                while (inputQueue.size() > 1) {
                    inputQueue.remove();
                }
            }

            /*
             * Map to name of the flag.
             *
             * Note: legacy API made it that FLAG_META stores not the flag name,
             * but the - or -- prefixed name or alias of the flag(s) instead.
             * This can be removed in the future.
             */
            //return parser.currentFlagBeingParsed.map(CommandFlag::getName);
            return parser.currentFlagNameBeingParsed;
        }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public @NonNull List<@NonNull String> suggestions(
                final @NonNull CommandContext<C> commandContext,
                final @NonNull String input
        ) {
            /* Check if we have a last flag stored */
            final String lastArg = commandContext.getOrDefault(FLAG_META_KEY, "");
            if (lastArg.isEmpty() || !lastArg.startsWith("-")) {
                final String rawInput = commandContext.getRawInputJoined();
                /* Collection containing all used flags */
                final List<CommandFlag<?>> usedFlags = new LinkedList<>();
                /* Find all "primary" flags, using --flag */
                final Matcher primaryMatcher = FLAG_PRIMARY_PATTERN.matcher(rawInput);
                while (primaryMatcher.find()) {
                    final String name = primaryMatcher.group("name");
                    for (final CommandFlag<?> flag : this.flags) {
                        if (flag.getName().equalsIgnoreCase(name)) {
                            usedFlags.add(flag);
                            break;
                        }
                    }
                }
                /* Find all alias flags */
                final Matcher aliasMatcher = FLAG_ALIAS_PATTERN.matcher(rawInput);
                while (aliasMatcher.find()) {
                    final String name = aliasMatcher.group("name");
                    for (final CommandFlag<?> flag : this.flags) {
                        for (final String alias : flag.getAliases()) {
                            /* Aliases are single-char strings */
                            if (name.contains(alias)) {
                                usedFlags.add(flag);
                                break;
                            }
                        }
                    }
                }
                /* Suggestions */
                final List<String> strings = new LinkedList<>();
                /* Recommend "primary" flags */
                for (final CommandFlag<?> flag : this.flags) {
                    if (usedFlags.contains(flag) || !commandContext.hasPermission(flag.getCommandPermission())) {
                        continue;
                    }
                    strings.add(
                            String.format(
                                    "--%s",
                                    flag.getName()
                            )
                    );
                }
                /* Recommend aliases */
                final boolean suggestCombined = input.length() > 1 && input.charAt(0) == '-' && input.charAt(1) != '-';
                for (final CommandFlag<?> flag : this.flags) {
                    if (usedFlags.contains(flag) || !commandContext.hasPermission(flag.getCommandPermission())) {
                        continue;
                    }
                    for (final String alias : flag.getAliases()) {
                        if (suggestCombined && flag.getCommandArgument() == null) {
                            strings.add(
                                    String.format(
                                            "%s%s",
                                            input,
                                            alias
                                    )
                            );
                        } else {
                            strings.add(
                                    String.format(
                                            "-%s",
                                            alias
                                    )
                            );
                        }
                    }
                }
                /* If we are suggesting the combined flag, then also suggest the current input */
                if (suggestCombined) {
                    strings.add(input);
                }
                return strings;
            } else {
                CommandFlag<?> currentFlag = null;
                if (lastArg.startsWith("--")) {
                    final String flagName = lastArg.substring(2);
                    for (final CommandFlag<?> flag : this.flags) {
                        if (flagName.equalsIgnoreCase(flag.getName())) {
                            currentFlag = flag;
                            break;
                        }
                    }
                } else if (lastArg.startsWith("-")) {
                    final String flagName = lastArg.substring(1);
                    for (final CommandFlag<?> flag : this.flags) {
                        for (final String alias : flag.getAliases()) {
                            if (alias.equalsIgnoreCase(flagName)) {
                                currentFlag = flag;
                                break;
                            }
                        }
                    }
                }
                if (currentFlag != null
                        && commandContext.hasPermission(currentFlag.getCommandPermission())
                        && currentFlag.getCommandArgument() != null) {
                    return (List<String>) ((BiFunction) currentFlag.getCommandArgument().getSuggestionsProvider())
                            .apply(commandContext, input);
                }
            }
            commandContext.store(FLAG_META_KEY, "");
            return this.suggestions(commandContext, input);
        }

        /**
         * Helper class to parse the command input queue into flags
         * and flag values. On failure the intermediate results
         * can be obtained, which are used for providing suggestions.
         */
        private class FlagParser {
            /** The current flag whose value is being parsed */
            @SuppressWarnings("unused")
            private Optional<CommandFlag<?>> currentFlagBeingParsed = Optional.empty();
            /**
             * The name of the current flag being parsed, can be obsoleted in the future.
             * This name includes the - or -- prefix.
             */
            private Optional<String> currentFlagNameBeingParsed = Optional.empty();

            @SuppressWarnings({"unchecked", "rawtypes"})
            public @NonNull ArgumentParseResult<@NonNull Object> parse(
                    final @NonNull CommandContext<@NonNull C> commandContext,
                    final @NonNull Queue<@NonNull String> inputQueue
            ) {
                /*
                This argument must necessarily be the last so we can just consume all remaining input. This argument type
                is similar to a greedy string in that sense. But, we need to keep all flag logic contained to the parser
                 */
                final Set<CommandFlag<?>> parsedFlags = new HashSet<>();
                CommandFlag<?> currentFlag = null;
                String currentFlagName = null;

                String string;
                while ((string = inputQueue.peek()) != null) {
                    /* No longer typing the value of the current flag */
                    this.currentFlagBeingParsed = Optional.empty();
                    this.currentFlagNameBeingParsed = Optional.empty();

                    /* Parse next flag name to set */
                    if (string.startsWith("-") && currentFlag == null) {
                        /* Remove flag argument from input queue */
                        inputQueue.poll();

                        if (string.startsWith("--")) {
                            final String flagName = string.substring(2);
                            for (final CommandFlag<?> flag : FlagArgumentParser.this.flags) {
                                if (flagName.equalsIgnoreCase(flag.getName())) {
                                    currentFlag = flag;
                                    currentFlagName = string;
                                    break;
                                }
                            }
                        } else {
                            final String flagName = string.substring(1);
                            if (flagName.length() > 1) {
                                boolean oneAdded = false;
                                /* This is a multi-alias flag, find all flags that apply */
                                for (final CommandFlag<?> flag : FlagArgumentParser.this.flags) {
                                    if (flag.getCommandArgument() != null) {
                                        continue;
                                    }
                                    for (final String alias : flag.getAliases()) {
                                        if (flagName.toLowerCase(Locale.ENGLISH).contains(alias.toLowerCase(Locale.ENGLISH))) {
                                            if (parsedFlags.contains(flag)) {
                                                return ArgumentParseResult.failure(new FlagParseException(
                                                        string,
                                                        FailureReason.DUPLICATE_FLAG,
                                                        commandContext
                                                ));
                                            } else if (!commandContext.hasPermission(flag.getCommandPermission())) {
                                                return ArgumentParseResult.failure(new FlagParseException(
                                                        string,
                                                        FailureReason.NO_PERMISSION,
                                                        commandContext
                                                ));
                                            }
                                            parsedFlags.add(flag);
                                            commandContext.flags().addPresenceFlag(flag);
                                            oneAdded = true;
                                            break;
                                        }
                                    }
                                }
                                /* We need to parse at least one flag */
                                if (!oneAdded) {
                                    return ArgumentParseResult.failure(new FlagParseException(
                                            string,
                                            FailureReason.NO_FLAG_STARTED,
                                            commandContext
                                    ));
                                }
                                continue;
                            } else {
                                for (final CommandFlag<?> flag : FlagArgumentParser.this.flags) {
                                    for (final String alias : flag.getAliases()) {
                                        if (alias.equalsIgnoreCase(flagName)) {
                                            currentFlag = flag;
                                            currentFlagName = string;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        if (currentFlag == null) {
                            return ArgumentParseResult.failure(new FlagParseException(
                                    string,
                                    FailureReason.UNKNOWN_FLAG,
                                    commandContext
                            ));
                        } else if (parsedFlags.contains(currentFlag)) {
                            return ArgumentParseResult.failure(new FlagParseException(
                                    string,
                                    FailureReason.DUPLICATE_FLAG,
                                    commandContext
                            ));
                        } else if (!commandContext.hasPermission(currentFlag.getCommandPermission())) {
                            return ArgumentParseResult.failure(new FlagParseException(
                                    string,
                                    FailureReason.NO_PERMISSION,
                                    commandContext
                            ));
                        }
                        parsedFlags.add(currentFlag);
                        if (currentFlag.getCommandArgument() == null) {
                            /* It's a presence flag */
                            commandContext.flags().addPresenceFlag(currentFlag);
                            /* We don't want to parse a value for this flag */
                            currentFlag = null;
                        }
                    } else {
                        if (currentFlag == null) {
                            return ArgumentParseResult.failure(new FlagParseException(
                                    string,
                                    FailureReason.NO_FLAG_STARTED,
                                    commandContext
                            ));
                        } else {
                            /* Mark this flag as the one currently being typed */
                            this.currentFlagBeingParsed = Optional.of(currentFlag);
                            this.currentFlagNameBeingParsed = Optional.of(currentFlagName);

                            // Don't attempt to parse empty strings
                            if (inputQueue.peek().isEmpty()) {
                                return ArgumentParseResult.failure(new FlagParseException(
                                        currentFlag.getName(),
                                        FailureReason.MISSING_ARGUMENT,
                                        commandContext
                                ));
                            }

                            final ArgumentParseResult<?> result =
                                    ((CommandArgument) currentFlag.getCommandArgument())
                                            .getParser()
                                            .parse(
                                                    commandContext,
                                                    inputQueue
                                            );
                            if (result.getFailure().isPresent()) {
                                return ArgumentParseResult.failure(result.getFailure().get());
                            } else if (result.getParsedValue().isPresent()) {
                                final CommandFlag erasedFlag = currentFlag;
                                final Object value = result.getParsedValue().get();
                                commandContext.flags().addValueFlag(erasedFlag, value);
                                currentFlag = null;
                            } else {
                                throw new IllegalStateException("Neither result or value were present. Panicking.");
                            }
                        }
                    }
                }

                /* Queue ran out while a flag argument needs to be parsed still */
                if (currentFlag != null) {
                    return ArgumentParseResult.failure(new FlagParseException(
                            currentFlag.getName(),
                            FailureReason.MISSING_ARGUMENT,
                            commandContext
                    ));
                }

                /* We've consumed everything */
                return ArgumentParseResult.success(FLAG_PARSE_RESULT_OBJECT);
            }
        }
    }

    /**
     * Flag parse exception
     */
    public static final class FlagParseException extends ParserException {

        private static final long serialVersionUID = -7725389394142868549L;
        private final String input;

        /**
         * Construct a new flag parse exception
         *
         * @param input         Input
         * @param failureReason The reason of failure
         * @param context       Command context
         */
        public FlagParseException(
                final @NonNull String input,
                final @NonNull FailureReason failureReason,
                final @NonNull CommandContext<?> context
        ) {
            super(
                    FlagArgument.FlagArgumentParser.class,
                    context,
                    failureReason.getCaption(),
                    CaptionVariable.of("input", input),
                    CaptionVariable.of("flag", input)
            );
            this.input = input;
        }

        /**
         * Get the supplied input
         *
         * @return String value
         */
        public String getInput() {
            return this.input;
        }

    }

    /**
     * Reasons for which flag parsing may fail
     */
    public enum FailureReason {

        UNKNOWN_FLAG(StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_FLAG_UNKNOWN_FLAG),
        DUPLICATE_FLAG(StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_FLAG_DUPLICATE_FLAG),
        NO_FLAG_STARTED(StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_FLAG_NO_FLAG_STARTED),
        MISSING_ARGUMENT(StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_FLAG_MISSING_ARGUMENT),
        NO_PERMISSION(StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_FLAG_NO_PERMISSION);

        private final Caption caption;

        FailureReason(final @NonNull Caption caption) {
            this.caption = caption;
        }

        /**
         * Get the caption used for this failure reason
         *
         * @return The caption
         */
        public @NonNull Caption getCaption() {
            return this.caption;
        }

    }

}
