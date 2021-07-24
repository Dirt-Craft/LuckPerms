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

import me.lucko.luckperms.common.config.ConfigKeys;
import me.lucko.luckperms.common.locale.Message;
import me.lucko.luckperms.forge.LPForgePlugin;
import me.lucko.luckperms.forge.event.PreParseCommandEvent;
import net.minecraft.command.CommandSource;
import net.minecraftforge.common.MinecraftForge;

import java.util.regex.Pattern;

public class ForgeOtherListeners {
    private static final Pattern OP_COMMAND_PATTERN = Pattern.compile("^/?(deop|op)( .*)?$");

    private LPForgePlugin plugin;

    public ForgeOtherListeners(LPForgePlugin plugin) {
        this.plugin = plugin;
    }

    public void registerListeners() {
        MinecraftForge.EVENT_BUS.addListener(this::onPreExecuteCommand);
    }

    private void onPreExecuteCommand(PreParseCommandEvent event) {
        String input = event.getInput();
        CommandSource source = event.getSource();
        if (input.isEmpty()) {
            return;
        }

        if (this.plugin.getConfiguration().get(ConfigKeys.OPS_ENABLED)) {
            return;
        }

        if (OP_COMMAND_PATTERN.matcher(input).matches()) {
            Message.OP_DISABLED.send(this.plugin.getSenderFactory().wrap(source));
            event.setCanceled(true);
        }
    }
}
