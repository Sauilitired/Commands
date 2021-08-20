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
package cloud.commandframework.sponge.data;

import cloud.commandframework.sponge.exception.ComponentMessageRuntimeException;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.data.persistence.DataContainer;
import org.spongepowered.api.item.ItemType;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;

/**
 * Result for an argument which parses an {@link ItemType} and optional extra NBT data.
 */
public interface ProtoItemStack {

    /**
     * Get the {@link ItemType} of this {@link ProtoItemStack}.
     *
     * @return the {@link ItemType}
     */
    @NonNull ItemType itemType();

    /**
     * Get any extra data besides the {@link ItemType} that may have been parsed.
     *
     * <p>Will return {@code null} if there is no extra data.</p>
     *
     * @return the extra data or {@code null}
     */
    @Nullable DataContainer extraData();

    /**
     * Create a new {@link ItemStack} from the state of this {@link ProtoItemStack}.
     *
     * <p>A {@link ComponentMessageRuntimeException} will be thrown if the stack size was too large for the
     * provided {@link ItemType}.</p>
     *
     * @param stackSize               stack size
     * @param respectMaximumStackSize whether to respect {@link ItemType#maxStackQuantity()}
     * @return the created {@link ItemStack}
     * @throws ComponentMessageRuntimeException if the {@link ItemStack} could not be created
     */
    @NonNull ItemStack createItemStack(int stackSize, boolean respectMaximumStackSize) throws ComponentMessageRuntimeException;

    /**
     * Create a new {@link ItemStackSnapshot} from the state of this {@link ProtoItemStack}.
     *
     * <p>A {@link ComponentMessageRuntimeException} will be thrown if the stack size was too large for the
     * provided {@link ItemType}.</p>
     *
     * @param stackSize               stack size
     * @param respectMaximumStackSize whether to respect {@link ItemType#maxStackQuantity()}
     * @return the created {@link ItemStackSnapshot}
     * @throws ComponentMessageRuntimeException if the {@link ItemStackSnapshot} could not be created
     */
    @NonNull ItemStackSnapshot createItemStackSnapshot(
            int stackSize,
            boolean respectMaximumStackSize
    ) throws ComponentMessageRuntimeException;

}
