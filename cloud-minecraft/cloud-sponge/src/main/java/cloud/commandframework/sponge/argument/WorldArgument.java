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
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.arguments.DimensionArgument;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.registrar.tree.CommandTreeNode;
import org.spongepowered.api.command.registrar.tree.CommandTreeNodeTypes;
import org.spongepowered.api.world.server.ServerWorld;
import org.spongepowered.api.world.server.WorldManager;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Argument for retrieving {@link ServerWorld ServerWorlds} from the {@link WorldManager} by their {@link ResourceKey}.
 *
 * @param <C> sender type
 */
public final class WorldArgument<C> extends CommandArgument<C, ServerWorld> {

    private WorldArgument(
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
                ServerWorld.class,
                suggestionsProvider,
                defaultDescription
        );
    }

    /**
     * Create a new required {@link WorldArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link WorldArgument}
     */
    public static <C> @NonNull WorldArgument<C> of(final @NonNull String name) {
        return WorldArgument.<C>builder(name).build();
    }

    /**
     * Create a new optional {@link WorldArgument}.
     *
     * @param name argument name
     * @param <C>  sender type
     * @return a new {@link WorldArgument}
     */
    public static <C> @NonNull WorldArgument<C> optional(final @NonNull String name) {
        return WorldArgument.<C>builder(name).asOptional().build();
    }

    /**
     * Create a new optional {@link WorldArgument} with the specified default value.
     *
     * @param name         argument name
     * @param defaultValue default value
     * @param <C>          sender type
     * @return a new {@link WorldArgument}
     */
    public static <C> @NonNull WorldArgument<C> optional(final @NonNull String name, final @NonNull ResourceKey defaultValue) {
        return WorldArgument.<C>builder(name).asOptionalWithDefault(defaultValue).build();
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
     * Parser for {@link ServerWorld ServerWorlds} in the {@link WorldManager}.
     *
     * @param <C> sender type
     */
    public static final class Parser<C> implements NodeSupplyingArgumentParser<C, ServerWorld> {

        private static final DynamicCommandExceptionType ERROR_INVALID_VALUE;

        static {
            try {
                // todo: use an accessor
                final Field errorInvalidValueField = DimensionArgument.class
                        .getDeclaredField("ERROR_INVALID_VALUE");
                errorInvalidValueField.setAccessible(true);
                ERROR_INVALID_VALUE = (DynamicCommandExceptionType) errorInvalidValueField.get(null);
            } catch (final ReflectiveOperationException ex) {
                throw new RuntimeException("Couldn't access ERROR_INVALID_VALUE command exception type.", ex);
            }
        }

        @Override
        public @NonNull ArgumentParseResult<@NonNull ServerWorld> parse(
                @NonNull final CommandContext<@NonNull C> commandContext,
                @NonNull final Queue<@NonNull String> inputQueue
        ) {
            final String input = inputQueue.peek();
            final ResourceKey key = ResourceKeyUtil.resourceKey(input);
            if (key == null) {
                return ResourceKeyUtil.invalidResourceKey();
            }
            final Optional<ServerWorld> entry = Sponge.server().worldManager().world(key);
            if (entry.isPresent()) {
                inputQueue.remove();
                return ArgumentParseResult.success(entry.get());
            }
            return ArgumentParseResult.failure(ERROR_INVALID_VALUE.create(key));
        }

        @Override
        public @NonNull List<@NonNull String> suggestions(
                final @NonNull CommandContext<C> commandContext,
                final @NonNull String input
        ) {
            return Sponge.server().worldManager().worlds().stream().flatMap(world -> {
                if (world.key().namespace().equals(ResourceKey.MINECRAFT_NAMESPACE)) {
                    return Stream.of(world.key().value(), world.key().asString());
                }
                return Stream.of(world.key().asString());
            }).collect(Collectors.toList());
        }

        @Override
        public CommandTreeNode.@NonNull Argument<? extends CommandTreeNode.Argument<?>> node() {
            return CommandTreeNodeTypes.RESOURCE_LOCATION.get().createNode().customCompletions();
        }

    }

    /**
     * Builder for {@link WorldArgument}.
     *
     * @param <C> sender type
     */
    public static final class Builder<C> extends TypedBuilder<C, ServerWorld, Builder<C>> {

        Builder(final @NonNull String name) {
            super(ServerWorld.class, name);
        }

        @Override
        public @NonNull WorldArgument<C> build() {
            return new WorldArgument<>(
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
        public @NonNull Builder<C> asOptionalWithDefault(final @NonNull ResourceKey defaultValue) {
            return this.asOptionalWithDefault(defaultValue.asString());
        }

    }

}
