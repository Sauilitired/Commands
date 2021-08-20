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
package cloud.commandframework.minecraft.extras;

import cloud.commandframework.ArgumentDescription;
import cloud.commandframework.Command;
import cloud.commandframework.CommandComponent;
import cloud.commandframework.CommandHelpHandler;
import cloud.commandframework.CommandManager;
import cloud.commandframework.arguments.CommandArgument;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.LinearComponents;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static net.kyori.adventure.text.Component.space;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.event.ClickEvent.runCommand;

/**
 * Opinionated extension of {@link CommandHelpHandler} for Minecraft
 *
 * @param <C> Command sender type
 */
@SuppressWarnings("unused")
public final class MinecraftHelp<C> {

    public static final int DEFAULT_HEADER_FOOTER_LENGTH = 46;
    public static final int DEFAULT_MAX_RESULTS_PER_PAGE = 6;

    /**
     * The default color scheme for {@link MinecraftHelp}
     */
    public static final HelpColors DEFAULT_HELP_COLORS = HelpColors.of(
            NamedTextColor.GOLD,
            NamedTextColor.GREEN,
            NamedTextColor.YELLOW,
            NamedTextColor.GRAY,
            NamedTextColor.DARK_GRAY
    );
//todo show permission
    public static final String MESSAGE_HELP_TITLE = "help";
    public static final String MESSAGE_COMMAND = "command";
    public static final String MESSAGE_DESCRIPTION = "description";
    public static final String MESSAGE_NO_DESCRIPTION = "no_description";
    public static final String MESSAGE_ARGUMENTS = "arguments";
    public static final String MESSAGE_OPTIONAL = "optional";
    public static final String MESSAGE_SHOWING_RESULTS_FOR_QUERY = "showing_results_for_query";
    public static final String MESSAGE_NO_RESULTS_FOR_QUERY = "no_results_for_query";
    public static final String MESSAGE_AVAILABLE_COMMANDS = "available_commands";
    public static final String MESSAGE_CLICK_TO_SHOW_HELP = "click_to_show_help";
    public static final String MESSAGE_PAGE_OUT_OF_RANGE = "page_out_of_range";
    public static final String MESSAGE_CLICK_FOR_NEXT_PAGE = "click_for_next_page";
    public static final String MESSAGE_CLICK_FOR_PREVIOUS_PAGE = "click_for_previous_page";

    private final AudienceProvider<C> audienceProvider;
    private final CommandManager<C> commandManager;
    private final String commandPrefix;
    private final Map<String, String> messageMap = new HashMap<>();

    private Predicate<Command<C>> commandFilter = c -> true;
    private BiFunction<C, String, String> stringMessageProvider = (sender, key) -> this.messageMap.get(key);
    private MessageProvider<C> messageProvider =
            (sender, key, args) -> text(this.stringMessageProvider.apply(sender, key));
    private Function<String, Component> descriptionDecorator = Component::text;
    private HelpColors colors = DEFAULT_HELP_COLORS;
    private int headerFooterLength = DEFAULT_HEADER_FOOTER_LENGTH;
    private int maxResultsPerPage = DEFAULT_MAX_RESULTS_PER_PAGE;


    /**
     * Construct a new Minecraft help instance for a sender type which is an {@link Audience}.
     *
     * @param commandPrefix  Command that was used to trigger the help menu. Used to help insertion generation
     * @param commandManager command manager
     * @param <C>            sender type extending {@link Audience}
     * @return new MinecraftHelp instance
     * @since 1.5.0
     */
    public static <C extends Audience> MinecraftHelp<C> createNative(
            final @NonNull String commandPrefix,
            final @NonNull CommandManager<C> commandManager
    ) {
        return new MinecraftHelp<>(
                commandPrefix,
                AudienceProvider.nativeAudience(),
                commandManager
        );
    }

