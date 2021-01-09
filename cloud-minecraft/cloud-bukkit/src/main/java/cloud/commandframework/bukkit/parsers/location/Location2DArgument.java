//
// MIT License
//
// Copyright (c) 2020 Alexander Söderberg & Contributors
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
package cloud.commandframework.bukkit.parsers.location;

import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.parser.ArgumentParseResult;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.bukkit.parsers.location.LocationArgument.LocationParseException;
import cloud.commandframework.context.CommandContext;
import io.leangen.geantyref.TypeToken;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.BiFunction;

/**
 * Argument parser that parses {@link Location2D} from two doubles. This will use the command
 * senders world when it exists, or else it'll use the first loaded Bukkit world
 *
 * @param <C> Command sender type
 * @since 1.4.0
 */
public final class Location2DArgument<C> extends CommandArgument<C, Location2D> {

    private Location2DArgument(
            final boolean required,
            final @NonNull String name,
            final @NonNull String defaultValue,
            final @Nullable BiFunction<CommandContext<C>, String, List<String>> suggestionsProvider,
            final @NonNull Collection<@NonNull BiFunction<@NonNull CommandContext<C>,
                    @NonNull Queue<@NonNull String>, @NonNull ArgumentParseResult<Boolean>>> argumentPreprocessors
    ) {
        super(
                required,
                name,
                new Location2DParser<>(),
                defaultValue,
                TypeToken.get(Location2D.class),
                suggestionsProvider,
                argumentPreprocessors
        );
    }

    /**
     * Create a new argument builder
     *
     * @param name Argument name
     * @param <C>  Command sender type
     * @return Builder instance
     */
    public static <C> @NonNull Builder<C> newBuilder(
            final @NonNull String name
    ) {
        return new Builder<>(name);
    }

    /**
     * Create a new required argument
     *
     * @param name Argument name
     * @param <C>  Command sender type
     * @return Constructed argument
     */
    public static <C> @NonNull CommandArgument<C, Location2D> of(
            final @NonNull String name
    ) {
        return Location2DArgument.<C>newBuilder(
                name
        ).asRequired().build();
    }

    /**
     * Create a new optional argument
     *
     * @param name Argument name
     * @param <C>  Command sender type
     * @return Constructed argument
     */
    public static <C> @NonNull CommandArgument<C, Location2D> optional(
            final @NonNull String name
    ) {
        return Location2DArgument.<C>newBuilder(
                name
        ).asOptional().build();
    }


    public static final class Builder<C> extends CommandArgument.Builder<C, Location2D> {

        private Builder(
                final @NonNull String name
        ) {
            super(
                    TypeToken.get(Location2D.class),
                    name
            );
        }

        @Override
        public @NonNull CommandArgument<@NonNull C, @NonNull Location2D> build() {
            return new Location2DArgument<>(
                    this.isRequired(),
                    this.getName(),
                    this.getDefaultValue(),
                    this.getSuggestionsProvider(),
                    new LinkedList<>()
            );
        }

    }


    public static final class Location2DParser<C> implements ArgumentParser<C, Location2D> {

        private static final int EXPECTED_PARAMETER_COUNT = 2;

        private final LocationCoordinateParser<C> locationCoordinateParser = new LocationCoordinateParser<>();

