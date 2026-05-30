/*
 * Copyright (c) 2016-2017 Daniel Ennis (Aikar) - MIT License
 *
 *  Permission is hereby granted, free of charge, to any person obtaining
 *  a copy of this software and associated documentation files (the
 *  "Software"), to deal in the Software without restriction, including
 *  without limitation the rights to use, copy, modify, merge, publish,
 *  distribute, sublicense, and/or sell copies of the Software, and to
 *  permit persons to whom the Software is furnished to do so, subject to
 *  the following conditions:
 *
 *  The above copyright notice and this permission notice shall be
 *  included in all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 *  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 *  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 *  LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 *  OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 *  WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package co.aikar.commands;

import co.aikar.commands.apachecommonslang.ApacheCommonsExceptionUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginIdentifiableCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.help.GenericCommandHelpTopic;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.ScoreboardManager;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Patched version of ACF's BukkitCommandManager.
 *
 * <p>This replaces the bundled ACF {@code BukkitCommandManager} to fix a
 * {@link NoSuchFieldException} that occurs on Paper 1.20.5+ when a player
 * joins. Newer Paper versions removed the {@code CraftPlayer.locale} field
 * that the original ACF code was reading via reflection. The fix uses the
 * public Bukkit API {@link Player#getLocale()} (available since Bukkit 1.12)
 * instead of reflection, which works correctly on all supported server
 * versions.</p>
 *
 * @see <a href="https://github.com/nicholasnassar/SimpleClans/issues">SimpleClans issue tracker</a>
 */