    /**
     * Construct a new Minecraft help instance.
     *
     * @param commandPrefix    Command that was used to trigger the help menu. Used to help insertion generation
     * @param audienceProvider Provider that maps the command sender type to {@link Audience}
     * @param commandManager   Command manager instance
     */
    public MinecraftHelp(
            final @NonNull String commandPrefix,
            final @NonNull AudienceProvider<C> audienceProvider,
            final @NonNull CommandManager<C> commandManager
    ) {
        this.commandPrefix = commandPrefix;
        this.audienceProvider = audienceProvider;
        this.commandManager = commandManager;

        /* Default Messages */
        this.messageMap.put(MESSAGE_HELP_TITLE, "Help");
        this.messageMap.put(MESSAGE_COMMAND, "Command");
        this.messageMap.put(MESSAGE_DESCRIPTION, "Description");
        this.messageMap.put(MESSAGE_NO_DESCRIPTION, "No description");
        this.messageMap.put(MESSAGE_ARGUMENTS, "Arguments");
        this.messageMap.put(MESSAGE_OPTIONAL, "Optional");
        this.messageMap.put(MESSAGE_SHOWING_RESULTS_FOR_QUERY, "Showing search results for query");
        this.messageMap.put(MESSAGE_NO_RESULTS_FOR_QUERY, "No results for query");
        this.messageMap.put(MESSAGE_AVAILABLE_COMMANDS, "Available Commands");
        this.messageMap.put(MESSAGE_CLICK_TO_SHOW_HELP, "Click to show help for this command");
        this.messageMap.put(MESSAGE_PAGE_OUT_OF_RANGE, "Error: Page <page> is not in range. Must be in range [1, <max_pages>]");
        this.messageMap.put(MESSAGE_CLICK_FOR_NEXT_PAGE, "Click for next page");
        this.messageMap.put(MESSAGE_CLICK_FOR_PREVIOUS_PAGE, "Click for previous page");
    }

    /**
     * Get the command manager instance
     *
     * @return Command manager
     */
    public @NonNull CommandManager<C> getCommandManager() {
        return this.commandManager;
    }

    /**
     * Get the audience provider that was used to create this instance
     *
     * @return Audience provider
     */
    public @NonNull AudienceProvider<C> getAudienceProvider() {
        return this.audienceProvider;
    }

    /**
     * Map a command sender to an {@link Audience}
     *
     * @param sender Sender to map
     * @return Mapped audience
     */
    public @NonNull Audience getAudience(final @NonNull C sender) {
        return this.audienceProvider.apply(sender);
    }

    /**
     * Sets a filter for what commands are visible inside the help menu.
     * When the {@link Predicate} tests {@code true}, then the command
     * is included in the listings.
     * <p>
     * The default filter will return true for all commands.
     *
     * @param commandPredicate Predicate to filter commands by
     * @since 1.4.0
     */
    public void commandFilter(final @NonNull Predicate<Command<C>> commandPredicate) {
        this.commandFilter = commandPredicate;
    }

    /**
     * Set the description decorator which will turn command and argument description strings into components.
     * <p>
     * The default decorator simply calls {@link Component#text(String)}
     *
     * @param decorator description decorator
     * @since 1.4.0
     */
    public void descriptionDecorator(final @NonNull Function<@NonNull String, @NonNull Component> decorator) {
        this.descriptionDecorator = decorator;
    }

    /**
     * Configure a message
     *
     * @param key   Message key. These are constants in {@link MinecraftHelp}
     * @param value The text for the message
     */
    public void setMessage(
            final @NonNull String key,
            final @NonNull String value
    ) {
        this.messageMap.put(key, value);
    }

    /**
     * Set a custom message provider function to be used for getting messages from keys.
     * <p>
     * The keys are constants in {@link MinecraftHelp}.
     *
     * @param messageProvider The message provider to use
     */
    public void setMessageProvider(final @NonNull BiFunction<C, String, String> messageProvider) {
        this.stringMessageProvider = messageProvider;
    }

