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
package cloud.commandframework.sponge;

import cloud.commandframework.CommandTree;
import cloud.commandframework.execution.CommandExecutionCoordinator;
import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.util.Types;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.api.command.CommandCause;

import java.lang.reflect.Type;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * Injection module that allows for {@link SpongeCommandManager} to be injectable.
 *
 * @param <C> Command sender type
 */
public final class CloudInjectionModule<C> extends AbstractModule {

    private final Class<C> commandSenderType;
    private final Function<@NonNull CommandTree<C>, @NonNull CommandExecutionCoordinator<C>> commandExecutionCoordinator;
    private final Function<@NonNull C, @NonNull CommandCause> causeMapper;
    private final Function<@NonNull CommandCause, @NonNull C> backwardsCauseMapper;

    /**
     * Create a new injection module.
     *
     * @param commandSenderType           Your command sender type
     * @param commandExecutionCoordinator Command execution coordinator
     * @param causeMapper                 Function mapping the custom command sender type to a Sponge CommandCause
     * @param backwardsCauseMapper        Function mapping Sponge CommandCause to the custom command sender type
     */
    public CloudInjectionModule(
            final @NonNull Class<C> commandSenderType,
            final @NonNull Function<@NonNull CommandTree<C>, @NonNull CommandExecutionCoordinator<C>> commandExecutionCoordinator,
            final @NonNull Function<@NonNull C, @NonNull CommandCause> causeMapper,
            final @NonNull Function<@NonNull CommandCause, @NonNull C> backwardsCauseMapper
    ) {
        this.commandSenderType = commandSenderType;
        this.commandExecutionCoordinator = commandExecutionCoordinator;
        this.causeMapper = causeMapper;
        this.backwardsCauseMapper = backwardsCauseMapper;
    }

    /**
     * Create a new injection module using Sponge's {@link CommandCause} as the sender type.
     *
     * @param commandExecutionCoordinator Command execution coordinator
     * @return new injection module
     */
    public static @NonNull CloudInjectionModule<@NonNull CommandCause> createNative(
            final @NonNull Function<@NonNull CommandTree<@NonNull CommandCause>,
                    @NonNull CommandExecutionCoordinator<@NonNull CommandCause>> commandExecutionCoordinator
    ) {
        return new CloudInjectionModule<>(
                CommandCause.class,
                commandExecutionCoordinator,
                UnaryOperator.identity(),
                UnaryOperator.identity()
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    protected void configure() {
        final Type commandTreeType = Types.newParameterizedType(CommandTree.class, this.commandSenderType);
        final Type commandExecutionCoordinatorType = Types.newParameterizedType(
                CommandExecutionCoordinator.class, this.commandSenderType
        );
        final Type executorFunction = Types.newParameterizedType(
                Function.class, commandTreeType, commandExecutionCoordinatorType
        );
        final Key executorFunctionKey = Key.get(executorFunction);
        this.bind(executorFunctionKey).toInstance(this.commandExecutionCoordinator);

        final Type commandSenderMapperFunction = Types.newParameterizedType(
                Function.class, this.commandSenderType, CommandCause.class
        );
        final Key commandSenderMapperFunctionKey = Key.get(commandSenderMapperFunction);
        this.bind(commandSenderMapperFunctionKey).toInstance(this.causeMapper);

        final Type backwardsCommandSenderMapperFunction = Types.newParameterizedType(
                Function.class, CommandCause.class, this.commandSenderType
        );
        final Key backwardsCommandSenderMapperFunctionKey = Key.get(backwardsCommandSenderMapperFunction);
        this.bind(backwardsCommandSenderMapperFunctionKey).toInstance(this.backwardsCauseMapper);
    }

}
