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
package cloud.commandframework.kotlin

import cloud.commandframework.ArgumentDescription
import cloud.commandframework.Command
import cloud.commandframework.CommandManager
import cloud.commandframework.Description
import cloud.commandframework.arguments.CommandArgument
import cloud.commandframework.execution.CommandExecutionHandler
import cloud.commandframework.kotlin.extension.command
import cloud.commandframework.kotlin.extension.senderType
import cloud.commandframework.meta.CommandMeta
import cloud.commandframework.permission.CommandPermission
import kotlin.reflect.KClass

/**
 * A mutable [Command.Builder] wrapper, providing functions to assist in creating commands using the
 * Kotlin builder DSL style
 *
 * @since 1.3.0
 */
public class MutableCommandBuilder<C : Any> {
    private val commandManager: CommandManager<C>
    private var commandBuilder: Command.Builder<C>

    /**
     * Create a new [MutableCommandBuilder]
     *
     * @param name name for the root command node
     * @param description description for the root command node
     * @param aliases aliases for the root command node
     * @param commandManager the command manager which will own this command
     * @since 1.3.0
     */
    @Suppress("DEPRECATION")
    @Deprecated(
        message = "ArgumentDescription should be used over Description",
        level = DeprecationLevel.HIDDEN)
    public constructor(
        name: String,
        description: Description = Description.empty(),
        aliases: Array<String> = emptyArray(),
        commandManager: CommandManager<C>
    ) {
        this.commandManager = commandManager
        this.commandBuilder = commandManager.commandBuilder(name, description, *aliases)
    }

    /**
     * Create a new [MutableCommandBuilder]
     *
     * @param name name for the root command node
     * @param description description for the root command node
     * @param aliases aliases for the root command node
     * @param commandManager the command manager which will own this command
     * @since 1.4.0
     */
    public constructor(
        name: String,
        description: ArgumentDescription = ArgumentDescription.empty(),
        aliases: Array<String> = emptyArray(),
        commandManager: CommandManager<C>
    ) {
        this.commandManager = commandManager
        this.commandBuilder = commandManager.commandBuilder(name, description, *aliases)
    }

    /**
     * Create a new [MutableCommandBuilder] and invoke the provided receiver lambda on it
     *
     * @param name name for the root command node
     * @param description description for the root command node
     * @param aliases aliases for the root command node
     * @param commandManager the command manager which will own this command
     * @param lambda receiver lambda which will be invoked on the new builder
     * @since 1.3.0
     */
    @Suppress("DEPRECATION")
    @Deprecated(
        message = "ArgumentDescription should be used over Description",
        level = DeprecationLevel.HIDDEN)
    public constructor(
        name: String,
        description: Description = Description.empty(),
        aliases: Array<String> = emptyArray(),
        commandManager: CommandManager<C>,
        lambda: MutableCommandBuilder<C>.() -> Unit
    ) : this(name, description, aliases, commandManager) {
        lambda(this)
    }

    /**
     * Create a new [MutableCommandBuilder] and invoke the provided receiver lambda on it
     *
     * @param name name for the root command node
     * @param description description for the root command node
     * @param aliases aliases for the root command node
     * @param commandManager the command manager which will own this command
     * @param lambda receiver lambda which will be invoked on the new builder
     * @since 1.4.0
     */
    public constructor(
        name: String,
        description: ArgumentDescription = ArgumentDescription.empty(),
        aliases: Array<String> = emptyArray(),
        commandManager: CommandManager<C>,
        lambda: MutableCommandBuilder<C>.() -> Unit
    ) : this(name, description, aliases, commandManager) {
        lambda(this)
    }

    private constructor(commandManager: CommandManager<C>, commandBuilder: Command.Builder<C>) {
        this.commandManager = commandManager
        this.commandBuilder = commandBuilder
    }

    /**
     * Build a [Command] from the current state of this builder
     *
     * @return built command
     * @since 1.3.0
     */
    public fun build(): Command<C> = this.commandBuilder.build()