    /**
     * Set a custom message provider function to be used for getting messages from keys.
     * <p>
     * The keys are constants in {@link MinecraftHelp}.
     * <p>
     * This version of the method which takes a {@link MessageProvider} will have priority over a message provider
     * registered through {@link #setMessageProvider(BiFunction)}
     *
     * @param messageProvider The message provider to use
     * @since 1.4.0
     */
    public void messageProvider(final @NonNull MessageProvider<C> messageProvider) {
        this.messageProvider = messageProvider;
    }

    /**
     * Set the colors to use for help messages.
     *
     * @param colors The new {@link HelpColors} to use
     */
    public void setHelpColors(final @NonNull HelpColors colors) {
        this.colors = colors;
    }

    /**
     * Get the colors used for help messages.
     *
     * @return The active {@link HelpColors}
     */
    public @NonNull HelpColors getHelpColors() {
        return this.colors;
    }

    /**
     * Set the length of the header/footer of help menus
     * <p>
     * Defaults to {@link MinecraftHelp#DEFAULT_HEADER_FOOTER_LENGTH}
     *
     * @param headerFooterLength The new length
     */
    public void setHeaderFooterLength(final int headerFooterLength) {
        this.headerFooterLength = headerFooterLength;
    }

    /**
     * Set the maximum number of help results to display on one page
     * <p>
     * Defaults to {@link MinecraftHelp#DEFAULT_MAX_RESULTS_PER_PAGE}
     *
     * @param maxResultsPerPage The new value
     */
    public void setMaxResultsPerPage(final int maxResultsPerPage) {
        this.maxResultsPerPage = maxResultsPerPage;
    }

    /**
     * Query commands and send the results to the recipient. Will respect permissions.
     *
     * @param rawQuery  Command query (without leading '/', including optional page number)
     * @param recipient Recipient
     */
    public void queryCommands(
            final @NonNull String rawQuery,
            final @NonNull C recipient
    ) {
        final String[] splitQuery = rawQuery.split(" ");
        int page;
        String query;
        try {
            final String pageText = splitQuery[splitQuery.length - 1];
            page = Integer.parseInt(pageText);
            query = rawQuery.substring(0, Math.max(rawQuery.lastIndexOf(pageText) - 1, 0));
        } catch (NumberFormatException e) {
            page = 1;
            query = rawQuery;
        }
        final Audience audience = this.getAudience(recipient);
        this.printTopic(
                recipient,
                query,
                page,
                this.commandManager.getCommandHelpHandler(this.commandFilter).queryHelp(recipient, query)
        );
    }

    private void printTopic(
            final @NonNull C sender,
            final @NonNull String query,
            final int page,
            final CommandHelpHandler.@NonNull HelpTopic<C> helpTopic
    ) {
        if (helpTopic instanceof CommandHelpHandler.IndexHelpTopic) {
            this.printIndexHelpTopic(sender, query, page, (CommandHelpHandler.IndexHelpTopic<C>) helpTopic);
        } else if (helpTopic instanceof CommandHelpHandler.MultiHelpTopic) {
            this.printMultiHelpTopic(sender, query, page, (CommandHelpHandler.MultiHelpTopic<C>) helpTopic);
        } else if (helpTopic instanceof CommandHelpHandler.VerboseHelpTopic) {
            this.printVerboseHelpTopic(sender, query, (CommandHelpHandler.VerboseHelpTopic<C>) helpTopic);
        } else {
            throw new IllegalArgumentException("Unknown help topic type");
        }
    }

    private void printNoResults(
            final @NonNull C sender,
            final @NonNull String query
    ) {
        final Audience audience = this.getAudience(sender);
        audience.sendMessage(this.basicHeader(sender));
        audience.sendMessage(LinearComponents.linear(
                this.messageProvider.provide(sender, MESSAGE_NO_RESULTS_FOR_QUERY).color(this.colors.text),
                text(": \"", this.colors.text),
                this.highlight(text("/" + query, this.colors.highlight)),
                text("\"", this.colors.text)
        ));
        audience.sendMessage(this.footer(sender));
    }

