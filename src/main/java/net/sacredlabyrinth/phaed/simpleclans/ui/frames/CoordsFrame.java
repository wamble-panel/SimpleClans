package net.sacredlabyrinth.phaed.simpleclans.ui.frames;

import com.cryptomorin.xseries.XMaterial;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.ClanPlayer;
import net.sacredlabyrinth.phaed.simpleclans.RankPermission;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryDrawer;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponent;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponentImpl;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCFrame;
import net.sacredlabyrinth.phaed.simpleclans.utils.Paginator;
import net.sacredlabyrinth.phaed.simpleclans.utils.VanishUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;

public class CoordsFrame extends SCFrame {

	private final List<ClanPlayer> allMembers;
	private final Paginator paginator;

	public CoordsFrame(Player viewer, SCFrame parent, Clan subject) {
		super(parent, viewer);
		allMembers = VanishUtils.getNonVanished(getViewer(), subject);
		// Leaders first.
		allMembers.sort((cp1, cp2) -> Boolean.compare(cp2.isLeader(), cp1.isLeader()));

		paginator = new Paginator(getSize() - 9, allMembers);
	}

	@Override
	public void createComponents() {
		for (int slot = 0; slot < 9; slot++) {
			if (slot == 2 || slot == 6 || slot == 7)
				continue;
			add(Components.getPanelComponent(slot));
		}

		add(Components.getBackComponent(getParent(), 2, getViewer()));

		add(Components.getPreviousPageComponent(6, this::previousPage, paginator, getViewer()));
		add(Components.getNextPageComponent(7, this::nextPage, paginator, getViewer()));
		int slot = 9;
		for (int i = paginator.getMinIndex(); paginator.isValidIndex(i); i++) {
			ClanPlayer cp = allMembers.get(i);
			Player member = cp.toPlayer();
			if (member == null) {
				continue; // logged out since the menu was opened
			}
			Location cpLoc = member.getLocation();
			Location viewerLoc = getViewer().getLocation();
			String distance = Objects.equals(cpLoc.getWorld(), viewerLoc.getWorld())
					? lang("gui.coords.player.lore.distance", getViewer(),
					String.valueOf((int) Math.ceil(cpLoc.distance(viewerLoc))))
					: lang("gui.coords.player.lore.other.world", getViewer());
			String world = cpLoc.getWorld() != null ? cpLoc.getWorld().getName() : "?";

			// Coordinates as strings so MessageFormat doesn't add thousands separators.
			SCComponent c = new SCComponentImpl(lang("gui.playerdetails.player.title",getViewer(), cp.getName()),
					Arrays.asList(distance,
							lang("gui.coords.player.lore.coords",getViewer(), String.valueOf(cpLoc.getBlockX()),
									String.valueOf(cpLoc.getBlockY()), String.valueOf(cpLoc.getBlockZ())),
							lang("gui.coords.player.lore.world",getViewer(), world)),
					XMaterial.PLAYER_HEAD, slot);
			OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(cp.getUniqueId());
			Components.setOwningPlayer(c.getItem(), offlinePlayer);
			c.setListener(ClickType.LEFT, () -> InventoryDrawer.open(new PlayerDetailsFrame(getViewer(), this, offlinePlayer)));
			c.setLorePermission(RankPermission.COORDS);
			add(c);
			slot++;
		
		}
	}

	private void previousPage() {
		if (paginator.previousPage()) {
			updateFrame();
		}
	}

	private void nextPage() {
		if (paginator.nextPage()) {
			updateFrame();
		}
	}

	private void updateFrame() {
		InventoryDrawer.open(this);
	}

	@Override
	@NotNull
	public String getTitle() {
		return lang("gui.coords.title",getViewer());
	}

	@Override
	public int getSize() {
		return 6 * 9;
	}
}