    /**
     * Invoke the provided receiver lambda on this builder, then build a [Command] from the
     * resulting state
     *
     * @param lambda receiver lambda which will be invoked on builder before building
     * @return built command
     * @since 1.3.0
     */
    public fun build(lambda: MutableCommandBuilder<C>.() -> Unit): Command<C> {
        lambda(this)
        return this.commandBuilder.build()
    }

    /**
     * Modify this [MutableCommandBuilder]'s internal [Command.Builder] with a unary function
     *
     * @param mutator mutator function
     * @return this mutable builder
     * @since 1.3.0
     */
    public fun mutate(
        mutator: (Command.Builder<C>) -> Command.Builder<C>
    ): MutableCommandBuilder<C> {
        this.commandBuilder = mutator(this.commandBuilder)
        return this
    }

    private fun onlyMutate(mutator: (Command.Builder<C>) -> Command.Builder<C>): Unit {
        mutate(mutator)
    }

    /**
     * Make a new copy of this [MutableCommandBuilder]
     *
     * @return a copy of this mutable builder
     * @since 1.3.0
     */
    public fun copy(): MutableCommandBuilder<C> =
        MutableCommandBuilder(this.commandManager, this.commandBuilder)

    /**
     * Make a new copy of this [MutableCommandBuilder] and invoke the provided receiver lambda on it
     *
     * @param lambda receiver lambda which will be invoked on the new builder
     * @return a copy of this mutable builder
     * @since 1.3.0
     */
    public fun copy(lambda: MutableCommandBuilder<C>.() -> Unit): MutableCommandBuilder<C> =
        copy().apply { lambda(this) }

    /**
     * Make a new copy of this [MutableCommandBuilder], append a literal, and invoke the provided
     * receiver lambda on it
     *
     * @param literal name for the literal
     * @param description description for the literal
     * @param lambda receiver lambda which will be invoked on the new builder
     * @return a copy of this mutable builder
     * @since 1.3.0
     */
    @Suppress("DEPRECATION")
    @Deprecated(
        message = "ArgumentDescription should be used over Description",
        level = DeprecationLevel.HIDDEN)
    public fun copy(
        literal: String,
        description: Description,
        lambda: MutableCommandBuilder<C>.() -> Unit
    ): MutableCommandBuilder<C> =
        copy().apply {
            literal(literal, description)
            lambda(this)
        }

    /**
     * Make a new copy of this [MutableCommandBuilder], append a literal, and invoke the provided
     * receiver lambda on it
     *
     * @param literal name for the literal
     * @param description description for the literal
     * @param lambda receiver lambda which will be invoked on the new builder
     * @return a copy of this mutable builder
     * @since 1.4.0
     */
    public fun copy(
        literal: String,
        description: ArgumentDescription,
        lambda: MutableCommandBuilder<C>.() -> Unit
    ): MutableCommandBuilder<C> =
        copy().apply {
            literal(literal, description)
            lambda(this)
        }

    /**
     * Make a new copy of this [MutableCommandBuilder], append a literal, and invoke the provided
     * receiver lambda on it
     *
     * @param literal name for the literal
     * @param lambda receiver lambda which will be invoked on the new builder
     * @return a copy of this mutable builder
     * @since 1.3.0
     */
    public fun copy(
        literal: String,
        lambda: MutableCommandBuilder<C>.() -> Unit
    ): MutableCommandBuilder<C> =
        copy().apply {
            literal(literal)
            lambda(this)
        }

    /**
     * Build and register this command with the owning command manager
     *
     * @return this mutable builder
     * @see [CommandManager.command]
     * @since 1.3.0
     */
    public fun register(): MutableCommandBuilder<C> = apply { this.commandManager.command(this) }

    /**
     * Create a new copy of this mutable builder, act on it with a receiver lambda, and then
     * register it with the owning command manager
     *
     * @param lambda receiver lambda which will be invoked on the new builder
     * @return the new mutable builder
     * @see [CommandManager.command]
     * @since 1.3.0
     */
    public fun registerCopy(lambda: MutableCommandBuilder<C>.() -> Unit): MutableCommandBuilder<C> =
        copy(lambda).register()

