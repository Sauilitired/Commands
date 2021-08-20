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
package cloud.commandframework.sponge.argument;

import cloud.commandframework.arguments.parser.ArgumentParseResult;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.resources.ResourceLocation;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.api.ResourceKey;

import java.lang.reflect.Field;

/**
 * Shared utilities for ResourceKey based arguments. Not API.
 */
final class ResourceKeyUtil {

    private ResourceKeyUtil() {
    }

    private static final SimpleCommandExceptionType ERROR_INVALID_RESOURCE_LOCATION;

    static {
        try {
            // todo: use an accessor
            final Field errorInvalidResourceLocationField = ResourceLocation.class.getDeclaredField("ERROR_INVALID");
            errorInvalidResourceLocationField.setAccessible(true);
            ERROR_INVALID_RESOURCE_LOCATION = (SimpleCommandExceptionType) errorInvalidResourceLocationField.get(null);
        } catch (final ReflectiveOperationException ex) {
            throw new RuntimeException("Couldn't access ERROR_INVALID command exception type.", ex);
        }
    }

    static <T> ArgumentParseResult<T> invalidResourceKey() {
        return ArgumentParseResult.failure(ERROR_INVALID_RESOURCE_LOCATION.create());
    }

    static @Nullable ResourceKey resourceKey(final @NonNull String input) {
        try {
            return ResourceKey.resolve(input);
        } catch (final IllegalStateException ex) {
            return null;
        }
    }

}
