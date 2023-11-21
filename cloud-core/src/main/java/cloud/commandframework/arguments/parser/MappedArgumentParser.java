//
// MIT License
//
// Copyright (c) 2022 Alexander Söderberg & Contributors
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
package cloud.commandframework.arguments.parser;

import cloud.commandframework.arguments.suggestion.Suggestion;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.context.CommandInput;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apiguardian.api.API;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An argument parser which wraps another argument parser, converting the output type.
 *
 * @param <C> sender type
 * @param <I> base output type
 * @param <O> mapped output type
 * @since 1.5.0
 */
@API(status = API.Status.STABLE, since = "1.5.0")
public final class MappedArgumentParser<C, I, O> implements ArgumentParser.FutureArgumentParser<C, O> {

    private final ArgumentParser<C, I> base;
    private final Mapper<C, I, O> mapper;

    MappedArgumentParser(
            final ArgumentParser<C, I> base,
            final Mapper<C, I, O> mapper
    ) {
        this.base = base;
        this.mapper = mapper;
    }

    /**
     * Get the parser this one is derived from.
     *
     * @return the base parser
     */
    public ArgumentParser<C, I> getBaseParser() {
        return this.base;
    }

    @Override
    public @NonNull CompletableFuture<@NonNull O> parseFuture(
            @NonNull final CommandContext<@NonNull C> commandContext,
            @NonNull final CommandInput commandInput
    ) {
       return this.base.parseFuture(commandContext, commandInput)
               .thenCompose(result -> this.mapper.map(commandContext, result));
    }

    @Override
    public @NonNull List<@NonNull Suggestion> suggestions(
            final @NonNull CommandContext<C> commandContext,
            final @NonNull String input
    ) {
        return this.base.suggestions(commandContext, input);
    }

    @Override
    public @NonNull <O1> ArgumentParser<C, O1> map(final Mapper<C, O, O1> mapper) {
        final Mapper<C, I, O1> composedMapper = (ctx, original) -> this.mapper.map(ctx, original)
                .thenCompose(value -> mapper.map(ctx, value));
        return new MappedArgumentParser<>(
                this.base,
                composedMapper
        );
    }

    @Override
    public boolean isContextFree() {
        return this.base.isContextFree();
    }

    @Override
    public int getRequestedArgumentCount() {
        return this.base.getRequestedArgumentCount();
    }

    @Override
    public int hashCode() {
        return 31 + this.base.hashCode()
                + 7 * this.mapper.hashCode();
    }

    @Override
    public boolean equals(final @Nullable Object other) {
        if (!(other instanceof MappedArgumentParser<?, ?, ?>)) {
            return false;
        }

        final MappedArgumentParser<?, ?, ?> that = (MappedArgumentParser<?, ?, ?>) other;
        return this.base.equals(that.base)
                && this.mapper.equals(that.mapper);
    }

    @Override
    public String toString() {
        return "MappedArgumentParser{"
                + "base=" + this.base + ','
                + "mapper=" + this.mapper + '}';
    }


    @FunctionalInterface
    @API(status = API.Status.STABLE, since = "2.0.0")
    public interface Mapper<C, I, O> {

        /**
         * Maps the input to a future that completes with the output.
         *
         * @param context the context
         * @param input   the input
         * @return future that completes with the output
         */
        @NonNull CompletableFuture<O> map(@NonNull CommandContext<C> context, @NonNull I input);
    }
}
