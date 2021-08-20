# cloud-minecraft

This directory hosts Minecraft specific implementations of cloud. Their features are summarized in the table below:

| Module | Platform | Minecraft Version | Brigadier Support?|
|--|--|--|--|
| `cloud-bukkit` | Bukkit, Spigot, Paper | 1.8+ | Via commodore for MC 1.13+ |
| `cloud-paper` | Bukkit, Spigot, Paper | 1.8+ | Via Paper for MC 1.15+, Via commodore for MC 1.13+ |
| `cloud-bungee` | BungeeCord | 1.8+ | No |
| `cloud-velocity` | Velocity 1.1.0 | 1.7+ | Yes |
| `cloud-cloudburst` | CloudBurst 1.0.0 | Bedrock 1.16.20+ | No |
| `cloud-fabric` |  Minecraft, via Fabric | 1.16+ | Yes |


There is also a `cloud-minecraft-extras` module that contains a few extra minecraft related features

## cloud-bukkit
Bukkit mappings for cloud. If `commodore` is present on the classpath and the server is running at least version 1.13+, Brigadier 
mappings will be available.

### dependency
**maven**:
```xml
<dependency>
    <groupId>cloud.commandframework</groupId>
    <artifactId>cloud-bukkit</artifactId>
    <version>1.6.0-SNAPSHOT</version>
</dependency>
```

**gradle**:
```groovy
dependencies {
    implementation 'cloud.commandframework:cloud-bukkit:1.6.0-SNAPSHOT'
}
```