    private void printIndexHelpTopic(
            final @NonNull C sender,
            final @NonNull String query,
            final int page,
            final CommandHelpHandler.@NonNull IndexHelpTopic<C> helpTopic
    ) {
        if (helpTopic.isEmpty()) {
            this.printNoResults(sender, query);
            return;
        }

        final Audience audience = this.getAudience(sender);
        new Pagination<CommandHelpHandler.VerboseHelpEntry<C>>(
                (currentPage, maxPages) -> {
                    final List<Component> header = new ArrayList<>();
                    header.add(this.paginatedHeader(sender, currentPage, maxPages));
                    header.add(this.showingResults(sender, query));
                    header.add(text()
                            .append(this.lastBranch())
                            .append(space())
                            .append(
                                    this.messageProvider.provide(
                                            sender,
                                            MESSAGE_AVAILABLE_COMMANDS
                                    ).color(this.colors.text)
                            )
                            .append(text(":", this.colors.text))
                            .build()
                    );
                    return header;
                },
                (helpEntry, isLastOfPage) -> {
                    final Optional<Component> richDescription =
                            helpEntry.getCommand().getCommandMeta().get(MinecraftExtrasMetaKeys.DESCRIPTION);
                    final Component description;
                    if (richDescription.isPresent()) {
                        description = richDescription.get();
                    } else if (helpEntry.getDescription().isEmpty()) {
                        description = this.messageProvider.provide(sender, MESSAGE_CLICK_TO_SHOW_HELP);
                    } else {
                        description = this.descriptionDecorator.apply(helpEntry.getDescription());
                    }

                    final boolean lastBranch =
                            isLastOfPage || helpTopic.getEntries().indexOf(helpEntry) == helpTopic.getEntries().size() - 1;

                    return text()
                            .append(text("   "))
                            .append(lastBranch ? this.lastBranch() : this.branch())
                            .append(this.highlight(text(
                                    String.format(" /%s", helpEntry.getSyntaxString()),
                                    this.colors.highlight
                                    ))
                                            .hoverEvent(description.color(this.colors.text))
                                            .clickEvent(runCommand(this.commandPrefix + " " + helpEntry.getSyntaxString()))
                            )
                            .build();
                },
                (currentPage, maxPages) -> this.paginatedFooter(sender, currentPage, maxPages, query),
                (attemptedPage, maxPages) -> this.pageOutOfRange(sender, attemptedPage, maxPages)
        ).render(helpTopic.getEntries(), page, this.maxResultsPerPage).forEach(audience::sendMessage);
    }

    private void printMultiHelpTopic(
            final @NonNull C sender,
            final @NonNull String query,
            final int page,
            final CommandHelpHandler.@NonNull MultiHelpTopic<C> helpTopic
    ) {
        if (helpTopic.getChildSuggestions().isEmpty()) {
            this.printNoResults(sender, query);
            return;
        }

        final Audience audience = this.getAudience(sender);
        final int headerIndentation = helpTopic.getLongestPath().length();
        new Pagination<String>(
                (currentPage, maxPages) -> {
                    final List<Component> header = new ArrayList<>();
                    header.add(this.paginatedHeader(sender, currentPage, maxPages));
                    header.add(this.showingResults(sender, query));
                    header.add(this.lastBranch()
                            .append(this.highlight(text(" /" + helpTopic.getLongestPath(), this.colors.highlight))));
                    return header;
                },
                (suggestion, isLastOfPage) -> {
                    final boolean lastBranch = isLastOfPage
                            || helpTopic.getChildSuggestions().indexOf(suggestion) == helpTopic.getChildSuggestions().size() - 1;

                    return ComponentHelper.repeat(space(), headerIndentation)
                            .append(lastBranch ? this.lastBranch() : this.branch())
                            .append(this.highlight(text(" /" + suggestion, this.colors.highlight))
                                    .hoverEvent(this.messageProvider.provide(sender, MESSAGE_CLICK_TO_SHOW_HELP)
                                            .color(this.colors.text))
                                    .clickEvent(runCommand(this.commandPrefix + " " + suggestion)));
                },
                (currentPage, maxPages) -> this.paginatedFooter(sender, currentPage, maxPages, query),
                (attemptedPage, maxPages) -> this.pageOutOfRange(sender, attemptedPage, maxPages)
        ).render(helpTopic.getChildSuggestions(), page, this.maxResultsPerPage).forEach(audience::sendMessage);
    }

