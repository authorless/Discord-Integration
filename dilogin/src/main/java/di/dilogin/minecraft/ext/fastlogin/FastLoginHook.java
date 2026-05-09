package di.dilogin.minecraft.ext.fastlogin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;

import di.dilogin.controller.MainController;
import di.dilogin.dao.DIUserDao;
import di.dilogin.entity.DIUser;
import di.dilogin.minecraft.cache.UserSessionCache;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.dv8tion.jda.api.entities.User;

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
     * Register DILogin as FastLogin's auth plugin so FastLogin stops complaining
     * about "No support offline Auth plugin found" and delegates premium logins
     * to us. Uses reflection + a JDK proxy so we never need a compile-time
     * dependency on FastLogin.
     *
     * @return {@code true} if the hook was registered.
     */
    public static boolean registerAuthHookIfPresent(Logger logger) {
        if (!isInstalled())
            return false;

        Class<?> authPluginIface = resolveAuthPluginInterface();
        if (authPluginIface == null) {
            logger.warning("FastLogin AuthPlugin interface not found — auth bridging disabled. "
                    + "FastLogin may use a package this plugin doesn't recognise.");
            return false;
        }

        try {
            Object proxy = Proxy.newProxyInstance(
                    authPluginIface.getClassLoader(),
                    new Class<?>[] { authPluginIface },
                    new AuthPluginInvocationHandler());

            Object core = invoke(cachedPlugin, "getCore");
            if (core == null) {
                logger.warning("FastLogin#getCore() returned null — cannot register auth hook.");
                return false;
            }

            // Try every plausible registration method exposed by FastLogin core.
            String[] setterCandidates = { "setAuthPluginHook", "registerAuthPlugin", "setAuthPlugin" };
            for (String name : setterCandidates) {
                Method setter = findMethod(core.getClass(), name, authPluginIface);
                if (setter == null)
                    setter = findMethod(core.getClass(), name, Object.class);
                if (setter != null) {
                    setter.invoke(core, proxy);
                    logger.info("Registered DILogin as FastLogin's auth plugin via " + name + "().");
                    return true;
                }
            }

            logger.warning("FastLogin core has no recognised setAuthPluginHook / registerAuthPlugin — auth bridging disabled.");
            return false;
        } catch (Throwable t) {
            logger.log(Level.WARNING, "Failed to register DILogin as FastLogin auth plugin: " + t.getMessage(), t);
            return false;
        }
    }

    /**
     * Resolves FastLogin's {@code AuthPlugin} interface against the runtime
     * jar. Confirmed against FastLogin v1.12-SNAPSHOT (July 2025): the
     * canonical package is {@code com.github.games647.fastlogin.core.hooks}.
     * Older / forked versions are tried as a fallback.
     */
    private static Class<?> resolveAuthPluginInterface() {
        if (cachedPlugin == null)
            return null;
        ClassLoader cl = cachedPlugin.getClass().getClassLoader();
        try {
            return Class.forName("com.github.games647.fastlogin.core.hooks.AuthPlugin", true, cl);
        } catch (ClassNotFoundException ignored) {
            // fall through to legacy candidates
        }
        String[] legacy = {
                "com.github.games647.fastlogin.bukkit.hooks.AuthPlugin",
                "com.github.games647.fastlogin.bukkit.hook.AuthPlugin",
                "com.github.games647.fastlogin.core.hook.AuthPlugin"
        };
        for (String name : legacy) {
            try {
                return Class.forName(name, true, cl);
            } catch (ClassNotFoundException ignored) {
                // try next
            }
        }
        return null;
    }

    /**
     * Bridge between FastLogin's {@code AuthPlugin<Player>} interface and
     * DILogin. We do not implement the interface directly to avoid a compile-
     * time dependency on FastLogin classes; a JDK proxy dispatches the three
     * methods FastLogin actually calls.
     */
    private static final class AuthPluginInvocationHandler implements InvocationHandler {
        @Override
        public Object invoke(Object proxyObj, Method method, Object[] args) {
            String name = method.getName();
            try {
                switch (name) {
                    case "forceLogin":
                        return forceLogin((Player) args[0]);
                    case "isRegistered":
                        return isRegistered((String) args[0]);
                    case "forceRegister":
                        return forceRegister((Player) args[0], (String) args[1]);
                    case "toString":
                        return "DILoginAuthPlugin";
                    case "hashCode":
                        return System.identityHashCode(proxyObj);
                    case "equals":
                        return proxyObj == args[0];
                    default:
                        return false;
                }
            } catch (Throwable t) {
                return false;
            }
        }

        private boolean forceLogin(Player player) {
            if (player == null) return false;
            try {
                DIUserDao dao = MainController.getDILoginController().getDIUserDao();
                if (!dao.contains(player.getName()))
                    return false;
                String ip = player.getAddress() != null
                        ? player.getAddress().getAddress().getHostAddress() : "unknown";
                UserSessionCache.addSession(player.getName(), ip);
                java.util.Optional<DIUser> diUser = dao.get(player.getName());
                if (diUser.isPresent() && diUser.get().getPlayerDiscord().isPresent()) {
                    User discordUser = diUser.get().getPlayerDiscord().get();
                    MainController.getDILoginController().loginUser(player.getName(), discordUser);
                }
                return true;
            } catch (Throwable t) {
                return false;
            }
        }

        private boolean isRegistered(String playerName) {
            try {
                return MainController.getDILoginController().getDIUserDao().contains(playerName);
            } catch (Throwable t) {
                return false;
            }
        }

        private boolean forceRegister(Player player, String password) {
            // DILogin registration requires Discord linking — we cannot complete it
            // from FastLogin's side. Returning false makes FastLogin treat the
            // player as unregistered so the standard DILogin register flow kicks in.
            return false;
        }
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
