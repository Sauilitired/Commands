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
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.context.CommandContext;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;
import java.util.function.BiFunction;

/**
 * Parent of {@link Vector3dArgument} and {@link Vector2dArgument} containing shared methods.
 *
 * <p>Not for extension by API users.</p>
 *
 * @param <C> sender type
 * @param <V> vector type
 */
public abstract class VectorArgument<C, V> extends CommandArgument<C, V> {

    private final boolean centerIntegers;

    protected VectorArgument(
            final boolean required,
            final @NonNull String name,
            final @NonNull ArgumentParser<C, V> parser,
            final @NonNull String defaultValue,
            final @NonNull Class<V> vectorType,
            final boolean centerIntegers,
            final @Nullable BiFunction<CommandContext<C>, String, List<String>> suggestionsProvider,
            final @NonNull ArgumentDescription defaultDescription
    ) {
        super(required, name, parser, defaultValue, vectorType, suggestionsProvider, defaultDescription);
        this.centerIntegers = centerIntegers;
    }

    /**
     * Get whether integers will be centered to x.5. Defaults to false.
     *
     * @return whether integers will be centered
     */
    public boolean centerIntegers() {
        return this.centerIntegers;
    }

    /**
     * Parent of {@link Vector3dArgument.Builder} and {@link Vector2dArgument.Builder}.
     *
     * <p>Not for extension by API users.</p>
     *
     * @param <C> sender type
     * @param <V> vector type
     * @param <B> builder subtype
     */
    public abstract static class VectorArgumentBuilder<C, V, B extends VectorArgumentBuilder<C, V, B>>
            extends TypedBuilder<C, V, B> {

        private boolean centerIntegers = false;

        protected VectorArgumentBuilder(final @NonNull Class<V> vectorClass, final @NonNull String name) {
            super(vectorClass, name);
        }

        /**
         * Set whether integers will be centered to x.5. Will be false by default if unset.
         *
         * @param centerIntegers whether integers will be centered
         * @return this builder
         */
        public @NonNull B centerIntegers(final boolean centerIntegers) {
            this.centerIntegers = centerIntegers;
            return this.self();
        }

        /**
         * Get whether integers will be centered to x.5. Defaults to false.
         *
         * @return whether integers will be centered
         */
        public boolean centerIntegers() {
            return this.centerIntegers;
        }

    }

}