    private void printVerboseHelpTopic(
            final @NonNull C sender,
            final @NonNull String query,
            final CommandHelpHandler.@NonNull VerboseHelpTopic<C> helpTopic
    ) {
        final Audience audience = this.getAudience(sender);
        audience.sendMessage(this.basicHeader(sender));
        audience.sendMessage(this.showingResults(sender, query));
        final String command = this.commandManager.getCommandSyntaxFormatter()
                .apply(helpTopic.getCommand().getArguments(), null);
        audience.sendMessage(text()
                .append(this.lastBranch())
                .append(space())
                .append(this.messageProvider.provide(sender, MESSAGE_COMMAND).color(this.colors.primary))
                .append(text(": ", this.colors.primary))
                .append(this.highlight(text("/" + command, this.colors.highlight)))
        );
        /* Topics will use the long description if available, but fall back to the short description. */
        final Component richDescription =
                helpTopic.getCommand().getCommandMeta().get(MinecraftExtrasMetaKeys.LONG_DESCRIPTION)
                        .orElse(helpTopic.getCommand().getCommandMeta().get(MinecraftExtrasMetaKeys.DESCRIPTION)
                                .orElse(null));

        final Component topicDescription;
        if (richDescription != null) {
            topicDescription = richDescription;
        } else if (helpTopic.getDescription().isEmpty()) {
            topicDescription = this.messageProvider.provide(sender, MESSAGE_NO_DESCRIPTION);
        } else {
            topicDescription = this.descriptionDecorator.apply(helpTopic.getDescription());
        }

        final boolean hasArguments = helpTopic.getCommand().getArguments().size() > 1;
        audience.sendMessage(text()
                .append(text("   "))
                .append(hasArguments ? this.branch() : this.lastBranch())
                .append(space())
                .append(this.messageProvider.provide(sender, MESSAGE_DESCRIPTION).color(this.colors.primary))
                .append(text(": ", this.colors.primary))
                .append(topicDescription.color(this.colors.text))
        );
        if (hasArguments) {
            audience.sendMessage(text()
                    .append(text("   "))
                    .append(this.lastBranch())
                    .append(space())
                    .append(this.messageProvider.provide(sender, MESSAGE_ARGUMENTS).color(this.colors.primary))
                    .append(text(":", this.colors.primary))
            );

            final Iterator<CommandComponent<C>> iterator = helpTopic.getCommand().getComponents().iterator();
            /* Skip the first one because it's the command literal */
            iterator.next();

            while (iterator.hasNext()) {
                final CommandComponent<C> component = iterator.next();
                final CommandArgument<C, ?> argument = component.getArgument();

                final String syntax = this.commandManager.getCommandSyntaxFormatter()
                        .apply(Collections.singletonList(argument), null);

                final TextComponent.Builder textComponent = text()
                        .append(text("       "))
                        .append(iterator.hasNext() ? this.branch() : this.lastBranch())
                        .append(this.highlight(text(" " + syntax, this.colors.highlight)));
                if (!argument.isRequired()) {
                    textComponent.append(text(" (", this.colors.alternateHighlight));
                    textComponent.append(
                            this.messageProvider.provide(sender, MESSAGE_OPTIONAL).color(this.colors.alternateHighlight)
                    );
                    textComponent.append(text(")", this.colors.alternateHighlight));
                }
                final ArgumentDescription description = component.getArgumentDescription();
                if (!description.isEmpty()) {
                    textComponent.append(text(" - ", this.colors.accent));
                    textComponent.append(this.formatDescription(description).colorIfAbsent(this.colors.text));
                }

                audience.sendMessage(textComponent);
            }
        }
        audience.sendMessage(this.footer(sender));
    }

