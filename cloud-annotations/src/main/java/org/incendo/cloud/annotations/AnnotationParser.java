//
// MIT License
//
// Copyright (c) 2024 Incendo
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
package org.incendo.cloud.annotations;

import io.leangen.geantyref.GenericTypeReflector;
import io.leangen.geantyref.TypeToken;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apiguardian.api.API;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.incendo.cloud.CommandManager;
import org.incendo.cloud.annotations.assembler.ArgumentAssembler;
import org.incendo.cloud.annotations.assembler.ArgumentAssemblerImpl;
import org.incendo.cloud.annotations.assembler.FlagAssembler;
import org.incendo.cloud.annotations.assembler.FlagAssemblerImpl;
import org.incendo.cloud.annotations.descriptor.ArgumentDescriptor;
import org.incendo.cloud.annotations.descriptor.CommandDescriptor;
import org.incendo.cloud.annotations.descriptor.FlagDescriptor;
import org.incendo.cloud.annotations.exception.ExceptionHandler;
import org.incendo.cloud.annotations.exception.ExceptionHandlerFactory;
import org.incendo.cloud.annotations.extractor.ArgumentExtractor;
import org.incendo.cloud.annotations.extractor.CommandExtractor;
import org.incendo.cloud.annotations.extractor.CommandExtractorImpl;
import org.incendo.cloud.annotations.extractor.FlagExtractor;
import org.incendo.cloud.annotations.extractor.FlagExtractorImpl;
import org.incendo.cloud.annotations.extractor.StandardArgumentExtractor;
import org.incendo.cloud.annotations.injection.RawArgs;
import org.incendo.cloud.annotations.parser.MethodArgumentParserFactory;
import org.incendo.cloud.annotations.parser.Parser;
import org.incendo.cloud.annotations.processing.CommandContainer;
import org.incendo.cloud.annotations.processing.CommandContainerProcessor;
import org.incendo.cloud.annotations.string.StringProcessor;
import org.incendo.cloud.annotations.suggestion.SuggestionProviderFactory;
import org.incendo.cloud.annotations.suggestion.Suggestions;
import org.incendo.cloud.caption.Caption;
import org.incendo.cloud.component.CommandComponent;
import org.incendo.cloud.component.preprocessor.RegexPreprocessor;
import org.incendo.cloud.description.Description;
import org.incendo.cloud.execution.CommandExecutionHandler;
import org.incendo.cloud.internal.CommandInputTokenizer;
import org.incendo.cloud.meta.CommandMeta;
import org.incendo.cloud.meta.CommandMetaBuilder;
import org.incendo.cloud.parser.ParserDescriptor;
import org.incendo.cloud.parser.ParserParameter;
import org.incendo.cloud.parser.ParserParameters;
import org.incendo.cloud.parser.flag.CommandFlag;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.incendo.cloud.type.tuple.Pair;
import org.incendo.cloud.util.annotation.AnnotationAccessor;

import static org.incendo.cloud.description.CommandDescription.commandDescription;

/**
 * Parser that parses class instances {@link org.incendo.cloud.Command commands}
 *
 * @param <C> command sender type
 */
@SuppressWarnings("unused")
public final class AnnotationParser<C> {

    private static final Comparator<Class<?>> COMMAND_CONTAINER_COMPARATOR = Comparator.<Class<?>>comparingInt(clazz -> {
        final CommandContainer commandContainer = clazz.getAnnotation(CommandContainer.class);
        if (commandContainer == null) {
            return 1;
        }
        return commandContainer.priority();
    }).reversed();

    /**
     * The value of {@link Argument} that should be used to infer argument names from parameter names.
     */
    public static final String INFERRED_ARGUMENT_NAME = "__INFERRED_ARGUMENT_NAME__";

    private final CommandManager<C> manager;
    private final Map<Class<? extends Annotation>, AnnotationMapper<?>> annotationMappers;
    private final Map<Class<? extends Annotation>, PreprocessorMapper<?, C>> preprocessorMappers;
    private final Map<Class<? extends Annotation>, BuilderModifier<?, C>> builderModifiers;
    private final List<BuilderDecorator<C>> builderDecorators;
    private final Map<Predicate<Method>, CommandMethodExecutionHandlerFactory<C>> commandMethodFactories;
    private final TypeToken<C> commandSenderType;
    private final MetaFactory metaFactory;

