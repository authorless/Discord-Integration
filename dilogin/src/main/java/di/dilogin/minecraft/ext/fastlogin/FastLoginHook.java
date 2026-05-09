package di.dilogin.minecraft.ext.fastlogin;

import java.lang.reflect.Method;

import di.dilogin.controller.MainController;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Reflective bridge into FastLogin. Detects whether a player connecting under
 * a given name is verified as the real Mojang owner by FastLogin's
 * protocol-level handshake.
 *
 * The plugin is treated as a soft optional dependency: if FastLogin is not
 * installed every call returns {@code false} and the bypass is skipped.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FastLoginHook {

    private static volatile Boolean available;
    private static volatile Object cachedPlugin;

    /**
     * @return {@code true} if FastLogin (any flavour: Bukkit/Bungee) is loaded
     *         and enabled in this JVM.
     */
    public static boolean isInstalled() {
        if (available != null && available) {
            return true;
        }
        Object plugin = resolveFastLoginPlugin();
        cachedPlugin = plugin;
        available = plugin != null;
        return available;
    }

    /**
     * Best-effort premium check. Returns {@code true} only when FastLogin
     * confirms the connecting account is premium and verified. Any error or
     * absence of data falls back to {@code false} so the bypass is conservative.
     */
    public static boolean isVerifiedPremium(String playerName) {
        if (playerName == null || playerName.isEmpty())
            return false;
        if (!isInstalled())
            return false;
        try {
            Object plugin = cachedPlugin;
            if (plugin == null)
                return false;

            Object core = invoke(plugin, "getCore");
            if (core == null)
                return false;

            Object storage = invoke(core, "getStorage");
            if (storage == null)
                return false;

            Method loadProfile = findMethod(storage.getClass(), "loadProfile", String.class);
            if (loadProfile == null)
                return false;
            Object profile = loadProfile.invoke(storage, playerName);
            if (profile == null)
                return false;

            Object isPremium = invoke(profile, "isPremium");
            return Boolean.TRUE.equals(isPremium);
        } catch (Throwable t) {
            // Swallow reflection errors — bypass should never crash the join flow.
            return false;
        }
    }

    private static Object resolveFastLoginPlugin() {
        try {
            // Bukkit-side
            Class.forName("org.bukkit.Bukkit");
            Object pluginManager = Class.forName("org.bukkit.Bukkit")
                    .getMethod("getPluginManager").invoke(null);
            Object plugin = pluginManager.getClass().getMethod("getPlugin", String.class)
                    .invoke(pluginManager, "FastLogin");
            if (plugin != null && Boolean.TRUE.equals(plugin.getClass().getMethod("isEnabled").invoke(plugin))) {
                return plugin;
            }
        } catch (Throwable ignored) {
            // not Bukkit or FastLogin not present
        }
        try {
            // Bungee-side
            Class<?> proxyServer = Class.forName("net.md_5.bungee.api.ProxyServer");
            Object proxy = proxyServer.getMethod("getInstance").invoke(null);
            Object pluginManager = proxy.getClass().getMethod("getPluginManager").invoke(proxy);
            Object plugin = pluginManager.getClass().getMethod("getPlugin", String.class)
                    .invoke(pluginManager, "FastLogin");
            if (plugin != null) {
                return plugin;
            }
        } catch (Throwable ignored) {
            // not Bungee or FastLogin not present
        }
        return null;
    }

    private static Object invoke(Object target, String methodName) throws Exception {
        Method m = findMethod(target.getClass(), methodName);
        if (m == null)
            return null;
        return m.invoke(target);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params) {
        Class<?> current = type;
        while (current != null) {
            try {
                Method m = current.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * @return {@code true} if the {@code bypass_premium_login} flag is enabled
     *         in the dilogin config.
     */
    public static boolean isBypassEnabled() {
        try {
            return MainController.getDIApi().getInternalController().getConfigManager()
                    .contains("bypass_premium_login")
                    && MainController.getDIApi().getInternalController().getConfigManager()
                    .getBoolean("bypass_premium_login");
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * Bypass is granted only when the operator opted in, FastLogin says the
     * player is a verified premium account, <em>and</em> the player already has
     * a registered DIUser in the database.
     *
     * Skipping the Discord flow for an unregistered premium player would mean
     * they could play without ever linking their Discord account — defeating
     * the point of DILogin. So unregistered premiums still go through the
     * normal register flow once.
     */
    public static boolean shouldBypass(String playerName) {
        if (!isBypassEnabled() || !isVerifiedPremium(playerName))
            return false;
        try {
            return MainController.getDILoginController().getDIUserDao().contains(playerName);
        } catch (Throwable t) {
            // DB not ready or DAO unavailable — fall back to non-bypass to be safe.
            return false;
        }
    }
}
