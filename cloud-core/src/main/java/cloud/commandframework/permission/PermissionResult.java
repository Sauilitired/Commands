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
package cloud.commandframework.permission;

import org.apiguardian.api.API;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * The cached result of a permission check, representing whether a command may be executed.
 * <p>
 * Implementations must be immutable. Most importantly, {@link #succeeded()} must always return the same value as previous
 * invocations.
 * <p>
 * Custom implementations may be used in order to provide more information.
 * For example, the reason that the permission lookup returned false.
 *
 * @since 2.0.0
 */
@API(status = API.Status.STABLE, since = "2.0.0")
public interface PermissionResult {

    /**
     * Returns true if the command may be executed
     *
     * @return true if the command may be executed
     */
    boolean succeeded();

    /**
     * Returns true if the command may not be executed
     *
     * @return true if the command may not be executed
     */
    default boolean failed() {
        return !this.succeeded();
    }

    /**
     * Returns the permission that this result came from
     *
     * @return the permission that this result came from
     */
    @NonNull CommandPermission permission();

    /**
     * Creates a result that wraps the given boolean result
     *
     * @param result true if the command may be executed, false otherwise
     * @param permission the permission that this result came from
     * @return a PermissionResult of the boolean result
     */
    static @NonNull PermissionResult of(boolean result, @NonNull CommandPermission permission) {
        return new SimplePermissionResult(result, permission);
    }

    /**
     * Creates a successful result for the given permission
     *
     * @param permission the permission that this result came from
     * @return a successful PermissionResult
     */
    static @NonNull PermissionResult succeeded(@NonNull CommandPermission permission) {
        return new SimplePermissionResult(true, permission);
    }

    /**
     * Creates a failed result for the given permission
     *
     * @param permission the permission that this result came from
     * @return a failed PermissionResult
     */
    static @NonNull PermissionResult failed(@NonNull CommandPermission permission) {
        return new SimplePermissionResult(false, permission);
    }

}
