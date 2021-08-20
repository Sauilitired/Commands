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
import cloud.commandframework.sponge.data.SinglePlayerSelector;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.command.registrar.tree.CommandTreeNode;
import org.spongepowered.api.command.registrar.tree.CommandTreeNodeTypes;
import org.spongepowered.api.command.selector.Selector;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.server.ServerPlayer;

import java.util.List;
import java.util.Queue;
import java.util.function.BiFunction;

/**
 * Argument for selecting a single {@link Player} using a {@link Selector}.
 *
 * @param <C> sender type
 */
public final class SinglePlayerSelectorArgument<C> extends CommandArgument<C, SinglePlayerSelector> {

    private SinglePlayerSelectorArgument(
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
                SinglePlayerSelector.class,
                suggestionsProvider,
                defaultDescription
        );
    }

    /**
     * Create a new required {@link SinglePlayerSelectorArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link SinglePlayerSelectorArgument}
     */
    public static <C> @NonNull SinglePlayerSelectorArgument<C> of(final @NonNull String name) {
        return SinglePlayerSelectorArgument.<C>builder(name).build();
    }

    /**
     * Create a new optional {@link SinglePlayerSelectorArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link SinglePlayerSelectorArgument}
     */
    public static <C> @NonNull SinglePlayerSelectorArgument<C> optional(final @NonNull String name) {
        return SinglePlayerSelectorArgument.<C>builder(name).asOptional().build();
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
     * Parser for {@link SinglePlayerSelector}.
     *
     * @param <C> sender type
     */
    public static final class Parser<C> implements NodeSupplyingArgumentParser<C, SinglePlayerSelector> {

        private final ArgumentParser<C, EntitySelector> nativeParser = new WrappedBrigadierParser<>(EntityArgument.player());

        @Override
        public @NonNull ArgumentParseResult<@NonNull SinglePlayerSelector> parse(
                @NonNull final CommandContext<@NonNull C> commandContext,
                @NonNull final Queue<@NonNull String> inputQueue
        ) {
            final String originalInput = String.join(" ", inputQueue);
            final ArgumentParseResult<EntitySelector> result = this.nativeParser.parse(commandContext, inputQueue);
            if (result.getFailure().isPresent()) {
                return ArgumentParseResult.failure(result.getFailure().get());
            }
            final String consumedInput = String.join(" ", inputQueue);
            final EntitySelector parsed = result.getParsedValue().get();
            final ServerPlayer player;
            try {
                // todo: a more proper fix then setting permission level 2
                player = (ServerPlayer) parsed.findSinglePlayer(
                        ((CommandSourceStack) commandContext.get(SpongeCommandContextKeys.COMMAND_CAUSE)).withPermission(2)
                );
            } catch (final CommandSyntaxException ex) {
                return ArgumentParseResult.failure(ex);
            }
            final int consumedChars = originalInput.length() - consumedInput.length();
            final String input = originalInput.substring(0, consumedChars);
            return ArgumentParseResult.success(new SinglePlayerSelectorImpl((Selector) parsed, input, player));
        }

        @Override
        public @NonNull List<@NonNull String> suggestions(
                final @NonNull CommandContext<C> commandContext,
                final @NonNull String input
        ) {
            return this.nativeParser.suggestions(commandContext, input);
        }

        @Override
        public CommandTreeNode.@NonNull Argument<? extends CommandTreeNode.Argument<?>> node() {
            return CommandTreeNodeTypes.ENTITY.get().createNode().playersOnly().single();
        }

    }

    /**
     * Builder for {@link SinglePlayerSelectorArgument}.
     *
     * @param <C> sender type
     */
    public static final class Builder<C> extends TypedBuilder<C, SinglePlayerSelector, Builder<C>> {

        Builder(final @NonNull String name) {
            super(SinglePlayerSelector.class, name);
        }

        @Override
        public @NonNull SinglePlayerSelectorArgument<C> build() {
            return new SinglePlayerSelectorArgument<>(
                    this.isRequired(),
                    this.getName(),
                    this.getDefaultValue(),
                    this.getSuggestionsProvider(),
                    this.getDefaultDescription()
            );
        }

    }

    private static final class SinglePlayerSelectorImpl implements SinglePlayerSelector {

        private final Selector selector;
        private final String inputString;
        private final ServerPlayer result;

        private SinglePlayerSelectorImpl(
                final Selector selector,
                final String inputString,
                final ServerPlayer result
        ) {
            this.selector = selector;
            this.inputString = inputString;
            this.result = result;
        }

        @Override
        public @NonNull Selector selector() {
            return this.selector;
        }

        @Override
        public @NonNull String inputString() {
            return this.inputString;
        }

        @Override
        public @NonNull ServerPlayer getSingle() {
            return this.result;
        }

    }

}
