# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.6.0]

### Added
- Kotlin: Support for suspending command functions using `AnnotationParser<C>.installCoroutineSupport()`
- Flags can be bound to a permission

### Changed
- Added `executeFuture` to `CommandExecutionHandler` which is now used internally. By default, this delegates to the old 
  `execute` method

## [1.5.0]

### Added
 - `@Quoted` annotation for the `String` argument ([#239](https://github.com/Incendo/cloud/pull/239))
 - Expose `min` and `max` values on numerous number aruments ([#255](https://github.com/Incendo/cloud/pull/255))
 - `CommandArgument.TypedBuilder`, a variant of `CommandArgument.Builder` designed for subclassing, that returns a self type
 - `MappedArgumentParser` and `ArgumentParser#map` for creating mapped argument parsers
 - Helper methods for mapping values/failures of `ArgumentParseResult`s (`ArgumentParseResult#mapParsedValue`, `ArgumentParseResult#flatMapParsedValue`, and `ArgumentParseResult#mapFailure`)
 - JDA: Role argument parser ([#219](https://github.com/Incendo/cloud/pull/219))
 - Brigadier: `WrappedBrigadierParser` for wrapping Brigadier `ArgumentType`s as cloud `ArgumentParser`s
 - Bukkit: Implement parser for ProtoItemStack ([#257](https://github.com/Incendo/cloud/pull/257))
 - Bukkit: Implement parsers for ItemStackPredicate and BlockPredicate ([#259](https://github.com/Incendo/cloud/pull/259))
 - Bukkit: Support for Mojang mapped servers ([#267](https://github.com/Incendo/cloud/pull/267))
 - Fabric: Fabric implementation added ([#223](https://github.com/Incendo/cloud/pull/223))
 - Minecraft-Extras/Bukkit/Paper/Velocity: `AudienceProvider.nativeAudience`, `MinecraftHelp.createNative` , `BukkitCommandManager.createNative` , `PaperCommandManager.createNative`, `CloudInjectionModule.createNative`  helper methods for `Audience` sender types ([#240](https://github.com/Incendo/cloud/pull/240))

### Changed
 - `Long` argument parser is now public ([#229](https://github.com/Incendo/cloud/pull/229))
 - JDA: Add isolation system to JDA user argument ([#220](https://github.com/Incendo/cloud/pull/220))
 - Bukkit: Use Command instead of TabCompleteEvent on Bukkit
 - Bukkit: Minecraft 1.17 support added

### Deprecated
 - JDA: Deprecated old UserParser that did not take an isolation parameter ([#220](https://github.com/Incendo/cloud/pull/220))
 
### Fixed
 - Tuple implementations now do not throw an error when using the toArray method ([#222](https://github.com/Incendo/cloud/pull/222))
 - Argument parser for `long` types was not registered ([#229](https://github.com/Incendo/cloud/pull/229))

## [1.4.0] - 2021-01-16

### Added
 - Predicate command filters to the help system ([#187](https://github.com/Incendo/cloud/pull/187))
 - Allow flags to contain compound arguments ([#192](https://github.com/Incendo/cloud/pull/192))
 - Allow for components in exceptions and meta data ([#200](https://github.com/Incendo/cloud/pull/200))
 - Location2DArgument ([#201](https://github.com/Incendo/cloud/pull/201))
 - Expose the Command which led to `InvalidCommandSenderException`s ([#204](https://github.com/Incendo/cloud/pull/211))
 - Expose the CommandContext which led to `CommandExecutionException`s ([#204](https://github.com/Incendo/cloud/pull/211))
 - Helper methods for command flags to MutableCommandBuilder ([#205](https://github.com/Incendo/cloud/pull/205))
 - CommandFlag accepting getters to FlagContext ([#206](https://github.com/Incendo/cloud/pull/206))
 - More abstract description concept ([#207](https://github.com/Incendo/cloud/pull/207))
 - Predicate permissions ([#210](https://github.com/Incendo/cloud/pull/210))
 - Injection services ([#211](https://github.com/Incendo/cloud/pull/211))
 - Sponge v7 support ([#212](https://github.com/Incendo/cloud/pull/211))
 - Logical `AND` and `OR` operations for `CommandPermission`s ([#213](https://github.com/Incendo/cloud/pull/213))

### Changed
 - Allow command argument names to include `_` and `-` ([#186](https://github.com/Incendo/cloud/pull/186))
 - Make it easier to use translatable components with MinecraftHelp ([#197](https://github.com/Incendo/cloud/pull/197))
 - Show "No result for query" when a multi-help topic is empty
 - Use the method+field annotation accessor rather than the method accessor when injecting method parameters

### Deprecated
 - Description, and everything using Description directly ([#207](https://github.com/Incendo/cloud/pull/207))
 - ParameterInjectorRegistry#injectors ([#211](https://github.com/Incendo/cloud/pull/211))

### Fixed
 - Issue where suggestions were shown multiple times when using Brigadier ([#184](https://github.com/Incendo/cloud/pull/184))
 - Issue where the command manager was in the wrong state if no commands had been registered ([#196](https://github.com/Incendo/cloud/pull/196))
 - Issues with JDA ([#198](https://github.com/Incendo/cloud/pull/198)) ([#199](https://github.com/Incendo/cloud/pull/199)) ([#214](https://github.com/Incendo/cloud/pull/214))
 - Console suggestions for Bukkit

## [1.3.0] - 2020-12-18

### Added
 - `@Suggestions` annotated methods
 - `@Parser` annotated methods
 - Type safe meta system
 - Allow interception of command builders based on annotations in AnnotationParser
 - Kotlin DSL

### Changed
 - Move the parser injector registry into CommandManager and added injection to CommandContext
 - Supporting repeating literals or argument names
 - Make CommandMeta and FlagContext more Kotlin friendly

### Deprecated
 - String keyed command meta
 - ParameterInjectorRegistry#injectors

### Fixed
 - Fixed issue with task synchronization

## [1.2.0] - 2020-12-07

### Added
 - Access to the CloudBrigadierManager from Brigadier-enabled command managers
 - Parameter injectors (cloud-annotations)
 - Store currently parsing command argument in the command context
 - A method to CloudBrigadierManager to enable or disable Brigadier native suggestions for specific argument types
 - A method to get the failure reason of SelectorParseExceptions
 - Some methods to FlagContext to work with flag values as optionals
 - Allow for use of named suggestion providers with `@Flag`s (cloud-annotations)
 - `CommandExecutionException` which wraps any exception thrown during the execution of command handlers. Should be
  handled using `CommandManager#registerExceptionHandler`, similar to `NoSuchCommandException`, `ArgumentParseException`, etc.
 - Registration state to command managers
 - ALLOW_UNSAFE_REGISTRATION ManagerSetting to disable state checks when registering commands
 - OVERRIDE_EXISTING_COMMANDS ManagerSetting to allow for overriding of existing commands on supported platforms
 
### Changed
 - Allow for use of `@Completions` annotation with argument types other than String
 - Allow for use of a BiFunction<C, E, Component> instead of just a Function<E, Component> in MinecraftExceptionHandler
 
### Deprecated
 - LockableCommandManager in favor of CommandManager state
 
### Fixed
 - Use the correct default range for Double and Float parsers in the StandardParserRegistry
 - Bukkit alias command suggestions without Brigadier
 - Bukkit command alias registration when using Brigadier
 - A bug where providing valid input for an argument caused cloud to no longer make suggestions
 - Detect and throw an exception for ambiguous nodes in more cases
 - CloudBrigadierManager no longer forgets the command sender

## [1.1.0] - 2020-10-24

### Added
 - ExampleVelocityPlugin
 - CloudInjectionModule to cloud-velocity
 - PlayerArgument to cloud-velocity
 - TextColorArgument to minecraft-extras
 - LocationArgument to cloud-bukkit
 - ServerArgument to cloud-velocity
 - LockableCommandManager to cloud-core
 - VelocityCommandPreprocessor to cloud-velocity
 - PlayerArgument to cloud-bungee
 - ServerArgument to cloud-bungee
 - ExampleBungeePlugin
 - CaptionKeys to cloud-bungee
 - BungeeCommandPreprocessor to cloud-bungee
 - Named suggestion providers
 - PircBotX implementation

### Changed
 - Allow for combined presence flags, such that `-a -b -c` is equivalent to `-abc`
 - Allow for class annotations as a default for when an annotation is not present on a method
 - Allow for annotated annotations
 
### Fixed
 - Arguments with no required children not being executors (cloud-brigadier)

## [1.0.2] - 2020-10-18

### Fixed
 - Quoted parsing in StringArgument
 - Wrong suggestions following invalid literals
 - Chained optionals not allowing the command to be executed when more than one optional is omitted

### Changed
 - Update adventure-api from 4.0.0 to 4.1.1
 - Update Velocity module for breaking API changes (sendMessage needs an Identity)

## [1.0.1] - 2020-10-14

### Changes
 - Switch from a snapshot to a release version of adventure 4.0.0
 - Add `Identity.nil()` when sending adventure messages
