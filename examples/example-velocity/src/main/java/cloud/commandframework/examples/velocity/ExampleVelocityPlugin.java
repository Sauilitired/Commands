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
package cloud.commandframework.examples.velocity;

import cloud.commandframework.execution.CommandExecutionCoordinator;
import cloud.commandframework.minecraft.extras.AudienceProvider;
import cloud.commandframework.minecraft.extras.MinecraftExceptionHandler;
import cloud.commandframework.velocity.CloudInjectionModule;
import cloud.commandframework.velocity.VelocityCommandManager;
import cloud.commandframework.velocity.arguments.PlayerArgument;
import cloud.commandframework.velocity.arguments.ServerArgument;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.function.Function;

@Plugin(
        id = "example-plugin",
        name = "Cloud example plugin",
        authors = "Cloud Team",
        version = "1.6.0"
)
public final class ExampleVelocityPlugin {

    @Inject
    private Injector injector;

    /**
     * Listener that listeners for the initialization event
     *
     * @param event Initialization event
     */
    @Subscribe
    public void onProxyInitialization(final @NonNull ProxyInitializeEvent event) {
        final Injector childInjector = this.injector.createChildInjector(
                new CloudInjectionModule<>(
                        CommandSource.class,
                        CommandExecutionCoordinator.simpleCoordinator(),
                        Function.identity(),
                        Function.identity()
                )
        );
        final VelocityCommandManager<CommandSource> commandManager = childInjector.getInstance(
                Key.get(new TypeLiteral<VelocityCommandManager<CommandSource>>() {
                })
        );
        new MinecraftExceptionHandler<CommandSource>()
                .withDefaultHandlers()
                .withDecorator(component -> Component.text()
                        .append(Component.text('['))
                        .append(Component.text("cloud-velocity-example", TextColor.color(0x1CBAE0)))
                        .append(Component.text("] "))
                        .append(component)
                        .build())
                .apply(commandManager, AudienceProvider.nativeAudience());
        commandManager.command(
                commandManager.commandBuilder("example")
                        .argument(PlayerArgument.of("player"))
                        .handler(context -> {
                                    final Player player = context.get("player");
                                    context.getSender().sendMessage(
                                            Identity.nil(),
                                            Component.text().append(
                                                    Component.text("Selected ", NamedTextColor.GOLD)
                                            ).append(
                                                    Component.text(player.getUsername(), NamedTextColor.AQUA)
                                            ).build()
                                    );
                                }
                        )
        );
        commandManager.command(
                commandManager.commandBuilder("example-server")
                        .argument(ServerArgument.of("server"))
                        .handler(context -> {
                            final RegisteredServer server = context.get("server");
                            context.getSender().sendMessage(
                                    Identity.nil(),
                                    Component.text().append(
                                            Component.text("Selected ", NamedTextColor.GOLD)
                                    ).append(
                                            Component.text(server.getServerInfo().getName(), NamedTextColor.AQUA)
                                    ).build()
                            );
                        })
        );
    }

}
