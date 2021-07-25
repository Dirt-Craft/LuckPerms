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
import me.lucko.luckperms.common.calculator.result.TristateResult;
import me.lucko.luckperms.common.context.contextset.ContextImpl;
import me.lucko.luckperms.common.model.User;
import me.lucko.luckperms.common.query.QueryOptionsImpl;
import me.lucko.luckperms.common.verbose.VerboseCheckTarget;
import me.lucko.luckperms.common.verbose.event.PermissionCheckEvent;
import me.lucko.luckperms.common.verbose.event.PermissionCheckEvent.Origin;
import me.lucko.luckperms.forge.LPForgePlugin;
import me.lucko.luckperms.forge.context.ForgePlayerCalculator;
import me.lucko.luckperms.forge.model.MixinUser;
import net.luckperms.api.context.DefaultContextKeys;
import net.luckperms.api.context.MutableContextSet;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;
import net.minecraft.command.CommandSource;
import net.minecraft.command.ISuggestionProvider;
import net.minecraftforge.server.permission.DefaultPermissionLevel;
import net.minecraftforge.server.permission.IPermissionHandler;
import net.minecraftforge.server.permission.context.IContext;
import org.checkerframework.checker.nullness.qual.NonNull;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Listener to route permission checks made via fabric-permissions-api to LuckPerms
 */
public class LuckPermissionHandler implements IPermissionHandler {
    private final Map<String, DefaultPermissionLevel> defaults = new HashMap<>();
    private final Map<String, String> descriptions = new HashMap<>();
    private final LPForgePlugin plugin;
    private QueryOptions defaultQuery;

    public LuckPermissionHandler(LPForgePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void registerNode(@NonNull String node, @NonNull DefaultPermissionLevel level, @NonNull String desc) {
        defaults.put(node, level);
        descriptions.put(node, desc);
    }

    @Override
    @NonNull
    public Collection<String> getRegisteredNodes() {
        return new ArrayList<>(defaults.keySet());
    }

    @Override
    public boolean hasPermission(@NonNull GameProfile profile, @NonNull String node, @Nullable IContext context) {

        User user = plugin.getUserManager().getIfLoaded(profile.getId());
        if (user == null) return defaults.get(node) == DefaultPermissionLevel.ALL;


        final QueryOptions queryOptions;
        if (context != null && context.getPlayer() != null) queryOptions = ((MixinUser)context.getPlayer()).getQueryOptionsCache().getQueryOptions();
        else if (context != null && context.getWorld() != null) {
                MutableContextSet ctxSet = plugin.getContextManager().getStaticContext().mutableCopy();
                ctxSet.add(new ContextImpl(DefaultContextKeys.WORLD_KEY, ForgePlayerCalculator.getContextKey(context.getWorld().dimension().getRegistryName())));
                queryOptions = QueryOptions.contextual(ctxSet);
        } else queryOptions = getDefaultQuery();


        TristateResult result = user.getCachedData().getPermissionData(queryOptions).checkPermission(node, Origin.PLATFORM_PERMISSION_CHECK);
        VerboseCheckTarget target = VerboseCheckTarget.internal(profile.getName());
        this.plugin.getVerboseHandler().offerPermissionCheckEvent(Origin.PLATFORM_PERMISSION_CHECK, target, QueryOptionsImpl.DEFAULT_CONTEXTUAL, node, result);
        this.plugin.getPermissionRegistry().offer(node);

        switch (result.result()) {
            case TRUE: return true;
            case FALSE: return false;
            case UNDEFINED: return defaults.get(node) == DefaultPermissionLevel.ALL;
            default: throw new AssertionError();
        }
    }

    @Override
    @NonNull
    public String getNodeDescription(@NonNull String node) {
        String desc = descriptions.get(node);
        return desc == null? "": desc;
    }

    public QueryOptions getDefaultQuery(){
        if (defaultQuery == null) defaultQuery = QueryOptions.contextual(plugin.getContextManager().getStaticContext());
        return defaultQuery;
    }

    private Tristate onOtherPermissionCheck(ISuggestionProvider source, String permission) {
        if (source instanceof CommandSource) {
            String name = ((CommandSource) source).getTextName();
            VerboseCheckTarget target = VerboseCheckTarget.internal(name);

            this.plugin.getVerboseHandler().offerPermissionCheckEvent(Origin.PLATFORM_PERMISSION_CHECK, target, QueryOptionsImpl.DEFAULT_CONTEXTUAL, permission, TristateResult.UNDEFINED);
            this.plugin.getPermissionRegistry().offer(permission);
        }

        return Tristate.UNDEFINED;
    }
}