### setup
Simply do:
```java
final BukkitCommandManager<YourSender> bukkitCommandManager = new BukkitCommandManager<>(
  yourPlugin, yourExecutionCoordinator, forwardMapper, backwardsMapper);
```
The `forwardMapper` is a function that maps your chosen sender type to Bukkit's 
[CommandSender](https://jd.bukkit.org/org/bukkit/command/CommandSender.html), and the `backwardsMapper` does the opposite. In
 the case that you don't need a custom sender type, you can simply use `CommandSender`as the generic type 
 and pass `Function.identity()` as the forward and backward mappers.

### commodore
To use commodore, include it as a dependency:

**maven**:
```xml
<dependency>
    <groupId>me.lucko</groupId>
    <artifactId>commodore</artifactId>
    <version>1.9</version>
</dependency>
```

**gradle**:
```groovy
dependencies {
    implementation 'me.lucko:commodore:1.9'
}
```

Then initialize the commodore mappings using:
```java
try {
  bukkitCommandManager.registerBrigadier();
} catch (final Exception e) {
  plugin.getLogger().warning("Failed to initialize Brigadier support: " + e.getMessage());
}
```
The mappings will then be created and registered automatically whenever a new command is registered.

**Note:** The mapper must be initialized *before* any commands are registered.

You can check whether or not the running server supports Brigadier, by using `bukkitCommandManager.queryCapability(...)`.

## cloud-paper

An example plugin using the `cloud-paper` API can be found [here](https://github.com/Sauilitired/cloud/tree/master/examples/example-bukkit).

`cloud-paper`works on all Bukkit derivatives and has graceful fallbacks for cases where Paper specific features are missing. It 
is initialized the same way as the Bukkit manager, except `PaperCommandManager`is used instead. When using Paper 1.15+ Brigadier
mappings are available even without commodore present.

### dependency
**maven**:
```xml
<dependency>
    <groupId>cloud.commandframework</groupId>
    <artifactId>cloud-paper</artifactId>
    <version>1.6.0-SNAPSHOT</version>
</dependency>
```

**gradle**:
```groovy
dependencies {
    implementation 'cloud.commandframework:cloud-paper:1.6.0-SNAPSHOT'
}
```

### asynchronous completions
`cloud-paper`supports asynchronous completions when running on Paper. First check if the capability is present, by using 
`paperCommandManager.queryCapability(CloudBukkitCapabilities.ASYNCHRONOUS_COMPLETION)` and then initialize the asynchronous 
completion listener by using `paperCommandManager.registerAsynchronousCompletions()`.

## cloud-bungee
BungeeCord mappings for cloud.

### dependency
**maven**:
```xml
<dependency>
    <groupId>cloud.commandframework</groupId>
    <artifactId>cloud-bungee</artifactId>
    <version>1.6.0-SNAPSHOT</version>
</dependency>
```

**gradle**:
```groovy
dependencies {
    implementation 'cloud.commandframework:cloud-bungee:1.6.0-SNAPSHOT'
}
```

### setup
Simply do:
```java
final BungeeCommandManager<YourSender> bungeeCommandManager = new BungeeCommandManager<>(
  yourPlugin, yourExecutionCoordinator, forwardMapper, backwardsMapper);
```
The `forwardMapper` is a function that maps your chosen sender type to Bungee's 
[CommandSender](https://ci.md-5.net/job/BungeeCord/ws/api/target/apidocs/net/md_5/bungee/api/CommandSender.html), and 
the `backwardsMapper`does the opposite. In the case that you don't need a custom sender type, you can simply use `CommandSender`as
 the generic type and pass `Function.identity()` as the forward and backward mappers.

## cloud-velocity

cloud mappings for Velocity 1.1.0.

### dependency
**maven**:
```xml
<dependency>
    <groupId>cloud.commandframework</groupId>
    <artifactId>cloud-velocity</artifactId>
    <version>1.6.0-SNAPSHOT</version>
</dependency>
```

**gradle**:
```groovy
dependencies {
    implementation 'cloud.commandframework:cloud-velocity:1.6.0-SNAPSHOT'
}
```

Simply do:
```java
final VelocityCommandManager<YourSender> velocityCommandManager = new VelocityCommandManager<>(
  proxyServer, yourExecutionCoordinator, forwardMapper, backwardsMapper);
```
The `forwardMapper` is a function that maps your chosen sender type to Velocity's 
[CommandSource](https://jd.velocitypowered.com/1.1.0/com/velocitypowered/api/command/CommandSource.html), and the 
`backwardsMapper` does the opposite. In the case that you don't need a custom sender type, you can simply use `CommandSource`as 
the generic type and pass `Function.identity()` as the forward and backward mappers.

## cloud-cloudburst

cloud mappings for CloudBurst 1.0.0-SNAPSHOT.

### dependency
**maven**:
```xml
<dependency>
    <groupId>cloud.commandframework</groupId>
    <artifactId>cloud-cloudburst</artifactId>
    <version>1.6.0-SNAPSHOT</version>
</dependency>
```

**gradle**:
```groovy
dependencies {
    implementation 'cloud.commandframework:cloud-velocity:1.6.0-SNAPSHOT'
}
```

Simply do:
```java
final CloudburstCommandManager<YourSender> cloudburstCommandManager = new CloudburstCommandManager<>(
  yourPlugin, yourExecutionCoordinator, forwardMapper, backwardsMapper);
```
The `forwardMapper` is a function that maps your chosen sender type to Cloudbursts's 
[CommandSender](https://ci.nukkitx.com/job/NukkitX/job/Nukkit/job/master/javadoc/cn/nukkit/command/CommandSender.html), and the 
`backwardsMapper` does the opposite. In the case that you don't need a custom sender type, you can simply use `CommandSource` as 
the generic type and pass `Function.identity()` as the forward and backward mappers.


## cloud-fabric

cloud mappings for the Fabric mod loader for Minecraft 1.16+

### dependency

**gradle**:
```groovy
dependencies {
    modImplementation 'cloud.commandframework:cloud-fabric:1.6.0-SNAPSHOT'
}
```

Simply do:
```java
final FabricCommandManager<YourSender> fabricCommandManager = new FabricCommandManager<>(
  yourExecutionCoordinator, forwarMapper, backwardsMapper);
```

The `forwardMapper` is a function that maps your chosen sender type to Minecraft's 
[ServerCommandSource](https://maven.fabricmc.net/docs/yarn-1.16.4+build.7/net/minecraft/server/command/ServerCommandSource.html), 
and the `backwardsMapper` does the opposite. 

In the case that you don't need a custom sender type, you can use the helper method 
`FabricCommandManager.createNative(yourExecutionCoordinator)` instead, which will create a command manager that works directly
 with `ServerCommandSource`s.
