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
package cloud.commandframework.jda;

import cloud.commandframework.execution.preprocessor.CommandPreprocessingContext;
import cloud.commandframework.execution.preprocessor.CommandPreprocessor;
import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Command preprocessor which decorates incoming {@link cloud.commandframework.context.CommandContext}
 * with Bukkit specific objects
 *
 * @param <C> Command sender type
 */
@SuppressWarnings("deprecation")
final class JDACommandPreprocessor<C> implements CommandPreprocessor<C> {

    private final JDACommandManager<C> mgr;

    /**
     * The JDA Command Preprocessor for storing JDA-specific contexts in the command contexts
     *
     * @param mgr The JDACommandManager
     */
    JDACommandPreprocessor(final @NonNull JDACommandManager<C> mgr) {
        this.mgr = mgr;
    }

    /**
     * Stores the {@link net.dv8tion.jda.api.JDA} in the context with the key "JDA",
     * the {@link net.dv8tion.jda.api.events.message.MessageReceivedEvent} with the key "MessageReceivedEvent", and
     * the {@link net.dv8tion.jda.api.entities.MessageChannel} with the key "MessageChannel".
     * <p>
     * If the message was sent in a guild, the {@link net.dv8tion.jda.api.entities.Guild} will be stored in the context with the
     * key "Guild". If the message was also sent in a text channel, the {@link net.dv8tion.jda.api.entities.TextChannel} will be
     * stored in the context with the key "TextChannel".
     * <p>
     * If the message was sent in a DM instead of in a guild, the {@link net.dv8tion.jda.api.entities.PrivateChannel} will be
     * stored in the context with the key "PrivateChannel".
     */
    @Override
    public void accept(final @NonNull CommandPreprocessingContext<C> context) {
        context.getCommandContext().store("JDA", this.mgr.getJDA());

        MessageReceivedEvent event;
        try {
            event = this.mgr.getBackwardsCommandSenderMapper().apply(context.getCommandContext().getSender());
        } catch (IllegalStateException e) {
            // The event could not be resolved from the backwards command sender mapper
            return;
        }

        context.getCommandContext().store("MessageReceivedEvent", event);
        context.getCommandContext().store("MessageChannel", event.getChannel());
        context.getCommandContext().store("Message", event.getMessage());

        if (event.isFromGuild()) {
            Guild guild = event.getGuild();
            context.getCommandContext().store("Guild", guild);

            if (event.isFromType(ChannelType.TEXT)) {
                context.getCommandContext().store("TextChannel", event.getTextChannel());
            }
        } else if (event.isFromType(ChannelType.PRIVATE)) {
            context.getCommandContext().store("PrivateChannel", event.getPrivateChannel());
        }
    }

}
