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
package cloud.commandframework.brigadier.node;

import cloud.commandframework.brigadier.suggestion.SuggestionsType;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import org.apiguardian.api.API;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

@API(status = API.Status.INTERNAL, since = "2.0.0")
final class ArgumentMapping<S> {

    private final ArgumentType<?> argumentType;
    private final SuggestionProvider<S> suggestionProvider;
    private final SuggestionsType suggestionsType;

    ArgumentMapping(
            final @NonNull ArgumentType<?> argumentType,
            final @Nullable SuggestionProvider<S> suggestionProvider,
            final @NonNull SuggestionsType suggestionsType
    ) {
        this.argumentType = argumentType;
        this.suggestionProvider = suggestionProvider;
        this.suggestionsType = suggestionsType;
    }

    ArgumentMapping(
            final @NonNull ArgumentType<?> argumentType,
            final @Nullable SuggestionProvider<S> suggestionProvider
    ) {
        this(argumentType, suggestionProvider, SuggestionsType.BRIGADIER_SUGGESTIONS);
    }

    ArgumentMapping(
            final @NonNull ArgumentType<?> argumentType,
            final @NonNull SuggestionsType suggestionsType
    ) {
        this(argumentType, null, suggestionsType);
    }

    ArgumentMapping(
            final @NonNull ArgumentType<?> argumentType
    ) {
        this(argumentType, null, SuggestionsType.BRIGADIER_SUGGESTIONS);
    }

    @NonNull ArgumentType<?> argumentType() {
        return this.argumentType;
    }

    @NonNull SuggestionsType suggestionsType() {
        return this.suggestionsType;
    }

    @Nullable SuggestionProvider<S> suggestionProvider() {
        return this.suggestionProvider;
    }
}