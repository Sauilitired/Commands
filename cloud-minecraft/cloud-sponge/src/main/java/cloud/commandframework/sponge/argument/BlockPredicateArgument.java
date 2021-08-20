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
import cloud.commandframework.sponge.data.BlockPredicate;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.command.registrar.tree.CommandTreeNode;
import org.spongepowered.api.command.registrar.tree.CommandTreeNodeTypes;
import org.spongepowered.api.world.server.ServerLocation;
import org.spongepowered.common.util.VecHelper;

import java.util.List;
import java.util.Queue;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * An argument for parsing {@link BlockPredicate BlockPredicates}.
 *
 * @param <C> sender type
 */
public final class BlockPredicateArgument<C> extends CommandArgument<C, BlockPredicate> {

    private BlockPredicateArgument(
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
                BlockPredicate.class,
                suggestionsProvider,
                defaultDescription
        );
    }

    /**
     * Create a new required {@link BlockPredicateArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link BlockPredicateArgument}
     */
    public static <C> @NonNull BlockPredicateArgument<C> of(final @NonNull String name) {
        return BlockPredicateArgument.<C>builder(name).build();
    }

    /**
     * Create a new optional {@link BlockPredicateArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link BlockPredicateArgument}
     */
    public static <C> @NonNull BlockPredicateArgument<C> optional(final @NonNull String name) {
        return BlockPredicateArgument.<C>builder(name).asOptional().build();
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
     * Parser for {@link BlockPredicate}.
     *
     * @param <C> sender type
     */
    public static final class Parser<C> implements NodeSupplyingArgumentParser<C, BlockPredicate> {

        private final ArgumentParser<C, BlockPredicate> mappedParser =
                new WrappedBrigadierParser<C, net.minecraft.commands.arguments.blocks.BlockPredicateArgument.Result>(
                        net.minecraft.commands.arguments.blocks.BlockPredicateArgument.blockPredicate()
                ).map((ctx, result) -> {
                    final CommandSourceStack commandSourceStack =
                            (CommandSourceStack) ctx.get(SpongeCommandContextKeys.COMMAND_CAUSE);
                    try {
                        return ArgumentParseResult.success(
                                new BlockPredicateImpl(result.create(commandSourceStack.getLevel().getTagManager()))
                        );
                    } catch (final CommandSyntaxException ex) {
                        return ArgumentParseResult.failure(ex);
                    }
                });

        @Override
        public @NonNull ArgumentParseResult<@NonNull BlockPredicate> parse(
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
            return CommandTreeNodeTypes.BLOCK_PREDICATE.get().createNode();
        }

        private static final class BlockPredicateImpl implements BlockPredicate {

            private final Predicate<BlockInWorld> predicate;

            BlockPredicateImpl(final @NonNull Predicate<BlockInWorld> predicate) {
                this.predicate = predicate;
            }

            private boolean testImpl(final @NonNull ServerLocation location, final boolean loadChunks) {
                return this.predicate.test(new BlockInWorld(
                        (ServerLevel) location.world(),
                        VecHelper.toBlockPos(location.position()),
                        loadChunks
                ));
            }

            @Override
            public boolean test(final @NonNull ServerLocation location) {
                return this.testImpl(location, false);
            }

            @Override
            public @NonNull BlockPredicate loadChunks() {
                return new BlockPredicate() {
                    @Override
                    public @NonNull BlockPredicate loadChunks() {
                        return this;
                    }

                    @Override
                    public boolean test(final @NonNull ServerLocation location) {
                        return BlockPredicateImpl.this.testImpl(location, true);
                    }
                };
            }

        }

    }

    /**
     * Builder for {@link BlockPredicateArgument}.
     *
     * @param <C> sender type
     */
    public static final class Builder<C> extends TypedBuilder<C, BlockPredicate, Builder<C>> {

        Builder(final @NonNull String name) {
            super(BlockPredicate.class, name);
        }

        @Override
        public @NonNull BlockPredicateArgument<C> build() {
            return new BlockPredicateArgument<>(
                    this.isRequired(),
                    this.getName(),
                    this.getDefaultValue(),
                    this.getSuggestionsProvider(),
                    this.getDefaultDescription()
            );
        }

    }

}
