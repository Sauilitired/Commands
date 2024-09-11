//
// MIT License
//
// Copyright (c) 2024 Incendo
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
package org.incendo.cloud.meta;

import java.util.HashMap;
import java.util.Map;
import org.apiguardian.api.API;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.common.returnsreceiver.qual.This;
import org.incendo.cloud.key.CloudKey;

@API(status = API.Status.STABLE)
public class CommandMetaBuilder {

    private final Map<CloudKey<?>, Object> map = new HashMap<>();

    CommandMetaBuilder() {
    }

    /**
     * Copies all values from another {@link CommandMeta} instance
     *
     * @param commandMeta the instance to copy from
     * @return {@code this}
     */
    public @This @NonNull CommandMetaBuilder with(final @NonNull CommandMeta commandMeta) {
        this.map.putAll(commandMeta.all());
        return this;
    }

    /**
     * Stores the given {@code key}-{@code value} pair.
     *
     * @param <V>   the value type
     * @param key   the key
     * @param value the value
     * @return {@code this}
     */
    public <V> @This @NonNull CommandMetaBuilder with(
            final @NonNull CloudKey<V> key,
            final @NonNull V value
    ) {
        this.map.put(key, value);
        return this;
    }

    /**
     * Stores the given {@code key} with no value.
     *
     * @param key   the key
     * @return {@code this}
     */
    public @This @NonNull CommandMetaBuilder with(
            final @NonNull CloudKey<Void> key
    ) {
        this.map.put(key, new Object());
        return this;
    }

    /**
     * Builds a new {@link CommandMeta} instance using the stored {@code key}-{@code value} pairs.
     *
     * @return the instance
     */
    public @NonNull CommandMeta build() {
        return new SimpleCommandMeta(this.map);
    }
}
