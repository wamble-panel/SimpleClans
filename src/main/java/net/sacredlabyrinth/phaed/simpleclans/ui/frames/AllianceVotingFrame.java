package net.sacredlabyrinth.phaed.simpleclans.ui.frames;

import com.cryptomorin.xseries.XMaterial;
import net.sacredlabyrinth.phaed.simpleclans.ChatBlock;
import net.sacredlabyrinth.phaed.simpleclans.Clan;
import net.sacredlabyrinth.phaed.simpleclans.SimpleClans;
import net.sacredlabyrinth.phaed.simpleclans.alliance.AllianceMeetingManager;
import net.sacredlabyrinth.phaed.simpleclans.alliance.AllianceType;
import net.sacredlabyrinth.phaed.simpleclans.alliance.Proposal;
import net.sacredlabyrinth.phaed.simpleclans.ui.InventoryDrawer;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponent;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCComponentImpl;
import net.sacredlabyrinth.phaed.simpleclans.ui.SCFrame;
import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static net.sacredlabyrinth.phaed.simpleclans.SimpleClans.lang;

/**
 * The per-alliance voting GUI. Lists the proposals locked into the active meeting,
 * each with an Agree (green concrete) and Disagree (red concrete) button; the
 * currently-selected option is highlighted with an enchant glow. Votes can be
 * changed until the meeting ends.
 *
 * <p>Strictly scoped: the frame carries its {@link AllianceType} and reads/writes
 * only that alliance's proposals — clicking can never affect the rival alliance.</p>
 */
public class AllianceVotingFrame extends SCFrame {

    private final AllianceType type;
    private final AllianceMeetingManager manager;

    public AllianceVotingFrame(@Nullable SCFrame parent, Player viewer, AllianceType type) {
        super(parent, viewer);
        this.type = type;
        this.manager = SimpleClans.getInstance().getAllianceMeetingManager();
    }

    @Override
    public String getTitle() {
        return ChatColor.translateAlternateColorCodes('&',
                type.getColorCode() + type.getPlainSymbol() + " " + lang("alliance.gui.vote.title", getViewer(),
                        type.getDisplayName()));
    }

    @Override
    public int getSize() {
        return 54;
    }

    @Override
    public void createComponents() {
        List<Proposal> list = manager.getMeetingProposals(type);
        Clan clan = SimpleClans.getInstance().getClanManager().getClanByPlayerUniqueId(getViewer().getUniqueId());
        String clanTag = clan != null ? clan.getTag() : null;

        int rows = getSize() / 9;
        int shown = Math.min(list.size(), rows);
        for (int i = 0; i < shown; i++) {
            Proposal proposal = list.get(i);
            int base = i * 9;

            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + lang("alliance.gui.vote.proposer", getViewer(), proposal.getProposerName()));
            lore.add(ChatColor.GREEN + "Agree: " + proposal.getAgreeCount()
                    + ChatColor.GRAY + "  " + ChatColor.RED + "Disagree: " + proposal.getDisagreeCount());

            SCComponent label = new SCComponentImpl.Builder(XMaterial.PAPER)
                    .withDisplayName(ChatColor.AQUA + "#" + proposal.getId() + " " + ChatColor.WHITE + proposal.describe())
                    .withLore(lore)
                    .withSlot(base)
                    .build();
            add(label);

            Boolean current = clanTag != null ? proposal.getVote(clanTag) : null;

            SCComponent agree = new SCComponentImpl.Builder(XMaterial.LIME_CONCRETE)
                    .withDisplayName(ChatColor.GREEN + lang("alliance.gui.vote.agree", getViewer()))
                    .withSlot(base + 5)
                    .build();
            if (Boolean.TRUE.equals(current)) {
                applyGlow(agree);
            }
            agree.setListener(ClickType.LEFT, () -> vote(proposal, true));
            add(agree);

            SCComponent disagree = new SCComponentImpl.Builder(XMaterial.RED_CONCRETE)
                    .withDisplayName(ChatColor.RED + lang("alliance.gui.vote.disagree", getViewer()))
                    .withSlot(base + 6)
                    .build();
            if (Boolean.FALSE.equals(current)) {
                applyGlow(disagree);
            }
            disagree.setListener(ClickType.LEFT, () -> vote(proposal, false));
            add(disagree);
        }
    }

    private void vote(Proposal proposal, boolean agree) {
        Player viewer = getViewer();
        Clan clan = SimpleClans.getInstance().getClanManager().getClanByPlayerUniqueId(viewer.getUniqueId());
        if (clan == null) {
            ChatBlock.sendMessageKey(viewer, "not.a.member.of.any.clan");
            return;
        }
        AllianceMeetingManager.VoteResult result = manager.castVote(clan, type, proposal.getId(), agree);
        if (result == AllianceMeetingManager.VoteResult.NO_MEETING) {
            ChatBlock.sendMessageKey(viewer, "alliance.vote.closed");
            viewer.closeInventory();
            return;
        }
        // Re-render so the glow moves to the chosen option and tallies refresh.
        InventoryDrawer.open(this);
    }

    private void applyGlow(SCComponent component) {
        ItemMeta meta = component.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            component.setItemMeta(meta);
        }
    }
}