    private StringProcessor stringProcessor;
    private SyntaxParser syntaxParser;
    private ArgumentExtractor argumentExtractor;
    private ArgumentAssembler<C> argumentAssembler;
    private FlagExtractor flagExtractor;
    private FlagAssembler flagAssembler;
    private CommandExtractor commandExtractor;
    private SuggestionProviderFactory<C> suggestionProviderFactory;
    private MethodArgumentParserFactory<C> methodArgumentParserFactory;
    private ExceptionHandlerFactory<C> exceptionHandlerFactory;
    private DescriptionMapper descriptionMapper;
    private DefaultValueRegistry<C> defaultValueRegistry;

    /**
     * Construct a new annotation parser
     *
     * @param manager            Command manager instance
     * @param commandSenderClass Command sender class
     * @param metaMapper         Function that is used to create {@link CommandMeta} instances from annotations on the
     *                           command methods. These annotations will be mapped to
     *                           {@link ParserParameter}. Mappers for the
     *                           parser parameters can be registered using {@link #registerAnnotationMapper(Class, AnnotationMapper)}
     */
    public AnnotationParser(
            final @NonNull CommandManager<C> manager,
            final @NonNull Class<C> commandSenderClass,
            final @NonNull Function<@NonNull ParserParameters, @NonNull CommandMeta> metaMapper
    ) {
        this(manager, TypeToken.get(commandSenderClass), metaMapper);
    }

    /**
     * Construct a new annotation parser
     *
     * @param manager            Command manager instance
     * @param commandSenderClass Command sender class
     */
    public AnnotationParser(
            final @NonNull CommandManager<C> manager,
            final @NonNull Class<C> commandSenderClass
    ) {
        this(manager, TypeToken.get(commandSenderClass), parameters -> CommandMeta.empty());
    }

    /**
     * Construct a new annotation parser
     *
     * @param manager            Command manager instance
     * @param commandSenderClass Command sender class
     */
    public AnnotationParser(
            final @NonNull CommandManager<C> manager,
            final @NonNull TypeToken<C> commandSenderClass
    ) {
        this(manager, commandSenderClass, parameters -> CommandMeta.empty());
    }

    /**
     * Construct a new annotation parser
     *
     * @param manager           Command manager instance
     * @param commandSenderType Command sender type
     * @param metaMapper        Function that is used to create {@link CommandMeta} instances from annotations on the
     *                          command methods. These annotations will be mapped to
     *                          {@link ParserParameter}. Mappers for the
     *                          parser parameters can be registered using {@link #registerAnnotationMapper(Class, AnnotationMapper)}
     */
    @API(status = API.Status.STABLE)
    public AnnotationParser(
            final @NonNull CommandManager<C> manager,
            final @NonNull TypeToken<C> commandSenderType,
            final @NonNull Function<@NonNull ParserParameters, @NonNull CommandMeta> metaMapper
    ) {
        this.commandSenderType = commandSenderType;
        this.manager = manager;
        this.metaFactory = new MetaFactory(this, metaMapper);
        this.annotationMappers = new HashMap<>();
        this.preprocessorMappers = new HashMap<>();
        this.builderModifiers = new HashMap<>();
        this.commandMethodFactories = new HashMap<>();
        this.flagExtractor = new FlagExtractorImpl(this);
        this.flagAssembler = new FlagAssemblerImpl(manager);
        this.syntaxParser = new SyntaxParserImpl();
        this.descriptionMapper = DescriptionMapper.simple();
        this.argumentExtractor = StandardArgumentExtractor.create(this);
        this.argumentAssembler = new ArgumentAssemblerImpl<>(this);
        this.commandExtractor = new CommandExtractorImpl(this);
        this.suggestionProviderFactory = SuggestionProviderFactory.defaultFactory();
        this.methodArgumentParserFactory = MethodArgumentParserFactory.defaultFactory();
        this.exceptionHandlerFactory = ExceptionHandlerFactory.defaultFactory();
        this.builderDecorators = new ArrayList<>();
        this.defaultValueRegistry = new DefaultValueRegistryImpl<>();
        this.registerBuilderModifier(
                CommandDescription.class,
                (description, builder) -> builder.commandDescription(commandDescription(this.mapDescription(description.value())))
        );
        this.registerPreprocessorMapper(Regex.class, annotation -> RegexPreprocessor.of(
                this.processString(annotation.value()),
                Caption.of(this.processString(annotation.failureCaption()))
        ));
        this.manager.parameterInjectorRegistry().registerInjector(
                String[].class,
                (context, annotations) -> annotations.annotation(RawArgs.class) == null
                        ? null
                        : new CommandInputTokenizer(context.rawInput().remainingInput()).tokenize().toArray(new String[0])
        );
        this.stringProcessor = StringProcessor.noOp();
    }

