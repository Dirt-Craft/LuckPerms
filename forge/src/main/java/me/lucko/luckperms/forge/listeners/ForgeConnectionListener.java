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

import com.mojang.authlib.GameProfile;
import me.lucko.luckperms.common.config.ConfigKeys;
import me.lucko.luckperms.common.locale.Message;
import me.lucko.luckperms.common.locale.TranslationManager;
import me.lucko.luckperms.common.model.User;
import me.lucko.luckperms.common.plugin.util.AbstractConnectionListener;
import me.lucko.luckperms.forge.ForgeSenderFactory;
import me.lucko.luckperms.forge.LPForgePlugin;
import me.lucko.luckperms.forge.event.PlayerAuthenticationEvent;
import me.lucko.luckperms.forge.model.MixinUser;
import net.kyori.adventure.text.Component;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.network.login.ServerLoginNetHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ForgeConnectionListener extends AbstractConnectionListener {
    private final LPForgePlugin plugin;

    public ForgeConnectionListener(LPForgePlugin plugin) {
        super(plugin);
        this.plugin = plugin;
    }

    public void registerListeners() {
        MinecraftForge.EVENT_BUS.addListener(this::onAuth);
        MinecraftForge.EVENT_BUS.addListener(this::onLogin);
        MinecraftForge.EVENT_BUS.addListener(this::onLogout);
    }

    private void onAuth(PlayerAuthenticationEvent event){
        /* Called when the player first attempts a connection with the server. */

        // Get their profile from the net handler - it should have been initialised by now.
        GameProfile profile = event.getGameProfile();
        UUID uniqueId = PlayerEntity.createPlayerUUID(profile);
        String username = profile.getName();

        if (this.plugin.getConfiguration().get(ConfigKeys.DEBUG_LOGINS)) {
            this.plugin.getLogger().info("Processing pre-login (sync phase) for " + uniqueId + " - " + username);
        }

        // Register with the LoginSynchronizer that we want to perform a task before the login proceeds.
        event.addWaitCondition(CompletableFuture.runAsync(() -> onPreLoginAsync(event.getHandler(), uniqueId, username), this.plugin.getBootstrap().getScheduler().async()));

    }

    private void onPreLoginAsync(ServerLoginNetHandler netHandler, UUID uniqueId, String username) {
        if (this.plugin.getConfiguration().get(ConfigKeys.DEBUG_LOGINS)) {
            this.plugin.getLogger().info("Processing pre-login (async phase) for " + uniqueId + " - " + username);
        }

        /* Actually process the login for the connection.
           We do this here to delay the login until the data is ready.
           If the login gets cancelled later on, then this will be cleaned up.

           This includes:
           - loading uuid data
           - loading permissions
           - creating a user instance in the UserManager for this connection.
           - setting up cached data. */
        try {
            User user = loadUser(uniqueId, username);
            recordConnection(uniqueId);
            this.plugin.getEventDispatcher().dispatchPlayerLoginProcess(uniqueId, username, user);
        } catch (Exception ex) {
            this.plugin.getLogger().severe("Exception occurred whilst loading data for " + uniqueId + " - " + username, ex);

            // deny the connection
            Component reason = TranslationManager.render(Message.LOADING_DATABASE_ERROR.build());
            netHandler.disconnect(ForgeSenderFactory.toNativeText(reason));
            this.plugin.getEventDispatcher().dispatchPlayerLoginProcess(uniqueId, username, null);
        }
    }

    private void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        final ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();

        if (this.plugin.getConfiguration().get(ConfigKeys.DEBUG_LOGINS)) {
            this.plugin.getLogger().info("Processing login for " + player.getUUID() + " - " + player.getGameProfile().getName());
        }

        final User user = this.plugin.getUserManager().getIfLoaded(player.getUUID());

        /* User instance is null for whatever reason. Could be that it was unloaded between asyncpre and now. */
        if (user == null) {
            this.plugin.getLogger().warn("User " + player.getUUID() + " - " + player.getGameProfile().getName() +
                    " doesn't currently have data pre-loaded - denying login.");
            Component reason = TranslationManager.render(Message.LOADING_STATE_ERROR.build());
            player.connection.disconnect(ForgeSenderFactory.toNativeText(reason));
            return;
        }

        // init permissions handler
        ((MixinUser) player).initializePermissions(user);

        this.plugin.getContextManager().signalContextUpdate(player);

    }

    private void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        handleDisconnect(event.getPlayer().getUUID());
    }
}
