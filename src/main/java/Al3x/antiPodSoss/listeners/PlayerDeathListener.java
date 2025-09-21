package Al3x.antiPodSoss.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import Al3x.antiPodSoss.AntiPodSos;

import java.util.ArrayList;
import java.util.List;

public class PlayerDeathListener implements Listener {

    private final AntiPodSos plugin;

    public PlayerDeathListener(AntiPodSos plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer == null || killer.equals(victim)) {
            return;
        }

        List<ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();
        event.setDroppedExp(0);

        for (ItemStack itemStack : drops) {
            if (itemStack != null && !itemStack.getType().isAir()) {
                org.bukkit.entity.Item item = victim.getWorld().dropItemNaturally(
                        victim.getLocation(), itemStack
                );
                plugin.getLootManager().protectLoot(item, killer, victim);
            }
        }
    }
}