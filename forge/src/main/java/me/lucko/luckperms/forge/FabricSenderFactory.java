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

package me.lucko.luckperms.forge;

import me.lucko.luckperms.common.locale.TranslationManager;
import me.lucko.luckperms.common.sender.Sender;
import me.lucko.luckperms.common.sender.SenderFactory;
import me.lucko.luckperms.forge.model.MixinUser;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.luckperms.api.util.Tristate;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraftforge.server.permission.PermissionAPI;

import java.util.Locale;
import java.util.UUID;

public class FabricSenderFactory extends SenderFactory<LPForgePlugin, CommandSource> {
    private final LPForgePlugin plugin;

    public FabricSenderFactory(LPForgePlugin plugin) {
        super(plugin);
        this.plugin = plugin;
    }

    @Override
    protected LPForgePlugin getPlugin() {
        return this.plugin;
    }

    @Override
    protected UUID getUniqueId(CommandSource commandSource) {
        if (commandSource.getEntity() != null) {
            return commandSource.getEntity().getUUID();
        }
        return Sender.CONSOLE_UUID;
    }

    @Override
    protected String getName(CommandSource commandSource) {
        String name = commandSource.getTextName();
        if (commandSource.getEntity() != null && name.equals("Server")) {
            return Sender.CONSOLE_NAME;
        }
        return name;
    }

    @Override
    protected void sendMessage(CommandSource sender, Component message) {
        Locale locale = null;
        if (sender.getEntity() instanceof ServerPlayerEntity) {
            locale = ((MixinUser) sender.getEntity()).getCachedLocale();
        }
        sender.sendSuccess(toNativeText(TranslationManager.render(message, locale)), false);
    }

    @Override
    protected Tristate getPermissionValue(CommandSource commandSource, String node) {
        Entity entity = commandSource.getEntity();
        if (entity instanceof PlayerEntity) {
            return Tristate.of(PermissionAPI.hasPermission((PlayerEntity) entity, node));
        } else {
            return Tristate.of(commandSource.hasPermission(1));
        }
    }

    @Override
    protected boolean hasPermission(CommandSource commandSource, String node) {
        return getPermissionValue(commandSource, node).asBoolean();
    }

    @Override
    protected void performCommand(CommandSource sender, String command) {
        sender.getServer().getCommands().performCommand(sender, command);
    }

    @Override
    protected boolean isConsole(CommandSource sender) {
        return sender.getEntity() == null;
    }

    public static IFormattableTextComponent toNativeText(Component component) {
        return IFormattableTextComponent.Serializer.fromJson(GsonComponentSerializer.gson().serialize(component));
    }
}
