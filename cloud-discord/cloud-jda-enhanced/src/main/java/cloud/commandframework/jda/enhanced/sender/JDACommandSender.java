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
package cloud.commandframework.jda.enhanced.sender;

import net.dv8tion.jda.api.entities.ChannelType;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.checkerframework.checker.nullness.qual.NonNull;


/**
 *
 */
public interface JDACommandSender {

    /**
     * Create a JDA Command Sender from a {@link MessageReceivedEvent}
     *
     * @param event Message Received Event
     * @return Constructed JDA Command Sender
     */
    @SuppressWarnings("ClassReferencesSubclass")
    static @NonNull JDAMessageCommandSender of(final @NonNull MessageReceivedEvent event) {
        if (event.isFromType(ChannelType.PRIVATE)) {
            return new JDAPrivateMessageCommandSender(event);
        } else {
            return new JDAGuildMessageCommandSender(event);
        }
    }

    /**
     * Get the channel the user sent the message in
     *
     * @return Channel that the message was sent in
     */
    @NonNull MessageChannel getChannel();

    /**
     * Get the event that triggered this command
     *
     * @return The event that triggered this command
     */
    @NonNull GenericEvent getEvent();

    /**
     * Get the user the command sender represents
     *
     * @return User that sent the message
     */
    @NonNull User getUser();
}
