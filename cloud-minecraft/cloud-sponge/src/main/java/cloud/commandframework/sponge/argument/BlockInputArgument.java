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
import cloud.commandframework.sponge.data.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.block.BlockType;
import org.spongepowered.api.command.registrar.tree.CommandTreeNode;
import org.spongepowered.api.command.registrar.tree.CommandTreeNodeTypes;
import org.spongepowered.api.data.persistence.DataContainer;
import org.spongepowered.api.registry.DefaultedRegistryReference;
import org.spongepowered.api.registry.RegistryTypes;
import org.spongepowered.api.world.BlockChangeFlag;
import org.spongepowered.api.world.BlockChangeFlags;
import org.spongepowered.api.world.schematic.PaletteTypes;
import org.spongepowered.api.world.server.ServerLocation;
import org.spongepowered.common.data.persistence.NBTTranslator;
import org.spongepowered.common.util.VecHelper;
import org.spongepowered.common.world.SpongeBlockChangeFlag;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Queue;
import java.util.function.BiFunction;

/**
 * An argument for parsing {@link BlockInput} from a {@link BlockState}
 * and optional extra NBT data.
 *
 * <p>Example input strings:</p>
 * <ul>
 *     <li>{@code stone}</li>
 *     <li>{@code minecraft:stone}</li>
 *     <li>{@code andesite_stairs[waterlogged=true,facing=east]}</li>
 * </ul>
 *
 * @param <C> sender type
 */
public final class BlockInputArgument<C> extends CommandArgument<C, BlockInput> {

