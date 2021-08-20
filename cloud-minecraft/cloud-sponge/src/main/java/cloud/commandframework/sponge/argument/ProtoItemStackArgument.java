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
import cloud.commandframework.sponge.data.ProtoItemStack;
import cloud.commandframework.sponge.exception.ComponentMessageRuntimeException;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.kyori.adventure.util.ComponentMessageThrowable;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.nbt.CompoundTag;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.command.registrar.tree.CommandTreeNode;
import org.spongepowered.api.command.registrar.tree.CommandTreeNodeTypes;
import org.spongepowered.api.data.persistence.DataContainer;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.registry.DefaultedRegistryReference;
import org.spongepowered.common.data.persistence.NBTTranslator;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Queue;
import java.util.function.BiFunction;

/**
 * An argument for parsing {@link ProtoItemStack ProtoItemStacks} from an {@link ItemType} identifier
 * and optional NBT data.
 *
 * <p>Example input strings:</p>
 * <ul>
 *     <li>{@code apple}</li>
 *     <li>{@code minecraft:apple}</li>
 *     <li>{@code diamond_sword{Enchantments:[{id:sharpness,lvl:5}]}}</li>
 * </ul>
 *
 * @param <C> sender type
 */
public final class ProtoItemStackArgument<C> extends CommandArgument<C, ProtoItemStack> {