    /**
     * Create a new copy of this mutable builder, append a literal, act on it with a receiver
     * lambda, and then register it with the owning command manager
     *
     * @param literal name for the literal
     * @param lambda receiver lambda which will be invoked on the new builder
     * @return the new mutable builder
     * @see [CommandManager.command]
     * @since 1.3.0
     */
    public fun registerCopy(
        literal: String,
        lambda: MutableCommandBuilder<C>.() -> Unit
    ): MutableCommandBuilder<C> = copy(literal, lambda).register()

    /**
     * Create a new copy of this mutable builder, append a literal, act on it with a receiver
     * lambda, and then register it with the owning command manager
     *
     * @param literal name for the literal
     * @param description description for the literal
     * @param lambda receiver lambda which will be invoked on the new builder
     * @return the new mutable builder
     * @see [CommandManager.command]
     * @since 1.3.0
     */
    @Suppress("DEPRECATION")
    @Deprecated(
        message = "ArgumentDescription should be used over Description",
        level = DeprecationLevel.HIDDEN)
    public fun registerCopy(
        literal: String,
        description: Description,
        lambda: MutableCommandBuilder<C>.() -> Unit
    ): MutableCommandBuilder<C> = copy(literal, description, lambda).register()

    /**
     * Create a new copy of this mutable builder, append a literal, act on it with a receiver
     * lambda, and then register it with the owning command manager
     *
     * @param literal name for the literal
     * @param description description for the literal
     * @param lambda receiver lambda which will be invoked on the new builder
     * @return the new mutable builder
     * @see [CommandManager.command]
     * @since 1.4.0
     */
    public fun registerCopy(
        literal: String,
        description: ArgumentDescription,
        lambda: MutableCommandBuilder<C>.() -> Unit
    ): MutableCommandBuilder<C> = copy(literal, description, lambda).register()

    /**
     * Set the value for a certain [CommandMeta.Key] in the command meta storage for this builder
     *
     * @param T value type
     * @param key the key to set a value for
     * @param value new value
     * @return this mutable builder
     * @since 1.3.0
     */
    public fun <T : Any> meta(key: CommandMeta.Key<T>, value: T): MutableCommandBuilder<C> =
        mutate {
        it.meta(key, value)
    }

    /**
     * Set the value for a certain [CommandMeta.Key] in the command meta storage for this builder
     *
     * @param T value type
     * @param value new value
     * @return this mutable builder
     * @since 1.3.0
     */
    public infix fun <T : Any> CommandMeta.Key<T>.to(value: T): MutableCommandBuilder<C> =
        meta(this, value)

    /**
     * Set the [CommandMeta.DESCRIPTION] meta for this command
     *
     * @param description command description
     * @return this mutable builder
     * @since 1.3.0
     */
    public fun commandDescription(description: String): MutableCommandBuilder<C> =
        meta(CommandMeta.DESCRIPTION, description)

    /**
     * Set the [CommandMeta.LONG_DESCRIPTION] meta for this command
     *
     * @param description command description
     * @return this mutable builder
     * @since 1.3.0
     */
    public fun longCommandDescription(description: String): MutableCommandBuilder<C> =
        meta(CommandMeta.LONG_DESCRIPTION, description)

    /**
     * Set the [CommandMeta.HIDDEN] meta for this command
     *
     * @param hidden whether this command should be hidden
     * @return this mutable builder
     * @since 1.3.0
     */
    public fun hidden(hidden: Boolean = true): MutableCommandBuilder<C> =
        meta(CommandMeta.HIDDEN, hidden)

    /**
     * Specify a required sender type
     *
     * @param T sender type
     * @return this mutable builder
     * @since 1.3.0
     */
    public inline fun <reified T : C> senderType(): MutableCommandBuilder<C> = mutate {
        it.senderType(T::class)
    }

    /**
     * Specify a required sender type
     *
     * @param type sender type
     * @return this mutable builder
     * @since 1.3.0
     */
    public fun senderType(type: KClass<out C>): MutableCommandBuilder<C> = mutate {
        it.senderType(type)
    }

