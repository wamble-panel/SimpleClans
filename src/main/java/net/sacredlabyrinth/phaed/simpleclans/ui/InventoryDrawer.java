package net.sacredlabyrinth.phaed.simpleclans.ui;

import net.sacredlabyrinth.phaed.simpleclans.RankPermission;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.events.FrameOpenEvent;
import net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager;
import net.sacredlabyrinth.phaed.simpleclans.utils.WordWrapper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;
import static net.sacredlabyrinth.phaed.simpleclans.managers.SettingsManager.ConfigField.*;

public class InventoryDrawer {
    private static final SimpleClans plugin = SimpleClans.getInstance();
    private static final ConcurrentHashMap<UUID, SCFrame> OPENING = new ConcurrentHashMap<>();
    private static final int LEGACY_TITLE_LIMIT = 32;
    private static final boolean LONG_TITLES = supportsLongTitles();

    private InventoryDrawer() {
    }

    public static void open(@Nullable SCFrame frame) {
        if (frame == null) {
            return;
        }
        UUID uuid = frame.getViewer().getUniqueId();
        if (frame.equals(OPENING.get(uuid))) {
            return;
        }

	FrameOpenEvent event = new FrameOpenEvent(frame.getViewer(), frame);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
	}
        OPENING.put(uuid, frame);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Inventory inventory;
            try {
                inventory = prepareInventory(frame);
            } catch (RuntimeException ex) {
                // Without this, the stale OPENING entry would make every later open() of this
                // frame (refresh, paging, Back) silently do nothing.
                OPENING.remove(uuid, frame);
                plugin.getLogger().log(Level.SEVERE, "Error building GUI " + frame.getClass().getSimpleName(), ex);
                return;
            }

            if (!frame.equals(OPENING.get(uuid))) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                OPENING.remove(uuid, frame);
                Player viewer = frame.getViewer();
                if (!viewer.isOnline()) {
                    return;
                }
                viewer.openInventory(inventory);
                InventoryController.register(frame);
            });
        });
    }

    @NotNull
    private static Inventory prepareInventory(@NotNull SCFrame frame) {
        Inventory inventory = Bukkit.createInventory(frame.getViewer(), frame.getSize(), fitTitle(frame.getTitle()));
        long start = System.currentTimeMillis();
        setComponents(inventory, frame);

        if (plugin.getSettingsManager().is(DEBUG)) {
            plugin.getLogger().log(Level.INFO,
                    String.format("It took %s millisecond(s) to load the frame %s for %s",
                            System.currentTimeMillis() - start, frame.getTitle(), frame.getViewer().getName()));
        }
        return inventory;
    }

    /**
     * Servers before 1.14 reject inventory titles longer than 32 characters (colour
     * codes included), which crashes the menu. Newer servers get the full title.
     */
    @NotNull
    private static String fitTitle(@NotNull String title) {
        if (LONG_TITLES || title.length() <= LEGACY_TITLE_LIMIT) {
            return title;
        }
        String cut = title.substring(0, LEGACY_TITLE_LIMIT);
        // Don't leave a dangling colour-code prefix at the end.
        return cut.endsWith("§") ? cut.substring(0, cut.length() - 1) : cut;
    }

    private static boolean supportsLongTitles() {
        try {
            // e.g. "1.16.5-R0.1-SNAPSHOT", or year-based versions like "26.2-R0.1-SNAPSHOT"
            String[] parts = Bukkit.getBukkitVersion().split("-")[0].split("\\.");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return major > 1 || minor >= 14;
        } catch (RuntimeException ex) {
            return true;
        }
    }

    private static void setComponents(@NotNull Inventory inventory, @NotNull SCFrame frame) {
        frame.clear();
        try {
            frame.createComponents();
        } catch (NoSuchFieldError ex) {
            runHelpCommand(frame.getViewer());
            return;
        }

        Set<SCComponent> components = frame.getComponents();
        if (components.isEmpty()) {
            plugin.getLogger().warning(String.format("Frame %s has no components", frame.getTitle()));
            return;
        }
        for (SCComponent c : frame.getComponents()) {
            if (c.getSlot() >= frame.getSize()) {
                continue;
            }
            checkLorePermission(frame, c);
            processLineBreaks(c);
            inventory.setItem(c.getSlot(), c.getItem());
        }
    }

    private static void processLineBreaks(SCComponent c) {
        ItemMeta itemMeta = c.getItemMeta();
        if (itemMeta != null) {
            List<String> oldLore = itemMeta.getLore();
            if (oldLore != null) {
                ArrayList<String> newLore = new ArrayList<>();
                for (String line : oldLore) {
                    if (line.isEmpty()) {
                        continue;
                    }
                    WordWrapper wrapper = new WordWrapper(line, plugin.getSettingsManager().getInt(LORE_LENGTH));
                    newLore.addAll(Arrays.asList(wrapper.wrap()));
                }
                itemMeta.setLore(newLore);
                c.setItemMeta(itemMeta);
            }
        }
    }

    private static void runHelpCommand(@NotNull Player player) {
        // Called from the async frame builder; config writes and commands must run on the main thread.
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getServer().getConsoleSender().sendMessage(lang("gui.not.supported"));
            SettingsManager settingsManager = plugin.getSettingsManager();
            settingsManager.set(ENABLE_GUI, false);
            player.performCommand(settingsManager.getString(COMMANDS_CLAN));
        });
    }

    private static void checkLorePermission(@NotNull SCFrame frame, @NotNull SCComponent component) {
        ItemMeta itemMeta = component.getItemMeta();
        if (itemMeta != null) {
            List<String> lore = itemMeta.getLore();
            if (lore != null) {
                Object permission = component.getLorePermission();
                if (permission != null) {
                    if (!hasPermission(frame.getViewer(), permission)) {
                        lore.clear();
                        lore.add(lang("gui.lore.no.permission", frame.getViewer()));
                        itemMeta.setLore(lore);
                        component.setItemMeta(itemMeta);
                    }
                }
            }
        }
    }

    private static boolean hasPermission(@NotNull Player viewer, @NotNull Object permission) {
    	if (permission instanceof String) {
    		return plugin.getPermissionsManager().has(viewer, (String) permission);
		}
    	return plugin.getPermissionsManager().has(viewer, (RankPermission) permission, false);
	}

}
