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
package cloud.commandframework.sponge.argument;

import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.brigadier.argument.WrappedBrigadierParser;
import cloud.commandframework.captions.Caption;
import cloud.commandframework.captions.CaptionVariable;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.ParserException;
import cloud.commandframework.sponge.NodeSupplyingArgumentParser;
import cloud.commandframework.sponge.SpongeCaptionKeys;
import cloud.commandframework.sponge.SpongeCommandContextKeys;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.registrar.tree.CommandTreeNode;
import org.spongepowered.api.command.registrar.tree.CommandTreeNodeTypes;
import org.spongepowered.api.command.selector.Selector;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.user.UserManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Argument for parsing {@link User Users} in the {@link UserManager} from
 * a {@link Selector}, last known username, or {@link UUID}.
 *
 * TODO: Update javadocs
 *
 * @param <C> sender type
 */
public final class UserArgument<C> extends CommandArgument<C, UUID> {

    private UserArgument(
            final boolean required,
            final @NonNull String name,
            final @NonNull String defaultValue,
            final @Nullable BiFunction<CommandContext<C>, String, List<String>> suggestionsProvider,
            final @NonNull ArgumentDescription defaultDescription
    ) {
        super(
                required,
                name,
                new Parser<>(),
                defaultValue,
                UUID.class,
                suggestionsProvider,
                defaultDescription
        );
    }

    /**
     * Create a new required {@link UserArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link UserArgument}
     */
    public static <C> @NonNull UserArgument<C> of(final @NonNull String name) {
        return UserArgument.<C>builder(name).build();
    }

    /**
     * Create a new optional {@link UserArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link UserArgument}
     */
    public static <C> @NonNull UserArgument<C> optional(final @NonNull String name) {
        return UserArgument.<C>builder(name).asOptional().build();
    }

    /**
     * Create a new {@link Builder}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link Builder}
     */
    public static <C> @NonNull Builder<C> builder(final @NonNull String name) {
        return new Builder<>(name);
    }

    /**
     * Parser for {@link User Users} in the {@link UserManager} by
     * {@link Selector}, last known username, or {@link UUID}.
     *
     * @param <C> sender type
     */
    public static final class Parser<C> implements NodeSupplyingArgumentParser<C, UUID> {

        private final ArgumentParser<C, EntitySelector> singlePlayerSelectorParser =
                new WrappedBrigadierParser<>(EntityArgument.player());

        @Override
        public @NonNull ArgumentParseResult<@NonNull UUID> parse(
                @NonNull final CommandContext<@NonNull C> commandContext,
                @NonNull final Queue<@NonNull String> inputQueue
        ) {
            final String peek = inputQueue.peek();
            if (peek.startsWith("@")) {
                return this.handleSelector(commandContext, inputQueue);
            }

            try {
                final Optional<GameProfile> optionalUser = Sponge.server().gameProfileManager().cache().findByName(peek);
                // valid username
                if (optionalUser.isPresent()) {
                    inputQueue.remove();
                    return ArgumentParseResult.success(optionalUser.get().uniqueId());
                }
                return ArgumentParseResult.failure(new UserNotFoundException(
                        commandContext, UserNotFoundException.Type.NAME, peek
                ));
            } catch (final IllegalArgumentException ex) {
                // not a valid username
            }

            try {
                final UUID uuid = UUID.fromString(peek);
                // valid uuid
                if (Sponge.server().userManager().exists(uuid)) {
                    return ArgumentParseResult.success(uuid);
                }

                return ArgumentParseResult.failure(new UserNotFoundException(
                        commandContext, UserNotFoundException.Type.UUID, peek
                ));
            } catch (final IllegalArgumentException ex) {
                // not a valid uuid
            }

            return ArgumentParseResult.failure(new UserNotFoundException(
                    commandContext, UserNotFoundException.Type.INVALID_INPUT, peek
            ));
        }

        private @NonNull ArgumentParseResult<@NonNull UUID> handleSelector(
                final @NonNull CommandContext<@NonNull C> commandContext,
                final @NonNull Queue<@NonNull String> inputQueue
        ) {
            final ArgumentParseResult<EntitySelector> result = this.singlePlayerSelectorParser.parse(commandContext, inputQueue);
            if (result.getFailure().isPresent()) {
                return ArgumentParseResult.failure(result.getFailure().get());
            }
            final EntitySelector parsed = result.getParsedValue().get();
            final ServerPlayer player;
            try {
                player = (ServerPlayer) parsed.findSinglePlayer(
                        (CommandSourceStack) commandContext.get(SpongeCommandContextKeys.COMMAND_CAUSE)
                );
            } catch (final CommandSyntaxException ex) {
                return ArgumentParseResult.failure(ex);
            }
            return ArgumentParseResult.success(player.uniqueId());
        }

        @Override
        public @NonNull List<@NonNull String> suggestions(
                final @NonNull CommandContext<C> commandContext,
                final @NonNull String input
        ) {
            final List<String> suggestions = new ArrayList<>(this.singlePlayerSelectorParser.suggestions(commandContext, input));
            if (!input.startsWith("@")) {
                suggestions.addAll(Sponge.server().userManager().streamOfMatches(input)
                        .filter(GameProfile::hasName)
                        .map(profile -> profile.name().orElse(null))
                        .filter(Objects::nonNull)
                        .filter(name -> !suggestions.contains(name))
                        .collect(Collectors.toList()));
            }
            return suggestions;
        }

        @Override
        public CommandTreeNode.@NonNull Argument<? extends CommandTreeNode.Argument<?>> node() {
            return CommandTreeNodeTypes.GAME_PROFILE.get().createNode().customCompletions();
        }

    }

    /**
     * Builder for {@link UserArgument}.
     *
     * @param <C> sender type
     */
    public static final class Builder<C> extends TypedBuilder<C, UUID, Builder<C>> {

        Builder(final @NonNull String name) {
            super(UUID.class, name);
        }

        @Override
        public @NonNull UserArgument<C> build() {
            return new UserArgument<>(
                    this.isRequired(),
                    this.getName(),
                    this.getDefaultValue(),
                    this.getSuggestionsProvider(),
                    this.getDefaultDescription()
            );
        }

    }

    /**
     * An exception thrown when a {@link User} cannot be found for the provided input.
     */
    private static final class UserNotFoundException extends ParserException {

        private static final long serialVersionUID = -24501459406523175L;

        UserNotFoundException(
                final CommandContext<?> context,
                final @NonNull Type type,
                final @NonNull String input
        ) {
            super(
                    Parser.class,
                    context,
                    type.caption,
                    type.variable(input)
            );
        }

        private enum Type {
            UUID("uuid", SpongeCaptionKeys.ARGUMENT_PARSE_FAILURE_USER_CANNOT_FIND_USER_WITH_UUID),
            NAME("name", SpongeCaptionKeys.ARGUMENT_PARSE_FAILURE_USER_CANNOT_FIND_USER_WITH_NAME),
            INVALID_INPUT("input", SpongeCaptionKeys.ARGUMENT_PARSE_FAILURE_USER_INVALID_INPUT);

            private final String key;
            private final Caption caption;

            Type(final @NonNull String key, final @NonNull Caption caption) {
                this.key = key;
                this.caption = caption;
            }

            CaptionVariable variable(final @NonNull String input) {
                return CaptionVariable.of(this.key, input);
            }
        }

    }

}
