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
package cloud.commandframework.captions;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Caption registry that uses bi-functions to produce messages
 *
 * @param <C> Command sender type
 */
public class SimpleCaptionRegistry<C> implements FactoryDelegatingCaptionRegistry<C> {

    /**
     * Default caption for {@link StandardCaptionKeys#ARGUMENT_PARSE_FAILURE_NO_INPUT_PROVIDED}.
     */
    public static final String ARGUMENT_PARSE_FAILURE_NO_INPUT_PROVIDED = "No input was provided";
    /**
     * Default caption for {@link StandardCaptionKeys#ARGUMENT_PARSE_FAILURE_BOOLEAN}.
     */
    public static final String ARGUMENT_PARSE_FAILURE_BOOLEAN = "Could not parse boolean from '{input}'";
    /**
     * Default caption for {@link StandardCaptionKeys#ARGUMENT_PARSE_FAILURE_NUMBER}
     */
    public static final String ARGUMENT_PARSE_FAILURE_NUMBER = "'{input}' is not a valid number in the range {min} to {max}";
    /**
     * Default caption for {@link StandardCaptionKeys#ARGUMENT_PARSE_FAILURE_CHAR}
     */
    public static final String ARGUMENT_PARSE_FAILURE_CHAR = "'{input}' is not a valid character";
    /**
     * Default caption for {@link StandardCaptionKeys#ARGUMENT_PARSE_FAILURE_ENUM}
     */
    public static final String ARGUMENT_PARSE_FAILURE_ENUM = "'{input}' is not one of the following: {acceptableValues}";
    /**
     * Default caption for {@link StandardCaptionKeys#ARGUMENT_PARSE_FAILURE_STRING}
     */
    public static final String ARGUMENT_PARSE_FAILURE_STRING = "'{input}' is not a valid string of type {stringMode}";
    /**
     * Default caption for {@link StandardCaptionKeys#ARGUMENT_PARSE_FAILURE_UUID}
     */
    public static final String ARGUMENT_PARSE_FAILURE_UUID = "'{input}' is not a valid UUID";
    /**
     * Default caption for {@link StandardCaptionKeys#ARGUMENT_PARSE_FAILURE_FLAG_UNKNOWN_FLAG}
     */
    public static final String ARGUMENT_PARSE_FAILURE_FLAG_UNKNOWN_FLAG = "Unknown flag '{flag}'";
    /**
     * Default caption for {@link StandardCaptionKeys#ARGUMENT_PARSE_FAILURE_FLAG_DUPLICATE_FLAG}
     */
    public static final String ARGUMENT_PARSE_FAILURE_FLAG_DUPLICATE_FLAG = "Duplicate flag '{flag}'";
    /**
     * Default caption for {@link StandardCaptionKeys#ARGUMENT_PARSE_FAILURE_FLAG_NO_FLAG_STARTED}
     */
    public static final String ARGUMENT_PARSE_FAILURE_FLAG_NO_FLAG_STARTED = "No flag started. Don't know what to do with '{input}'";
    /**
     * Default caption for {@link StandardCaptionKeys#ARGUMENT_PARSE_FAILURE_FLAG_MISSING_ARGUMENT}
     */
    public static final String ARGUMENT_PARSE_FAILURE_FLAG_MISSING_ARGUMENT = "Missing argument for '{flag}'";
    /**
     * Default caption for {@link StandardCaptionKeys#ARGUMENT_PARSE_FAILURE_FLAG_NO_PERMISSION}
     */
    public static final String ARGUMENT_PARSE_FAILURE_FLAG_NO_PERMISSION = "You don't have permission to use '{flag}'";
    /**
     * Default caption for {@link StandardCaptionKeys#ARGUMENT_PARSE_FAILURE_COLOR}
     */
    public static final String ARGUMENT_PARSE_FAILURE_COLOR = "'{input}' is not a valid color";

    private final Map<Caption, BiFunction<Caption, C, String>> messageFactories = new HashMap<>();

    protected SimpleCaptionRegistry() {
        this.registerMessageFactory(
                StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_NO_INPUT_PROVIDED,
                (caption, sender) -> ARGUMENT_PARSE_FAILURE_NO_INPUT_PROVIDED
        );
        this.registerMessageFactory(
                StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_BOOLEAN,
                (caption, sender) -> ARGUMENT_PARSE_FAILURE_BOOLEAN
        );
        this.registerMessageFactory(
                StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_NUMBER,
                (caption, sender) -> ARGUMENT_PARSE_FAILURE_NUMBER
        );
        this.registerMessageFactory(
                StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_CHAR,
                (caption, sender) -> ARGUMENT_PARSE_FAILURE_CHAR
        );
        this.registerMessageFactory(
                StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_ENUM,
                (caption, sender) -> ARGUMENT_PARSE_FAILURE_ENUM
        );
        this.registerMessageFactory(
                StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_STRING,
                (caption, sender) -> ARGUMENT_PARSE_FAILURE_STRING
        );
        this.registerMessageFactory(
                StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_UUID,
                (caption, sender) -> ARGUMENT_PARSE_FAILURE_UUID
        );
        this.registerMessageFactory(
                StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_FLAG_UNKNOWN_FLAG,
                (caption, sender) -> ARGUMENT_PARSE_FAILURE_FLAG_UNKNOWN_FLAG
        );
        this.registerMessageFactory(
                StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_FLAG_DUPLICATE_FLAG,
                (caption, sender) -> ARGUMENT_PARSE_FAILURE_FLAG_DUPLICATE_FLAG
        );
        this.registerMessageFactory(
                StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_FLAG_NO_FLAG_STARTED,
                (caption, sender) -> ARGUMENT_PARSE_FAILURE_FLAG_NO_FLAG_STARTED
        );
        this.registerMessageFactory(
                StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_FLAG_MISSING_ARGUMENT,
                (caption, sender) -> ARGUMENT_PARSE_FAILURE_FLAG_MISSING_ARGUMENT
        );
        this.registerMessageFactory(
                StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_FLAG_NO_PERMISSION,
                (caption, sender) -> ARGUMENT_PARSE_FAILURE_FLAG_NO_PERMISSION
        );
        this.registerMessageFactory(
                StandardCaptionKeys.ARGUMENT_PARSE_FAILURE_COLOR,
                (caption, sender) -> ARGUMENT_PARSE_FAILURE_COLOR
        );
    }

    @Override
    public final @NonNull String getCaption(
            final @NonNull Caption caption,
            final @NonNull C sender
    ) {
        final BiFunction<Caption, C, String> messageFactory = this.messageFactories.get(caption);
        if (messageFactory == null) {
            throw new IllegalArgumentException(
                    String.format(
                            "There is no caption stored with key '%s'",
                            caption
                    )
            );
        }
        return messageFactory.apply(caption, sender);
    }

    @Override
    public final void registerMessageFactory(
            final @NonNull Caption caption,
            final @NonNull BiFunction<Caption, C, String> messageFactory
    ) {
        this.messageFactories.put(caption, messageFactory);
    }

}
