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
package cloud.commandframework.arguments.parser;

import io.leangen.geantyref.TypeToken;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Common parser parameters used when resolving types in the {@link ParserRegistry}
 */
public final class StandardParameters {

    /**
     * Minimum value accepted by a numerical parser
     */
    public static final ParserParameter<Number> RANGE_MIN = create("min", TypeToken.get(Number.class));
    /**
     * Maximum value accepted by a numerical parser
     */
    public static final ParserParameter<Number> RANGE_MAX = create("max", TypeToken.get(Number.class));
    /**
     * Command description
     */
    public static final ParserParameter<String> DESCRIPTION = create("description", TypeToken.get(String.class));
    /**
     * Command confirmation
     */
    public static final ParserParameter<Boolean> CONFIRMATION = create("confirmation", TypeToken.get(Boolean.class));
    /**
     * Command completions
     */
    public static final ParserParameter<String[]> COMPLETIONS = create("completions", TypeToken.get(String[].class));
    /**
     * The command should be hidden from help menus, etc
     */
    public static final ParserParameter<Boolean> HIDDEN = create("hidden", TypeToken.get(Boolean.class));
    /**
     * Indicates that a string argument should be greedy
     */
    public static final ParserParameter<Boolean> GREEDY = create("greedy", TypeToken.get(Boolean.class));
    /**
     * Indicates that a string argument should be quoted.
     *
     * @since 1.5.0
     */
    public static final ParserParameter<Boolean> QUOTED = create("quoted", TypeToken.get(Boolean.class));
    /**
     * Indicates that a boolean argument should be liberal.
     *
     * @since 1.6.0
     */
    public static final ParserParameter<Boolean> LIBERAL = create("liberal", TypeToken.get(Boolean.class));

    private StandardParameters() {
    }

    private static <T> @NonNull ParserParameter<T> create(
            final @NonNull String key,
            final @NonNull TypeToken<T> expectedType
    ) {
        return new ParserParameter<>(key, expectedType);
    }

}
