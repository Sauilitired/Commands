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

import cloud.commandframework.CommandManager;
import cloud.commandframework.CommandTree;
import cloud.commandframework.execution.CommandExecutionCoordinator;
import cloud.commandframework.internal.CommandRegistrationHandler;
import cloud.commandframework.jda.parsers.ChannelArgument;
import cloud.commandframework.jda.parsers.EmoteArgument;
import cloud.commandframework.jda.parsers.MemberArgument;
import cloud.commandframework.jda.parsers.RoleArgument;
import cloud.commandframework.jda.parsers.UserArgument;
import cloud.commandframework.jda.permission.BotPermissionPostProcessor;
import cloud.commandframework.jda.permission.UserPermissionPostProcessor;
import cloud.commandframework.meta.CommandMeta;
import cloud.commandframework.meta.SimpleCommandMeta;
import io.leangen.geantyref.TypeToken;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Emote;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Command manager for use with JDA
 *
 * @param <C> Command sender type
 * @deprecated Use {@link JDA4CommandManager}
 */
@Deprecated
public class JDACommandManager<C> extends CommandManager<C> {

    private final JDA jda;
    private final long botId;

    private final Function<@NonNull C, @NonNull String> prefixMapper;
    private final Function<@NonNull C, @NonNull List<String>> auxiliaryPrefixMapper;
    private final BiFunction<@NonNull C, @NonNull String, @NonNull Boolean> permissionMapper;
    private final Function<@NonNull MessageReceivedEvent, @NonNull C> commandSenderMapper;
    private final Function<@NonNull C, @NonNull MessageReceivedEvent> backwardsCommandSenderMapper;

    /**
     * Construct a new JDA Command Manager
     *
     * @param jda                          JDA instance to register against
     * @param prefixMapper                 Function that maps the sender to a command prefix string
     * @param permissionMapper             Function used to check if a command sender has the permission to execute a command
     * @param commandExecutionCoordinator  Execution coordinator instance. The coordinator is in charge of executing incoming
     *                                     commands. Some considerations must be made when picking a suitable execution coordinator
     *                                     for your platform. For example, an entirely asynchronous coordinator is not suitable
     *                                     when the parsers used in that particular platform are not thread safe. If you have
     *                                     commands that perform blocking operations, however, it might not be a good idea to
     *                                     use a synchronous execution coordinator. In most cases you will want to pick between
     *                                     {@link CommandExecutionCoordinator#simpleCoordinator()} and
     *                                     {@link cloud.commandframework.execution.AsynchronousCommandExecutionCoordinator}
     * @param commandSenderMapper          Function that maps {@link MessageReceivedEvent} to the command sender type
     * @param backwardsCommandSenderMapper Function that maps the command sender type to {@link MessageReceivedEvent}
     * @throws InterruptedException If the jda instance does not ready correctly
     */
    public JDACommandManager(
            final @NonNull JDA jda,
            final @NonNull Function<@NonNull C, @NonNull String> prefixMapper,
            final @Nullable BiFunction<@NonNull C, @NonNull String, @NonNull Boolean> permissionMapper,
            final @NonNull Function<CommandTree<C>, CommandExecutionCoordinator<C>> commandExecutionCoordinator,
            final @NonNull Function<@NonNull MessageReceivedEvent, @NonNull C> commandSenderMapper,
            final @NonNull Function<@NonNull C, @NonNull MessageReceivedEvent> backwardsCommandSenderMapper
    ) throws InterruptedException {
        this(
                jda,
                prefixMapper,
                (c) -> Collections.emptyList(),
                permissionMapper,
                commandExecutionCoordinator,
                commandSenderMapper,
                backwardsCommandSenderMapper
        );
    }