    private Component formatDescription(final ArgumentDescription description) {
        if (description instanceof RichDescription) {
            return ((RichDescription) description).getContents();
        } else {
            return this.descriptionDecorator.apply(description.getDescription());
        }
    }

    private @NonNull Component showingResults(
            final @NonNull C sender,
            final @NonNull String query
    ) {
        return text()
                .append(this.messageProvider.provide(sender, MESSAGE_SHOWING_RESULTS_FOR_QUERY).color(this.colors.text))
                .append(text(": \"", this.colors.text))
                .append(this.highlight(text("/" + query, this.colors.highlight)))
                .append(text("\"", this.colors.text))
                .build();
    }

    private @NonNull Component button(
            final char icon,
            final @NonNull String command,
            final @NonNull Component hoverText
    ) {
        return text()
                .append(space())
                .append(text('[', this.colors.accent))
                .append(text(icon, this.colors.alternateHighlight))
                .append(text(']', this.colors.accent))
                .append(space())
                .clickEvent(runCommand(command))
                .hoverEvent(hoverText)
                .build();
    }

    private @NonNull Component footer(final @NonNull C sender) {
        return this.paginatedFooter(sender, 1, 1, "");
    }

    private @NonNull Component paginatedFooter(
            final @NonNull C sender,
            final int currentPage,
            final int maxPages,
            final @NonNull String query
    ) {
        final boolean firstPage = currentPage == 1;
        final boolean lastPage = currentPage == maxPages;

        if (firstPage && lastPage) {
            return this.line(this.headerFooterLength);
        }

        final String nextPageCommand = String.format("%s %s %s", this.commandPrefix, query, currentPage + 1);
        final Component nextPageButton = this.button('→', nextPageCommand,
                this.messageProvider.provide(sender, MESSAGE_CLICK_FOR_NEXT_PAGE).color(this.colors.text)
        );
        if (firstPage) {
            return this.header(sender, nextPageButton);
        }

        final String previousPageCommand = String.format("%s %s %s", this.commandPrefix, query, currentPage - 1);
        final Component previousPageButton = this.button('←', previousPageCommand,
                this.messageProvider.provide(sender, MESSAGE_CLICK_FOR_PREVIOUS_PAGE).color(this.colors.text)
        );
        if (lastPage) {
            return this.header(sender, previousPageButton);
        }

        final Component buttons = text()
                .append(previousPageButton)
                .append(this.line(3))
                .append(nextPageButton).build();
        return this.header(sender, buttons);
    }

    private @NonNull Component header(
            final @NonNull C sender,
            final @NonNull Component title
    ) {
        final int sideLength = (this.headerFooterLength - ComponentHelper.length(title)) / 2;
        return text()
                .append(this.line(sideLength))
                .append(title)
                .append(this.line(sideLength))
                .build();
    }

    private @NonNull Component basicHeader(final @NonNull C sender) {
        return this.header(sender, LinearComponents.linear(
                space(),
                this.messageProvider.provide(sender, MESSAGE_HELP_TITLE).color(this.colors.highlight),
                space()
        ));
    }

    private @NonNull Component paginatedHeader(
            final @NonNull C sender,
            final int currentPage,
            final int pages
    ) {
        return this.header(sender, text()
                .append(space())
                .append(this.messageProvider.provide(sender, MESSAGE_HELP_TITLE).color(this.colors.highlight))
                .append(space())
                .append(text("(", this.colors.alternateHighlight))
                .append(text(currentPage, this.colors.text))
                .append(text("/", this.colors.alternateHighlight))
                .append(text(pages, this.colors.text))
                .append(text(")", this.colors.alternateHighlight))
                .append(space())
                .build()
        );
    }