    @SuppressWarnings("unchecked")
    static <A extends Annotation> @Nullable A getAnnotationRecursively(
            final @NonNull AnnotationAccessor annotations,
            final @NonNull Class<A> clazz,
            final @NonNull Set<Class<? extends Annotation>> checkedAnnotations
    ) {
        A innerCandidate = null;
        for (final Annotation annotation : annotations.annotations()) {
            if (!checkedAnnotations.add(annotation.annotationType())) {
                continue;
            }
            if (annotation.annotationType().equals(clazz)) {
                return (A) annotation;
            }
            if (annotation.annotationType().getPackage().getName().startsWith("java.lang")) {
                continue;
            }
            final A inner = getAnnotationRecursively(
                    AnnotationAccessor.of(annotation.annotationType()),
                    clazz,
                    checkedAnnotations
            );
            if (inner != null) {
                innerCandidate = inner;
            }
        }
        return innerCandidate;
    }

    static <A extends Annotation> @Nullable A getMethodOrClassAnnotation(
            final @NonNull Method method,
            final @NonNull Class<A> clazz
    ) {
        A annotation = getAnnotationRecursively(
                AnnotationAccessor.of(method),
                clazz,
                new HashSet<>()
        );
        if (annotation == null) {
            annotation = getAnnotationRecursively(
                    AnnotationAccessor.of(method.getDeclaringClass()),
                    clazz,
                    new HashSet<>()
            );
        }
        return annotation;
    }

    static <A extends Annotation> boolean methodOrClassHasAnnotation(
            final @NonNull Method method,
            final @NonNull Class<A> clazz
    ) {
        return getMethodOrClassAnnotation(method, clazz) != null;
    }

    /**
     * Returns the command manager that was used to create this parser
     *
     * @return Command manager
     */
    public @NonNull CommandManager<C> manager() {
        return this.manager;
    }

    /**
     * Registers a new command execution method factory. This allows for the registration of
     * custom command method execution strategies.
     *
     * @param predicate the predicate that decides whether to apply the custom execution handler to the given method
     * @param factory   the function that produces the command execution handler
     */
    @API(status = API.Status.STABLE)
    public void registerCommandExecutionMethodFactory(
            final @NonNull Predicate<@NonNull Method> predicate,
            final @NonNull CommandMethodExecutionHandlerFactory<C> factory
    ) {
        this.commandMethodFactories.put(predicate, factory);
    }

    /**
     * Register a builder modifier for a specific annotation. The builder modifiers are
     * allowed to act on a {@link org.incendo.cloud.Command.Builder} after all arguments have been added
     * to the builder. This allows for modifications of the builder instance before
     * the command is registered to the command manager.
     *
     * @param annotation      Annotation (class) that the builder modifier reacts to
     * @param builderModifier Modifier that acts on the given annotation and the incoming builder. Command builders
     *                        are immutable, so the modifier should return the instance of the command builder that is
     *                        returned as a result of any operation on the builder
     * @param <A>             Annotation type
     */
    public <A extends Annotation> void registerBuilderModifier(
            final @NonNull Class<A> annotation,
            final @NonNull BuilderModifier<A, C> builderModifier
    ) {
        this.builderModifiers.put(annotation, builderModifier);
    }

    /**
     * Registers the given {@code decorator}.
     * <p>
     * The decorators are allowed to modify the command builders to set up default values.
     * All other steps of the command construction process take priority over the decorators.
     *
     * @param decorator the decorator
     */
    @API(status = API.Status.STABLE)
    public void registerBuilderDecorator(final @NonNull BuilderDecorator<C> decorator) {
        this.builderDecorators.add(decorator);
    }

    /**
     * Register an annotation mapper
     *
     * @param annotation Annotation class
     * @param mapper     Mapping function
     * @param <A>        Annotation type
     */
    public <A extends Annotation> void registerAnnotationMapper(
            final @NonNull Class<A> annotation,
            final @NonNull AnnotationMapper<A> mapper
    ) {
        this.annotationMappers.put(annotation, mapper);
    }

    /**
     * Registers a preprocessor mapper
     *
     * @param annotation         annotation class
     * @param preprocessorMapper preprocessor mapper
     * @param <A>                annotation type
     */
    @API(status = API.Status.STABLE)
    public <A extends Annotation> void registerPreprocessorMapper(
            final @NonNull Class<A> annotation,
            final @NonNull PreprocessorMapper<A, C> preprocessorMapper
    ) {
        this.preprocessorMappers.put(annotation, preprocessorMapper);
    }

    /**
     * Returns an unmodifiable view of the preprocessor mappers.
     *
     * @return the preprocessor mappers
     */
    @API(status = API.Status.STABLE)
    public @NonNull Map<@NonNull Class<? extends Annotation>, @NonNull PreprocessorMapper<?, C>> preprocessorMappers() {
        return Collections.unmodifiableMap(this.preprocessorMappers);
    }

