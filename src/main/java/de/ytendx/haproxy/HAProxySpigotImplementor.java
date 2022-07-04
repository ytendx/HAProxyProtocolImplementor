package de.ytendx.haproxy;

import de.ytendx.haproxy.tinyprotocol.TinyProtocol;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public class HAProxySpigotImplementor extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("HAProxySpigotImplementor by ytendx using TinyProtocol is being activated....");

        getLogger().info("----------------------------------------------------------------------------");
        getLogger().info("We recommend protecting your server with NeoProtect (https://neoprotect.net/)");
        getLogger().info("----------------------------------------------------------------------------");

        new TinyProtocol(this);

        this.getCommand("haproxyimplementor").setExecutor((sender, command, label, args) -> {
            sender.sendMessage(ChatColor.GRAY + "The server uses HAProxyImplementor made by "
                    + ChatColor.YELLOW + "ytendx " + ChatColor.GRAY + "for " + ChatColor.AQUA + "NeoProtect.net");
            return true;
        });

        getLogger().info("HAProxySpigotImplementor was successfully activated!");
    }

    @Override
    public void onDisable() {
        getLogger().info("HAProxySpigotImplementor is now deactivated!");
    }
}