    private @NonNull Component line(final int length) {
        return ComponentHelper.repeat(
                text("-", this.colors.primary, TextDecoration.STRIKETHROUGH),
                length
        );
    }

    private @NonNull Component branch() {
        return text("├─", this.colors.accent);
    }

    private @NonNull Component lastBranch() {
        return text("└─", this.colors.accent);
    }

    private @NonNull Component highlight(final @NonNull Component component) {
        return ComponentHelper.highlight(component, this.colors.alternateHighlight);
    }

    private @NonNull Component pageOutOfRange(
            final @NonNull C sender,
            final int attemptedPage,
            final int maxPages
    ) {
        return this.highlight(
                this.messageProvider.provide(
                        sender,
                        MESSAGE_PAGE_OUT_OF_RANGE,
                        String.valueOf(attemptedPage),
                        String.valueOf(maxPages)
                )
                        .color(this.colors.text)
                        .replaceText(config -> {
                            config.matchLiteral("<page>");
                            config.replacement(String.valueOf(attemptedPage));
                        })
                        .replaceText(config -> {
                            config.matchLiteral("<max_pages>");
                            config.replacement(String.valueOf(maxPages));
                        })
        );
    }

    @FunctionalInterface
    public interface MessageProvider<C> {

        /**
         * Creates a component from a command sender, key, and arguments
         *
         * @param sender command sender
         * @param key    message key (constants in {@link MinecraftHelp}
         * @param args   args
         * @return component
         */
        @NonNull Component provide(@NonNull C sender, @NonNull String key, @NonNull String... args);

    }

    /**
     * Class for holding the {@link TextColor TextColors} used for help menus
     */
    public static final class HelpColors {

        private final TextColor primary;
        private final TextColor highlight;
        private final TextColor alternateHighlight;
        private final TextColor text;
        private final TextColor accent;

        private HelpColors(
                final @NonNull TextColor primary,
                final @NonNull TextColor highlight,
                final @NonNull TextColor alternateHighlight,
                final @NonNull TextColor text,
                final @NonNull TextColor accent
        ) {
            this.primary = primary;
            this.highlight = highlight;
            this.alternateHighlight = alternateHighlight;
            this.text = text;
            this.accent = accent;
        }

        /**
         * Get the configured primary color
         *
         * @return The primary color for the color scheme
         */
        public @NonNull TextColor primary() {
            return this.primary;
        }

        /**
         * Get the configured highlight color
         *
         * @return The primary color used to highlight commands and queries
         */
        public @NonNull TextColor highlight() {
            return this.highlight;
        }

        /**
         * Get the configured alternate highlight color
         *
         * @return The secondary color used to highlight commands and queries
         */
        public @NonNull TextColor alternateHighlight() {
            return this.alternateHighlight;
        }

        /**
         * Get the configured text color
         *
         * @return The color used for description text
         */
        public @NonNull TextColor text() {
            return this.text;
        }

        /**
         * Get the configured accent color
         *
         * @return The color used for accents and symbols
         */
        public @NonNull TextColor accent() {
            return this.accent;
        }

        /**
         * Create a new {@link HelpColors} instance
         *
         * @param primary            The primary color for the color scheme
         * @param highlight          The primary color used to highlight commands and queries
         * @param alternateHighlight The secondary color used to highlight commands and queries
         * @param text               The color used for description text
         * @param accent             The color used for accents and symbols
         * @return A new {@link HelpColors} instance
         */
        public static @NonNull HelpColors of(
                final @NonNull TextColor primary,
                final @NonNull TextColor highlight,
                final @NonNull TextColor alternateHighlight,
                final @NonNull TextColor text,
                final @NonNull TextColor accent
        ) {
            return new HelpColors(primary, highlight, alternateHighlight, text, accent);
        }

    }

}