    /**
     * Returns the string processor used by this parser.
     *
     * @return the string processor
     */
    public @NonNull StringProcessor stringProcessor() {
        return this.stringProcessor;
    }

    /**
     * Replaces the string processor of this parser.
     *
     * @param stringProcessor the new string processor
     */
    public void stringProcessor(final @NonNull StringProcessor stringProcessor) {
        this.stringProcessor = stringProcessor;
    }

    /**
     * Processes the {@code input} string and returns the processed result.
     *
     * @param input the input string
     * @return the processed string
     */
    public @NonNull String processString(final @NonNull String input) {
        return this.stringProcessor().processString(input);
    }

    /**
     * Processes the input {@code strings} and returns the processed result.
     *
     * @param strings the input strings
     * @return the processed strings
     */
    public @NonNull String[] processStrings(final @NonNull String[] strings) {
        return Arrays.stream(strings).map(this::processString).toArray(String[]::new);
    }

    /**
     * Processes the input {@code strings} and returns the processed result.
     *
     * @param strings the input strings
     * @return the processed strings
     */
    @API(status = API.Status.STABLE)
    public @NonNull List<@NonNull String> processStrings(final @NonNull Collection<@NonNull String> strings) {
        return strings.stream().map(this::processString).collect(Collectors.toList());
    }

    /**
     * Returns the syntax parser.
     *
     * @return the syntax parser
     */
    public @NonNull SyntaxParser syntaxParser() {
        return this.syntaxParser;
    }

    /**
     * Sets the syntax parser.
     *
     * @param syntaxParser new syntax parser
     */
    @API(status = API.Status.STABLE)
    public void syntaxParser(final @NonNull SyntaxParser syntaxParser) {
        this.syntaxParser = syntaxParser;
    }

    /**
     * Returns the argument extractor.
     *
     * @return the argument extractor
     */
    @API(status = API.Status.STABLE)
    public @NonNull ArgumentExtractor argumentExtractor() {
        return this.argumentExtractor;
    }

    /**
     * Sets the argument extractor.
     *
     * @param argumentExtractor new argument extractor
     */
    @API(status = API.Status.STABLE)
    public void argumentExtractor(final @NonNull ArgumentExtractor argumentExtractor) {
        this.argumentExtractor = argumentExtractor;
    }

    /**
     * Returns the argument assembler.
     *
     * @return the argument assembler
     */
    @API(status = API.Status.STABLE)
    public @NonNull ArgumentAssembler<C> argumentAssembler() {
        return this.argumentAssembler;
    }

    /**
     * Sets the argument assembler
     *
     * @param argumentAssembler new argument assembler
     */
    @API(status = API.Status.STABLE)
    public void argumentAssembler(final @NonNull ArgumentAssembler<C> argumentAssembler) {
        this.argumentAssembler = argumentAssembler;
    }

    /**
     * Returns the flag extractor.
     *
     * @return the flag extractor
     */
    @API(status = API.Status.STABLE)
    public @NonNull FlagExtractor flagExtractor() {
        return this.flagExtractor;
    }

    /**
     * Sets the flag extractor.
     *
     * @param flagExtractor new flag extractor
     */
    @API(status = API.Status.STABLE)
    public void flagExtractor(final @NonNull FlagExtractor flagExtractor) {
        this.flagExtractor = flagExtractor;
    }

    /**
     * Returns the flag assembler.
     *
     * @return the flag assembler
     */
    @API(status = API.Status.STABLE)
    public @NonNull FlagAssembler flagAssembler() {
        return this.flagAssembler;
    }

    /**
     * Sets the flag assembler
     *
     * @param flagAssembler new flag assembler
     */
    @API(status = API.Status.STABLE)
    public void flagAssembler(final @NonNull FlagAssembler flagAssembler) {
        this.flagAssembler = flagAssembler;
    }

    /**
     * Returns the command extractor.
     *
     * @return the command extractor
     */
    @API(status = API.Status.STABLE)
    public @NonNull CommandExtractor commandExtractor() {
        return this.commandExtractor;
    }

    /**
     * Sets the command extractor.
     *
     * @param commandExtractor new command extractor
     */
    @API(status = API.Status.STABLE)
    public void commandExtractor(final @NonNull CommandExtractor commandExtractor) {
        this.commandExtractor = commandExtractor;
    }

    /**
     * Returns the suggestion provider factory.
     *
     * @return the suggestion provider factory
     */
    @API(status = API.Status.STABLE)
    public @NonNull SuggestionProviderFactory<C> suggestionProviderFactory() {
        return this.suggestionProviderFactory;
    }

