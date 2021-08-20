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
import cloud.commandframework.arguments.CommandArgument;
import cloud.commandframework.arguments.StaticArgument;
import cloud.commandframework.arguments.compound.CompoundArgument;
import cloud.commandframework.arguments.parser.ArgumentParser;
import cloud.commandframework.exceptions.ArgumentParseException;
import cloud.commandframework.exceptions.CommandExecutionException;
import cloud.commandframework.exceptions.InvalidCommandSenderException;
import cloud.commandframework.exceptions.InvalidSyntaxException;
import cloud.commandframework.exceptions.NoPermissionException;
import cloud.commandframework.exceptions.NoSuchCommandException;
import cloud.commandframework.permission.CommandPermission;
import cloud.commandframework.permission.Permission;
import cloud.commandframework.types.tuples.Pair;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.util.ComponentMessageThrowable;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.api.command.Command;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.CommandCompletion;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.parameter.ArgumentReader;
import org.spongepowered.api.command.registrar.tree.CommandTreeNode;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

final class CloudSpongeCommand<C> implements Command.Raw {

    private static final Component NULL = Component.text("null");
    private static final Component MESSAGE_INTERNAL_ERROR =
            text("An internal error occurred while attempting to perform this command.", RED);
    private static final Component MESSAGE_NO_PERMS =
            text("I'm sorry, but you do not have permission to perform this command. "
                    + "Please contact the server administrators if you believe that this is in error.", RED);
    private static final Component MESSAGE_UNKNOWN_COMMAND = text("Unknown command. Type \"/help\" for help.");

    private final SpongeCommandManager<C> commandManager;
    private final String label;

    CloudSpongeCommand(
            final @NonNull String label,
            final @NonNull SpongeCommandManager<C> commandManager
    ) {
        this.label = label;
        this.commandManager = commandManager;
    }

    @Override
    public CommandResult process(final @NonNull CommandCause cause, final ArgumentReader.@NonNull Mutable arguments) {
        final C cloudSender = this.commandManager.backwardsCauseMapper().apply(cause);
        final Audience audience = cause.audience();
        final String input = this.formatCommandForParsing(arguments.input());
        this.commandManager.executeCommand(cloudSender, input).whenComplete((result, throwable) -> {
            if (throwable == null) {
                return;
            }
            if (throwable instanceof CompletionException) {
                throwable = throwable.getCause();
            }
            this.handleCommandException(cloudSender, audience, throwable);
        });
        return CommandResult.success();
    }

    private void handleCommandException(final C cloudSender, final Audience audience, final Throwable throwable) {
        if (throwable instanceof InvalidSyntaxException) {
            this.commandManager.handleException(
                    cloudSender,
                    InvalidSyntaxException.class,
                    (InvalidSyntaxException) throwable,
                    (c, e) -> audience.sendMessage(TextComponent.ofChildren(
                            text("Invalid Command Syntax. Correct command syntax is: ", RED),
                            text("/" + e.getCorrectSyntax(), GRAY)
                    ))
            );
        } else if (throwable instanceof InvalidCommandSenderException) {
            this.commandManager.handleException(
                    cloudSender,
                    InvalidCommandSenderException.class,
                    (InvalidCommandSenderException) throwable,
                    (c, e) -> audience.sendMessage(text(throwable.getMessage(), RED))
            );
        } else if (throwable instanceof NoPermissionException) {
            this.commandManager.handleException(
                    cloudSender,
                    NoPermissionException.class,
                    (NoPermissionException) throwable,
                    (c, e) -> audience.sendMessage(MESSAGE_NO_PERMS)
            );
        } else if (throwable instanceof NoSuchCommandException) {
            this.commandManager.handleException(
                    cloudSender,
                    NoSuchCommandException.class,
                    (NoSuchCommandException) throwable,
                    (c, e) -> audience.sendMessage(MESSAGE_UNKNOWN_COMMAND)
            );
        } else if (throwable instanceof ArgumentParseException) {
            this.commandManager.handleException(
                    cloudSender,
                    ArgumentParseException.class,
                    (ArgumentParseException) throwable,
                    (c, e) -> audience.sendMessage(TextComponent.ofChildren(
                            text("Invalid Command Argument: ", RED),
                            getMessage(throwable.getCause()).colorIfAbsent(GRAY)
                    ))
            );
        } else if (throwable instanceof CommandExecutionException) {
            this.commandManager.handleException(
                    cloudSender,
                    CommandExecutionException.class,
                    (CommandExecutionException) throwable,
                    (c, e) -> {
                        audience.sendMessage(MESSAGE_INTERNAL_ERROR);
                        this.commandManager.owningPluginContainer().logger()
                                .error("Exception executing command handler", throwable.getCause());
                    }
            );
        } else {
            audience.sendMessage(MESSAGE_INTERNAL_ERROR);
            this.commandManager.owningPluginContainer().logger()
                    .error("An unhandled exception was thrown during command execution", throwable);
        }
    }

    private static Component getMessage(final Throwable throwable) {
        final Component msg = ComponentMessageThrowable.getOrConvertMessage(throwable);
        return msg == null ? NULL : msg;
    }

    @Override
    public List<CommandCompletion> complete(
            final @NonNull CommandCause cause,
            final ArgumentReader.@NonNull Mutable arguments
    ) {
        return this.commandManager.suggest(
                this.commandManager.backwardsCauseMapper().apply(cause),
                this.formatCommandForSuggestions(arguments.input())
        ).stream().map(CommandCompletion::of).collect(Collectors.toList());
    }

