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

package me.lucko.luckperms.forge.mixin;

import com.mojang.authlib.GameProfile;
import me.lucko.luckperms.forge.event.PlayerAuthenticationEvent;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.login.ServerLoginNetHandler;
import net.minecraft.network.login.client.CEncryptionResponsePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.common.MinecraftForge;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

@Mixin(ServerLoginNetHandler.class)
public abstract class ServerLoginNetHandlerMixin {
    @Shadow @Final private MinecraftServer server;

    @Shadow @Final public NetworkManager connection;

    @Shadow private GameProfile gameProfile;

    @Shadow public abstract void disconnect(ITextComponent p_194026_1_);

    @Inject(method = "handleKey", at = @At("HEAD"))
    public void onHello(CEncryptionResponsePacket p_147315_1_, CallbackInfo ci) {
        ArrayList<CompletableFuture<?>> waitConditions = new ArrayList<>();
        PlayerAuthenticationEvent startEvent = new PlayerAuthenticationEvent(server, (ServerLoginNetHandler) (Object) this, gameProfile, connection, waitConditions);
        if (MinecraftForge.EVENT_BUS.post(startEvent)) {
            ITextComponent message = startEvent.getCancelReason() == null? new StringTextComponent("A mod has cancelled your login"): startEvent.getCancelReason();
            disconnect(message);
        }
        CompletableFuture.allOf(waitConditions.toArray(new CompletableFuture[0]));
    }
}
