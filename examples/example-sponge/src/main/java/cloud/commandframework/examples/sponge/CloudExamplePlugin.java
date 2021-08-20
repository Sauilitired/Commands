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
package cloud.commandframework.examples.sponge;

import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.Command;
import cloud.commandframework.arguments.standard.DoubleArgument;
import cloud.commandframework.arguments.standard.IntegerArgument;
import cloud.commandframework.arguments.standard.StringArgument;
import cloud.commandframework.context.CommandContext;
import cloud.commandframework.execution.CommandExecutionCoordinator;
import cloud.commandframework.minecraft.extras.MinecraftExceptionHandler;
import cloud.commandframework.sponge.CloudInjectionModule;
import cloud.commandframework.sponge.SpongeCommandManager;
import cloud.commandframework.sponge.argument.BlockInputArgument;
import cloud.commandframework.sponge.argument.BlockPredicateArgument;
import cloud.commandframework.sponge.argument.DataContainerArgument;
import cloud.commandframework.sponge.argument.ItemStackPredicateArgument;
import cloud.commandframework.sponge.argument.MultipleEntitySelectorArgument;
import cloud.commandframework.sponge.argument.NamedTextColorArgument;
import cloud.commandframework.sponge.argument.OperatorArgument;
import cloud.commandframework.sponge.argument.ProtoItemStackArgument;
import cloud.commandframework.sponge.argument.RegistryEntryArgument;
import cloud.commandframework.sponge.argument.SinglePlayerSelectorArgument;
import cloud.commandframework.sponge.argument.UserArgument;
import cloud.commandframework.sponge.argument.Vector3dArgument;
import cloud.commandframework.sponge.argument.Vector3iArgument;
import cloud.commandframework.sponge.argument.WorldArgument;
import cloud.commandframework.sponge.data.BlockInput;
import cloud.commandframework.sponge.data.BlockPredicate;
import cloud.commandframework.sponge.data.ItemStackPredicate;
import cloud.commandframework.sponge.data.MultipleEntitySelector;
import cloud.commandframework.sponge.data.ProtoItemStack;
import cloud.commandframework.sponge.data.SinglePlayerSelector;
import cloud.commandframework.types.tuples.Pair;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import io.leangen.geantyref.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.parameter.managed.operator.Operator;
import org.spongepowered.api.command.parameter.managed.operator.Operators;
import org.spongepowered.api.data.Keys;
import org.spongepowered.api.data.persistence.DataContainer;
import org.spongepowered.api.data.type.ProfessionType;
import org.spongepowered.api.data.type.VillagerType;
import org.spongepowered.api.effect.sound.SoundType;
import org.spongepowered.api.entity.EntityType;
import org.spongepowered.api.entity.EntityTypes;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.entity.living.trader.Villager;
import org.spongepowered.api.item.enchantment.Enchantment;
import org.spongepowered.api.item.enchantment.EnchantmentType;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.Slot;
import org.spongepowered.api.item.inventory.entity.Hotbar;
import org.spongepowered.api.item.inventory.transaction.InventoryTransactionResult;
import org.spongepowered.api.registry.RegistryHolder;
import org.spongepowered.api.registry.RegistryTypes;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.biome.Biome;
import org.spongepowered.api.world.schematic.PaletteTypes;
import org.spongepowered.api.world.server.ServerLocation;
import org.spongepowered.api.world.server.ServerWorld;
import org.spongepowered.math.vector.Vector3d;
import org.spongepowered.math.vector.Vector3i;
import org.spongepowered.plugin.jvm.Plugin;

import java.util.Optional;
import java.util.function.Function;

import static net.kyori.adventure.text.Component.newline;
import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.AQUA;
import static net.kyori.adventure.text.format.NamedTextColor.BLUE;
import static net.kyori.adventure.text.format.NamedTextColor.GRAY;
import static net.kyori.adventure.text.format.NamedTextColor.GREEN;
import static net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.TextColor.color;