    /**
     * Sets the suggestion provider factory.
     *
     * @param suggestionProviderFactory new suggestion provider factory
     */
    @API(status = API.Status.STABLE)
    public void suggestionProviderFactory(final @NonNull SuggestionProviderFactory<C> suggestionProviderFactory) {
        this.suggestionProviderFactory = suggestionProviderFactory;
    }

    /**
     * Returns the method argument parser factory.
     *
     * @return the method argument parser factory
     */
    @API(status = API.Status.EXPERIMENTAL)
    public @NonNull MethodArgumentParserFactory<C> methodArgumentParserFactory() {
        return this.methodArgumentParserFactory;
    }

    /**
     * Sets the method argument parser factory.
     *
     * @param methodArgumentParserFactory new method argument parser factory
     */
    @API(status = API.Status.EXPERIMENTAL)
    public void methodArgumentParserFactory(final @NonNull MethodArgumentParserFactory<C> methodArgumentParserFactory) {
        this.methodArgumentParserFactory = methodArgumentParserFactory;
    }

    /**
     * Returns the exception provider factory.
     *
     * @return the exception provider factory
     */
    @API(status = API.Status.STABLE)
    public @NonNull ExceptionHandlerFactory<C> exceptionHandlerFactory() {
        return this.exceptionHandlerFactory;
    }

    /**
     * Sets the exception provider factory.
     *
     * @param exceptionHandlerFactory new exception provider factory
     */
    @API(status = API.Status.STABLE)
    public void exceptionHandlerFactory(final @NonNull ExceptionHandlerFactory<C> exceptionHandlerFactory) {
        this.exceptionHandlerFactory = exceptionHandlerFactory;
    }

    /**
     * Returns the description mapper.
     *
     * @return the description mapper
     */
    @API(status = API.Status.STABLE)
    public @NonNull DescriptionMapper descriptionMapper() {
        return this.descriptionMapper;
    }

    /**
     * Sets the description mapper.
     *
     * @param descriptionMapper new description mapper
     */
    @API(status = API.Status.STABLE)
    public void descriptionMapper(final @NonNull DescriptionMapper descriptionMapper) {
        this.descriptionMapper = descriptionMapper;
    }

    /**
     * Returns the default value registry.
     *
     * @return the default value registry
     */
    public @NonNull DefaultValueRegistry<C> defaultValueRegistry() {
        return this.defaultValueRegistry;
    }

    /**
     * Sets the default value registry.
     *
     * @param defaultValueRegistry default value registry
     */
    public void defaultValueRegistry(final @NonNull DefaultValueRegistry<C> defaultValueRegistry) {
        this.defaultValueRegistry = Objects.requireNonNull(defaultValueRegistry, "defaultValueRegistry");
    }

    /**
     * Parses all known {@link org.incendo.cloud.annotations.processing.CommandContainer command containers}.
     *
     * <p>This will use the {@link ClassLoader class loader} of the current class to retrieve the stored information about the
     * command containers.</p>
     *
     * @return Collection of parsed commands
     * @throws Exception re-throws all encountered exceptions.
     * @see org.incendo.cloud.annotations.processing.CommandContainer CommandContainer for more information.
     */
    public @NonNull Collection<org.incendo.cloud.@NonNull Command<C>> parseContainers() throws Exception {
        return this.parseContainers(this.getClass().getClassLoader());
    }

    /**
     * Parses all known {@link org.incendo.cloud.annotations.processing.CommandContainer command containers}.
     *
     * @param classLoader class loader to use to scan for {@link CommandContainerProcessor#PATH}
     * @return Collection of parsed commands
     * @throws Exception re-throws all encountered exceptions.
     * @see org.incendo.cloud.annotations.processing.CommandContainer CommandContainer for more information.
     */
    @API(status = API.Status.STABLE)
    public @NonNull Collection<org.incendo.cloud.@NonNull Command<C>> parseContainers(
            final @NonNull ClassLoader classLoader
    ) throws Exception {
        final List<String> classNames;
        try (InputStream stream = classLoader.getResourceAsStream(CommandContainerProcessor.PATH)) {
            if (stream == null) {
                return Collections.emptyList();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                classNames = reader.lines().distinct().collect(Collectors.toList());
            }
        }

        final List<Class<?>> classes = new ArrayList<>();
        for (final String className : classNames) {
            classes.add(Class.forName(className, true, classLoader));
        }
        classes.sort(COMMAND_CONTAINER_COMPARATOR);

        final List<Object> instances = new LinkedList<>();
        for (final Class<?> commandContainer : classes) {
            // We now have the class, and we now just need to decide what constructor to invoke.
            // We first try to find a constructor which takes in the parser.
            @MonotonicNonNull Object instance;
            try {
                instance = commandContainer.getConstructor(AnnotationParser.class).newInstance(this);
            } catch (final NoSuchMethodException ignored) {
                try {
                    // Then we try to find a no-arg constructor.
                    instance = commandContainer.getConstructor().newInstance();
                } catch (final NoSuchMethodException e) {
                    // If neither are found, we panic!
                    throw new IllegalStateException(
                            String.format(
                                    "Command container %s has no valid constructors",
                                    commandContainer
                            ),
                            e
                    );
                }
            }
            instances.add(instance);
        }

        return this.parse(instances);
    }

