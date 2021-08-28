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
package cloud.commandframework.jda.permission;

import cloud.commandframework.Command;
import cloud.commandframework.annotations.AnnotationParser;
import cloud.commandframework.meta.CommandMeta;
import io.leangen.geantyref.TypeToken;
import net.dv8tion.jda.api.Permission;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

public final class JDAPermissionMeta {

    public static final CommandMeta.Key<List<Permission>> BOT_PERMISSIONS = CommandMeta.Key.of(
            new TypeToken<List<Permission>>() {
            },
            "bot-permissions",
            (meta) -> Collections.emptyList()
    );

    public static final CommandMeta.Key<List<Permission>> USER_PERMISSIONS = CommandMeta.Key.of(
            new TypeToken<List<Permission>>() {
            },
            "user-permissions",
            (meta) -> Collections.emptyList()
    );

    private JDAPermissionMeta() {
    }

    public static <C> AnnotationParser<C> register(final AnnotationParser<C> annotationParser) {

        annotationParser.registerBuilderModifier(JDABotPermission.class, new BotPermissionMetaModifier<>());
        annotationParser.registerBuilderModifier(JDAUserPermission.class, new UserPermissionMetaModifier<>());

        return annotationParser;
    }

    public static final class BotPermissionMetaModifier<C> implements
            BiFunction<JDABotPermission, Command.Builder<C>, Command.Builder<C>> {


        @Override
        public Command.Builder<C> apply(final JDABotPermission botPermission, final Command.Builder<C> builder) {
            return builder.meta(BOT_PERMISSIONS, Arrays.asList(botPermission.permissions()));
        }

    }

    public static final class UserPermissionMetaModifier<C> implements
            BiFunction<JDAUserPermission, Command.Builder<C>, Command.Builder<C>> {

        @Override
        public Command.Builder<C> apply(final JDAUserPermission userPermission, final Command.Builder<C> builder) {
            return builder.meta(USER_PERMISSIONS, Arrays.asList(userPermission.permissions()));
        }

    }

}
