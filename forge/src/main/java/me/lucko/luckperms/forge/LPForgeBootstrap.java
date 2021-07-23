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

import com.mojang.authlib.GameProfile;

import me.lucko.luckperms.common.plugin.bootstrap.LuckPermsBootstrap;
import me.lucko.luckperms.common.plugin.classpath.ClassPathAppender;
import me.lucko.luckperms.common.plugin.logging.Log4jPluginLogger;
import me.lucko.luckperms.common.plugin.logging.PluginLogger;
import me.lucko.luckperms.common.plugin.scheduler.SchedulerAdapter;

import net.luckperms.api.platform.Platform;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLDedicatedServerSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.event.server.FMLServerStoppingEvent;
import net.minecraftforge.fml.loading.moddiscovery.ModInfo;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * Bootstrap plugin for LuckPerms running on Fabric.
 */
@Mod(value = LPForgeBootstrap.MODID)
public final class LPForgeBootstrap implements LuckPermsBootstrap {

    protected static final String MODID = "luckperms";
    protected static final ModContainer MOD_CONTAINER = ModList.get().getModContainerById(MODID).orElse(null);

    /**
     * The plugin logger
     */
    private final PluginLogger logger;

    /**
     * A scheduler adapter for the platform
     */
    private final SchedulerAdapter schedulerAdapter;

    /**
     * The plugin class path appender
     */
    private final ClassPathAppender classPathAppender;

    /**
     * The plugin instance
     */
    private LPForgePlugin plugin;

    /**
     * The time when the plugin was enabled
     */
    private Instant startTime;

    // load/enable latches
    private final CountDownLatch loadLatch = new CountDownLatch(1);
    private final CountDownLatch enableLatch = new CountDownLatch(1);

    /**
     * The Minecraft server instance
     */
    private MinecraftServer server;
    
    public LPForgeBootstrap() {
        MinecraftForge.EVENT_BUS.addListener(this::onInitializeServer);
        this.logger = new Log4jPluginLogger(LogManager.getLogger(MODID));
        this.schedulerAdapter = new FabricSchedulerAdapter(this);
        this.classPathAppender = new FabricClassPathAppender();
        this.plugin = new LPForgePlugin(this);
    }
    
    // provide adapters

    @Override
    public PluginLogger getPluginLogger() {
        return this.logger;
    }

    @Override
    public SchedulerAdapter getScheduler() {
        return this.schedulerAdapter;
    }

    @Override
    public ClassPathAppender getClassPathAppender() {
        return this.classPathAppender;
    }
    
    // lifecycle
    public void onInitializeServer(FMLDedicatedServerSetupEvent event) {
        this.plugin = new LPForgePlugin(this);
        try {
            this.plugin.load();
        } finally {
            this.loadLatch.countDown();
        }

        // Register the Server startup/shutdown events now
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarting);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStopping);
        this.plugin.registerFabricListeners();
    }

    private void onServerStarting(FMLServerStartingEvent event) {
        this.server = event.getServer();
        this.startTime = Instant.now();
        this.plugin.enable();
    }

    private void onServerStopping(FMLServerStoppingEvent event) {
        this.plugin.disable();
        this.server = null;
    }

    @Override
    public CountDownLatch getLoadLatch() {
        return this.loadLatch;
    }

    @Override
    public CountDownLatch getEnableLatch() {
        return this.enableLatch;
    }

    // MinecraftServer singleton getter

    public Optional<MinecraftServer> getServer() {
        return Optional.ofNullable(this.server);
    }

    // provide information about the plugin

    @Override
    public String getVersion() {
        return MOD_CONTAINER.getModInfo().getVersion().getQualifier();
    }

    @Override
    public Instant getStartupTime() {
        return this.startTime;
    }

    // provide information about the platform

    @Override
    public Platform.Type getType() {
        return Platform.Type.FABRIC;
    }

    @Override
    public String getServerBrand() {
        return "forge@" + ModList.get().getModContainerById("forge")
                .map(c -> c.getModInfo().getVersion().getQualifier())
                .orElse("unknown");
    }

    @Override
    public String getServerVersion() {
        String forgeApiVersion = "forge@" + ModList.get().getModContainerById("forge")
                .map(c -> c.getModInfo().getVersion().getQualifier())
                .orElse("unknown");

        return getServer().map(MinecraftServer::getServerVersion).orElse("null") + " - fabric-api@" + forgeApiVersion;
    }

    @Override
    public Path getDataDirectory() {
        return Paths.get(".", "mods", MODID);
    }

    @Override
    public Path getConfigDirectory() {
        return Paths.get(".", "config", MODID);
    }

    @Override
    public InputStream getResourceStream(String path) {
        try {
            return Files.newInputStream(((ModInfo)MOD_CONTAINER.getModInfo()).getOwningFile().getFile().getFilePath().resolve(path));
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public Optional<ServerPlayerEntity> getPlayer(UUID uniqueId) {
        return getServer().map(MinecraftServer::getPlayerList).map(s -> s.getPlayer(uniqueId));
    }

    @Override
    public Optional<UUID> lookupUniqueId(String username) {
        return getServer().map(MinecraftServer::getProfileCache).map(c -> c.get(username)).map(GameProfile::getId);

    }

    @Override
    public Optional<String> lookupUsername(UUID uniqueId) {
        return getServer().map(MinecraftServer::getProfileCache).map(c -> c.get(uniqueId)).map(GameProfile::getName);
    }

    @Override
    public int getPlayerCount() {
        return getServer().map(MinecraftServer::getPlayerCount).orElse(0);
    }

    @Override
    public Collection<String> getPlayerList() {
        return getServer().map(MinecraftServer::getPlayerList)
                .map(server -> {
                    List<ServerPlayerEntity> players = server.getPlayers();
                    List<String> list = new ArrayList<>(players.size());
                    for (ServerPlayerEntity player : players) {
                        list.add(player.getGameProfile().getName());
                    }
                    return list;
                })
                .orElse(Collections.emptyList());
    }

    @Override
    public Collection<UUID> getOnlinePlayers() {
        return getServer().map(MinecraftServer::getPlayerList)
                .map(server -> {
                    List<ServerPlayerEntity> players = server.getPlayers();
                    List<UUID> list = new ArrayList<>(players.size());
                    for (ServerPlayerEntity player : players) {
                        list.add(player.getGameProfile().getId());
                    }
                    return list;
                })
                .orElse(Collections.emptyList());
    }

    @Override
    public boolean isPlayerOnline(UUID uniqueId) {
        return getServer().map(MinecraftServer::getPlayerList).map(s -> s.getPlayer(uniqueId) != null).orElse(false);
    }

}