        @Override
        public @NonNull ArgumentParseResult<@NonNull Location2D> parse(
                final @NonNull CommandContext<@NonNull C> commandContext,
                final @NonNull Queue<@NonNull String> inputQueue
        ) {
            if (inputQueue.size() < 2) {
                final StringBuilder input = new StringBuilder();
                for (int i = 0; i < inputQueue.size(); i++) {
                    input.append(((LinkedList<String>) inputQueue).get(i));
                }
                return ArgumentParseResult.failure(
                        new LocationParseException(
                                commandContext,
                                LocationParseException.FailureReason.WRONG_FORMAT,
                                input.toString()
                        )
                );
            }
            final LocationCoordinate[] coordinates = new LocationCoordinate[2];
            for (int i = 0; i < 2; i++) {
                final ArgumentParseResult<@NonNull LocationCoordinate> coordinate = this.locationCoordinateParser.parse(
                        commandContext,
                        inputQueue
                );
                if (coordinate.getFailure().isPresent()) {
                    return ArgumentParseResult.failure(
                            coordinate.getFailure().get()
                    );
                }
                coordinates[i] = coordinate.getParsedValue().orElseThrow(NullPointerException::new);
            }
            final Location originalLocation;
            final CommandSender bukkitSender = commandContext.get("BukkitCommandSender");

            if (bukkitSender instanceof BlockCommandSender) {
                originalLocation = ((BlockCommandSender) bukkitSender).getBlock().getLocation();
            } else if (bukkitSender instanceof Entity) {
                originalLocation = ((Entity) bukkitSender).getLocation();
            } else {
                originalLocation = new Location(Bukkit.getWorlds().get(0), 0, 0, 0);
            }

            if (coordinates[0].getType() == LocationCoordinateType.LOCAL && coordinates[1].getType() != LocationCoordinateType.LOCAL) {
                return ArgumentParseResult.failure(
                        new LocationParseException(
                                commandContext,
                                LocationParseException.FailureReason.MIXED_LOCAL_ABSOLUTE,
                                ""
                        )
                );
            }

            if (coordinates[0].getType() == LocationCoordinateType.ABSOLUTE) {
                originalLocation.setX(coordinates[0].getCoordinate());
            } else if (coordinates[0].getType() == LocationCoordinateType.RELATIVE) {
                originalLocation.add(coordinates[0].getCoordinate(), 0, 0);
            }

            if (coordinates[1].getType() == LocationCoordinateType.ABSOLUTE) {
                originalLocation.setZ(coordinates[1].getCoordinate());
            } else if (coordinates[1].getType() == LocationCoordinateType.RELATIVE) {
                originalLocation.add(0, 0, coordinates[1].getCoordinate());
            } else {
                final double multiplier = 0.017453292D;
                final double f = Math.cos((originalLocation.getYaw() + 90.0F) * multiplier);
                final double f1 = Math.sin((originalLocation.getY() + 90.0F) * multiplier);
                final double f2 = Math.cos(-originalLocation.getPitch() * multiplier);
                final double f3 = Math.sin(-originalLocation.getPitch() * multiplier);
                final double f4 = Math.cos((-originalLocation.getPitch() + 90.0F) * multiplier);
                final double f5 = Math.sin((-originalLocation.getPitch() + 90.0F) * multiplier);
                final Vector vec1 = new Vector(f * f2, f3, f1 * f2);
                final Vector vec2 = new Vector(f * f4, f5, f1 * f4);
                final Vector vec3 = vec1.crossProduct(vec2).multiply(-1);
                final Vector vec4 = new Vector(
                        vec1.getX() * vec2.getX() * coordinates[1].getCoordinate() + vec3.getX() * coordinates[0].getCoordinate(),
                        0,
                        vec1.getZ() * coordinates[1].getCoordinate() + vec2.getZ() * coordinates[1].getCoordinate()
                                + vec3.getZ() * coordinates[0].getCoordinate()
                );
                originalLocation.add(vec4);
            }

            return ArgumentParseResult.success(Location2D.from(
                    originalLocation.getWorld(),
                    originalLocation.getX(),
                    originalLocation.getZ()
            ));
        }

        @Override
        public @NonNull List<@NonNull String> suggestions(
                final @NonNull CommandContext<C> commandContext,
                final @NonNull String input
        ) {
            return LocationArgument.LocationParser.getSuggestions(commandContext, input);
        }

        @Override
        public int getRequestedArgumentCount() {
            return EXPECTED_PARAMETER_COUNT;
        }

    }

}
