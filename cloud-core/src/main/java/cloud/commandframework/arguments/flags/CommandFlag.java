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
package cloud.commandframework.arguments.flags;

import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.permission.CommandPermission;
import cloud.commandframework.permission.Permission;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A flag is an optional command argument that may have an associated parser,
 * and is identified by its name. Essentially, it's a mixture of a command literal
 * and an optional variable command argument.
 *
 * @param <T> Command argument type. {@link Void} is used when no argument is present.
 */
@SuppressWarnings("unused")
public final class CommandFlag<T> {

    private final @NonNull String name;
    private final @NonNull String @NonNull [] aliases;
    private final @NonNull ArgumentDescription description;
    private final @NonNull CommandPermission permission;

    private final @Nullable CommandArgument<?, T> commandArgument;

    private CommandFlag(
            final @NonNull String name,
            final @NonNull String @NonNull [] aliases,
            final @NonNull ArgumentDescription description,
            final @NonNull CommandPermission permission,
            final @Nullable CommandArgument<?, T> commandArgument
    ) {
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.aliases = Objects.requireNonNull(aliases, "aliases cannot be null");
        this.description = Objects.requireNonNull(description, "description cannot be null");
        this.permission = Objects.requireNonNull(permission, "permission cannot be null");
        this.commandArgument = commandArgument;
    }

    /**
     * Create a new flag builder
     *
     * @param name Flag name
     * @return Flag builder
     */
    public static @NonNull Builder<Void> newBuilder(final @NonNull String name) {
        return new Builder<>(name);
    }

    /**
     * Get the flag name
     *
     * @return Flag name
     */
    public @NonNull String getName() {
        return this.name;
    }

    /**
     * Get all flag aliases. This does not include the flag name
     *
     * @return Flag aliases
     */
    public @NonNull Collection<@NonNull String> getAliases() {
        return Arrays.asList(this.aliases);
    }

    /**
     * Get the flag description
     * <p>
     *
     * @return Flag description
     * @deprecated for removal since 1.4.0. Use {@link #getArgumentDescription()} instead.
     */
    @Deprecated
    public cloud.commandframework.@NonNull Description getDescription() {
        if (this.description instanceof cloud.commandframework.Description) {
            return ((cloud.commandframework.Description) this.description);
        } else {
            return cloud.commandframework.Description.of(this.description.getDescription());
        }
    }

    /**
     * Get the flag description.
     *
     * @return Flag description
     * @since 1.4.0
     */
    public @NonNull ArgumentDescription getArgumentDescription() {
        return this.description;
    }

    /**
     * Get the command argument, if it exists
     *
     * @return Command argument, or {@code null}
     */
    public @Nullable CommandArgument<?, T> getCommandArgument() {
        return this.commandArgument;
    }

    /**
     * Get the command permission, if it exists
     *
     * @return Command permission, or {@code null}
     * @since 1.6.0
     */
    public CommandPermission getCommandPermission() {
        return this.permission;
    }

    @Override
    public String toString() {
        return String.format("--%s", this.name);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || this.getClass() != o.getClass()) {
            return false;
        }
        final CommandFlag<?> that = (CommandFlag<?>) o;
        return this.getName().equals(that.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getName());
    }


    public static final class Builder<T> {

        private final String name;
        private final String[] aliases;
        private final ArgumentDescription description;
        private final CommandPermission permission;
        private final CommandArgument<?, T> commandArgument;

        private Builder(
                final @NonNull String name,
                final @NonNull String[] aliases,
                final @NonNull ArgumentDescription description,
                final @NonNull CommandPermission permission,
                final @Nullable CommandArgument<?, T> commandArgument
        ) {
            this.name = name;
            this.aliases = aliases;
            this.description = description;
            this.permission = permission;
            this.commandArgument = commandArgument;
        }

        private Builder(final @NonNull String name) {
            this(name, new String[0], ArgumentDescription.empty(), Permission.empty(), null);
        }

        /**
         * Create a new builder instance using the given flag aliases.
         * These may at most be one character in length
         *
         * @param aliases Flag aliases
         * @return New builder instance
         */
        public Builder<T> withAliases(final @NonNull String... aliases) {
            final Set<String> filteredAliases = new HashSet<>();
            for (final String alias : aliases) {
                if (alias.isEmpty()) {
                    continue;
                }
                if (alias.length() > 1) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Alias '%s' has name longer than one character. This is not allowed",
                                    alias
                            )
                    );
                }
                filteredAliases.add(alias);
            }
            return new Builder<>(
                    this.name,
                    filteredAliases.toArray(new String[0]),
                    this.description,
                    this.permission,
                    this.commandArgument
            );
        }

        /**
         * Create a new builder instance using the given flag description
         *
         * @param description Flag description
         * @return New builder instance
         * @deprecated for removal since 1.4.0. Use {@link #withDescription(ArgumentDescription)} instead.
         */
        @Deprecated
        public Builder<T> withDescription(final cloud.commandframework.@NonNull Description description) {
            return this.withDescription((ArgumentDescription) description);
        }

        /**
         * Create a new builder instance using the given flag description
         *
         * @param description Flag description
         * @return New builder instance
         * @since 1.4.0
         */
        public Builder<T> withDescription(final @NonNull ArgumentDescription description) {
            return new Builder<>(this.name, this.aliases, description, this.permission, this.commandArgument);
        }

        /**
         * Create a new builder instance using the given command argument
         *
         * @param argument Command argument
         * @param <N>      New argument type
         * @return New builder instance
         */
        public <N> Builder<N> withArgument(final @NonNull CommandArgument<?, N> argument) {
            return new Builder<>(this.name, this.aliases, this.description, this.permission, argument);
        }

        /**
         * Create a new builder instance using the given command argument
         *
         * @param builder Command argument builder. {@link CommandArgument.Builder#build()} will be invoked.
         * @param <N>     New argument type
         * @return New builder instance
         */
        public <N> Builder<N> withArgument(final CommandArgument.@NonNull Builder<?, N> builder) {
            return this.withArgument(builder.build());
        }

        /**
         * Create a new builder instance using the given flag permission
         *
         * @param permission Flag permission
         * @return New builder instance
         * @since 1.6.0
         */
        public Builder<T> withPermission(final @NonNull CommandPermission permission) {
            return new Builder<>(this.name, this.aliases, this.description, permission, this.commandArgument);
        }

        /**
         * Build a new command flag instance
         *
         * @return Constructed instance
         */
        public @NonNull CommandFlag<T> build() {
            return new CommandFlag<>(this.name, this.aliases, this.description, this.permission, this.commandArgument);
        }

    }

}
