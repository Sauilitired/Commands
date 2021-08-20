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

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.block.BlockState;
import org.spongepowered.api.data.persistence.DataContainer;
import org.spongepowered.api.world.BlockChangeFlag;
import org.spongepowered.api.world.server.ServerLocation;

/**
 * Intermediary result for an argument which parses a {@link BlockState} and optional extra NBT data.
 */
public interface BlockInput {

    /**
     * Get the parsed {@link BlockState}.
     *
     * @return the {@link BlockState}
     */
    @NonNull BlockState blockState();

    /**
     * Get any extra data besides the {@link BlockState} that may have been parsed.
     *
     * <p>Will return {@code null} if there is no extra data.</p>
     *
     * @return the extra data or {@code null}
     */
    @Nullable DataContainer extraData();

    /**
     * Replace the block at the given {@link ServerLocation} with the parsed {@link BlockState},
     * and if the placed block is a {@link org.spongepowered.api.block.entity.BlockEntity}, applies any
     * extra NBT data from {@link #extraData()}.
     *
     * @param location location
     * @return return whether the block change was successful
     * @see ServerLocation#setBlock(BlockState)
     */
    boolean place(@NonNull ServerLocation location);

    /**
     * Replace the block at the given {@link ServerLocation} with the parsed {@link BlockState},
     * and if the placed block is a {@link org.spongepowered.api.block.entity.BlockEntity}, applies any
     * extra NBT data from {@link #extraData()}.
     *
     * @param location location
     * @param flag     the various {@link BlockChangeFlag change flags} controlling some interactions
     * @return return whether the block change was successful
     * @see ServerLocation#setBlock(BlockState, BlockChangeFlag)
     */
    boolean place(@NonNull ServerLocation location, @NonNull BlockChangeFlag flag);

}
