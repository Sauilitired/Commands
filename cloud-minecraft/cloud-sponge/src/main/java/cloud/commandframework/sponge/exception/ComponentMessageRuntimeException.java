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
package cloud.commandframework.sponge.exception;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.util.ComponentMessageThrowable;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * A subclass of {@link RuntimeException} that contains a rich message that is an instance of
 * {@link Component} rather than a String. This allows formatted and localized
 * exception messages.
 */
public class ComponentMessageRuntimeException extends RuntimeException implements ComponentMessageThrowable {

    private static final long serialVersionUID = 2152146048432114275L;

    private final @Nullable Component message;

    /**
     * Constructs a new {@link ComponentMessageRuntimeException}.
     */
    public ComponentMessageRuntimeException() {
        this.message = null;
    }

    /**
     * Constructs a new {@link ComponentMessageRuntimeException} with the given message.
     *
     * @param message The detail message
     */
    public ComponentMessageRuntimeException(final @Nullable ComponentLike message) {
        this.message = message == null ? null : message.asComponent();
    }

    /**
     * Constructs a new {@link ComponentMessageRuntimeException} with the given message and
     * cause.
     *
     * @param message   The detail message
     * @param throwable The cause
     */
    public ComponentMessageRuntimeException(final @Nullable ComponentLike message, final @Nullable Throwable throwable) {
        super(throwable);
        this.message = message == null ? null : message.asComponent();
    }

    /**
     * Constructs a new {@link ComponentMessageRuntimeException} with the given cause.
     *
     * @param throwable The cause
     */
    public ComponentMessageRuntimeException(final @Nullable Throwable throwable) {
        super(throwable);
        this.message = null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable String getMessage() {
        return PlainTextComponentSerializer.plainText().serializeOrNull(this.message);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable Component componentMessage() {
        return this.message;
    }

}