    private BlockInputArgument(
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
                BlockInput.class,
                suggestionsProvider,
                defaultDescription
        );
    }

    /**
     * Create a new required {@link BlockInputArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link BlockInputArgument}
     */
    public static <C> @NonNull BlockInputArgument<C> of(final @NonNull String name) {
        return BlockInputArgument.<C>builder(name).build();
    }

    /**
     * Create a new optional {@link BlockInputArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link BlockInputArgument}
     */
    public static <C> @NonNull BlockInputArgument<C> optional(final @NonNull String name) {
        return BlockInputArgument.<C>builder(name).asOptional().build();
    }

    /**
     * Create a new optional {@link BlockInputArgument} with the specified default value.
     *
     * @param name         argument name
     * @param defaultValue default value
     * @param <C>          sender type
     * @return a new {@link BlockInputArgument}
     */
    public static <C> @NonNull BlockInputArgument<C> optional(
            final @NonNull String name,
            final @NonNull BlockState defaultValue
    ) {
        return BlockInputArgument.<C>builder(name).asOptionalWithDefault(defaultValue).build();
    }

    /**
     * Create a new optional {@link BlockInputArgument} with the specified default value.
     *
     * @param name         argument name
     * @param defaultValue default value
     * @param <C>          sender type
     * @return a new {@link BlockInputArgument}
     */
    public static <C> @NonNull BlockInputArgument<C> optional(
            final @NonNull String name,
            final @NonNull BlockType defaultValue
    ) {
        return BlockInputArgument.<C>builder(name).asOptionalWithDefault(defaultValue).build();
    }

    /**
     * Create a new optional {@link BlockInputArgument} with the specified default value.
     *
     * @param name         argument name
     * @param defaultValue default value
     * @param <C>          sender type
     * @return a new {@link BlockInputArgument}
     */
    public static <C> @NonNull BlockInputArgument<C> optional(
            final @NonNull String name,
            final @NonNull DefaultedRegistryReference<BlockType> defaultValue
    ) {
        return BlockInputArgument.<C>builder(name).asOptionalWithDefault(defaultValue).build();
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
     * Parser for {@link BlockState BlockStates}.
     *
     * @param <C> sender type
     */
    public static final class Parser<C> implements NodeSupplyingArgumentParser<C, BlockInput> {

        private final ArgumentParser<C, BlockInput> mappedParser =
                new WrappedBrigadierParser<C, net.minecraft.commands.arguments.blocks.BlockInput>(BlockStateArgument.block())
                        .map((ctx, blockInput) -> ArgumentParseResult.success(new BlockInputImpl(blockInput)));

        @Override
        public @NonNull ArgumentParseResult<cloud.commandframework.sponge.data.@NonNull BlockInput> parse(
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
            return CommandTreeNodeTypes.BLOCK_STATE.get().createNode();
        }

        private static final class BlockInputImpl implements BlockInput {

            private static final Field COMPOUND_TAG_FIELD;

            static {
                try {
                    COMPOUND_TAG_FIELD = net.minecraft.commands.arguments.blocks.BlockInput.class.getDeclaredField("tag");
                    COMPOUND_TAG_FIELD.setAccessible(true);
                } catch (final ReflectiveOperationException ex) {
                    throw new RuntimeException(ex);
                }
            }

            private final net.minecraft.commands.arguments.blocks.BlockInput blockInput;
            private final @Nullable DataContainer extraData;

            BlockInputImpl(final net.minecraft.commands.arguments.blocks.@NonNull BlockInput blockInput) {
                this.blockInput = blockInput;
                try {
                    final CompoundTag tag = (CompoundTag) COMPOUND_TAG_FIELD.get(blockInput);
                    this.extraData = tag == null ? null : NBTTranslator.INSTANCE.translate(tag);
                } catch (final IllegalAccessException ex) {
                    throw new RuntimeException(ex);
                }
            }

            @Override
            public @NonNull BlockState blockState() {
                return (BlockState) this.blockInput.getState();
            }

            @Override
            public @Nullable DataContainer extraData() {
                return this.extraData;
            }

            @Override
            public boolean place(final @NonNull ServerLocation location) {
                return this.place(location, BlockChangeFlags.DEFAULT_PLACEMENT);
            }

            @Override
            public boolean place(final @NonNull ServerLocation location, final @NonNull BlockChangeFlag flag) {
                return this.blockInput.place(
                        (ServerLevel) location.world(),
                        VecHelper.toBlockPos(location.position()),
                        ((SpongeBlockChangeFlag) flag).getRawFlag()
                );
            }

        }

    }

    /**
     * Builder for {@link BlockInputArgument}.
     *
     * @param <C> sender type
     */
    public static final class Builder<C> extends TypedBuilder<C, BlockInput, Builder<C>> {

        Builder(final @NonNull String name) {
            super(BlockInput.class, name);
        }

        @Override
        public @NonNull BlockInputArgument<C> build() {
            return new BlockInputArgument<>(
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
         * @param defaultState     default {@link BlockState}
         * @param defaultExtraData default extra data
         * @return this builder
         * @see CommandArgument.Builder#asOptionalWithDefault(String)
         */
        public @NonNull Builder<C> asOptionalWithDefault(
                final @NonNull BlockState defaultState,
                final @Nullable DataContainer defaultExtraData
        ) {
            final String value = PaletteTypes.BLOCK_STATE_PALETTE.get().stringifier()
                    .apply(RegistryTypes.BLOCK_TYPE.get(), defaultState)
                    + (defaultExtraData == null ? "" : NBTTranslator.INSTANCE.translate(defaultExtraData).toString());
            return this.asOptionalWithDefault(value);
        }

        /**
         * Sets the command argument to be optional, with the specified default value.
         *
         * @param defaultValue default value
         * @return this builder
         * @see CommandArgument.Builder#asOptionalWithDefault(String)
         */
        public @NonNull Builder<C> asOptionalWithDefault(final @NonNull BlockState defaultValue) {
            return this.asOptionalWithDefault(defaultValue, null);
        }

        /**
         * Sets the command argument to be optional, with the specified default value.
         *
         * @param defaultValue default value
         * @return this builder
         * @see CommandArgument.Builder#asOptionalWithDefault(String)
         */
        public @NonNull Builder<C> asOptionalWithDefault(final @NonNull BlockType defaultValue) {
            return this.asOptionalWithDefault(defaultValue.defaultState());
        }

        /**
         * Sets the command argument to be optional, with the specified default value.
         *
         * @param defaultValue default value
         * @return this builder
         * @see CommandArgument.Builder#asOptionalWithDefault(String)
         */
        public @NonNull Builder<C> asOptionalWithDefault(final @NonNull DefaultedRegistryReference<BlockType> defaultValue) {
            return this.asOptionalWithDefault(defaultValue.get());
        }

    }

}
