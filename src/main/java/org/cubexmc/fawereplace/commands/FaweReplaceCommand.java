package org.cubexmc.fawereplace.commands;

import org.cubexmc.fawereplace.FAWEReplace;
import org.cubexmc.fawereplace.LanguageManager;
import org.cubexmc.fawereplace.tasks.CleaningTask;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Locale;

/**
 * 处理 /fawereplace 命令的执行逻辑
 */
public class FaweReplaceCommand implements CommandExecutor {

    private final FAWEReplace plugin;
    private final CleaningTask cleaningTask;
    private final LanguageManager lang;

    public FaweReplaceCommand(FAWEReplace plugin, CleaningTask cleaningTask) {
        this.plugin = plugin;
        this.cleaningTask = cleaningTask;
        this.lang = plugin.getLanguageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 权限检查
        if (!sender.hasPermission("fawereplace.use")) {
            sender.sendMessage(lang.getMessage("no_permission"));
            return true;
        }

        // 参数检查
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        // 处理子命令
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start":
                handleStart(sender, args);
                return true;
            case "stop":
                handleStop(sender);
                return true;
            case "status":
                handleStatus(sender);
                return true;
            case "reload":
                handleReload(sender);
                return true;
            case "help":
                sendHelp(sender, label);
                return true;
            default:
                sendUsage(sender, label);
                return true;
        }
    }

    /**
     * 处理 start 子命令
     */
    private void handleStart(CommandSender sender, String[] args) {
        // 检查世界是否已正确配置
        if (!plugin.isWorldConfigured()) {
            sender.sendMessage(lang.getMessage("start.world_not_configured", "world", plugin.getConfiguredWorldName()));
            sender.sendMessage(lang.getMessage("start.check_config"));
            return;
        }

        if (cleaningTask.isRunning()) {
            sender.sendMessage(lang.getMessage("start.already_running"));
            return;
        }

        // 检查是否有 --fresh 参数，强制重新开始
        boolean forceRestart = false;
        if (args.length > 1 && args[1].equalsIgnoreCase("--fresh")) {
            forceRestart = true;
        }

        cleaningTask.start(sender, forceRestart);
    }

    /**
     * 处理 stop 子命令
     */
    private void handleStop(CommandSender sender) {
        if (!cleaningTask.isRunning()) {
            sender.sendMessage(lang.getMessage("stop.not_running"));
            return;
        }

        cleaningTask.stop(sender);
        sender.sendMessage(lang.getMessage("stop.stopping"));
    }

    /**
     * 处理 status 子命令
     */
    private void handleStatus(CommandSender sender) {
        // 显示世界配置状态
        if (!plugin.isWorldConfigured()) {
            sender.sendMessage(lang.getMessage("status.world_status", "world", plugin.getConfiguredWorldName()));
            sender.sendMessage(lang.getMessage("status.reload_suggestion"));
            sender.sendMessage("");
        }
        
        cleaningTask.sendStatus(sender);
    }

    /**
     * 处理 reload 子命令
     */
    private void handleReload(CommandSender sender) {
        if (cleaningTask.isRunning()) {
            sender.sendMessage(lang.getMessage("reload.cannot_while_running"));
            return;
        }

        sender.sendMessage(lang.getMessage("reload.reloading"));
        
        // 调用主插件的配置重载方法
        if (plugin.reloadConfiguration()) {
            sender.sendMessage(lang.getMessage("reload.success"));
            sender.sendMessage(lang.getMessage("reload.world_found", "world", plugin.getConfiguredWorldName()));
            sender.sendMessage(lang.getMessage("reload.language_changed", "language", lang.getCurrentLanguage()));
            sender.sendMessage(lang.getMessage("reload.apply_next_start"));
        } else {
            sender.sendMessage(lang.getMessage("reload.failed"));
            if (!plugin.isWorldConfigured()) {
                sender.sendMessage(lang.getMessage("reload.world_not_found", "world", plugin.getConfiguredWorldName()));
                sender.sendMessage(lang.getMessage("reload.check_world_name"));
            } else {
                sender.sendMessage(lang.getMessage("reload.check_console"));
            }
        }
    }

    /**
     * 发送命令用法
     */
    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(lang.getMessage("usage", "label", label));
    }

    /**
     * 发送帮助信息
     */
    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(lang.getMessage("help.header"));
        sender.sendMessage(lang.getMessage("help.title"));
        sender.sendMessage("");
        sender.sendMessage(lang.getMessage("help.start", "label", label));
        sender.sendMessage(lang.getMessage("help.start_desc"));
        sender.sendMessage(lang.getMessage("help.start_fresh", "label", label));
        sender.sendMessage(lang.getMessage("help.start_fresh_desc"));
        sender.sendMessage(lang.getMessage("help.stop", "label", label));
        sender.sendMessage(lang.getMessage("help.stop_desc"));
        sender.sendMessage(lang.getMessage("help.status", "label", label));
        sender.sendMessage(lang.getMessage("help.reload", "label", label));
        sender.sendMessage(lang.getMessage("help.help", "label", label));
        sender.sendMessage("");
        sender.sendMessage(lang.getMessage("help.aliases"));
        sender.sendMessage(lang.getMessage("help.footer"));
    }
}
