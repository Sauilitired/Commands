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
import cloud.commandframework.sponge.NodeSupplyingArgumentParser;
import cloud.commandframework.sponge.SpongeCommandContextKeys;
import cloud.commandframework.sponge.data.GameProfileCollection;
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
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * Argument for parsing a {@link Collection} of {@link GameProfile GameProfiles} from a
 * {@link Selector}. A successfully parsed result will contain at least one element.
 *
 * @param <C> sender type
 */
public final class GameProfileCollectionArgument<C> extends CommandArgument<C, GameProfileCollection> {

    private GameProfileCollectionArgument(
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
                GameProfileCollection.class,
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
    public static <C> @NonNull GameProfileCollectionArgument<C> of(final @NonNull String name) {
        return GameProfileCollectionArgument.<C>builder(name).build();
    }

    /**
     * Create a new optional {@link GameProfileArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link GameProfileArgument}
     */
    public static <C> @NonNull GameProfileCollectionArgument<C> optional(final @NonNull String name) {
        return GameProfileCollectionArgument.<C>builder(name).asOptional().build();
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
     * Parser for a {@link Collection} of {@link GameProfile GameProfiles}. A successfully parsed result will
     * contain at least one element.
     *
     * @param <C> sender type
     */
    public static final class Parser<C> implements NodeSupplyingArgumentParser<C, GameProfileCollection> {

        private final ArgumentParser<C, GameProfileCollection> mappedParser =
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
                    final List<SpongeGameProfile> result = profiles.stream()
                            .map(SpongeGameProfile::of).collect(Collectors.toList());
                    return ArgumentParseResult.success(new GameProfileCollection() {
                        @Override
                        public @NonNull Collection<@NonNull GameProfile> gameProfiles() {
                            return Collections.unmodifiableCollection(result);
                        }
                    });
                });

        @Override
        public @NonNull ArgumentParseResult<@NonNull GameProfileCollection> parse(
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
     * Builder for {@link GameProfileCollectionArgument}.
     *
     * @param <C> sender type
     */
    public static final class Builder<C> extends TypedBuilder<C, GameProfileCollection, Builder<C>> {

        Builder(final @NonNull String name) {
            super(GameProfileCollection.class, name);
        }

        @Override
        public @NonNull GameProfileCollectionArgument<C> build() {
            return new GameProfileCollectionArgument<>(
                    this.isRequired(),
                    this.getName(),
                    this.getDefaultValue(),
                    this.getSuggestionsProvider(),
                    this.getDefaultDescription()
            );
        }

    }

}
