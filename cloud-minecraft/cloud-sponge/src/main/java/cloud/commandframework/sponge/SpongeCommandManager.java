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

import cloud.commandframework.CommandManager;
import cloud.commandframework.CommandTree;
import cloud.commandframework.arguments.parser.ParserParameters;
import cloud.commandframework.execution.CommandExecutionCoordinator;
import cloud.commandframework.meta.CommandMeta;
import cloud.commandframework.meta.SimpleCommandMeta;
import cloud.commandframework.sponge.annotation.specifier.Center;
import cloud.commandframework.sponge.argument.BlockInputArgument;
import cloud.commandframework.sponge.argument.BlockPredicateArgument;
import cloud.commandframework.sponge.argument.ComponentArgument;
import cloud.commandframework.sponge.argument.DataContainerArgument;
import cloud.commandframework.sponge.argument.GameProfileArgument;
import cloud.commandframework.sponge.argument.GameProfileCollectionArgument;
import cloud.commandframework.sponge.argument.ItemStackPredicateArgument;
import cloud.commandframework.sponge.argument.MultipleEntitySelectorArgument;
import cloud.commandframework.sponge.argument.MultiplePlayerSelectorArgument;
import cloud.commandframework.sponge.argument.NamedTextColorArgument;
import cloud.commandframework.sponge.argument.OperatorArgument;
import cloud.commandframework.sponge.argument.ProtoItemStackArgument;
import cloud.commandframework.sponge.argument.RegistryEntryArgument;
import cloud.commandframework.sponge.argument.ResourceKeyArgument;
import cloud.commandframework.sponge.argument.SingleEntitySelectorArgument;
import cloud.commandframework.sponge.argument.SinglePlayerSelectorArgument;
import cloud.commandframework.sponge.argument.UserArgument;
import cloud.commandframework.sponge.argument.Vector2dArgument;
import cloud.commandframework.sponge.argument.Vector2iArgument;
import cloud.commandframework.sponge.argument.Vector3dArgument;
import cloud.commandframework.sponge.argument.Vector3iArgument;
import cloud.commandframework.sponge.argument.WorldArgument;
import cloud.commandframework.sponge.data.BlockPredicate;
import cloud.commandframework.sponge.data.GameProfileCollection;
import cloud.commandframework.sponge.data.ItemStackPredicate;
import cloud.commandframework.sponge.data.MultipleEntitySelector;
import cloud.commandframework.sponge.data.MultiplePlayerSelector;
import cloud.commandframework.sponge.data.ProtoItemStack;
import cloud.commandframework.sponge.data.SingleEntitySelector;
import cloud.commandframework.sponge.data.SinglePlayerSelector;
import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Module;
import io.leangen.geantyref.TypeToken;
import net.kyori.adventure.text.format.NamedTextColor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.api.ResourceKey;
import org.spongepowered.api.command.CommandCause;
import org.spongepowered.api.command.parameter.managed.operator.Operator;
import org.spongepowered.api.data.persistence.DataContainer;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.lifecycle.RegisterCommandEvent;
import org.spongepowered.api.profile.GameProfile;
import org.spongepowered.api.registry.DefaultedRegistryType;
import org.spongepowered.api.registry.Registry;
import org.spongepowered.api.registry.RegistryType;
import org.spongepowered.api.registry.RegistryTypes;
import org.spongepowered.api.world.server.ServerWorld;
import org.spongepowered.math.vector.Vector2d;
import org.spongepowered.math.vector.Vector2i;
import org.spongepowered.math.vector.Vector3d;
import org.spongepowered.math.vector.Vector3i;
import org.spongepowered.plugin.PluginContainer;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Command manager for Sponge API v8.
 * <p>
 * The manager supports Guice injection
 * as long as the {@link CloudInjectionModule} is present in the injector.
 * This can be achieved by using {@link com.google.inject.Injector#createChildInjector(Module...)}
 *
 * @param <C> Command sender type
 */
public final class SpongeCommandManager<C> extends CommandManager<C> {

    private final PluginContainer pluginContainer;
    private final Function<C, CommandCause> causeMapper;
    private final Function<CommandCause, C> backwardsCauseMapper;
    private final SpongeParserMapper<C> parserMapper;

    /**
     * Create a new command manager instance
     *
     * @param pluginContainer             Owning plugin
     * @param commandExecutionCoordinator Execution coordinator instance
     * @param causeMapper                 Function mapping the custom command sender type to a Sponge CommandCause
     * @param backwardsCauseMapper        Function mapping Sponge CommandCause to the custom command sender type
     */
    @SuppressWarnings("unchecked")
    @Inject
    public SpongeCommandManager(
            final @NonNull PluginContainer pluginContainer,
            final @NonNull Function<@NonNull CommandTree<C>, @NonNull CommandExecutionCoordinator<C>> commandExecutionCoordinator,
            final @NonNull Function<@NonNull C, @NonNull CommandCause> causeMapper,
            final @NonNull Function<@NonNull CommandCause, @NonNull C> backwardsCauseMapper
    ) {
        super(commandExecutionCoordinator, new SpongeRegistrationHandler<C>());
        this.pluginContainer = pluginContainer;
        ((SpongeRegistrationHandler<C>) this.getCommandRegistrationHandler()).initialize(this);
        this.causeMapper = causeMapper;
        this.backwardsCauseMapper = backwardsCauseMapper;
        this.parserMapper = new SpongeParserMapper<>();
        this.registerCommandPreProcessor(new SpongeCommandPreprocessor<>(this));
        this.registerParsers();
        this.setCaptionRegistry(new SpongeCaptionRegistry<>());
    }

    private void registerParsers() {
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(ComponentArgument.class),
                params -> new ComponentArgument.Parser<>()
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(NamedTextColor.class),
                params -> new NamedTextColorArgument.Parser<>()
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(Operator.class),
                params -> new OperatorArgument.Parser<>()
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(ServerWorld.class),
                params -> new WorldArgument.Parser<>()
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(ProtoItemStack.class),
                params -> new ProtoItemStackArgument.Parser<>()
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(ItemStackPredicate.class),
                params -> new ItemStackPredicateArgument.Parser<>()
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(ResourceKey.class),
                params -> new ResourceKeyArgument.Parser<>()
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(GameProfile.class),
                params -> new GameProfileArgument.Parser<>()
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(GameProfileCollection.class),
                params -> new GameProfileCollectionArgument.Parser<>()
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(BlockInputArgument.class),
                params -> new BlockInputArgument.Parser<>()
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(BlockPredicate.class),
                params -> new BlockPredicateArgument.Parser<>()
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(User.class),
                params -> new UserArgument.Parser<>()
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(DataContainer.class),
                params -> new DataContainerArgument.Parser<>()
        );

        // Position arguments
        this.getParserRegistry().registerAnnotationMapper(
                Center.class,
                (annotation, type) -> ParserParameters.single(SpongeParserParameters.CENTER_INTEGERS, true)
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(Vector2d.class),
                params -> new Vector2dArgument.Parser<>(params.get(SpongeParserParameters.CENTER_INTEGERS, false))
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(Vector3d.class),
                params -> new Vector3dArgument.Parser<>(params.get(SpongeParserParameters.CENTER_INTEGERS, false))
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(Vector2i.class),
                params -> new Vector2iArgument.Parser<>()
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(Vector3i.class),
                params -> new Vector3iArgument.Parser<>()
        );

        // Entity selectors
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(SinglePlayerSelector.class),
                params -> new SinglePlayerSelectorArgument.Parser<>()
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(MultiplePlayerSelector.class),
                params -> new MultiplePlayerSelectorArgument.Parser<>()
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(SingleEntitySelector.class),
                params -> new SingleEntitySelectorArgument.Parser<>()
        );
        this.getParserRegistry().registerParserSupplier(
                TypeToken.get(MultipleEntitySelector.class),
                params -> new MultipleEntitySelectorArgument.Parser<>()
        );

        this.registerRegistryParsers();
    }

    private void registerRegistryParsers() {
        final Set<RegistryType<?>> ignoredRegistryTypes = ImmutableSet.of(
                RegistryTypes.OPERATOR // We have a different Operator parser that doesn't use a ResourceKey as input
        );
        for (final Field field : RegistryTypes.class.getDeclaredFields()) {
            final Type generic = field.getGenericType(); /* RegistryType<?> */
            if (!(generic instanceof ParameterizedType)) {
                continue;
            }

            final RegistryType<?> registryType;
            try {
                registryType = (RegistryType<?>) field.get(null);
            } catch (final IllegalAccessException ex) {
                throw new RuntimeException("Failed to access RegistryTypes." + field.getName(), ex);
            }
            if (ignoredRegistryTypes.contains(registryType) || !(registryType instanceof DefaultedRegistryType)) {
                continue;
            }
            final DefaultedRegistryType<?> defaultedRegistryType = (DefaultedRegistryType<?>) registryType;
            final Type valueType = ((ParameterizedType) generic).getActualTypeArguments()[0];

            this.getParserRegistry().registerParserSupplier(
                    TypeToken.get(valueType),
                    params -> new RegistryEntryArgument.Parser<>(defaultedRegistryType)
            );
        }
    }

    @Override
    public boolean hasPermission(
            @NonNull final C sender,
            @NonNull final String permission
    ) {
        if (permission.isEmpty()) {
            return true;
        }
        return this.causeMapper.apply(sender).hasPermission(permission);
    }

    @Override
    public @NonNull CommandMeta createDefaultCommandMeta() {
        return SimpleCommandMeta.empty();
    }

    /**
     * Get the {@link PluginContainer} of the plugin that owns this command manager.
     *
     * @return plugin container
     */
    public @NonNull PluginContainer owningPluginContainer() {
        return this.pluginContainer;
    }

    /**
     * Get the {@link SpongeParserMapper}, responsible for mapping Cloud
     * {@link cloud.commandframework.arguments.CommandArgument CommandArguments} to Sponge
     * {@link org.spongepowered.api.command.registrar.tree.CommandTreeNode.Argument CommandTreeNode.Arguments}.
     *
     * @return the parser mapper
     */
    public @NonNull SpongeParserMapper<C> parserMapper() {
        return this.parserMapper;
    }

    /**
     * Get the {@link Function} used to map {@link C command senders} to {@link CommandCause CommandCauses}.
     *
     * @return command cause mapper
     */
    public @NonNull Function<@NonNull C, @NonNull CommandCause> causeMapper() {
        return this.causeMapper;
    }

    /**
     * Get the {@link Function} used to map {@link CommandCause CommandCauses} to {@link C command senders}.
     *
     * @return backwards command cause mapper
     */
    public @NonNull Function<@NonNull CommandCause, @NonNull C> backwardsCauseMapper() {
        return this.backwardsCauseMapper;
    }

    void registrationCalled() {
        if (!this.registrationCallbackListeners.isEmpty()) {
            this.registrationCallbackListeners.forEach(listener -> listener.accept(this));
            this.registrationCallbackListeners.clear();
        }
        if (this.getRegistrationState() != RegistrationState.AFTER_REGISTRATION) {
            this.lockRegistration();
        }
    }

    private final Set<Consumer<SpongeCommandManager<C>>> registrationCallbackListeners = new HashSet<>();

    /**
     * Add a listener to the command registration callback.
     *
     * <p>These listeners will be called just before command registration is finalized
     * (during the first invocation of Cloud's internal {@link RegisterCommandEvent} listener).</p>
     *
     * <p>This allows for registering commands at the latest possible point in the plugin
     * lifecycle, which may be necessary for certain {@link Registry Registries} to have
     * initialized.</p>
     *
     * @param listener listener
     */
    public void addRegistrationCallbackListener(final @NonNull Consumer<@NonNull SpongeCommandManager<C>> listener) {
        if (this.getRegistrationState() == RegistrationState.AFTER_REGISTRATION) {
            throw new IllegalStateException("The SpongeCommandManager is in the AFTER_REGISTRATION state!");
        }
        this.registrationCallbackListeners.add(listener);
    }

}
