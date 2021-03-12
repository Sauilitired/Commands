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
package cloud.commandframework.fabric.data;

import net.minecraft.command.EntitySelector;
import net.minecraft.entity.Entity;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Collection;

/**
 * A selector for multiple entities.
 *
 * @since 1.5.0
 */
public final class MultipleEntitySelector implements Selector<Entity> {

    private final String inputString;
    private final net.minecraft.command.EntitySelector entitySelector;
    private final Collection<Entity> selectedEntities;

    /**
     * Create a new MultipleEntitySelector.
     *
     * @param inputString      input string
     * @param entitySelector   entity selector
     * @param selectedEntities selected entities
     * @since 1.5.0
     */
    public MultipleEntitySelector(
            final @NonNull String inputString,
            final @NonNull EntitySelector entitySelector,
            final @NonNull Collection<Entity> selectedEntities
    ) {
        this.inputString = inputString;
        this.entitySelector = entitySelector;
        this.selectedEntities = selectedEntities;
    }

    @Override
    public @NonNull String getInput() {
        return this.inputString;
    }

    @Override
    public @NonNull EntitySelector getSelector() {
        return this.entitySelector;
    }

    @Override
    public @NonNull Collection<Entity> get() {
        return this.selectedEntities;
    }

}
