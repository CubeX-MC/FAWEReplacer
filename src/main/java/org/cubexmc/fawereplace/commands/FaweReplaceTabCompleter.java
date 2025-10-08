package org.cubexmc.fawereplace.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 处理 /fawereplace 命令的 Tab 补全
 */
public class FaweReplaceTabCompleter implements TabCompleter {

    private static final List<String> SUB_COMMANDS = Arrays.asList(
            "start", "stop", "status", "reload", "help"
    );
    
    private static final List<String> START_OPTIONS = Arrays.asList(
            "--fresh"
    );

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        // 只有拥有权限的玩家才能看到补全
        if (!sender.hasPermission("fawereplace.use")) {
            return new ArrayList<>();
        }

        // 第一个参数：子命令补全
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return SUB_COMMANDS.stream()
                    .filter(cmd -> cmd.startsWith(input))
                    .collect(Collectors.toList());
        }
        
        // 第二个参数：如果第一个参数是 start，提供选项补全
        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            String input = args[1].toLowerCase();
            return START_OPTIONS.stream()
                    .filter(opt -> opt.startsWith(input))
                    .collect(Collectors.toList());
        }

        // 其他参数不提供补全
        return new ArrayList<>();
    }
}
