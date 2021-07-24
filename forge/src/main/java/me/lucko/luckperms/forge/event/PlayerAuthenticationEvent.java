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

package me.lucko.luckperms.forge.event;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.login.ServerLoginNetHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.eventbus.api.Event;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PlayerAuthenticationEvent extends Event {
    private ITextComponent cancelReason;
    protected final GameProfile gameProfile;
    protected final MinecraftServer server;
    protected final NetworkManager connection;
    protected final ServerLoginNetHandler handler;
    private final List<CompletableFuture<?>> waitConditions;

    public PlayerAuthenticationEvent(MinecraftServer server, ServerLoginNetHandler handler, GameProfile profile, NetworkManager connection, List<CompletableFuture<?>> waitConditions){
        this.gameProfile = profile;
        this.server = server;
        this.connection = connection;
        this.handler = handler;
        this.waitConditions = waitConditions;
    }

    @Override
    public boolean isCancelable() {
        return false;
    }

    public ITextComponent getCancelReason(){
        return cancelReason;
    }

    public void setCanceled(ITextComponent message){
        this.setCanceled(true);
        this.cancelReason = message;
    }

    public GameProfile getGameProfile() {
        return gameProfile;
    }

    public ServerLoginNetHandler getHandler() {
        return handler;
    }

    public NetworkManager getConnection() {
        return connection;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public void addWaitCondition(CompletableFuture<?> task) {
        waitConditions.add(task);
    }
}