@SuppressWarnings("WeakerAccess")
public class BukkitCommandManager extends CommandManager<
        CommandSender,
        BukkitCommandIssuer,
        ChatColor,
        BukkitMessageFormatter,
        BukkitCommandExecutionContext,
        BukkitConditionContext
        > {

    @SuppressWarnings("WeakerAccess")
    protected final Plugin plugin;
    private final CommandMap commandMap;
    protected ACFBukkitScheduler scheduler;
    private final Logger logger;
    public final Integer mcMinorVersion;
    public final Integer mcPatchVersion;
    protected Map<String, Command> knownCommands = new HashMap<>();
    protected Map<String, BukkitRootCommand> registeredCommands = new HashMap<>();
    protected BukkitCommandContexts contexts;
    protected BukkitCommandCompletions completions;
    protected BukkitLocales locales;
    protected Map<UUID, String> issuersLocaleString = new ConcurrentHashMap<>();
    private boolean cantReadLocale = false;
    protected boolean autoDetectFromClient = true;

    public BukkitCommandManager(Plugin plugin) {
        this.plugin = plugin;

        // Use the standard Bukkit scheduler (ACFPaperScheduler requires Paper 1.20.6+
        // and may not be available in the version of ACF bundled with this plugin).
        this.scheduler = new ACFBukkitScheduler();

        String prefix = this.plugin.getDescription().getPrefix();
        this.logger = Logger.getLogger(prefix != null ? prefix : this.plugin.getName());
        this.commandMap = hookCommandMap();
        this.formatters.put(MessageType.ERROR, defaultFormatter = new BukkitMessageFormatter(ChatColor.RED, ChatColor.YELLOW, ChatColor.RED));
        this.formatters.put(MessageType.SYNTAX, new BukkitMessageFormatter(ChatColor.YELLOW, ChatColor.GREEN, ChatColor.WHITE));
        this.formatters.put(MessageType.INFO, new BukkitMessageFormatter(ChatColor.BLUE, ChatColor.DARK_GREEN, ChatColor.GREEN));
        this.formatters.put(MessageType.HELP, new BukkitMessageFormatter(ChatColor.AQUA, ChatColor.GREEN, ChatColor.YELLOW));
        Pattern versionPattern = Pattern.compile("\\(MC: (\\d)\\.(\\d+)\\.?(\\d+?)?\\)");
        Matcher matcher = versionPattern.matcher(Bukkit.getVersion());
        if (matcher.find()) {
            this.mcMinorVersion = ACFUtil.parseInt(matcher.toMatchResult().group(2), 0);
            this.mcPatchVersion = ACFUtil.parseInt(matcher.toMatchResult().group(3), 0);
        } else {
            this.mcMinorVersion = -1;
            this.mcPatchVersion = -1;
        }
        Bukkit.getHelpMap().registerHelpTopicFactory(BukkitRootCommand.class, command -> {
            if (hasUnstableAPI("help")) {
                return new ACFBukkitHelpTopic(this, (BukkitRootCommand) command);
            } else {
                return new GenericCommandHelpTopic(command);
            }
        });

        Bukkit.getPluginManager().registerEvents(new ACFBukkitListener(this, plugin), plugin);

        getLocales(); // auto load locales

        // Attempt to register a PlayerLocaleChangeEvent listener (Bukkit 1.12+) for
        // real-time locale change detection. If the event or the listener class is not
        // available, fall back to polling via the scheduler task.
        boolean registeredLocaleListener = false;
        try {
            Class.forName("org.bukkit.event.player.PlayerLocaleChangeEvent");
            // ACFBukkitLocalesListener may not exist in older ACF snapshots; load it
            // dynamically so we don't get a compile-time or class-not-found error.
            try {
                Class<?> listenerClass = Class.forName("co.aikar.commands.ACFBukkitLocalesListener");
                Listener listener = (Listener) listenerClass
                        .getDeclaredConstructor(BukkitCommandManager.class)
                        .newInstance(this);
                Bukkit.getPluginManager().registerEvents(listener, plugin);
                registeredLocaleListener = true;
            } catch (Exception ignored) {
                // ACFBukkitLocalesListener not present in this ACF build; use the
                // scheduler-based polling fallback below.
            }
        } catch (ClassNotFoundException ignored) {
            // PlayerLocaleChangeEvent is not in the server's classpath (very old
            // Bukkit); always use the scheduler polling fallback.
        }

        if (!registeredLocaleListener) {
            scheduler.createLocaleTask(plugin, () -> {
                if (this.cantReadLocale || !this.autoDetectFromClient) {
                    return;
                }
                Bukkit.getOnlinePlayers().forEach(this::readPlayerLocale);
            }, 30, 30);
        }

        this.validNamePredicate = ACFBukkitUtil::isValidName;

        registerDependency(plugin.getClass(), plugin);
        registerDependency(Logger.class, plugin.getLogger());
        registerDependency(FileConfiguration.class, plugin.getConfig());
        registerDependency(FileConfiguration.class, "config", plugin.getConfig());
        registerDependency(Plugin.class, plugin);
        registerDependency(JavaPlugin.class, plugin);
        registerDependency(PluginManager.class, Bukkit.getPluginManager());
        registerDependency(Server.class, Bukkit.getServer());
        scheduler.registerSchedulerDependencies(this);
        registerDependency(ScoreboardManager.class, Bukkit.getScoreboardManager());
        registerDependency(ItemFactory.class, Bukkit.getItemFactory());
        registerDependency(PluginDescriptionFile.class, plugin.getDescription());
    }

    @NotNull
    private CommandMap hookCommandMap() {
        CommandMap commandMap = null;
        try {
            Server server = Bukkit.getServer();
            Method getCommandMap = server.getClass().getDeclaredMethod("getCommandMap");
            getCommandMap.setAccessible(true);
            commandMap = (CommandMap) getCommandMap.invoke(server);
            if (!SimpleCommandMap.class.isAssignableFrom(commandMap.getClass())) {
                this.log(LogLevel.ERROR, "ERROR: CommandMap has been hijacked! Offending command map is located at: " + commandMap.getClass().getName());
                this.log(LogLevel.ERROR, "We are going to try to hijack it back and resolve this, but you are now in dangerous territory.");
                this.log(LogLevel.ERROR, "We can not guarantee things are going to work.");
                Field cmField = server.getClass().getDeclaredField("commandMap");
                commandMap = new ProxyCommandMap(this, commandMap);
                cmField.set(server, commandMap);
                this.log(LogLevel.INFO, "Injected Proxy Command Map... good luck...");
            }
            Field knownCommands = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownCommands.setAccessible(true);
            //noinspection unchecked
            this.knownCommands = (Map<String, Command>) knownCommands.get(commandMap);
        } catch (Exception e) {
            this.log(LogLevel.ERROR, "Failed to get Command Map. ACF will not function.");
            ACFUtil.sneaky(e);
        }
        return commandMap;
    }

    public Plugin getPlugin() {
        return this.plugin;
    }

    @Override
    public boolean isCommandIssuer(Class<?> type) {
        return CommandSender.class.isAssignableFrom(type);
    }

    @Override
    public synchronized CommandContexts<BukkitCommandExecutionContext> getCommandContexts() {
        if (this.contexts == null) {
            this.contexts = new BukkitCommandContexts(this);
        }
        return contexts;
    }

    @Override
    public synchronized CommandCompletions<BukkitCommandCompletionContext> getCommandCompletions() {
        if (this.completions == null) {
            this.completions = new BukkitCommandCompletions(this);
        }
        return completions;
    }

    @Override
    public BukkitLocales getLocales() {
        if (this.locales == null) {
            this.locales = new BukkitLocales(this);
            this.locales.loadLanguages();
        }
        return locales;
    }

    @Override
    public boolean hasRegisteredCommands() {
        return !registeredCommands.isEmpty();
    }

    public void registerCommand(BaseCommand command, boolean force) {
        final String plugin = this.plugin.getName().toLowerCase(Locale.ENGLISH);
        command.onRegister(this);
        for (Map.Entry<String, RootCommand> entry : command.registeredCommands.entrySet()) {
            String commandName = entry.getKey().toLowerCase(Locale.ENGLISH);
            BukkitRootCommand bukkitCommand = (BukkitRootCommand) entry.getValue();
            if (!bukkitCommand.isRegistered) {
                Command oldCommand = commandMap.getCommand(commandName);
                if (oldCommand instanceof PluginIdentifiableCommand && ((PluginIdentifiableCommand) oldCommand).getPlugin() == this.plugin) {
                    knownCommands.remove(commandName);
                    oldCommand.unregister(commandMap);
                } else if (oldCommand != null && force) {
                    knownCommands.remove(commandName);
                    for (Map.Entry<String, Command> ce : knownCommands.entrySet()) {
                        String key = ce.getKey();
                        Command value = ce.getValue();
                        if (key.contains(":") && oldCommand.equals(value)) {
                            String[] split = ACFPatterns.COLON.split(key, 2);
                            if (split.length > 1) {
                                oldCommand.unregister(commandMap);
                                oldCommand.setLabel(split[0] + ":" + command.getName());
                                oldCommand.register(commandMap);
                            }
                        }
                    }
                }
                commandMap.register(commandName, plugin, bukkitCommand);
            }
            bukkitCommand.isRegistered = true;
            registeredCommands.put(commandName, bukkitCommand);
        }
    }

    @Override
    public void registerCommand(BaseCommand command) {
        registerCommand(command, false);
    }

    public void unregisterCommand(BaseCommand command) {
        for (RootCommand rootcommand : command.registeredCommands.values()) {
            BukkitRootCommand bukkitCommand = (BukkitRootCommand) rootcommand;
            bukkitCommand.getSubCommands().values().removeAll(command.subCommands.values());
            if (bukkitCommand.isRegistered && bukkitCommand.getSubCommands().isEmpty()) {
                unregisterCommand(bukkitCommand);
                bukkitCommand.isRegistered = false;
            }
        }
    }

    /**
     * @param command the command to unregister
     * @deprecated Use unregisterCommand(BaseCommand) - this will be visibility reduced later.
     */
    @Deprecated
    public void unregisterCommand(BukkitRootCommand command) {
        final String plugin = this.plugin.getName().toLowerCase(Locale.ENGLISH);
        command.unregister(commandMap);
        String key = command.getName();
        Command registered = knownCommands.get(key);
        if (command.equals(registered)) {
            knownCommands.remove(key);
        }
        knownCommands.remove(plugin + ":" + key);
        registeredCommands.remove(key);
    }

    public void unregisterCommands() {
        for (String key : new HashSet<>(registeredCommands.keySet())) {
            unregisterCommand(registeredCommands.get(key));
        }
    }

    /**
     * Attempts to find the {@code entity} field that CraftBukkit uses to expose
     * the underlying NMS entity.  Returns {@code null} when the field cannot be
     * located (e.g. on a non-CraftBukkit server or a future version that renamed
     * the field).
     */
    private Field getEntityField(Player player) {
        Class<?> cls = player.getClass();
        while (cls != Object.class) {
            if (cls.getName().endsWith("CraftEntity")) {
                try {
                    Field field = cls.getDeclaredField("entity");
                    field.setAccessible(true);
                    return field;
                } catch (NoSuchFieldException e) {
                    return null;
                }
            }
            cls = cls.getSuperclass();
        }
        return null;
    }

    public Locale setPlayerLocale(Player player, Locale locale) {
        issuersLocaleString.put(player.getUniqueId(), locale.toString());
        return this.setIssuerLocale(player, locale);
    }

    /**
     * Reads the player's current locale and updates the internal locale map.
     *
     * <p><b>Patch note:</b> The original ACF implementation used
     * {@code CraftPlayer.getDeclaredField("locale")} which stopped working on
     * Paper 1.20.5+ (Paper's reflection rewriter blocks direct access to
     * internal fields). This version first tries the public Bukkit API
     * {@link Player#getLocale()} (available since Bukkit 1.12) and only falls
     * back to NMS reflection as a last resort for very old server builds.</p>
     */
    void readPlayerLocale(Player player) {
        if (!player.isOnline() || cantReadLocale) {
            return;
        }
        try {
            String localeString = null;
            Locale locale = null;

            // Primary: use the public Bukkit API (Bukkit 1.12+).
            // This works on all modern server implementations without requiring
            // internal (NMS/CraftBukkit) field access.
            try {
                localeString = player.getLocale();
            } catch (Exception ignored) {
                // Extremely unlikely on any supported server version, but fall
                // through to the NMS reflection path below just in case.
            }

            // Fallback: NMS reflection for servers older than Bukkit 1.12 that
            // don't expose getLocale().  Modern Paper versions (1.20.5+) will
            // throw NoSuchFieldException here because the reflection rewriter
            // prevents CraftPlayer.locale field access — that is exactly the
            // bug we are fixing by preferring getLocale() above.
            if (localeString == null || localeString.isEmpty()) {
                Field entityField = getEntityField(player);
                if (entityField != null) {
                    try {
                        Object nmsPlayer = entityField.get(player);
                        if (nmsPlayer != null) {
                            Field localeField = nmsPlayer.getClass().getDeclaredField("locale");
                            localeField.setAccessible(true);
                            Object localeObj = localeField.get(nmsPlayer);
                            if (localeObj != null) {
                                localeString = localeObj.toString();
                            }
                        }
                    } catch (Exception ignored) {
                        // Field not accessible on this server version — give up.
                    }
                }
            }

            if (localeString != null && !localeString.isEmpty()) {
                if (!localeString.equals(issuersLocaleString.get(player.getUniqueId()))) {
                    // Player#getLocale() returns "en_US" style tags; BCP 47
                    // (used by Locale.forLanguageTag) requires hyphens.
                    locale = Locale.forLanguageTag(localeString.replace('_', '-'));
                }
            }

            if (locale != null) {
                UUID playerUniqueId = player.getUniqueId();
                Locale prev = issuersLocale.put(playerUniqueId, locale);
                issuersLocaleString.put(playerUniqueId, localeString);
                if (!Objects.equals(locale, prev)) {
                    this.notifyLocaleChange(getCommandIssuer(player), prev, locale);
                }
            }
        } catch (Exception e) {
            cantReadLocale = true;
            try {
                this.scheduler.cancelLocaleTask();
            } catch (Exception ignored) {
                // cancelLocaleTask may not exist in the bundled ACF version.
            }
            this.log(LogLevel.INFO,
                    "Can't read players locale, you will be unable to automatically detect"
                    + " players language. Only Bukkit 1.12+ is supported for this.", e);
        }
    }

    public ACFBukkitScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public RootCommand createRootCommand(String cmd) {
        return new BukkitRootCommand(this, cmd);
    }

    @Override
    public Collection<RootCommand> getRegisteredRootCommands() {
        return Collections.unmodifiableCollection(registeredCommands.values());
    }

    @Override
    public BukkitCommandIssuer getCommandIssuer(Object issuer) {
        if (!(issuer instanceof CommandSender)) {
            throw new IllegalArgumentException(issuer.getClass().getName() + " is not a Command Issuer.");
        }
        return new BukkitCommandIssuer(this, (CommandSender) issuer);
    }

    @Override
    public BukkitCommandExecutionContext createCommandContext(RegisteredCommand command, CommandParameter parameter, CommandIssuer sender, List<String> args, int i, Map<String, Object> passedArgs) {
        return new BukkitCommandExecutionContext(command, parameter, (BukkitCommandIssuer) sender, args, i, passedArgs);
    }

    @Override
    public BukkitCommandCompletionContext createCompletionContext(RegisteredCommand command, CommandIssuer sender, String input, String config, String[] args) {
        return new BukkitCommandCompletionContext(command, (BukkitCommandIssuer) sender, input, config, args);
    }

    @Override
    public RegisteredCommand createRegisteredCommand(BaseCommand command, String cmdName, Method method, String prefSubCommand) {
        return new BukkitRegisteredCommand(command, cmdName, method, prefSubCommand);
    }

    @Override
    public BukkitConditionContext createConditionContext(CommandIssuer issuer, String config) {
        return new BukkitConditionContext((BukkitCommandIssuer) issuer, config);
    }

    @Override
    public void log(LogLevel level, String message, Throwable throwable) {
        Level logLevel = level == LogLevel.INFO ? Level.INFO : Level.SEVERE;
        logger.log(logLevel, LogLevel.LOG_PREFIX + message);
        if (throwable != null) {
            for (String line : ACFPatterns.NEWLINE.split(ApacheCommonsExceptionUtil.getFullStackTrace(throwable))) {
                logger.log(logLevel, LogLevel.LOG_PREFIX + line);
            }
        }
    }

    @Override
    public boolean usePerIssuerLocale(boolean setting) {
        return usePerIssuerLocale(setting, setting);
    }

    public boolean usePerIssuerLocale(boolean usePerIssuerLocale, boolean autoDetectFromClient) {
        boolean old = this.usePerIssuerLocale;
        this.usePerIssuerLocale = usePerIssuerLocale;
        this.autoDetectFromClient = autoDetectFromClient;
        return old;
    }

    @Override
    public String getCommandPrefix(CommandIssuer issuer) {
        return issuer.isPlayer() ? "/" : "";
    }

    @Override
    protected boolean handleUncaughtException(BaseCommand scope, RegisteredCommand registeredCommand, CommandIssuer sender, List<String> args, Throwable t) {
        if (t instanceof CommandException && t.getCause() != null && t.getMessage().startsWith("Unhandled exception")) {
            t = t.getCause();
        }
        return super.handleUncaughtException(scope, registeredCommand, sender, args, t);
    }
}
