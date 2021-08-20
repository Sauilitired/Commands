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
package cloud.commandframework.examples.bungee;

import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.Command;
import cloud.commandframework.CommandTree;
import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.bungee.BungeeCommandManager;
import cloud.commandframework.bungee.arguments.PlayerArgument;
import cloud.commandframework.bungee.arguments.ServerArgument;
import cloud.commandframework.execution.AsynchronousCommandExecutionCoordinator;
import cloud.commandframework.execution.CommandExecutionCoordinator;
import cloud.commandframework.extra.confirmation.CommandConfirmationManager;
import cloud.commandframework.meta.CommandMeta;
import cloud.commandframework.minecraft.extras.MinecraftExceptionHandler;
import cloud.commandframework.minecraft.extras.RichDescription;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.kyori.adventure.text.format.NamedTextColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static net.kyori.adventure.text.Component.text;

public final class ExamplePlugin extends Plugin {

    private BungeeCommandManager<CommandSender> manager;
    private BungeeAudiences bungeeAudiences;
    private CommandConfirmationManager<CommandSender> confirmationManager;


    @Override
    public void onEnable() {
        final Function<CommandTree<CommandSender>, CommandExecutionCoordinator<CommandSender>> executionCoordinatorFunction =
                AsynchronousCommandExecutionCoordinator.<CommandSender>newBuilder().build();

        final Function<CommandSender, CommandSender> mapperFunction = Function.identity();

        try {
            this.manager = new BungeeCommandManager<>(
                    this,
                    executionCoordinatorFunction,
                    mapperFunction,
                    mapperFunction
            );
        } catch (final Exception e) {
            this.getLogger().severe("Failed to initialize the command manager");
            return;
        }

        this.bungeeAudiences = BungeeAudiences.create(this);

        this.confirmationManager = new CommandConfirmationManager<>(
                30L,
                TimeUnit.SECONDS,
                context -> this.bungeeAudiences.sender(context.getCommandContext().getSender()).sendMessage(
                        text(
                                "Confirmation required. Confirm using /example confirm.", NamedTextColor.RED)),
                sender -> this.bungeeAudiences.sender(sender).sendMessage(
                        text("You do not have any pending commands.", NamedTextColor.RED))
        );

        this.confirmationManager.registerConfirmationProcessor(this.manager);

        new MinecraftExceptionHandler<CommandSender>()
                .withInvalidSyntaxHandler()
                .withInvalidSenderHandler()
                .withNoPermissionHandler()
                .withArgumentParsingHandler()
                .withCommandExecutionHandler()
                .withDecorator(component -> text()
                        .append(text("[", NamedTextColor.DARK_GRAY))
                        .append(text("Example", NamedTextColor.GOLD))
                        .append(text("] ", NamedTextColor.DARK_GRAY))
                        .append(component)
                        .build())
                .apply(this.manager, this.bungeeAudiences::sender);
        this.constructCommands();
    }

    private void constructCommands() {

        // Base command builder
        //
        final Command.Builder<CommandSender> builder = this.manager.commandBuilder("example");
        //
        // Add a confirmation command
        //
        this.manager.command(builder.literal("confirm")
                .meta(CommandMeta.DESCRIPTION, "Confirm a pending command")
                .handler(this.confirmationManager.createConfirmationExecutionHandler()));

        final CommandArgument<CommandSender, ProxiedPlayer> playerArgument = PlayerArgument.of("player");
        final CommandArgument<CommandSender, ServerInfo> serverArgument = ServerArgument.of("server");

        //
        // Create a player command
        //
        this.manager.command(
                this.manager.commandBuilder("player")
                        .senderType(ProxiedPlayer.class)
                        .argument(playerArgument, RichDescription.of(text("Player ").append(text("name", NamedTextColor.GOLD))))
                        .handler(context -> {
                            final ProxiedPlayer player = context.get("player");
                            this.bungeeAudiences.sender(context.getSender()).sendMessage(
                                    text("Selected ", NamedTextColor.GOLD)
                                            .append(text(player.getDisplayName(), NamedTextColor.AQUA))
                            );
                        })
        );

        //
        // Create a server command
        //
        this.manager.command(
                this.manager.commandBuilder("server")
                        .senderType(ProxiedPlayer.class)
                        .argument(serverArgument, ArgumentDescription.of("Server name"))
                        .handler(context -> {
                            final ServerInfo server = context.get("server");
                            this.bungeeAudiences.sender(context.getSender()).sendMessage(
                                    text("Selected ", NamedTextColor.GOLD)
                                            .append(text(server.getName(), NamedTextColor.AQUA))
                            );
                        })
        );
    }

}