    /**
     * Field to get and set the required sender type for this command builder
     *
     * @since 1.3.0
     */
    public var senderType: KClass<out C>?
        get() = this.commandBuilder.senderType()?.kotlin
        set(type) {
            if (type == null) throw UnsupportedOperationException("Cannot set a null sender type")
            onlyMutate { it.senderType(type) }
        }

    /**
     * Specify a required sender type
     *
     * @param type sender type
     * @return this mutable builder
     * @since 1.3.0
     */
    public fun senderType(type: Class<out C>): MutableCommandBuilder<C> = mutate {
        it.senderType(type)
    }

    /**
     * Specify a permission required to execute this command
     *
     * @param permission permission string
     * @return this mutable builder
     * @since 1.3.0
     */
    public fun permission(permission: String): MutableCommandBuilder<C> = mutate {
        it.permission(permission)
    }

    /**
     * Specify a permission required to execute this command
     *
     * @param permission command permission
     * @return this mutable builder
     * @since 1.3.0
     */
    public fun permission(permission: CommandPermission): MutableCommandBuilder<C> = mutate {
        it.permission(permission)
    }

    /**
     * Field to get and set the required permission for this command builder
     *
     * @since 1.3.0
     */
    public var permission: String
        get() = this.commandBuilder.commandPermission().toString()
        set(permission) = onlyMutate { it.permission(permission) }

    /**
     * Field to get and set the required permission for this command builder
     *
     * @since 1.3.0
     */
    public var commandPermission: CommandPermission
        get() = this.commandBuilder.commandPermission()
        set(permission) = onlyMutate { it.permission(permission) }

    /**
     * Add a new argument to this command
     *
     * @param argument argument to add
     * @param description description of the argument
     * @return this mutable builder
     * @since 1.3.0
     */
    @Suppress("DEPRECATION")
    @Deprecated(
        message = "ArgumentDescription should be used over Description",
        level = DeprecationLevel.HIDDEN)
    public fun argument(
        argument: CommandArgument<C, *>,
        description: Description = Description.empty()
    ): MutableCommandBuilder<C> = mutate { it.argument(argument, description) }

    /**
     * Add a new argument to this command
     *
     * @param argument argument to add
     * @param description description of the argument
     * @return this mutable builder
     * @since 1.4.0
     */
    public fun argument(
        argument: CommandArgument<C, *>,
        description: ArgumentDescription = ArgumentDescription.empty()
    ): MutableCommandBuilder<C> = mutate { it.argument(argument, description) }

    /**
     * Add a new argument to this command
     *
     * @param argument argument to add
     * @param description description of the argument
     * @return this mutable builder
     * @since 1.3.0
     */
    @Suppress("DEPRECATION")
    @Deprecated(
        message = "ArgumentDescription should be used over Description",
        level = DeprecationLevel.HIDDEN)
    public fun argument(
        argument: CommandArgument.Builder<C, *>,
        description: Description = Description.empty()
    ): MutableCommandBuilder<C> = mutate { it.argument(argument, description) }

    /**
     * Add a new argument to this command
     *
     * @param argument argument to add
     * @param description description of the argument
     * @return this mutable builder
     * @since 1.4.0
     */
    public fun argument(
        argument: CommandArgument.Builder<C, *>,
        description: ArgumentDescription = ArgumentDescription.empty()
    ): MutableCommandBuilder<C> = mutate { it.argument(argument, description) }

    /**
     * Add a new argument to this command
     *
     * @param description description of the argument
     * @param argumentSupplier supplier of the argument to add
     * @return this mutable builder
     * @since 1.3.0
     */
    @Suppress("DEPRECATION")
    @Deprecated(
        message = "ArgumentDescription should be used over Description",
        level = DeprecationLevel.HIDDEN)
    public fun argument(
        description: Description = Description.empty(),
        argumentSupplier: () -> CommandArgument<C, *>
    ): MutableCommandBuilder<C> = mutate { it.argument(argumentSupplier(), description) }

    /**
     * Add a new argument to this command
     *
     * @param description description of the argument
     * @param argumentSupplier supplier of the argument to add
     * @return this mutable builder
     * @since 1.4.0
     */
    public fun argument(
        description: ArgumentDescription = ArgumentDescription.empty(),
        argumentSupplier: () -> CommandArgument<C, *>
    ): MutableCommandBuilder<C> = mutate { it.argument(argumentSupplier(), description) }

