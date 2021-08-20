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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.command.registrar.tree.CommandTreeNode;
import org.spongepowered.api.command.registrar.tree.CommandTreeNodeTypes;
import org.spongepowered.common.adventure.SpongeAdventure;

import java.util.List;
import java.util.Queue;
import java.util.function.BiFunction;

/**
 * An argument for parsing {@link Component Components} from json formatted text.
 *
 * @param <C> sender type
 */
public final class ComponentArgument<C> extends CommandArgument<C, Component> {

    private ComponentArgument(
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
                Component.class,
                suggestionsProvider,
                defaultDescription
        );
    }

    /**
     * Create a new required {@link ComponentArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link ComponentArgument}
     */
    public static <C> @NonNull ComponentArgument<C> of(final @NonNull String name) {
        return ComponentArgument.<C>builder(name).build();
    }

    /**
     * Create a new optional {@link ComponentArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link ComponentArgument}
     */
    public static <C> @NonNull ComponentArgument<C> optional(final @NonNull String name) {
        return ComponentArgument.<C>builder(name).asOptional().build();
    }

    /**
     * Create a new optional {@link ComponentArgument} with the specified default value.
     *
     * @param name         argument name
     * @param defaultValue default value
     * @param <C>          sender type
     * @return a new {@link ComponentArgument}
     */
    public static <C> @NonNull ComponentArgument<C> optional(
            final @NonNull String name,
            final @NonNull ComponentLike defaultValue
    ) {
        return ComponentArgument.<C>builder(name).asOptionalWithDefault(defaultValue).build();
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
     * Parser for {@link Component Components} from json formatted text.
     *
     * @param <C> sender type
     */
    public static final class Parser<C> implements NodeSupplyingArgumentParser<C, Component> {

        private final ArgumentParser<C, Component> mappedParser =
                new WrappedBrigadierParser<C, net.minecraft.network.chat.Component>(
                        net.minecraft.commands.arguments.ComponentArgument.textComponent()
                ).map((ctx, component) ->
                        ArgumentParseResult.success(SpongeAdventure.asAdventure(component)));

        @Override
        public @NonNull ArgumentParseResult<@NonNull Component> parse(
                @NonNull final CommandContext<@NonNull C> commandContext,
                @NonNull final Queue<@NonNull String> inputQueue
        ) {
            return this.mappedParser.parse(commandContext, inputQueue);
        }

        @Override
        public CommandTreeNode.@NonNull Argument<? extends CommandTreeNode.Argument<?>> node() {
            return CommandTreeNodeTypes.COMPONENT.get().createNode();
        }

    }

    /**
     * Builder for {@link ComponentArgument}.
     *
     * @param <C> sender type
     */
    public static final class Builder<C> extends TypedBuilder<C, Component, Builder<C>> {

        Builder(final @NonNull String name) {
            super(Component.class, name);
        }

        @Override
        public @NonNull ComponentArgument<C> build() {
            return new ComponentArgument<>(
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
        public @NonNull Builder<C> asOptionalWithDefault(final @NonNull ComponentLike defaultValue) {
            return this.asOptionalWithDefault(GsonComponentSerializer.gson().serialize(defaultValue.asComponent()));
        }

    }

}
