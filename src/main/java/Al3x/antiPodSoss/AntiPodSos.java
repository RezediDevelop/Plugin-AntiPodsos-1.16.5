package Al3x.antiPodSoss;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import Al3x.antiPodSoss.listeners.PlayerDeathListener;
import Al3x.antiPodSoss.listeners.PlayerPickupListener;

public class AntiPodSos extends JavaPlugin {

    private static AntiPodSos instance;
    private LootManager lootManager;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        instance = this;

        // Сохраняем конфиг по умолчанию
        saveDefaultConfig();
        config = getConfig();

        // Инициализация менеджера лута
        lootManager = new LootManager(this);

        // Регистрация ивентов
        Bukkit.getPluginManager().registerEvents(new PlayerDeathListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerPickupListener(this), this);

        // Запуск таска для обновления частиц
        startParticleTask();

        getLogger().info("AntiPodSos успешно запущен!");
    }

    @Override
    public void onDisable() {
        // Очистка при выключении
        if (lootManager != null) {
            lootManager.cleanup();
        }
        getLogger().info("AntiPodSos выключен!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("antipodsos")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                // Команда перезагрузки конфига
                if (!sender.hasPermission("antipodsos.reload")) {
                    sendMessage(sender, "no-permission");
                    return true;
                }

                reloadConfig();
                config = getConfig();
                if (lootManager != null) {
                    lootManager.reloadConfig();
                }

                sendMessage(sender, "config-reloaded");
                return true;
            }

            // Показать помощь
            sender.sendMessage(ChatColor.YELLOW + "AntiPodSos v" + getDescription().getVersion());
            sender.sendMessage(ChatColor.YELLOW + "Используйте: /antipodsos reload");
            return true;
        }
        return false;
    }

    /**
     * Запускает задачу для обновления частиц
     */
    private void startParticleTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (lootManager != null) {
                    lootManager.updateParticles();
                }
            }
        }.runTaskTimer(this, 0L, 5L); // Обновление каждые 5 тиков (0.25 секунды)
    }

    /**
     * Отправляет сообщение из конфига
     * @param sender Получатель сообщения
     * @param messageKey Ключ сообщения в конфиге
     */
    public void sendMessage(CommandSender sender, String messageKey) {
        String message = config.getString("settings.messages." + messageKey, "");
        if (!message.isEmpty()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', message));
        }
    }

    /**
     * Получить экземпляр плагина
     */
    public static AntiPodSos getInstance() {
        return instance;
    }

    /**
     * Получить менеджер лута
     */
    public LootManager getLootManager() {
        return lootManager;
    }

    /**
     * Получить конфигурацию плагина
     */
    public FileConfiguration getPluginConfig() {
        return config;
    }
}