    /**
     * Scan some instances of {@link Command}-annotated types and attempt to
     * compile them into {@link org.incendo.cloud.Command} instances.
     *
     * @param instances instances to scan
     * @return collection of parsed commands
     */
    public @NonNull Collection<org.incendo.cloud.@NonNull Command<C>> parse(final @NonNull Object @NonNull... instances) {
        return this.parse(Arrays.asList(instances));
    }

    /**
     * Scan some instances of {@link Command}-annotated types and attempt to
     * compile them into {@link org.incendo.cloud.Command} instances.
     *
     * @param instances instances to scan
     * @return collection of parsed commands
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public @NonNull Collection<org.incendo.cloud.@NonNull Command<C>> parse(final @NonNull Collection<@NonNull Object> instances) {
        for (final Object instance : instances) {
            this.parseDefaultValues(instance);
        }
        for (final Object instance : instances) {
            this.parseSuggestions(instance);
        }
        for (final Object instance : instances) {
            this.parseParsers(instance);
        }
        for (final Object instance : instances) {
            this.parseExceptionHandlers(instance);
        }

        final List<org.incendo.cloud.Command<C>> result = new ArrayList<>();
        for (final Object instance : instances) {
            final Collection<CommandDescriptor> commandDescriptors = this.commandExtractor.extractCommands(instance);
            final Collection<org.incendo.cloud.Command<C>> commands = this.construct(instance, commandDescriptors);
            for (final org.incendo.cloud.Command<C> command : commands) {
                ((CommandManager) this.manager).command(command);
            }
            result.addAll(commands);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Maps the given {@code string} into a {@link Description}.
     *
     * @param string description literal
     * @return the description
     */
    @API(status = API.Status.INTERNAL, consumers = "org.incendo.cloud.annotations.*")
    public @NonNull Description mapDescription(final @NonNull String string) {
        return this.descriptionMapper.map(this.processString(string));
    }

