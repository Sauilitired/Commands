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
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.sponge.NodeSupplyingArgumentParser;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.arguments.OperationArgument;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.command.parameter.managed.operator.Operator;
import org.spongepowered.api.command.registrar.tree.CommandTreeNode;
import org.spongepowered.api.command.registrar.tree.CommandTreeNodeTypes;
import org.spongepowered.api.registry.DefaultedRegistryReference;
import org.spongepowered.api.registry.RegistryTypes;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/**
 * An argument for parsing {@link Operator Operators}.
 *
 * @param <C> sender type
 */
public final class OperatorArgument<C> extends CommandArgument<C, Operator> {

    private OperatorArgument(
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
                Operator.class,
                suggestionsProvider,
                defaultDescription
        );
    }

    /**
     * Create a new required {@link OperatorArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link OperatorArgument}
     */
    public static <C> @NonNull OperatorArgument<C> of(final @NonNull String name) {
        return OperatorArgument.<C>builder(name).build();
    }

    /**
     * Create a new optional {@link OperatorArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link OperatorArgument}
     */
    public static <C> @NonNull OperatorArgument<C> optional(final @NonNull String name) {
        return OperatorArgument.<C>builder(name).asOptional().build();
    }

    /**
     * Create a new optional {@link OperatorArgument} with the specified default value.
     *
     * @param name         argument name
     * @param defaultValue default value
     * @param <C>          sender type
     * @return a new {@link OperatorArgument}
     */
    public static <C> @NonNull OperatorArgument<C> optional(final @NonNull String name, final @NonNull Operator defaultValue) {
        return OperatorArgument.<C>builder(name).asOptionalWithDefault(defaultValue).build();
    }

    /**
     * Create a new optional {@link OperatorArgument} with the specified default value.
     *
     * @param name         argument name
     * @param <C>          sender type
     * @param defaultValue default value
     * @return a new {@link OperatorArgument}
     */
    public static <C> @NonNull OperatorArgument<C> optional(
            final @NonNull String name,
            final @NonNull DefaultedRegistryReference<Operator> defaultValue
    ) {
        return OperatorArgument.<C>builder(name).asOptionalWithDefault(defaultValue).build();
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
     * Argument parser for {@link Operator Operators}.
     *
     * @param <C> sender type
     */
    public static final class Parser<C> implements NodeSupplyingArgumentParser<C, Operator> {

        private static final SimpleCommandExceptionType ERROR_INVALID_OPERATION;

        static {
            try {
                // todo: use an accessor (Sponge has one but we don't have a way of using it)
                final Field errorInvalidOperationField = OperationArgument.class
                        .getDeclaredField("ERROR_INVALID_OPERATION");
                errorInvalidOperationField.setAccessible(true);
                ERROR_INVALID_OPERATION = (SimpleCommandExceptionType) errorInvalidOperationField.get(null);
            } catch (final ReflectiveOperationException ex) {
                throw new RuntimeException("Couldn't access ERROR_INVALID_OPERATION command exception type.", ex);
            }
        }

        @Override
        public @NonNull ArgumentParseResult<@NonNull Operator> parse(
                @NonNull final CommandContext<@NonNull C> commandContext,
                @NonNull final Queue<@NonNull String> inputQueue
        ) {
            final String input = inputQueue.peek();
            final Optional<Operator> operator = RegistryTypes.OPERATOR.get().stream()
                    .filter(op -> op.asString().equals(input))
                    .findFirst();
            if (!operator.isPresent()) {
                return ArgumentParseResult.failure(ERROR_INVALID_OPERATION.create());
            }
            inputQueue.remove();
            return ArgumentParseResult.success(operator.get());
        }

        @Override
        public @NonNull List<@NonNull String> suggestions(
                final @NonNull CommandContext<C> commandContext,
                final @NonNull String input
        ) {
            return RegistryTypes.OPERATOR.get().stream()
                    .map(Operator::asString)
                    .collect(Collectors.toList());
        }

        @Override
        public CommandTreeNode.@NonNull Argument<? extends CommandTreeNode.Argument<?>> node() {
            return CommandTreeNodeTypes.OPERATION.get().createNode();
        }

    }

    /**
     * Builder for {@link OperatorArgument}.
     *
     * @param <C> sender type
     */
    public static final class Builder<C> extends TypedBuilder<C, Operator, Builder<C>> {

        Builder(final @NonNull String name) {
            super(Operator.class, name);
        }

        @Override
        public @NonNull OperatorArgument<C> build() {
            return new OperatorArgument<>(
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
        public @NonNull Builder<C> asOptionalWithDefault(final @NonNull Operator defaultValue) {
            return this.asOptionalWithDefault(defaultValue.asString());
        }

        /**
         * Sets the command argument to be optional, with the specified default value.
         *
         * @param defaultValue default value
         * @return this builder
         * @see CommandArgument.Builder#asOptionalWithDefault(String)
         */
        public @NonNull Builder<C> asOptionalWithDefault(final @NonNull DefaultedRegistryReference<Operator> defaultValue) {
            return this.asOptionalWithDefault(defaultValue.get().asString());
        }

    }

}
