/*
 * This file is part of LuckPerms, licensed under the MIT License.
 *
 *  Copyright (c) lucko (Luck) <luck@lucko.me>
 *  Copyright (c) contributors
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.forge.listeners;

import me.lucko.luckperms.common.calculator.result.TristateResult;
import me.lucko.luckperms.common.query.QueryOptionsImpl;
import me.lucko.luckperms.common.verbose.VerboseCheckTarget;
import me.lucko.luckperms.common.verbose.event.PermissionCheckEvent.Origin;
import me.lucko.luckperms.forge.LPForgePlugin;
import me.lucko.luckperms.forge.TriState;
import me.lucko.luckperms.forge.model.MixinUser;

import net.minecraft.command.CommandSource;
import net.minecraft.command.ISuggestionProvider;
import net.minecraft.entity.Entity;

import net.minecraft.entity.player.ServerPlayerEntity;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Listener to route permission checks made via fabric-permissions-api to LuckPerms.
 */
public class PermissionCheckListener {
    private final LPForgePlugin plugin;

    public PermissionCheckListener(LPForgePlugin plugin) {
        this.plugin = plugin;
    }

    public void registerListeners() {
        //todo PermissionCheckEvent.EVENT.register(this::onPermissionCheck);
    }

    private @NonNull TriState onPermissionCheck(ISuggestionProvider source, String permission) {
        if (source instanceof CommandSource) {
            Entity entity = ((CommandSource) source).getEntity();
            if (entity instanceof ServerPlayerEntity) {
                return onPlayerPermissionCheck((ServerPlayerEntity) entity, permission);
            }
        }
        return onOtherPermissionCheck(source, permission);
    }

    private TriState onPlayerPermissionCheck(ServerPlayerEntity player, String permission) {
        switch (((MixinUser) player).hasPermission(permission)) {
            case TRUE:
                return TriState.TRUE;
            case FALSE:
                return TriState.FALSE;
            case UNDEFINED:
                return TriState.UNDEFINED;
            default:
                throw new AssertionError();
        }
    }

    private TriState onOtherPermissionCheck(ISuggestionProvider source, String permission) {
        if (source instanceof CommandSource) {
            String name = ((CommandSource) source).getTextName();
            VerboseCheckTarget target = VerboseCheckTarget.internal(name);

            this.plugin.getVerboseHandler().offerPermissionCheckEvent(Origin.PLATFORM_PERMISSION_CHECK, target, QueryOptionsImpl.DEFAULT_CONTEXTUAL, permission, TristateResult.UNDEFINED);
            this.plugin.getPermissionRegistry().offer(permission);
        }

        return TriState.UNDEFINED;
    }

}
