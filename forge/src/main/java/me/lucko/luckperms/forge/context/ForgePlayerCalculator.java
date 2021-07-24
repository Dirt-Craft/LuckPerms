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

package me.lucko.luckperms.forge.context;

import me.lucko.luckperms.common.config.ConfigKeys;
import me.lucko.luckperms.common.context.contextset.ImmutableContextSetImpl;
import me.lucko.luckperms.common.util.EnumNamer;
import me.lucko.luckperms.forge.LPForgePlugin;
import net.luckperms.api.context.*;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.GameType;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Optional;
import java.util.Set;

public class ForgePlayerCalculator implements ContextCalculator<ServerPlayerEntity> {
    private static final EnumNamer<GameType> GAMEMODE_NAMER = new EnumNamer<>(
            GameType.class,
            EnumNamer.LOWER_CASE_NAME
    );

    private final LPForgePlugin plugin;

    private final boolean gamemode;
    private final boolean world;
    //private final boolean dimensionType;

    public ForgePlayerCalculator(LPForgePlugin plugin, Set<String> disabled) {
        this.plugin = plugin;
        this.gamemode = !disabled.contains(DefaultContextKeys.GAMEMODE_KEY);
        this.world = !disabled.contains(DefaultContextKeys.WORLD_KEY);
        //this.dimensionType = !disabled.contains(DefaultContextKeys.DIMENSION_TYPE_KEY);
    }

    public void registerListeners() {
        MinecraftForge.EVENT_BUS.addListener(this::onWorldChange);
    }

    @Override
    public void calculate(@NonNull ServerPlayerEntity target, @NonNull ContextConsumer consumer) {
        GameType mode = target.gameMode.getGameModeForPlayer();
        final int GAME_MODE_NOT_SET = -1; // GameMode.NOT_SET with ID -1 was removed in 1.17
        if (this.gamemode && mode != null && mode.getId() != GAME_MODE_NOT_SET) {
            consumer.accept(DefaultContextKeys.GAMEMODE_KEY, GAMEMODE_NAMER.name(mode));
        }

        // TODO: figure out dimension type context too
        ServerWorld world = target.getLevel();
        if (this.world) {
            this.plugin.getConfiguration().get(ConfigKeys.WORLD_REWRITES).rewriteAndSubmit(getContextKey(world.dimension().getRegistryName()), consumer);
        }
    }

    @Override
    public @NonNull ContextSet estimatePotentialContexts() {
        ImmutableContextSet.Builder builder = new ImmutableContextSetImpl.BuilderImpl();

        if (this.gamemode) {
            for (GameType mode : GameType.values()) {
                builder.add(DefaultContextKeys.GAMEMODE_KEY, GAMEMODE_NAMER.name(mode));
            }
        }

        // TODO: dimension type

        Optional<MinecraftServer> server = this.plugin.getBootstrap().getServer();
        if (this.world && server.isPresent()) {
            Iterable<ServerWorld> worlds = server.get().getAllLevels();
            for (ServerWorld world : worlds) {
                String worldName = getContextKey(world.dimension().getRegistryName());
                if (Context.isValidValue(worldName)) {
                    builder.add(DefaultContextKeys.WORLD_KEY, worldName);
                }
            }
        }

        return builder.build();
    }

    public static String getContextKey(ResourceLocation key) {
        if (key.getNamespace().equals("minecraft")) {
            return key.getPath();
        }
        return key.toString();
    }

    private void onWorldChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayerEntity)) return;
        //well for whatever reason i did this but didn't realize it was not needed. oof.
        //MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        //ServerWorld origin = server.getLevel(event.getFrom());
        //ServerWorld destination = server.getLevel(event.getTo());
        ServerPlayerEntity player = (ServerPlayerEntity) event.getPlayer();
        if (this.world) {
            this.plugin.getContextManager().invalidateCache(player);
            this.plugin.getContextManager().signalContextUpdate(player);
        }
    }

}
