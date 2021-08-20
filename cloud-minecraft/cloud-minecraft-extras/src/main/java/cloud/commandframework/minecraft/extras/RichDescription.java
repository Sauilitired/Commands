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
package cloud.commandframework.minecraft.extras;

import cloud.commandframework.ArgumentDescription;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.translation.GlobalTranslator;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Locale;

import static java.util.Objects.requireNonNull;

/**
 * An argument description implementation that uses Adventure components.
 *
 * @since 1.4.0
 */
public final class RichDescription implements ArgumentDescription {
    private static final RichDescription EMPTY = new RichDescription(Component.empty());

    private final Component contents;

    RichDescription(final Component contents) {
        this.contents = contents;
    }

    /**
     * Get an empty description.
     *
     * @return the empty description
     */
    public static @NonNull RichDescription empty() {
        return EMPTY;
    }

    /**
     * Create a new rich description from the provided component.
     *
     * @param contents the rich contents
     * @return a new rich description
     */
    public static @NonNull RichDescription of(final @NonNull ComponentLike contents) {
        final Component componentContents = requireNonNull(contents, "contents").asComponent();
        if (Component.empty().equals(componentContents)) {
            return EMPTY;
        }

        return new RichDescription(componentContents);
    }

    /* Translatable helper methods */

    /**
     * Create a rich description pointing to a translation key.
     *
     * @param key the translation key
     * @return a new rich description
     */
    public static @NonNull RichDescription translatable(final @NonNull String key) {
        requireNonNull(key, "key");

        return new RichDescription(Component.translatable(key));
    }

    /**
     * Create a rich description pointing to a translation key.
     *
     * @param key the translation key
     * @param args the arguments to use with the translation key
     * @return a new rich description
     */
    public static @NonNull RichDescription translatable(
            final @NonNull String key,
            final @NonNull ComponentLike @NonNull... args
    ) {
        requireNonNull(key, "key");
        requireNonNull(args, "args");

        return new RichDescription(Component.translatable(key, args));
    }

    /**
     * {@inheritDoc}
     *
     * @deprecated to discourage use. A plain serialization is a somewhat expensive and lossy operation, use
     *      {@link #getContents()} instead.
     */
    @Override
    @Deprecated
    public @NonNull String getDescription() {
        return net.kyori.adventure.text.serializer.plain.PlainComponentSerializer.plain()
                .serialize(GlobalTranslator.render(this.contents, Locale.getDefault()));
    }

    /**
     * Get the contents of this description.
     *
     * @return the component contents of this description
     */
    public @NonNull Component getContents() {
        return this.contents;
    }

    @Override
    public boolean isEmpty() {
        return Component.empty().equals(this.contents);
    }

}
