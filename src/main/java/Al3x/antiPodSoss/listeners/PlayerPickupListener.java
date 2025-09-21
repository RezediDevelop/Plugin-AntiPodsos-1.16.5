package Al3x.antiPodSoss.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPickupItemEvent;
import Al3x.antiPodSoss.AntiPodSos;

public class PlayerPickupListener implements Listener {

    private final AntiPodSos plugin;

    public PlayerPickupListener(AntiPodSos plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerPickup(PlayerPickupItemEvent event) {
        if (!plugin.getLootManager().canPickup(event.getPlayer(), event.getItem())) {
            event.setCancelled(true);
        }
    }
}