    @Override
    public boolean canExecute(final @NonNull CommandCause cause) {
        final CommandTree.Node<CommandArgument<C, ?>> rootNode = this.commandManager.getCommandTree().getNamedNode(this.label);
        return this.commandManager.hasPermission(
                this.commandManager.backwardsCauseMapper().apply(cause),
                (CommandPermission) rootNode.getNodeMeta().getOrDefault("permission", Permission.empty())
        );
    }

    @Override
    public Optional<Component> shortDescription(final CommandCause cause) {
        // todo
        return Optional.of(text("short desc!"));
    }

    @Override
    public Optional<Component> extendedDescription(final CommandCause cause) {
        // todo
        return Optional.of(text("long desc!"));
    }

    @Override
    public Optional<Component> help(final @NonNull CommandCause cause) {
        // todo
        return Raw.super.help(cause);
    }

    @Override
    public Component usage(final CommandCause cause) {
        return text(this.commandManager.getCommandSyntaxFormatter().apply(
                Collections.emptyList(),
                this.commandManager.getCommandTree().getNamedNode(this.label)
        ));
    }

    @Override
    public CommandTreeNode.Root commandTree() {
        final CommandTreeNode<CommandTreeNode.Root> root = CommandTreeNode.root();

        final CommandTree.Node<CommandArgument<C, ?>> cloud = this.commandManager
                .getCommandTree()
                .getNamedNode(this.label);

        if (cloud.isLeaf() || cloud.getValue().getOwningCommand() != null) {
            root.executable();
        }

        this.addRequirement(cloud, root);

        this.addChildren(root, cloud);
        return (CommandTreeNode.Root) root;
    }

    @SuppressWarnings("unchecked")
    private void addChildren(final CommandTreeNode<?> node, final CommandTree.Node<CommandArgument<C, ?>> cloud) {
        for (final CommandTree.Node<CommandArgument<C, ?>> child : cloud.getChildren()) {
            final CommandArgument<C, ?> value = child.getValue();
            final CommandTreeNode.Argument<? extends CommandTreeNode.Argument<?>> treeNode;
            if (value instanceof StaticArgument) {
                treeNode = (CommandTreeNode.Argument<? extends CommandTreeNode.Argument<?>>) CommandTreeNode.literal();
            } else if (value instanceof CompoundArgument) {
                final CompoundArgument<?, C, ?> compound = (CompoundArgument<?, C, ?>) value;
                this.handleCompoundArgument(node, child, compound);
                continue;
            } else {
                treeNode = this.commandManager.parserMapper().mapArgument(value);
            }
            this.addRequirement(child, treeNode);
            if (canExecute(child)) {
                treeNode.executable();
            }
            this.addChildren(treeNode, child);
            node.child(value.getName(), treeNode);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleCompoundArgument(
            final CommandTreeNode<?> node,
            final CommandTree.Node<CommandArgument<C, ?>> child,
            final CompoundArgument<?, C, ?> compound
    ) {
        final CommandTreeNode.Argument<? extends CommandTreeNode.Argument<?>> treeNode;
        final Object[] names = compound.getNames().toArray();
        final Object[] parsers = compound.getParserTuple().toArray();
        final ArrayDeque<Pair<String, CommandTreeNode.Argument<? extends CommandTreeNode.Argument<?>>>> nodes = new ArrayDeque<>();
        for (int i = 0; i < parsers.length; i++) {
            final ArgumentParser<C, Object> parser = (ArgumentParser<C, Object>) parsers[i];
            final String name = (String) names[i];
            nodes.add(Pair.of(name, this.commandManager.parserMapper().mapParser(parser)));
        }
        Pair<String, CommandTreeNode.Argument<? extends CommandTreeNode.Argument<?>>> argument = null;
        while (!nodes.isEmpty()) {
            final Pair<String, CommandTreeNode.Argument<? extends CommandTreeNode.Argument<?>>> prev = argument;
            argument = nodes.removeLast();
            if (prev != null) {
                argument.getSecond().child(prev.getFirst(), prev.getSecond());
            } else {
                // last node
                if (canExecute(child)) {
                    argument.getSecond().executable();
                }
            }
            this.addRequirement(child, argument.getSecond());
        }
        treeNode = argument.getSecond();
        this.addChildren(treeNode, child);
        node.child(names[0].toString(), treeNode);
    }

    private static <C> boolean canExecute(final CommandTree.@NonNull Node<CommandArgument<C, ?>> node) {
        return node.isLeaf()
                || !node.getValue().isRequired()
                || node.getValue().getOwningCommand() != null
                || node.getChildren().stream().noneMatch(c -> c.getValue().isRequired());
    }

    private void addRequirement(
            final CommandTree.@NonNull Node<CommandArgument<C, ?>> cloud,
            final @NonNull CommandTreeNode<? extends CommandTreeNode<?>> node
    ) {
        final CommandPermission permission = (CommandPermission) cloud.getNodeMeta()
                .getOrDefault("permission", Permission.empty());
        if (permission == Permission.empty()) {
            return;
        }
        node.requires(cause ->
                this.commandManager.hasPermission(this.commandManager.backwardsCauseMapper().apply(cause), permission));
    }

    private String formatCommandForParsing(final @NonNull String arguments) {
        if (arguments.isEmpty()) {
            return this.label;
        }
        return this.label + " " + arguments;
    }

    private String formatCommandForSuggestions(final @NonNull String arguments) {
        return this.label + " " + arguments;
    }

}
