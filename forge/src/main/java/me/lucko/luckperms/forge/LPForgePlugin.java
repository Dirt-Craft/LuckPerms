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

import me.lucko.luckperms.common.api.LuckPermsApiProvider;
import me.lucko.luckperms.common.calculator.CalculatorFactory;
import me.lucko.luckperms.common.config.ConfigKeys;
import me.lucko.luckperms.common.config.generic.adapter.ConfigurationAdapter;
import me.lucko.luckperms.common.dependencies.Dependency;
import me.lucko.luckperms.common.event.AbstractEventBus;
import me.lucko.luckperms.common.locale.TranslationManager;
import me.lucko.luckperms.common.messaging.MessagingFactory;
import me.lucko.luckperms.common.model.User;
import me.lucko.luckperms.common.model.manager.group.StandardGroupManager;
import me.lucko.luckperms.common.model.manager.track.StandardTrackManager;
import me.lucko.luckperms.common.model.manager.user.StandardUserManager;
import me.lucko.luckperms.common.plugin.AbstractLuckPermsPlugin;
import me.lucko.luckperms.common.sender.DummyConsoleSender;
import me.lucko.luckperms.common.sender.Sender;
import me.lucko.luckperms.forge.context.ForgeContextManager;
import me.lucko.luckperms.forge.context.ForgePlayerCalculator;
import me.lucko.luckperms.forge.listeners.ForgeAutoOpListener;
import me.lucko.luckperms.forge.listeners.ForgeCommandListUpdater;
import me.lucko.luckperms.forge.listeners.ForgeConnectionListener;
import me.lucko.luckperms.forge.listeners.ForgeOtherListeners;
import me.lucko.luckperms.forge.listeners.PermissionCheckListener;
import me.lucko.luckperms.forge.messaging.ForgeMessagingFactory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.query.QueryOptions;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.OpList;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModContainer;
import net.minecraftforge.fml.event.server.FMLServerStartedEvent;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class LPForgePlugin extends AbstractLuckPermsPlugin {
    private final LPForgeBootstrap bootstrap;

    private ForgeConnectionListener connectionListener;
    private ForgeCommandExecutor commandManager;
    private ForgeSenderFactory senderFactory;
    private ForgeContextManager contextManager;
    private StandardUserManager userManager;
    private StandardGroupManager groupManager;
    private StandardTrackManager trackManager;

    public LPForgePlugin(LPForgeBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Override
    public LPForgeBootstrap getBootstrap() {
        return this.bootstrap;
    }

    protected void registerFabricListeners() {
        // Events are registered very early on, and persist between game states
        this.connectionListener = new ForgeConnectionListener(this);
        this.connectionListener.registerListeners();

        new PermissionCheckListener(this).registerListeners();

        // Command registration also need to occur early, and will persist across game states as well.
        this.commandManager = new ForgeCommandExecutor(this);
        this.commandManager.register();

        new ForgeOtherListeners(this).registerListeners();
    }

    @Override
    protected void setupSenderFactory() {
        this.senderFactory = new ForgeSenderFactory(this);
    }

    @Override
    protected Set<Dependency> getGlobalDependencies() {
        Set<Dependency> dependencies = super.getGlobalDependencies();
        dependencies.add(Dependency.CONFIGURATE_CORE);
        dependencies.add(Dependency.CONFIGURATE_HOCON);
        dependencies.add(Dependency.HOCON_CONFIG);
        return dependencies;
    }

    @Override
    protected ConfigurationAdapter provideConfigurationAdapter() {
        return new ForgeConfigAdapter(this, resolveConfig("luckperms.conf"));
    }

    @Override
    protected void registerPlatformListeners() {
        // Too late for Fabric, registered in #registerFabricListeners
    }

    @Override
    protected MessagingFactory<?> provideMessagingFactory() {
        return new ForgeMessagingFactory(this);
    }

    @Override
    protected void registerCommands() {
        // Too late for Fabric, registered in #registerFabricListeners
    }

    @Override
    protected void setupManagers() {
        this.userManager = new StandardUserManager(this);
        this.groupManager = new StandardGroupManager(this);
        this.trackManager = new StandardTrackManager(this);
    }

    @Override
    protected CalculatorFactory provideCalculatorFactory() {
        return new ForgeCalculatorFactory(this);
    }

    @Override
    protected void setupContextManager() {
        this.contextManager = new ForgeContextManager(this);

        ForgePlayerCalculator playerCalculator = new ForgePlayerCalculator(this, getConfiguration().get(ConfigKeys.DISABLED_CONTEXTS));
        playerCalculator.registerListeners();
        this.contextManager.registerCalculator(playerCalculator);
    }

    @Override
    protected void setupPlatformHooks() {
    }

    @Override
    protected AbstractEventBus<ModContainer> provideEventBus(LuckPermsApiProvider provider) {
        return new ForgeEventBus(this, provider);
    }

    @Override
    protected void registerApiOnPlatform(LuckPerms api) {
    }

    @Override
    protected void performFinalSetup() {
        // remove all operators on startup if they're disabled
        if (!getConfiguration().get(ConfigKeys.OPS_ENABLED)) {
            MinecraftForge.EVENT_BUS.<FMLServerStartedEvent>addListener(event->{
                OpList ops = event.getServer().getPlayerList().getOps();
                ops.getEntries().clear();
                try {
                    ops.save();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

        // register autoop listener
        if (getConfiguration().get(ConfigKeys.AUTO_OP)) {
            getApiProvider().getEventBus().subscribe(new ForgeAutoOpListener(this));
        }

        // register fabric command list updater
        if (getConfiguration().get(ConfigKeys.UPDATE_CLIENT_COMMAND_LIST)) {
            getApiProvider().getEventBus().subscribe(new ForgeCommandListUpdater(this));
        }
    }

    public ForgeSenderFactory getSenderFactory() {
        return this.senderFactory;
    }

    @Override
    public ForgeConnectionListener getConnectionListener() {
        return this.connectionListener;
    }

    @Override
    public ForgeCommandExecutor getCommandManager() {
        return this.commandManager;
    }

    @Override
    public ForgeContextManager getContextManager() {
        return this.contextManager;
    }

    @Override
    public StandardUserManager getUserManager() {
        return this.userManager;
    }

    @Override
    public StandardGroupManager getGroupManager() {
        return this.groupManager;
    }

    @Override
    public StandardTrackManager getTrackManager() {
        return this.trackManager;
    }

    @Override
    public Optional<QueryOptions> getQueryOptionsForUser(User user) {
        return this.bootstrap.getPlayer(user.getUniqueId()).map(player -> this.contextManager.getQueryOptions(player));
    }

    @Override
    public Stream<Sender> getOnlineSenders() {
        return Stream.concat(
                Stream.of(getConsoleSender()),
                this.bootstrap.getServer().map(MinecraftServer::getPlayerList).map(s -> s.getPlayers().stream().map(p -> this.senderFactory.wrap(p.createCommandSourceStack()))).orElseGet(Stream::empty)
        );
    }

    @Override
    public Sender getConsoleSender() {
        return this.bootstrap.getServer()
                .map(s -> this.senderFactory.wrap(s.createCommandSourceStack()))
                .orElseGet(() -> new DummyConsoleSender(this) {
                    @Override
                    public void sendMessage(Component message) {
                        LPForgePlugin.this.bootstrap.getPluginLogger().info(PlainComponentSerializer.plain().serialize(TranslationManager.render(message)));
                    }
                });
    }

}