@Plugin("cloud-example-sponge")
public final class CloudExamplePlugin {

    private static final Component COMMAND_PREFIX = text()
            .color(color(0x333333))
            .content("[")
            .append(text("Cloud-Sponge", color(0xF7CF0D)))
            .append(text(']'))
            .build();

    private final SpongeCommandManager<CommandCause> commandManager;

    /**
     * Create example plugin instance
     *
     * @param injector injector
     */
    @Inject
    public CloudExamplePlugin(final @NonNull Injector injector) {
        // Create child injector with cloud module
        final Injector childInjector = injector.createChildInjector(
                CloudInjectionModule.createNative(CommandExecutionCoordinator.simpleCoordinator())
        );

        // Get command manager instance
        this.commandManager = childInjector.getInstance(Key.get(new TypeLiteral<SpongeCommandManager<CommandCause>>() {
        }));

        // Use Cloud's enhanced number suggestions
        this.commandManager.parserMapper().cloudNumberSuggestions(true);

        // Register minecraft-extras exception handlers
        new MinecraftExceptionHandler<CommandCause>()
                .withDefaultHandlers()
                .withDecorator(message -> TextComponent.ofChildren(COMMAND_PREFIX, space(), message))
                .apply(this.commandManager, CommandCause::audience);

        this.registerCommands();
    }