    private ProtoItemStackArgument(
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
                ProtoItemStack.class,
                suggestionsProvider,
                defaultDescription
        );
    }

    /**
     * Create a new required {@link ProtoItemStackArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link ProtoItemStackArgument}
     */
    public static <C> @NonNull ProtoItemStackArgument<C> of(final @NonNull String name) {
        return ProtoItemStackArgument.<C>builder(name).build();
    }

    /**
     * Create a new optional {@link ProtoItemStackArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link ProtoItemStackArgument}
     */
    public static <C> @NonNull ProtoItemStackArgument<C> optional(final @NonNull String name) {
        return ProtoItemStackArgument.<C>builder(name).asOptional().build();
    }

    /**
     * Create a new optional {@link ProtoItemStackArgument} with the specified default value.
     *
     * @param name         argument name
     * @param defaultValue default value
     * @param <C>          sender type
     * @return a new {@link ProtoItemStackArgument}
     */
    public static <C> @NonNull ProtoItemStackArgument<C> optional(
            final @NonNull String name,
            final @NonNull ItemStackSnapshot defaultValue
    ) {
        return ProtoItemStackArgument.<C>builder(name).asOptionalWithDefault(defaultValue).build();
    }

    /**
     * Create a new optional {@link ProtoItemStackArgument} with the specified default value.
     *
     * @param name         argument name
     * @param defaultValue default value
     * @param <C>          sender type
     * @return a new {@link ProtoItemStackArgument}
     */
    public static <C> @NonNull ProtoItemStackArgument<C> optional(
            final @NonNull String name,
            final @NonNull ItemStack defaultValue
    ) {
        return ProtoItemStackArgument.<C>builder(name).asOptionalWithDefault(defaultValue).build();
    }

    /**
     * Create a new optional {@link ProtoItemStackArgument} with the specified default value.
     *
     * @param name         argument name
     * @param defaultValue default value
     * @param <C>          sender type
     * @return a new {@link ProtoItemStackArgument}
     */
    public static <C> @NonNull ProtoItemStackArgument<C> optional(
            final @NonNull String name,
            final @NonNull ItemType defaultValue
    ) {
        return ProtoItemStackArgument.<C>builder(name).asOptionalWithDefault(defaultValue).build();
    }

    /**
     * Create a new optional {@link ProtoItemStackArgument} with the specified default value.
     *
     * @param name         argument name
     * @param defaultValue default value
     * @param <C>          sender type
     * @return a new {@link ProtoItemStackArgument}
     */
    public static <C> @NonNull ProtoItemStackArgument<C> optional(
            final @NonNull String name,
            final @NonNull DefaultedRegistryReference<ItemType> defaultValue
    ) {
        return ProtoItemStackArgument.<C>builder(name).asOptionalWithDefault(defaultValue).build();
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
     * Parser for {@link ItemStackSnapshot ItemStackSnapshots} from an {@link ItemType} identifier and
     * optional NBT data. The stack size of the resulting snapshot will always be {@code 1}.
     *
     * @param <C> sender type
     */
    public static final class Parser<C> implements NodeSupplyingArgumentParser<C, ProtoItemStack> {

        private final ArgumentParser<C, ProtoItemStack> mappedParser =
                new WrappedBrigadierParser<C, ItemInput>(ItemArgument.item())
                        .map((ctx, itemInput) -> ArgumentParseResult.success(new ProtoItemStackImpl(itemInput)));

        @Override
        public @NonNull ArgumentParseResult<@NonNull ProtoItemStack> parse(
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
            return CommandTreeNodeTypes.ITEM_STACK.get().createNode();
        }

        private static final class ProtoItemStackImpl implements ProtoItemStack {

            private static final Field COMPOUND_TAG_FIELD;

            static {
                try {
                    COMPOUND_TAG_FIELD = ItemInput.class.getDeclaredField("tag");
                    COMPOUND_TAG_FIELD.setAccessible(true);
                } catch (final ReflectiveOperationException ex) {
                    throw new RuntimeException(ex);
                }
            }

            private final ItemInput itemInput;
            private final @Nullable DataContainer extraData;

            ProtoItemStackImpl(final @NonNull ItemInput itemInput) {
                this.itemInput = itemInput;
                try {
                    final CompoundTag tag = (CompoundTag) COMPOUND_TAG_FIELD.get(itemInput);
                    this.extraData = tag == null ? null : NBTTranslator.INSTANCE.translate(tag);
                } catch (final IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            }

            @Override
            public @NonNull ItemType itemType() {
                return (ItemType) this.itemInput.getItem();
            }

            @Override
            public @Nullable DataContainer extraData() {
                return this.extraData;
            }

            @SuppressWarnings("ConstantConditions")
            @Override
            public @NonNull ItemStack createItemStack(
                    final int stackSize,
                    final boolean respectMaximumStackSize
            ) throws ComponentMessageRuntimeException {
                try {
                    return (ItemStack) (Object) this.itemInput.createItemStack(stackSize, respectMaximumStackSize);
                } catch (final CommandSyntaxException ex) {
                    throw new ComponentMessageRuntimeException(ComponentMessageThrowable.getMessage(ex), ex);
                }
            }

            @Override
            public @NonNull ItemStackSnapshot createItemStackSnapshot(
                    final int stackSize,
                    final boolean respectMaximumStackSize
            ) throws ComponentMessageRuntimeException {
                return this.createItemStack(stackSize, respectMaximumStackSize).createSnapshot();
            }

        }

    }

    /**
     * Builder for {@link ProtoItemStackArgument}.
     *
     * @param <C> sender type
     */
    public static final class Builder<C> extends TypedBuilder<C, ProtoItemStack, Builder<C>> {

        Builder(final @NonNull String name) {
            super(ProtoItemStack.class, name);
        }

        @Override
        public @NonNull ProtoItemStackArgument<C> build() {
            return new ProtoItemStackArgument<>(
                    this.isRequired(),
                    this.getName(),
                    this.getDefaultValue(),
                    this.getSuggestionsProvider(),
                    this.getDefaultDescription()
            );
        }

        /**
         * Sets the command argument to be optional, with the specified default value.
         *
         * @param defaultValue default value
         * @return this builder
         * @see CommandArgument.Builder#asOptionalWithDefault(String)
         */
        public @NonNull Builder<C> asOptionalWithDefault(final @NonNull ItemType defaultValue) {
            return this.asOptionalWithDefault(ItemStack.of(defaultValue));
        }

        /**
         * Sets the command argument to be optional, with the specified default value.
         *
         * @param defaultValue default value
         * @return this builder
         * @see CommandArgument.Builder#asOptionalWithDefault(String)
         */
        public @NonNull Builder<C> asOptionalWithDefault(final @NonNull DefaultedRegistryReference<ItemType> defaultValue) {
            return this.asOptionalWithDefault(defaultValue.get());
        }

        /**
         * Sets the command argument to be optional, with the specified default value.
         *
         * @param defaultValue default value
         * @return this builder
         * @see CommandArgument.Builder#asOptionalWithDefault(String)
         */
        public @NonNull Builder<C> asOptionalWithDefault(final @NonNull ItemStackSnapshot defaultValue) {
            return this.asOptionalWithDefault(defaultValue.createStack());
        }

        /**
         * Sets the command argument to be optional, with the specified default value.
         *
         * @param defaultValue default value
         * @return this builder
         * @see CommandArgument.Builder#asOptionalWithDefault(String)
         */
        @SuppressWarnings("ConstantConditions")
        public @NonNull Builder<C> asOptionalWithDefault(final @NonNull ItemStack defaultValue) {
            final net.minecraft.world.item.ItemStack stack = (net.minecraft.world.item.ItemStack) (Object) defaultValue;
            return this.asOptionalWithDefault(new ItemInput(stack.getItem(), stack.getTag()).serialize());
        }

    }

}