    /**
     * Construct a new JDA Command Manager
     *
     * @param jda                          JDA instance to register against
     * @param prefixMapper                 Function that maps the sender to a command prefix string
     * @param auxiliaryPrefixMapper        Auxiliary prefix mapper used as a workaround for breaking changes until 2.0 is
     *                                     released. This has been added because there may be instances where you'd like more
     *                                     than 1 prefix to be checked against.
     * @param permissionMapper             Function used to check if a command sender has the permission to execute a command
     * @param commandExecutionCoordinator  Execution coordinator instance. The coordinator is in charge of executing incoming
     *                                     commands. Some considerations must be made when picking a suitable execution coordinator
     *                                     for your platform. For example, an entirely asynchronous coordinator is not suitable
     *                                     when the parsers used in that particular platform are not thread safe. If you have
     *                                     commands that perform blocking operations, however, it might not be a good idea to
     *                                     use a synchronous execution coordinator. In most cases you will want to pick between
     *                                     {@link CommandExecutionCoordinator#simpleCoordinator()} and
     *                                     {@link cloud.commandframework.execution.AsynchronousCommandExecutionCoordinator}
     * @param commandSenderMapper          Function that maps {@link MessageReceivedEvent} to the command sender type
     * @param backwardsCommandSenderMapper Function that maps the command sender type to {@link MessageReceivedEvent}
     * @throws InterruptedException If the jda instance does not ready correctly
     */
    public JDACommandManager(
            final @NonNull JDA jda,
            final @NonNull Function<@NonNull C, @NonNull String> prefixMapper,
            final @NonNull Function<@NonNull C, @NonNull List<String>> auxiliaryPrefixMapper,
            final @Nullable BiFunction<@NonNull C, @NonNull String, @NonNull Boolean> permissionMapper,
            final @NonNull Function<CommandTree<C>, CommandExecutionCoordinator<C>> commandExecutionCoordinator,
            final @NonNull Function<@NonNull MessageReceivedEvent, @NonNull C> commandSenderMapper,
            final @NonNull Function<@NonNull C, @NonNull MessageReceivedEvent> backwardsCommandSenderMapper
    )
            throws InterruptedException {
        super(commandExecutionCoordinator, CommandRegistrationHandler.nullCommandRegistrationHandler());
        this.jda = jda;
        this.prefixMapper = prefixMapper;
        this.auxiliaryPrefixMapper = auxiliaryPrefixMapper;
        this.permissionMapper = permissionMapper;
        this.commandSenderMapper = commandSenderMapper;
        this.backwardsCommandSenderMapper = backwardsCommandSenderMapper;
        jda.addEventListener(new JDACommandListener<>(this));
        jda.awaitReady();
        this.botId = jda.getSelfUser().getIdLong();

        /* Register JDA Preprocessor */
        this.registerCommandPreProcessor(new JDACommandPreprocessor<>(this));

        /* Register JDA Command Postprocessors */
        this.registerCommandPostProcessor(new BotPermissionPostProcessor<>());
        this.registerCommandPostProcessor(new UserPermissionPostProcessor<>());

        /* Register JDA Parsers */
        this.getParserRegistry().registerParserSupplier(TypeToken.get(User.class), parserParameters ->
                new UserArgument.UserParser<>(
                        EnumSet.allOf(UserArgument.ParserMode.class),
                        UserArgument.Isolation.GLOBAL
                ));
        this.getParserRegistry().registerParserSupplier(TypeToken.get(Member.class), parserParameters ->
                new MemberArgument.MemberParser<>(
                        EnumSet.allOf(MemberArgument.ParserMode.class)
                ));
        this.getParserRegistry().registerParserSupplier(TypeToken.get(MessageChannel.class), parserParameters ->
                new ChannelArgument.MessageParser<>(
                        EnumSet.allOf(ChannelArgument.ParserMode.class)
                ));
        this.getParserRegistry().registerParserSupplier(TypeToken.get(Role.class), parserParameters ->
                new RoleArgument.RoleParser<>(
                        EnumSet.allOf(RoleArgument.ParserMode.class)
                ));
        this.getParserRegistry().registerParserSupplier(TypeToken.get(Emote.class), parserParameters ->
                new EmoteArgument.EmoteParser<>(
                        EnumSet.allOf(EmoteArgument.ParserMode.class)
                ));
    }

    @Override
    public final @NonNull CommandMeta createDefaultCommandMeta() {
        return SimpleCommandMeta.empty();
    }

    /**
     * Get the JDA instance
     *
     * @return JDA instance
     */
    public final @NonNull JDA getJDA() {
        return this.jda;
    }

    /**
     * Get the prefix mapper
     *
     * @return Prefix mapper
     */
    public final @NonNull Function<@NonNull C, @NonNull String> getPrefixMapper() {
        return this.prefixMapper;
    }

    /**
     * Get the auxiliary prefix mapper
     *
     * @return Auxiliary prefix mapper
     */
    public final @NonNull Function<C, List<String>> getAuxiliaryPrefixMapper() {
        return auxiliaryPrefixMapper;
    }

    /**
     * Get the command sender mapper
     *
     * @return Command sender mapper
     */
    public final @NonNull Function<@NonNull MessageReceivedEvent, @NonNull C> getCommandSenderMapper() {
        return this.commandSenderMapper;
    }

    /**
     * Get the backwards command sender plugin
     *
     * @return The backwards command sender mapper
     */
    public final @NonNull Function<@NonNull C, @NonNull MessageReceivedEvent> getBackwardsCommandSenderMapper() {
        return this.backwardsCommandSenderMapper;
    }

    /**
     * Get the bots discord id
     *
     * @return Bots discord id
     */
    public final long getBotId() {
        return this.botId;
    }

    @Override
    public final boolean hasPermission(final @NonNull C sender, final @NonNull String permission) {
        if (permission.isEmpty()) {
            return true;
        }

        if (this.permissionMapper != null) {
            return this.permissionMapper.apply(sender, permission);
        }

        final JDACommandSender jdaSender = this.backwardsCommandSenderMapper.andThen(JDACommandSender::of).apply(sender);

        if (!(jdaSender instanceof JDAGuildSender)) {
            return true;
        }

        final JDAGuildSender guildSender = (JDAGuildSender) jdaSender;

        return guildSender.getMember().hasPermission(Permission.valueOf(permission));
    }

}
