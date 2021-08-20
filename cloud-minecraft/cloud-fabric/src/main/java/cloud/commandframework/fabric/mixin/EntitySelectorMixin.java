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
package cloud.commandframework.fabric.mixin;

import cloud.commandframework.fabric.internal.EntitySelectorAccess;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.spongepowered.asm.mixin.Implements;
import org.spongepowered.asm.mixin.Interface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntitySelector.class)
@Implements({@Interface(iface = EntitySelectorAccess.class, prefix = "cloud$", unique = true)})
@Unique
abstract class EntitySelectorMixin {

    // todo: permission instead of bypass? like sponge?
    private boolean bypassPermissionCheck = false;
    private String inputString;

    public @NonNull String cloud$inputString() {
        return this.inputString;
    }

    public void cloud$inputString(final @NonNull String inputString) {
        this.inputString = inputString;
    }

    public @NonNull EntitySelector cloud$bypassPermissionCheck(final boolean shouldBypass) {
        this.bypassPermissionCheck = shouldBypass;
        return (EntitySelector) (Object) this;
    }

    @Redirect(
            method = "checkPermissions",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/commands/CommandSourceStack;hasPermission(I)Z")
    )
    private boolean redirectHasPermission(final @NonNull CommandSourceStack commandSourceStack, final int level) {
        return this.bypassPermissionCheck || commandSourceStack.hasPermission(level);
    }

}