    private void registerCommands() {
        this.commandManager.command(this.commandManager.commandBuilder("cloud_test1")
                .permission("cloud.test1")
                .handler(ctx -> ctx.getSender().audience().sendMessage(text("success"))));
        this.commandManager.command(this.commandManager.commandBuilder("cloud_test2")
                .literal("test")
                .literal("test1")
                .handler(ctx -> ctx.getSender().audience().sendMessage(text("success"))));
        final Command.Builder<CommandCause> cloudTest3 = this.commandManager.commandBuilder("cloud_test3");
        final Command.Builder<CommandCause> test = cloudTest3.literal("test");
        this.commandManager.command(test.argument(StringArgument.single("string_arg"))
                .literal("test2")
                .handler(ctx -> ctx.getSender().audience().sendMessage(text("success"))));
        this.commandManager.command(test.literal("literal_arg")
                .handler(ctx -> ctx.getSender().audience().sendMessage(text("success"))));
        this.commandManager.command(cloudTest3.literal("another_test")
                .handler(ctx -> ctx.getSender().audience().sendMessage(text("success"))));
        final Command.Builder<CommandCause> cloud = this.commandManager.commandBuilder("cloud");
        this.commandManager.command(cloud.literal("string_test")
                .argument(StringArgument.single("single"))
                .argument(StringArgument.quoted("quoted"))
                .argument(StringArgument.greedy("greedy"))
                .handler(ctx -> ctx.getSender().audience().sendMessage(text("success"))));
        this.commandManager.command(cloud.literal("int_test")
                .argument(IntegerArgument.of("any"))
                .argument(IntegerArgument.<CommandCause>newBuilder("gt0").withMin(1))
                .argument(IntegerArgument.<CommandCause>newBuilder("lt100").withMax(99))
                .argument(IntegerArgument.<CommandCause>newBuilder("5to20").withMin(5).withMax(20))
                .handler(ctx -> ctx.getSender().audience().sendMessage(text("success"))));
        this.commandManager.command(cloud.literal("enchantment_type_test")
                .argument(RegistryEntryArgument.of("enchantment_type", EnchantmentType.class, RegistryTypes.ENCHANTMENT_TYPE))
                .argument(IntegerArgument.optional("level", 1))
                .handler(ctx -> {
                    final Object subject = ctx.getSender().subject();
                    if (!(subject instanceof Player)) {
                        ctx.getSender().audience().sendMessage(text("This command is for players only!", RED));
                        return;
                    }
                    final Player player = (Player) subject;
                    final Hotbar hotbar = player.inventory().hotbar();
                    final int index = hotbar.selectedSlotIndex();
                    final Slot slot = hotbar.slot(index).get();
                    final InventoryTransactionResult.Poll result = slot.poll();
                    if (result.type() != InventoryTransactionResult.Type.SUCCESS) {
                        player.sendMessage(text("You must hold an item to enchant!", RED));
                        return;
                    }
                    final ItemStack modified = ItemStack.builder()
                            .fromItemStack(result.polledItem().createStack())
                            .add(Keys.APPLIED_ENCHANTMENTS, ImmutableList.of(
                                    Enchantment.of(
                                            ctx.<EnchantmentType>get("enchantment_type"),
                                            ctx.<Integer>get("level")
                                    )
                            ))
                            .build();
                    slot.set(modified);
                }));
        this.commandManager.command(cloud.literal("color_test")
                .argument(NamedTextColorArgument.of("color"))
                .argument(StringArgument.greedy("message"))
                .handler(ctx -> {
                    ctx.getSender().audience().sendMessage(
                            text(ctx.get("message"), ctx.<NamedTextColor>get("color"))
                    );
                }));
        this.commandManager.command(cloud.literal("operator_test")
                .argument(IntegerArgument.of("first"))
                .argument(OperatorArgument.of("operator"))
                .argument(IntegerArgument.of("second"))
                .handler(ctx -> {
                    final int first = ctx.get("first");
                    final int second = ctx.get("second");
                    final Operator operator = ctx.get("operator");
                    if (!(operator instanceof Operator.Simple)) {
                        ctx.getSender().audience().sendMessage(
                                text("That type of operator is not applicable here!", RED)
                        );
                        return;
                    }
                    ctx.getSender().audience().sendMessage(text()
                            .color(AQUA)
                            .append(text(first))
                            .append(space())
                            .append(text(operator.asString(), BLUE))
                            .append(space())
                            .append(text(second))
                            .append(space())
                            .append(text('→', BLUE))
                            .append(space())
                            .append(text(((Operator.Simple) operator).apply(first, second)))
                    );
                }));
        this.commandManager.command(cloud.literal("modifylevel")
                .argument(OperatorArgument.of("operator"))
                .argument(DoubleArgument.of("value"))
                .handler(ctx -> {
                    final Object subject = ctx.getSender().subject();
                    if (!(subject instanceof Player)) { // todo: a solution to this
                        ctx.getSender().audience().sendMessage(text("This command is for players only!", RED));
                        return;
                    }
                    final Player player = (Player) subject;
                    final Operator operator = ctx.get("operator");
                    final double value = ctx.get("value");
                    if (operator == Operators.ASSIGN.get()) {
                        player.offer(Keys.EXPERIENCE, (int) value);
                        return;
                    }
                    if (!(operator instanceof Operator.Simple)) {
                        ctx.getSender().audience().sendMessage(
                                text("That type of operator is not applicable here!", RED)
                        );
                        return;
                    }
                    final int currentXp = player.get(Keys.EXPERIENCE).get();
                    player.offer(Keys.EXPERIENCE, (int) ((Operator.Simple) operator).apply(currentXp, value));
                }));
        this.commandManager.command(cloud.literal("selectplayer")
                .argument(SinglePlayerSelectorArgument.of("player"))
                .handler(ctx -> {
                    final Player player = ctx.<SinglePlayerSelector>get("player").getSingle();
                    ctx.getSender().audience().sendMessage(TextComponent.ofChildren(
                            text("Display name of selected player: ", GRAY),
                            player.displayName().get()
                    ));
                }));
        this.commandManager.command(cloud.literal("world_test")
                .argument(WorldArgument.of("world"))
                .handler(ctx -> {
                    ctx.getSender().audience().sendMessage(text(ctx.<ServerWorld>get("world").key().asString()));
                }));
        this.commandManager.command(cloud.literal("test_item")
                .argument(ProtoItemStackArgument.of("item"))
                .literal("is")
                .argument(ItemStackPredicateArgument.of("predicate"))
                .handler(ctx -> {
                    final ItemStack item = ctx.<ProtoItemStack>get("item").createItemStack(1, true);
                    final ItemStackPredicate predicate = ctx.get("predicate");
                    final Component message = text(builder -> {
                        builder.append(item.get(Keys.DISPLAY_NAME).orElse(item.type().asComponent()))
                                .append(space());
                        if (predicate.test(item)) {
                            builder.append(text("passes!", GREEN));
                            return;
                        }
                        builder.append(text("does not pass!", RED));
                    });
                    ctx.getSender().audience().sendMessage(message);
                }));
        this.commandManager.command(cloud.literal("test_entity_type")
                .argument(RegistryEntryArgument.of("type", new TypeToken<EntityType<?>>() {
                }, RegistryTypes.ENTITY_TYPE))
                .handler(ctx -> {
                    ctx.getSender().audience().sendMessage(ctx.<EntityType<?>>get("type"));
                }));
        final Function<CommandContext<CommandCause>, RegistryHolder> holderFunction = ctx -> ctx.getSender()
                .location()
                .map(Location::world)
                .orElse(Sponge.server().worldManager().defaultWorld())
                .registries();
        this.commandManager.command(cloud.literal("test_biomes")
                .argument(RegistryEntryArgument.of("biome", Biome.class, holderFunction, RegistryTypes.BIOME))
                .handler(ctx -> {
                    final ResourceKey biomeKey = holderFunction.apply(ctx)
                            .registry(RegistryTypes.BIOME)
                            .findValueKey(ctx.get("biome"))
                            .orElseThrow(IllegalStateException::new);
                    ctx.getSender().audience().sendMessage(text(biomeKey.asString()));
                }));
        this.commandManager.command(cloud.literal("test_sounds")
                .argument(RegistryEntryArgument.of("type", SoundType.class, RegistryTypes.SOUND_TYPE))
                .handler(ctx -> {
                    ctx.getSender().audience().sendMessage(text(ctx.<SoundType>get("type").key().asString()));
                }));
        this.commandManager.command(cloud.literal("summon_villager")
                .argument(RegistryEntryArgument.of("type", VillagerType.class, RegistryTypes.VILLAGER_TYPE))
                .argument(RegistryEntryArgument.of("profession", ProfessionType.class, RegistryTypes.PROFESSION_TYPE))
                .handler(ctx -> {
                    final ServerLocation loc = ctx.getSender().location().orElse(null);
                    if (loc == null) {
                        ctx.getSender().audience().sendMessage(text("No location!"));
                        return;
                    }
                    final ServerWorld world = loc.world();
                    final Villager villager = world.createEntity(EntityTypes.VILLAGER, loc.position());
                    villager.offer(Keys.VILLAGER_TYPE, ctx.get("type"));
                    villager.offer(Keys.PROFESSION_TYPE, ctx.get("profession"));
                    if (world.spawnEntity(villager)) {
                        ctx.getSender().audience().sendMessage(text()
                                .append(text("Spawned entity!", GREEN))
                                .append(space())
                                .append(villager.displayName().get())
                                .hoverEvent(villager));
                    } else {
                        ctx.getSender().audience().sendMessage(text("failed to spawn :("));
                    }
                }));
        this.commandManager.command(cloud.literal("vec3d")
                .argument(Vector3dArgument.of("vec3d"))
                .handler(ctx -> {
                    ctx.getSender().audience().sendMessage(text(ctx.<Vector3d>get("vec3d").toString()));
                }));
        this.commandManager.command(cloud.literal("selectentities")
                .argument(MultipleEntitySelectorArgument.of("selector"))
                .handler(ctx -> {
                    final MultipleEntitySelector selector = ctx.get("selector");
                    ctx.getSender().audience().sendMessage(TextComponent.ofChildren(
                            text("Using selector: ", BLUE),
                            text(selector.inputString()),
                            newline(),
                            text("Selected: ", LIGHT_PURPLE),
                            selector.get().stream()
                                    .map(e -> e.displayName().get())
                                    .collect(Component.toComponent(text(", ", GRAY)))
                    ));
                }));

        this.commandManager.command(cloud.literal("user")
                .argument(UserArgument.of("user"))
                .handler(ctx -> {
                    ctx.getSender().audience().sendMessage(text(ctx.<User>get("user").toString()));
                }));
        this.commandManager.command(cloud.literal("data")
                .argument(DataContainerArgument.of("data"))
                .handler(ctx -> {
                    ctx.getSender().audience().sendMessage(text(ctx.<DataContainer>get("data").toString()));
                }));
        this.commandManager.command(cloud.literal("setblock")
                .permission("cloud.setblock")
                .argument(Vector3iArgument.of("position"))
                .argument(BlockInputArgument.of("block"))
                .handler(ctx -> {
                    final Vector3i position = ctx.get("position");
                    final BlockInput input = ctx.get("block");
                    final Optional<ServerLocation> location = ctx.getSender().location();
                    if (location.isPresent()) {
                        final ServerWorld world = location.get().world();
                        input.place(world.location(position));
                        ctx.getSender().audience().sendMessage(text("set block!"));
                    } else {
                        ctx.getSender().audience().sendMessage(text("no location!"));
                    }
                }));
        this.commandManager.command(cloud.literal("blockinput")
                .argument(BlockInputArgument.of("block"))
                .handler(ctx -> {
                    final BlockInput input = ctx.get("block");
                    ctx.getSender().audience().sendMessage(text(
                            PaletteTypes.BLOCK_STATE_PALETTE.get().stringifier()
                                    .apply(RegistryTypes.BLOCK_TYPE.get(), input.blockState())
                    ));
                }));
        this.commandManager.command(this.commandManager.commandBuilder("gib")
                .permission("cloud.gib")
                .argumentPair(
                        "itemstack",
                        TypeToken.get(ItemStack.class),
                        Pair.of("item", "amount"),
                        Pair.of(ProtoItemStack.class, Integer.class),
                        (sender, pair) -> {
                            final ProtoItemStack proto = pair.getFirst();
                            final int amount = pair.getSecond();
                            return proto.createItemStack(amount, true);
                        },
                        ArgumentDescription.of("The ItemStack to give")
                )
                .handler(ctx -> ((Player) ctx.getSender().subject()).inventory().offer(ctx.<ItemStack>get("itemstack"))));
        this.commandManager.command(cloud.literal("replace")
                .permission(cause -> {
                    // works but error message is ugly
                    // todo: cause.cause().root() returns DedicatedServer during permission checks?
                    return cause.subject() instanceof Player;
                })
                .argument(BlockPredicateArgument.of("predicate"))
                .argument(IntegerArgument.of("radius"))
                .argument(BlockInputArgument.of("replacement"))
                .handler(ctx -> {
                    final BlockPredicate predicate = ctx.get("predicate");
                    final int radius = ctx.get("radius");
                    final BlockInput replacement = ctx.get("replacement");

                    // its a player so get is fine
                    final ServerLocation loc = ctx.getSender().location().get();
                    final ServerWorld world = loc.world();
                    final Vector3d vec = loc.position();

                    for (double x = vec.x() - radius; x < vec.x() + radius; x++) {
                        for (double y = vec.y() - radius; y < vec.y() + radius; y++) {
                            for (double z = vec.z() - radius; z < vec.z() + radius; z++) {
                                final ServerLocation location = world.location(x, y, z);
                                if (predicate.test(location)) {
                                    location.setBlock(replacement.blockState());
                                }
                            }
                        }
                    }
                }));
    }

}
