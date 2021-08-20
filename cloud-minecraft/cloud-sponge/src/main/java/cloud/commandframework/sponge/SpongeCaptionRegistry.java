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

import cloud.commandframework.captions.SimpleCaptionRegistry;

/**
 * Caption registry that uses bi-functions to produce messages.
 *
 * @param <C> command sender type
 */
public class SpongeCaptionRegistry<C> extends SimpleCaptionRegistry<C> {

    /**
     * Default caption for {@link SpongeCaptionKeys#ARGUMENT_PARSE_FAILURE_REGISTRY_ENTRY_UNKNOWN_ENTRY}
     */
    public static final String ARGUMENT_PARSE_FAILURE_REGISTRY_ENTRY_UNKNOWN_ENTRY =
            "No such entry '{id}' in registry '{registry}'.";

    /**
     * Default caption for {@link SpongeCaptionKeys#ARGUMENT_PARSE_FAILURE_USER_CANNOT_FIND_USER_WITH_NAME}
     */
    public static final String ARGUMENT_PARSE_FAILURE_USER_CANNOT_FIND_USER_WITH_NAME =
            "Cannot find a user with the name '{name}'.";

    /**
     * Default caption for {@link SpongeCaptionKeys#ARGUMENT_PARSE_FAILURE_USER_CANNOT_FIND_USER_WITH_UUID}
     */
    public static final String ARGUMENT_PARSE_FAILURE_USER_CANNOT_FIND_USER_WITH_UUID =
            "Cannot find a user with the UUID '{uuid}'.";

    /**
     * Default caption for {@link SpongeCaptionKeys#ARGUMENT_PARSE_FAILURE_USER_INVALID_INPUT}
     */
    public static final String ARGUMENT_PARSE_FAILURE_USER_INVALID_INPUT =
            "Input '{input}' is not a valid UUID or username.";

    /**
     * Default caption for {@link SpongeCaptionKeys#ARGUMENT_PARSE_FAILURE_GAME_PROFILE_TOO_MANY_SELECTED}
     */
    public static final String ARGUMENT_PARSE_FAILURE_GAME_PROFILE_TOO_MANY_SELECTED =
            "The provided selector matched multiple game profiles, but only one is allowed.";

    protected SpongeCaptionRegistry() {
        super();

        this.registerMessageFactory(
                SpongeCaptionKeys.ARGUMENT_PARSE_FAILURE_REGISTRY_ENTRY_UNKNOWN_ENTRY,
                (caption, sender) -> ARGUMENT_PARSE_FAILURE_REGISTRY_ENTRY_UNKNOWN_ENTRY
        );
        this.registerMessageFactory(
                SpongeCaptionKeys.ARGUMENT_PARSE_FAILURE_USER_CANNOT_FIND_USER_WITH_NAME,
                (caption, sender) -> ARGUMENT_PARSE_FAILURE_USER_CANNOT_FIND_USER_WITH_NAME
        );
        this.registerMessageFactory(
                SpongeCaptionKeys.ARGUMENT_PARSE_FAILURE_USER_CANNOT_FIND_USER_WITH_UUID,
                (caption, sender) -> ARGUMENT_PARSE_FAILURE_USER_CANNOT_FIND_USER_WITH_UUID
        );
        this.registerMessageFactory(
                SpongeCaptionKeys.ARGUMENT_PARSE_FAILURE_USER_INVALID_INPUT,
                (caption, sender) -> ARGUMENT_PARSE_FAILURE_USER_INVALID_INPUT
        );
        this.registerMessageFactory(
                SpongeCaptionKeys.ARGUMENT_PARSE_FAILURE_GAME_PROFILE_TOO_MANY_SELECTED,
                (caption, sender) -> ARGUMENT_PARSE_FAILURE_GAME_PROFILE_TOO_MANY_SELECTED
        );
    }


}
