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
package cloud.commandframework.annotations;

import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.CommandManager;
import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.flags.CommandFlag;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.arguments.parser.ParserParameters;
import cloud.commandframework.arguments.parser.ParserRegistry;
import cloud.commandframework.permission.Permission;
import io.leangen.geantyref.TypeToken;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

final class FlagExtractor implements Function<@NonNull Method, Collection<@NonNull CommandFlag<?>>> {

    private final CommandManager<?> commandManager;

    FlagExtractor(final @NonNull CommandManager<?> commandManager) {
        this.commandManager = commandManager;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public @NonNull Collection<@NonNull CommandFlag<?>> apply(final @NonNull Method method) {
        final Collection<CommandFlag<?>> flags = new LinkedList<>();
        for (final Parameter parameter : method.getParameters()) {
            if (!parameter.isAnnotationPresent(Flag.class)) {
                continue;
            }
            final Flag flag = parameter.getAnnotation(Flag.class);
            final CommandFlag.Builder<Void> builder = this.commandManager
                    .flagBuilder(flag.value())
                    .withDescription(ArgumentDescription.of(flag.description()))
                    .withAliases(flag.aliases())
                    .withPermission(Permission.of(flag.permission()));
            if (parameter.getType().equals(boolean.class)) {
                flags.add(builder.build());
            } else {
                final ParserRegistry<?> registry = this.commandManager.getParserRegistry();
                final ArgumentParser<?, ?> parser;
                if (flag.parserName().isEmpty()) {
                    parser = registry.createParser(TypeToken.get(parameter.getType()), ParserParameters.empty())
                            .orElse(null);
                } else {
                    parser = registry.createParser(flag.parserName(), ParserParameters.empty())
                            .orElse(null);
                }
                if (parser == null) {
                    throw new IllegalArgumentException(
                            String.format("Cannot find parser for type '%s' for flag '%s' in method '%s'",
                                    parameter.getType().getCanonicalName(), flag.value(), method.getName()
                            ));
                }
                final BiFunction<?, @NonNull String, @NonNull List<String>> suggestionProvider;
                if (!flag.suggestions().isEmpty()) {
                    suggestionProvider = registry.getSuggestionProvider(flag.suggestions()).orElse(null);
                } else {
                    suggestionProvider = null;
                }
                final CommandArgument.Builder argumentBuilder0 = CommandArgument.ofType(
                        parameter.getType(),
                        flag.value()
                );
                final CommandArgument.Builder argumentBuilder = argumentBuilder0.asRequired()
                        .manager(this.commandManager)
                        .withParser(parser);
                final CommandArgument argument;
                if (suggestionProvider != null) {
                    argument = argumentBuilder.withSuggestionsProvider(suggestionProvider).build();
                } else {
                    argument = argumentBuilder.build();
                }
                flags.add(builder.withArgument(argument).build());
            }
        }
        return flags;
    }

}
