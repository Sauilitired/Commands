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
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.exceptions.parsing.ParserException;
import cloud.commandframework.sponge.NodeSupplyingArgumentParser;
import cloud.commandframework.sponge.SpongeCaptionKeys;
import cloud.commandframework.sponge.SpongeCommandContextKeys;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.command.registrar.tree.CommandTreeNode;
import org.spongepowered.api.command.registrar.tree.CommandTreeNodeTypes;
import org.spongepowered.api.command.selector.Selector;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.common.profile.SpongeGameProfile;

import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.function.BiFunction;

/**
 * Argument for parsing a single {@link GameProfile} from a {@link Selector}.
 *
 * @param <C> sender type
 */
public final class GameProfileArgument<C> extends CommandArgument<C, GameProfile> {

    private GameProfileArgument(
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
                GameProfile.class,
                suggestionsProvider,
                defaultDescription
        );
    }

    /**
     * Create a new required {@link GameProfileArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link GameProfileArgument}
     */
    public static <C> @NonNull GameProfileArgument<C> of(final @NonNull String name) {
        return GameProfileArgument.<C>builder(name).build();
    }

    /**
     * Create a new optional {@link GameProfileArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link GameProfileArgument}
     */
    public static <C> @NonNull GameProfileArgument<C> optional(final @NonNull String name) {
        return GameProfileArgument.<C>builder(name).asOptional().build();
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
     * Create a new parser for a single {@link GameProfile}.
     *
     * @param <C> sender type
     */
    public static final class Parser<C> implements NodeSupplyingArgumentParser<C, GameProfile> {

        private final ArgumentParser<C, GameProfile> mappedParser =
                new WrappedBrigadierParser<C, net.minecraft.commands.arguments.GameProfileArgument.Result>(
                        net.minecraft.commands.arguments.GameProfileArgument.gameProfile()
                ).map((ctx, argumentResult) -> {
                    final Collection<com.mojang.authlib.GameProfile> profiles;
                    try {
                        profiles = argumentResult.getNames(
                                (CommandSourceStack) ctx.get(SpongeCommandContextKeys.COMMAND_CAUSE)
                        );
                    } catch (final CommandSyntaxException ex) {
                        return ArgumentParseResult.failure(ex);
                    }
                    if (profiles.size() > 1) {
                        return ArgumentParseResult.failure(new TooManyGameProfilesSelectedException(ctx));
                    }
                    final GameProfile profile = SpongeGameProfile.of(profiles.iterator().next());
                    return ArgumentParseResult.success(profile);
                });

        @Override
        public @NonNull ArgumentParseResult<@NonNull GameProfile> parse(
                @NonNull final CommandContext<@NonNull C> commandContext,
                @NonNull final Queue<@NonNull String> inputQueue
        ) {
            return this.mappedParser.parse(commandContext, inputQueue);
        }

        @Override
        public @NonNull List<@NonNull String> suggestions(
                final @NonNull CommandContext<C> commandContext,
                final @NonNull String input
        ) {
            return this.mappedParser.suggestions(commandContext, input);
        }

        @Override
        public CommandTreeNode.@NonNull Argument<? extends CommandTreeNode.Argument<?>> node() {
            return CommandTreeNodeTypes.GAME_PROFILE.get().createNode();
        }

    }

    /**
     * Builder for {@link GameProfileArgument}.
     *
     * @param <C> sender type
     */
    public static final class Builder<C> extends TypedBuilder<C, GameProfile, Builder<C>> {

        Builder(final @NonNull String name) {
            super(GameProfile.class, name);
        }

        @Override
        public @NonNull GameProfileArgument<C> build() {
            return new GameProfileArgument<>(
                    this.isRequired(),
                    this.getName(),
                    this.getDefaultValue(),
                    this.getSuggestionsProvider(),
                    this.getDefaultDescription()
            );
        }

    }

    /**
     * Exception thrown when too many game profiles are selected.
     */
    private static final class TooManyGameProfilesSelectedException extends ParserException {

        private static final long serialVersionUID = -2931411139985042222L;

        TooManyGameProfilesSelectedException(final @NonNull CommandContext<?> context) {
            super(
                    Parser.class,
                    context,
                    SpongeCaptionKeys.ARGUMENT_PARSE_FAILURE_GAME_PROFILE_TOO_MANY_SELECTED
            );
        }

    }

}
