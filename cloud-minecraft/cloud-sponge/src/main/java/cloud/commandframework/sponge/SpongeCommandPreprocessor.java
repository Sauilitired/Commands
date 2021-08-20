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

import cloud.commandframework.brigadier.argument.WrappedBrigadierParser;
import cloud.commandframework.execution.preprocessor.CommandPreprocessingContext;
import cloud.commandframework.execution.preprocessor.CommandPreprocessor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.api.command.CommandCause;

/**
 * Command preprocessor which decorates incoming {@link cloud.commandframework.context.CommandContext}
 * with Sponge specific objects
 *
 * @param <C> Command sender type
 */
final class SpongeCommandPreprocessor<C> implements CommandPreprocessor<C> {

    private final SpongeCommandManager<C> commandManager;

    /**
     * The Sponge Command Preprocessor for storing Sponge-specific contexts in the command contexts
     *
     * @param commandManager command manager
     */
    SpongeCommandPreprocessor(final @NonNull SpongeCommandManager<C> commandManager) {
        this.commandManager = commandManager;
    }

    @Override
    public void accept(final @NonNull CommandPreprocessingContext<C> context) {
        final CommandCause commandCause = this.commandManager.causeMapper().apply(context.getCommandContext().getSender());
        context.getCommandContext().store(SpongeCommandContextKeys.COMMAND_CAUSE, commandCause);

        // For WrappedBrigadierParser. The CloudBrigadierManager will store this in context as well, however we are not using
        // the CloudBrigadierManager, only the WrapperBrigadierParser. CommandCause is mixed into CommandSourceStack, which is
        // Minecraft's native Brigadier sender type on the server.
        context.getCommandContext().store(WrappedBrigadierParser.COMMAND_CONTEXT_BRIGADIER_NATIVE_SENDER, commandCause);
    }

}