    /**
     * Add a new literal argument to this command
     *
     * @param name main argument name
     * @param description literal description
     * @param aliases argument aliases
     * @return this mutable builder
     * @since 1.3.0
     */
    @Suppress("DEPRECATION")
    @Deprecated(
        message = "ArgumentDescription should be used over Description",
        level = DeprecationLevel.HIDDEN)
    public fun literal(
        name: String,
        description: Description = Description.empty(),
        vararg aliases: String
    ): MutableCommandBuilder<C> = mutate { it.literal(name, description, *aliases) }

    /**
     * Add a new literal argument to this command
     *
     * @param name main argument name
     * @param description literal description
     * @param aliases argument aliases
     * @return this mutable builder
     * @since 1.4.0
     */
    public fun literal(
        name: String,
        description: ArgumentDescription = ArgumentDescription.empty(),
        vararg aliases: String
    ): MutableCommandBuilder<C> = mutate { it.literal(name, description, *aliases) }

    /**
     * Set the [CommandExecutionHandler] for this builder
     *
     * @param handler command execution handler
     * @return this mutable builder
     * @since 1.3.0
     */
    public fun handler(handler: CommandExecutionHandler<C>): MutableCommandBuilder<C> = mutate {
        it.handler(handler)
    }

    /**
     * Add a new flag argument to this command
     *
     * @param name name of the flag
     * @param aliases flag aliases
     * @param description description of the flag
     * @param argumentSupplier argument supplier for the flag
     * @return this mutable builder
     * @since 1.4.0
     */
    public fun flag(
        name: String,
        aliases: Array<String> = emptyArray(),
        description: ArgumentDescription = ArgumentDescription.empty(),
        argumentSupplier: () -> CommandArgument<C, *>
    ): MutableCommandBuilder<C> = mutate {
        it.flag(
            this.commandManager
                .flagBuilder(name)
                .withAliases(*aliases)
                .withDescription(description)
                .withArgument(argumentSupplier())
                .build())
    }

    /**
     * Add a new flag argument to this command
     *
     * @param name name of the flag
     * @param aliases flag aliases
     * @param description description of the flag
     * @param argument argument for the flag
     * @return this mutable builder
     * @since 1.4.0
     */
    public fun flag(
        name: String,
        aliases: Array<String> = emptyArray(),
        description: ArgumentDescription = ArgumentDescription.empty(),
        argument: CommandArgument<C, *>
    ): MutableCommandBuilder<C> = mutate {
        it.flag(
            this.commandManager
                .flagBuilder(name)
                .withAliases(*aliases)
                .withDescription(description)
                .withArgument(argument)
                .build())
    }

    /**
     * Add a new flag argument to this command
     *
     * @param name name of the flag
     * @param aliases flag aliases
     * @param description description of the flag
     * @param argumentBuilder command argument builder for the flag
     * @return this mutable builder
     * @since 1.4.0
     */
    public fun flag(
        name: String,
        aliases: Array<String> = emptyArray(),
        description: ArgumentDescription = ArgumentDescription.empty(),
        argumentBuilder: CommandArgument.Builder<C, *>
    ): MutableCommandBuilder<C> = mutate {
        it.flag(
            this.commandManager
                .flagBuilder(name)
                .withAliases(*aliases)
                .withDescription(description)
                .withArgument(argumentBuilder)
                .build())
    }

    /**
     * Add a new presence flag argument to this command
     *
     * @param name name of the flag
     * @param aliases flag aliases
     * @param description description of the flag
     * @return this mutable builder
     * @since 1.4.0
     */
    public fun flag(
        name: String,
        aliases: Array<String> = emptyArray(),
        description: ArgumentDescription = ArgumentDescription.empty(),
    ): MutableCommandBuilder<C> = mutate {
        it.flag(
            this.commandManager
                .flagBuilder(name)
                .withAliases(*aliases)
                .withDescription(description)
                .build())
    }
}