    private <T> void parseSuggestions(final @NonNull T instance) {
        for (final Method method : instance.getClass().getMethods()) {
            final Suggestions suggestions = method.getAnnotation(Suggestions.class);
            if (suggestions == null) {
                continue;
            }
            if (!method.isAccessible()) {
                method.setAccessible(true);
            }

            final boolean valid = Iterable.class.isAssignableFrom(method.getReturnType())
                    || method.getReturnType().equals(Stream.class)
                    || method.getReturnType().equals(CompletableFuture.class)
                    || method.getReturnType().getSimpleName().equals("Sequence")
                    || method.getParameterCount() == 3; /* TODO(City): Fix this... */

            if (!valid) {
                throw new IllegalArgumentException(String.format(
                        "@Suggestions annotated method '%s' in class '%s' does not have the correct signature",
                        method.getName(),
                        instance.getClass().getCanonicalName()
                ));
            }

            try {

                this.manager.parserRegistry().registerSuggestionProvider(
                        this.processString(suggestions.value()),
                        this.suggestionProviderFactory.createSuggestionProvider(
                                instance,
                                method,
                                this.manager.parameterInjectorRegistry()
                        )
                );
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void parseExceptionHandlers(final @NonNull T instance) {
        for (final Method method : instance.getClass().getMethods()) {
            final ExceptionHandler exceptionHandler = method.getAnnotation(ExceptionHandler.class);
            if (exceptionHandler == null) {
                continue;
            }
            if (!method.isAccessible()) {
                method.setAccessible(true);
            }

            try {
                this.manager.exceptionController().registerHandler(
                        (Class<Throwable>) exceptionHandler.value(),
                        this.exceptionHandlerFactory.createExceptionHandler(
                                instance,
                                method,
                                this.manager.parameterInjectorRegistry()
                        )
                );
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private <T> void parseDefaultValues(final @NonNull T instance) {
        for (final Method method : instance.getClass().getMethods()) {
            final Default defaultValue = method.getAnnotation(Default.class);
            if (defaultValue == null) {
                continue;
            }

            final String name;
            if (defaultValue.name().isEmpty()) {
                name = method.getName();
            } else {
                name = defaultValue.name();
            }

            this.defaultValueRegistry().register(name, new MethodDefaultValueFactory<>(method, instance));
        }
    }

    private <T> void parseParsers(final @NonNull T instance) {
        for (final Method method : instance.getClass().getMethods()) {
            final Parser parser = method.getAnnotation(Parser.class);
            if (parser == null) {
                continue;
            }
            try {
                final String suggestions = this.processString(parser.suggestions());
                final SuggestionProvider<C> suggestionProvider;
                if (suggestions.isEmpty()) {
                    suggestionProvider = SuggestionProvider.noSuggestions();
                } else {
                    suggestionProvider = this.manager.parserRegistry().getSuggestionProvider(suggestions)
                            .orElseThrow(() -> new NullPointerException(
                                    String.format(
                                            "Cannot find the suggestion provider with name '%s'",
                                            suggestions
                                    )
                            ));
                }
                final ParserDescriptor<C, ?> parserDescriptor = this.methodArgumentParserFactory.createArgumentParser(
                        suggestionProvider,
                        instance,
                        method,
                        this.manager.parameterInjectorRegistry()
                );
                final String name = this.processString(parser.name());
                if (name.isEmpty()) {
                    this.manager.parserRegistry().registerParser(parserDescriptor);
                } else {
                    this.manager.parserRegistry().registerNamedParser(name, parserDescriptor);
                }
            } catch (final Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    private @NonNull Collection<org.incendo.cloud.@NonNull Command<C>> construct(
            final @NonNull Object instance,
            final @NonNull Collection<@NonNull CommandDescriptor> commandDescriptors
    ) {
        return commandDescriptors.stream()
                .flatMap(descriptor -> this.constructCommands(instance, descriptor).stream())
                .collect(Collectors.toList());
    }

    @SuppressWarnings({"unchecked"})
    private @NonNull Collection<org.incendo.cloud.@NonNull Command<C>> constructCommands(
            final @NonNull Object instance,
            final @NonNull CommandDescriptor commandDescriptor
    ) {
        final AnnotationAccessor classAnnotations = AnnotationAccessor.of(instance.getClass());
        final List<org.incendo.cloud.Command<C>> commands = new ArrayList<>();

        final Method method = commandDescriptor.method();
        final CommandManager<C> manager = this.manager;
        final CommandMetaBuilder metaBuilder = CommandMeta.builder().with(this.metaFactory.apply(method));

        org.incendo.cloud.Command.Builder<C> builder = manager.commandBuilder(
                commandDescriptor.commandToken(),
                commandDescriptor.syntax().get(0).minor(),
                metaBuilder.build()
        );
        for (final BuilderDecorator<C> decorator : this.builderDecorators) {
            builder = decorator.decorate(builder);
        }

        final Collection<ArgumentDescriptor> arguments = this.argumentExtractor.extractArguments(
                commandDescriptor.syntax(),
                method
        );
        final Collection<FlagDescriptor> flagDescriptors = this.flagExtractor.extractFlags(method);
        final Collection<CommandFlag<?>> flags = flagDescriptors.stream()
                .map(this.flagAssembler()::assembleFlag)
                .collect(Collectors.toList());
        final Map<String, CommandComponent<C>> commandComponents = this.constructComponents(arguments, commandDescriptor);

        boolean commandNameFound = false;
        /* Build the command tree */
        for (final SyntaxFragment token : commandDescriptor.syntax()) {
            if (!commandNameFound) {
                commandNameFound = true;
                continue;
            }
            if (token.argumentMode() == ArgumentMode.LITERAL) {
                builder = builder.literal(token.major(), token.minor().toArray(new String[0]));
            } else {
                final CommandComponent<C> component = commandComponents.get(token.major());
                if (component == null) {
                    throw new IllegalArgumentException(String.format(
                            "Found no mapping for argument '%s' in method '%s'",
                            token.major(), method.getName()
                    ));
                }

                builder = builder.argument(component);
            }
        }
        /* Try to find the command sender type */
        Class<? extends C> senderType = null;
        for (final Parameter parameter : method.getParameters()) {
            if (parameter.isAnnotationPresent(Argument.class)) {
                continue;
            }
            if (GenericTypeReflector.isSuperType(this.commandSenderType.getType(), parameter.getType())) {
                senderType = (Class<? extends C>) parameter.getType();
                break;
            }
        }

        final Permission permission = getMethodOrClassAnnotation(method, Permission.class);
        if (permission != null) {
            final String[] permissions = permission.value();
            if (permissions.length == 1) {
                builder = builder.permission(this.processString(permissions[0]));
            } else if (permissions.length > 1) {
                builder = builder.permission(
                        permission.mode().combine(
                                Arrays.stream(permissions)
                                        .map(this::processString)
                                        .map(org.incendo.cloud.permission.Permission::permission)
                        )
                );
            }
        }

        if (commandDescriptor.requiredSender() != Object.class) {
            if (!GenericTypeReflector.isSuperType(this.commandSenderType.getType(), commandDescriptor.requiredSender())) {
                throw new IllegalArgumentException(String.format(
                        "Command method %s#%s(%s) has invalid sender type requirement: %s does not inherit from %s",
                        method.getDeclaringClass().getName(),
                        method.getName(),
                        Arrays.stream(method.getParameterTypes()).map(Class::getName).collect(Collectors.joining(", ")),
                        commandDescriptor.requiredSender().getName(),
                        this.commandSenderType.getType().getTypeName()
                ));
            }
            builder = builder.senderType((Class<? extends C>) commandDescriptor.requiredSender());
        } else if (senderType != null) {
            builder = builder.senderType(senderType);
        }
        try {
            final MethodCommandExecutionHandler.CommandMethodContext<C> context =
                    new MethodCommandExecutionHandler.CommandMethodContext<>(
                            instance,
                            commandComponents,
                            arguments,
                            flagDescriptors,
                            method,
                            this /* annotationParser */
                    );

            /* Create the command execution handler */
            CommandExecutionHandler<C> commandExecutionHandler = new MethodCommandExecutionHandler<>(context);
            for (final Map.Entry<Predicate<Method>, CommandMethodExecutionHandlerFactory<C>> entry
                    : this.commandMethodFactories.entrySet()) {
                if (entry.getKey().test(method)) {
                    commandExecutionHandler = entry.getValue().createExecutionHandler(context);

                    /* Once we have our custom handler, we stop */
                    break;
                }
            }

            builder = builder.handler(commandExecutionHandler);
        } catch (final Exception e) {
            throw new RuntimeException("Failed to construct command execution handler", e);
        }
        /* Apply flags */
        for (final CommandFlag<?> flag : flags) {
            builder = builder.flag(flag);
        }

        /* Apply builder modifiers */
        for (final Annotation annotation
                : AnnotationAccessor.of(classAnnotations, AnnotationAccessor.of(method)).annotations()) {
            @SuppressWarnings("rawtypes") final BuilderModifier builderModifier = this.builderModifiers.get(
                    annotation.annotationType()
            );
            if (builderModifier == null) {
                continue;
            }
            builder = (org.incendo.cloud.Command.Builder<C>) builderModifier.modifyBuilder(annotation, builder);
        }

        /* Construct and register the command */
        final org.incendo.cloud.Command<C> builtCommand = builder.build();
        commands.add(builtCommand);

        if (method.isAnnotationPresent(ProxiedBy.class)) {
            manager.command(this.constructProxy(method.getAnnotation(ProxiedBy.class), builtCommand));
        }

        return commands;
    }

    private @NonNull Map<@NonNull String, @NonNull CommandComponent<C>> constructComponents(
            final @NonNull Collection<@NonNull ArgumentDescriptor> arguments,
            final @NonNull CommandDescriptor commandDescriptor
    ) {
        return arguments.stream()
                .map(argumentDescriptor -> this.argumentAssembler.assembleArgument(
                        this.findSyntaxFragment(commandDescriptor.syntax(), this.processString(argumentDescriptor.name())),
                        argumentDescriptor
                )).map(component -> Pair.of(component.name(), component))
                .collect(Collectors.toMap(Pair::first, Pair::second));
    }

    private org.incendo.cloud.@NonNull Command<C> constructProxy(
            final @NonNull ProxiedBy proxyAnnotation,
            final org.incendo.cloud.@NonNull Command<C> command
    ) {
        final String proxy = this.processString(proxyAnnotation.value());
        if (proxy.contains(" ")) {
            throw new IllegalArgumentException("@ProxiedBy proxies may only contain single literals");
        }
        return this.manager.commandBuilder(proxy, command.commandMeta())
                .proxies(command)
                .build();
    }

    private @NonNull SyntaxFragment findSyntaxFragment(
            final @NonNull List<@NonNull SyntaxFragment> fragments,
            final @NonNull String argumentName
    ) {
        return fragments.stream().filter(fragment -> fragment.argumentMode() != ArgumentMode.LITERAL)
                .filter(fragment -> fragment.major().equals(argumentName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Argument is not declared in syntax: " + argumentName));
    }

    @NonNull Map<Class<? extends @NonNull Annotation>, AnnotationMapper<?>> annotationMappers() {
        return this.annotationMappers;
    }
}
