package Al3x.antiPodSoss;

import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LootManager {

    private final JavaPlugin plugin;
    private final Map<UUID, ProtectedItem> protectedItems = new ConcurrentHashMap<>();
    private final Set<UUID> blockedPickupPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private FileConfiguration config;

    public LootManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
        startCleanupTask();
    }

    // Автоматическая очистка сообщений чата
    private void startCleanupTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cleanupChatMessages();
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    // Очистка сообщений чата
    private void cleanupChatMessages() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasMetadata("loot_timer_message")) {
                for (int i = 0; i < 5; i++) {
                    player.sendMessage(" ");
                }
                player.removeMetadata("loot_timer_message", plugin);
            }
        }
    }

    public void protectLoot(Item item, Player killer, Player victim) {
        UUID itemId = item.getUniqueId();

        int duration = config.getInt("settings.protection-time", 30);
        ProtectedItem protectedItem = new ProtectedItem(item, killer.getUniqueId(), duration);
        protectedItems.put(itemId, protectedItem);

        applyGlowEffect(item);
        createHologram(item, duration);

        // Блокируем подбор для всех кроме убийцы
        blockPickupForOthers(killer, duration);

        // Сообщение в actionbar вместо чата
        String message = config.getString("settings.messages.killer-notification",
                        "§aТолько вы можете забрать лут: §e%time%§a сек")
                .replace("%time%", String.valueOf(duration));

        sendActionBar(killer, message);

        // ЗАПУСК ТАЙМЕРА - ЭТО БЫЛО ПРОПУЩЕНО!
        startProtectionTimer(itemId);
    }

    // Блокировка подбора
    private void blockPickupForOthers(Player exception, int duration) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.equals(exception)) {
                blockedPickupPlayers.add(player.getUniqueId());
            }
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                blockedPickupPlayers.removeIf(uuid -> !uuid.equals(exception.getUniqueId()));
            }
        }.runTaskLater(plugin, duration * 20L);
    }

    // Отправка в actionbar
    private void sendActionBar(Player player, String message) {
        player.sendTitle("", ChatColor.translateAlternateColorCodes('&', message),
                10, 40, 10);
    }

    private void createHologram(Item item, int secondsLeft) {
        Location loc = item.getLocation().add(0, 0.5, 0);
        ArmorStand hologram = item.getWorld().spawn(loc, ArmorStand.class);

        hologram.setVisible(false);
        hologram.setGravity(false);
        hologram.setInvulnerable(true);
        hologram.setSmall(true);
        hologram.setMarker(true);
        hologram.setCustomNameVisible(true);
        hologram.setCustomName(formatTime(secondsLeft));

        ProtectedItem protectedItem = protectedItems.get(item.getUniqueId());
        if (protectedItem != null) {
            protectedItem.setHologram(hologram);
        }
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int secs = seconds % 60;
        return ChatColor.RED + "⏰ " + String.format("%02d:%02d", minutes, secs);
    }

    private void updateHologram(Item item, int secondsLeft) {
        ProtectedItem protectedItem = protectedItems.get(item.getUniqueId());
        if (protectedItem != null && protectedItem.getHologram() != null && !protectedItem.getHologram().isDead()) {
            protectedItem.getHologram().setCustomName(formatTime(secondsLeft));
            Location itemLoc = item.getLocation();
            protectedItem.getHologram().teleport(itemLoc.add(0, 0.5, 0));
        }
    }

    private void removeHologram(ProtectedItem protectedItem) {
        if (protectedItem != null && protectedItem.getHologram() != null && !protectedItem.getHologram().isDead()) {
            protectedItem.getHologram().remove();
        }
    }

    private void applyGlowEffect(Item item) {
        if (!config.getBoolean("settings.effects.enable-glow", true)) {
            return;
        }

        ItemStack itemStack = item.getItemStack();
        if (itemStack.getType() == Material.AIR) {
            return;
        }

        if (itemStack.getType().name().contains("LEATHER_")) {
            LeatherArmorMeta meta = (LeatherArmorMeta) itemStack.getItemMeta();
            if (meta != null) {
                int red = config.getInt("settings.glow-color.red", 255);
                int green = config.getInt("settings.glow-color.green", 0);
                int blue = config.getInt("settings.glow-color.blue", 0);

                meta.setColor(Color.fromRGB(red, green, blue));
                itemStack.setItemMeta(meta);
            }
        }

        item.setItemStack(itemStack);
        item.setGlowing(true);
    }

    private void startProtectionTimer(UUID itemId) {
        new BukkitRunnable() {
            @Override
            public void run() {
                ProtectedItem protectedItem = protectedItems.get(itemId);

                if (protectedItem == null || protectedItem.getItem().isDead()) {
                    if (protectedItem != null) {
                        removeHologram(protectedItem);
                        protectedItems.remove(itemId);
                    }
                    this.cancel();
                    return;
                }

                protectedItem.decreaseTime();
                int secondsLeft = protectedItem.getTimeLeft();

                // Обновляем голограмму
                updateHologram(protectedItem.getItem(), secondsLeft);

                if (secondsLeft <= 0) {
                    removeProtection(itemId);
                    this.cancel();

                    // Уведомление о конце защиты
                    Player killer = Bukkit.getPlayer(protectedItem.getKillerId());
                    if (killer != null && killer.isOnline()) {
                        sendActionBar(killer, "§aЗащита лута закончилась!");
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Запуск через 1 секунду, затем каждую секунду
    }

    // Основная проверка подбора
    public boolean canPickup(Player player, Item item) {
        UUID itemId = item.getUniqueId();

        if (!protectedItems.containsKey(itemId)) {
            return true;
        }

        ProtectedItem protectedItem = protectedItems.get(itemId);

        // Глобальная блокировка подбора
        if (blockedPickupPlayers.contains(player.getUniqueId())) {
            sendActionBar(player, "§cПодбор заблокирован во время таймера!");
            return false;
        }

        if (player.hasPermission("antipodsos.bypass") ||
                player.getUniqueId().equals(protectedItem.getKillerId())) {
            return true;
        }

        if (protectedItem.getTimeLeft() <= 0) {
            removeProtection(itemId);
            return true;
        }

        sendActionBar(player, "§cЭтот лут защищен!");
        return false;
    }

    public void removeProtection(UUID itemId) {
        ProtectedItem protectedItem = protectedItems.remove(itemId);

        if (protectedItem != null && !protectedItem.getItem().isDead()) {
            Item item = protectedItem.getItem();
            removeHologram(protectedItem);
            item.setGlowing(false);

            if (item.getItemStack().getType().name().contains("LEATHER_")) {
                ItemStack original = item.getItemStack().clone();
                LeatherArmorMeta meta = (LeatherArmorMeta) original.getItemMeta();
                if (meta != null) {
                    meta.setColor(Color.fromRGB(0xA0, 0x65, 0x40));
                    original.setItemMeta(meta);
                    item.setItemStack(original);
                }
            }
        }
    }

    public void updateParticles() {
        if (!config.getBoolean("settings.effects.enable-particles", true)) {
            return;
        }

        String particleType = config.getString("settings.effects.particle-type", "FLAME");
        int particleCount = config.getInt("settings.effects.particle-count", 3);

        try {
            Particle particle = Particle.valueOf(particleType);

            for (ProtectedItem protectedItem : protectedItems.values()) {
                Item item = protectedItem.getItem();
                if (!item.isDead() && protectedItem.getTimeLeft() > 0) {
                    Location loc = item.getLocation().add(0, 0.3, 0);
                    item.getWorld().spawnParticle(particle, loc, particleCount, 0.1, 0.1, 0.1, 0.01);
                }
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Неверный тип частицы: " + particleType);
        }
    }

    // Метод для проверки состояния таймеров
    public void debugTimers() {
        plugin.getLogger().info("=== DEBUG: ACTIVE TIMERS ===");
        plugin.getLogger().info("Total protected items: " + protectedItems.size());

        for (Map.Entry<UUID, ProtectedItem> entry : protectedItems.entrySet()) {
            ProtectedItem item = entry.getValue();
            plugin.getLogger().info("Item: " + entry.getKey() +
                    ", Time left: " + item.getTimeLeft() +
                    ", Killer: " + item.getKillerId());
        }

        plugin.getLogger().info("Blocked players: " + blockedPickupPlayers.size());
    }

    public void cleanup() {
        for (UUID itemId : new ArrayList<>(protectedItems.keySet())) {
            removeProtection(itemId);
        }
        protectedItems.clear();
        blockedPickupPlayers.clear();
    }

    public void reloadConfig() {
        this.config = plugin.getConfig();
    }

    // Внутренний класс ProtectedItem
    private static class ProtectedItem {
        private final Item item;
        private final UUID killerId;
        private int timeLeft;
        private ArmorStand hologram;

        public ProtectedItem(Item item, UUID killerId, int initialTime) {
            this.item = item;
            this.killerId = killerId;
            this.timeLeft = initialTime;
        }

        public Item getItem() { return item; }
        public UUID getKillerId() { return killerId; }
        public int getTimeLeft() { return timeLeft; }
        public void decreaseTime() { this.timeLeft--; }
        public ArmorStand getHologram() { return hologram; }
        public void setHologram(ArmorStand hologram) { this.hologram = hologram; }
    }
}