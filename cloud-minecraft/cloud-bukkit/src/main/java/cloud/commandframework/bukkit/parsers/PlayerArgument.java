//
// MIT License
//
// Copyright (c) 2020 Alexander Söderberg
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
package cloud.commandframework.bukkit.parsers;

import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.context.CommandContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.BiFunction;

@SuppressWarnings("unused")
public final class PlayerArgument<C> extends CommandArgument<C, Player> {
    private PlayerArgument(final boolean required, @Nonnull final String name,
                            @Nonnull final String defaultValue,
                           @Nonnull final BiFunction<CommandContext<C>, String, List<String>> suggestionsProvider) {
        super(required, name, new PlayerParser<>(), defaultValue, Player.class, suggestionsProvider);
    }

    /**
     * Create a new builder
     *
     * @param name Name of the component
     * @param <C>  Command sender type
     * @return Created builder
     */
    @Nonnull
    public static <C> Builder<C> newBuilder(@Nonnull final String name) {
        return new Builder<>(name);
    }

    /**
     * Create a new required command component
     *
     * @param name Component name
     * @param <C>  Command sender type
     * @return Created component
     */
    @Nonnull
    public static <C> CommandArgument<C, Player> required(@Nonnull final String name) {
        return PlayerArgument.<C>newBuilder(name).asRequired().build();
    }

    /**
     * Create a new optional command component
     *
     * @param name Component name
     * @param <C>  Command sender type
     * @return Created component
     */
    @Nonnull
    public static <C> CommandArgument<C, Player> optional(@Nonnull final String name) {
        return PlayerArgument.<C>newBuilder(name).asOptional().build();
    }

    /**
     * Create a new required command component with a default value
     *
     * @param name       Component name
     * @param defaultNum Default num
     * @param <C>        Command sender type
     * @return Created component
     */
    @Nonnull
    public static <C> CommandArgument<C, Player> optional(@Nonnull final String name,
                                                          final String defaultNum) {
        return PlayerArgument.<C>newBuilder(name).asOptionalWithDefault(defaultNum).build();
    }


    public static final class Builder<C> extends CommandArgument.Builder<C, Player> {

        protected Builder(@Nonnull final String name) {
            super(Player.class, name);
        }

        /**
         * Builder a new boolean component
         *
         * @return Constructed component
         */
        @Nonnull
        @Override
        public PlayerArgument<C> build() {
            return new PlayerArgument<>(this.isRequired(), this.getName(), this.getDefaultValue(), this.getSuggestionsProvider());
        }

    }


    private static final class PlayerParser<C> implements ArgumentParser<C, Player> {

        @Nonnull
        @Override
        public ArgumentParseResult<Player> parse(@Nonnull final CommandContext<C> commandContext,
                                                 @Nonnull final Queue<String> inputQueue) {
            final String input = inputQueue.peek();
            if (input == null) {
                return ArgumentParseResult.failure(new NullPointerException("No input was provided"));
            }
            inputQueue.remove();

            //noinspection deprecation
            Player player = Bukkit.getPlayer(input);

            if (player == null) {
                return ArgumentParseResult.failure(new PlayerParseException(input));
            }

            return ArgumentParseResult.success(player);
        }

        @Nonnull
        @Override
        public List<String> suggestions(@Nonnull final CommandContext<C> commandContext,
                                        @Nonnull final String input) {
            List<String> output = new ArrayList<>();

            for (Player player : Bukkit.getOnlinePlayers()) {
                output.add(player.getName());
            }

            return output;
        }
    }


    /**
     * Player parse exception
     */
    public static final class PlayerParseException extends IllegalArgumentException {

        private final String input;

        /**
         * Construct a new boolean parse exception
         *
         * @param input String input
         */
        public PlayerParseException(@Nonnull final String input) {
            this.input = input;
        }

        /**
         * Get the supplied input
         *
         * @return String value
         */
        public String getInput() {
            return input;
        }

    }
}
