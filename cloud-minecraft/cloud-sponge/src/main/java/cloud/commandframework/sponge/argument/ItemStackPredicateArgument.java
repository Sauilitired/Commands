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
import cloud.commandframework.sponge.data.ItemStackPredicate;
import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.ItemPredicateArgument;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.command.registrar.tree.CommandTreeNode;
import org.spongepowered.api.command.registrar.tree.CommandTreeNodeTypes;
import org.spongepowered.api.item.inventory.ItemStack;

import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * An argument for parsing {@link ItemStackPredicate ItemStackPredicates}.
 *
 * @param <C> sender type
 */
public final class ItemStackPredicateArgument<C> extends CommandArgument<C, ItemStackPredicate> {

    private ItemStackPredicateArgument(
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
                ItemStackPredicate.class,
                suggestionsProvider,
                defaultDescription
        );
    }

    /**
     * Create a new required {@link ItemStackPredicateArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link ItemStackPredicateArgument}
     */
    public static <C> @NonNull ItemStackPredicateArgument<C> of(final @NonNull String name) {
        return ItemStackPredicateArgument.<C>builder(name).build();
    }

    /**
     * Create a new optional {@link ItemStackPredicateArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link ItemStackPredicateArgument}
     */
    public static <C> @NonNull ItemStackPredicateArgument<C> optional(final @NonNull String name) {
        return ItemStackPredicateArgument.<C>builder(name).asOptional().build();
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
     * Parser for {@link ItemStackPredicate}.
     *
     * @param <C> sender type
     */
    public static final class Parser<C> implements NodeSupplyingArgumentParser<C, ItemStackPredicate> {

        private final ArgumentParser<C, ItemStackPredicate> mappedParser =
                new WrappedBrigadierParser<C, ItemPredicateArgument.Result>(
                        ItemPredicateArgument.itemPredicate()
                ).map((ctx, result) -> {
                    final CommandSourceStack commandSourceStack =
                            (CommandSourceStack) ctx.get(SpongeCommandContextKeys.COMMAND_CAUSE);
                    try {
                        final com.mojang.brigadier.context.CommandContext<CommandSourceStack> dummyContext =
                                createDummyContext(ctx, commandSourceStack);
                        return ArgumentParseResult.success(new ItemStackPredicateImpl(result.create(dummyContext)));
                    } catch (final CommandSyntaxException ex) {
                        return ArgumentParseResult.failure(ex);
                    }
                });

        @Override
        public @NonNull ArgumentParseResult<@NonNull ItemStackPredicate> parse(
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
            return CommandTreeNodeTypes.ITEM_PREDICATE.get().createNode();
        }

        private static final class ItemStackPredicateImpl implements ItemStackPredicate {

            private final Predicate<net.minecraft.world.item.ItemStack> predicate;

            ItemStackPredicateImpl(final @NonNull Predicate<net.minecraft.world.item.ItemStack> predicate) {
                this.predicate = predicate;
            }

            @SuppressWarnings("ConstantConditions")
            @Override
            public boolean test(final @NonNull ItemStack itemStack) {
                return this.predicate.test((net.minecraft.world.item.ItemStack) (Object) itemStack);
            }

        }

        private static <C> com.mojang.brigadier.context.@NonNull CommandContext<CommandSourceStack> createDummyContext(
                final @NonNull CommandContext<C> ctx,
                final @NonNull CommandSourceStack commandSourceStack
        ) {
            return new com.mojang.brigadier.context.CommandContext<>(
                    commandSourceStack,
                    ctx.getRawInputJoined(),
                    Collections.emptyMap(),
                    null,
                    null,
                    Collections.emptyList(),
                    StringRange.at(0),
                    null,
                    null,
                    false
            );
        }

    }

    /**
     * Builder for {@link ItemStackPredicateArgument}.
     *
     * @param <C> sender type
     */
    public static final class Builder<C> extends TypedBuilder<C, ItemStackPredicate, Builder<C>> {

        Builder(final @NonNull String name) {
            super(ItemStackPredicate.class, name);
        }

        @Override
        public @NonNull ItemStackPredicateArgument<C> build() {
            return new ItemStackPredicateArgument<>(
                    this.isRequired(),
                    this.getName(),
                    this.getDefaultValue(),
                    this.getSuggestionsProvider(),
                    this.getDefaultDescription()
            );
        }

    }

}
