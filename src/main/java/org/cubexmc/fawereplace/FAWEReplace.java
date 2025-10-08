package org.cubexmc.fawereplace;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldedit.world.block.BlockType;
import com.sk89q.worldedit.world.block.BlockTypes;
import org.cubexmc.fawereplace.commands.FaweReplaceCommand;
import org.cubexmc.fawereplace.commands.FaweReplaceTabCompleter;
import org.cubexmc.fawereplace.tasks.CleaningTask;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

/**
 * FAWEReplace 主插件类
 * 基于 FastAsyncWorldEdit 的世界方块批量替换与实体清理插件
 * 
 * @author cong0707, angushushu
 * @version 1.0.3
 */
public final class FAWEReplace extends JavaPlugin {

    private CleaningTask cleaningTask;
    private World world;
    private LanguageManager languageManager;

    @Override
    public void onEnable() {
        // 保存默认配置
        saveDefaultConfig();

        // 初始化语言管理器
        String language = getConfig().getString("language", "zh_CN");
        languageManager = new LanguageManager(this, language);

        // 初始化清理任务
        cleaningTask = new CleaningTask(getLogger(), getDataFolder(), languageManager);

        // 注册命令 (Paper Plugin API) - 无论配置是否成功都注册命令
        registerCommands();

        // 加载配置并配置任务
        if (!loadConfiguration()) {
            getLogger().warning(languageManager.getMessage("plugin.config_invalid"));
            getLogger().warning(languageManager.getMessage("plugin.command_unavailable"));
        } else {
            // 自动启动（仅当配置成功加载时）
            if (getConfig().getBoolean("confirm", false)) {
                Bukkit.getScheduler().runTask(this, () -> cleaningTask.start(null));
            }
        }

        getLogger().info(languageManager.getMessage("plugin.enabled"));
    }

    @Override
    public void onDisable() {
        // 停止清理任务
        if (cleaningTask != null && cleaningTask.isRunning()) {
            cleaningTask.stop(null);
        }
        getLogger().info(languageManager.getMessage("plugin.disabled"));
    }

    /**
     * 注册命令 (使用 Paper Plugin API)
     */
    private void registerCommands() {
        try {
            org.bukkit.command.Command fawereplaceCmd = new org.bukkit.command.Command("fawereplace") {
                private final FaweReplaceCommand executor = new FaweReplaceCommand(FAWEReplace.this, cleaningTask);
                private final FaweReplaceTabCompleter tabCompleter = new FaweReplaceTabCompleter();

                @Override
                public boolean execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
                    return executor.onCommand(sender, this, label, args);
                }

                @Override
                public List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
                    return tabCompleter.onTabComplete(sender, this, alias, args);
                }
            };

            fawereplaceCmd.setDescription("FAWE-based world cleaning and block replacement");
            fawereplaceCmd.setUsage("/<command> <start|stop|status|reload|help>");
            fawereplaceCmd.setPermission("fawereplace.use");
            fawereplaceCmd.setAliases(Arrays.asList("fawerl", "frl"));

            this.getServer().getCommandMap().register("fawereplace", fawereplaceCmd);
            getLogger().info(languageManager.getMessage("plugin.command_registered"));
        } catch (Exception e) {
            getLogger().severe(languageManager.getMessage("plugin.command_register_failed") + " " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取语言管理器
     */
    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    /**
     * 重新加载配置（公开方法，供命令调用）
     */
    public boolean reloadConfiguration() {
        reloadConfig();
        
        // 重新加载语言
        String language = getConfig().getString("language", "zh_CN");
        languageManager.reload(language);
        
        return loadConfiguration();
    }

    /**
     * 检查世界是否已正确配置
     */
    public boolean isWorldConfigured() {
        return world != null;
    }

    /**
     * 获取配置的世界名称
     */
    public String getConfiguredWorldName() {
        return getConfig().getString("world", "world");
    }

    /**
     * 加载配置并初始化清理任务
     */
    private boolean loadConfiguration() {
        try {
            // 读取世界
            String worldName = getConfig().getString("world", "world");
            world = getWorldFromFaweOrBukkit(worldName);
            if (world == null) {
                getLogger().severe(languageManager.getMessage("error.world_not_found", "world", worldName));
                return false;
            }

            // 读取并行度
            int parallel = getConfig().getInt("parallel", 4);

            // 读取区域大小
            int regionX = getConfig().getInt("region.x", 512);
            int regionZ = getConfig().getInt("region.z", 512);
            boolean regionYFullSpan = !getConfig().contains("region.y");
            int regionY = regionYFullSpan ? 0 : getConfig().getInt("region.y", 256);

            // 读取目标范围
            int startX = getConfig().getInt("target.start.x", 0);
            int startZ = getConfig().getInt("target.start.z", 0);
            int endX = getConfig().getInt("target.end.x", 1000);
            int endZ = getConfig().getInt("target.end.z", 1000);

            // Y 范围处理
            int startY, endY;
            if (!getConfig().contains("target.start.y") || !getConfig().contains("target.end.y")) {
                org.bukkit.World bw = BukkitAdapter.adapt(world);
                if (bw != null) {
                    startY = bw.getMinHeight();
                    endY = bw.getMaxHeight() - 1;
                } else {
                    startY = -64;
                    endY = 319;
                }
            } else {
                startY = getConfig().getInt("target.start.y");
                endY = getConfig().getInt("target.end.y");
            }

            // 如果 region.y 未设置，使用完整 Y 范围
            if (regionYFullSpan) {
                regionY = endY - startY + 1;
            }

            // 读取模式配置
            boolean tiling = getConfig().getBoolean("tiling.enabled", true);
            boolean fastMode = getConfig().getBoolean("fast-mode", true);

            // 读取恢复配置
            boolean resumeEnabled = getConfig().getBoolean("resume.enabled", false);
            int resumeSaveEvery = getConfig().getInt("resume.save-every", 25);
            String resumeFileName = getConfig().getString("resume.file", "progress.yml");
            File resumeFile = new File(getDataFolder(), resumeFileName);

            // 读取方块替换规则
            Map<com.sk89q.worldedit.world.block.BlockState, BlockType[]> blockRules = buildBlockRulesFromConfig();

            // 读取实体清理配置
            boolean entityCleanup = getConfig().getBoolean("entities.enabled", false);
            Set<EntityType> entityTypes = buildEntityTypesFromConfig();

            // 读取区块跳过配置
            boolean skipUngeneratedChunks = getConfig().getBoolean("skip-ungenerated-chunks", true);

            // 读取内存保护配置
            boolean memoryProtectionEnabled = getConfig().getBoolean("memory-protection.enabled", true);
            double minFreeMemoryPercent = getConfig().getDouble("memory-protection.min-free-memory-percent", 0.20);
            long waitOnLowMemoryMs = getConfig().getLong("memory-protection.wait-on-low-memory-ms", 5000);
            int maxMemoryRetries = getConfig().getInt("memory-protection.max-memory-retries", 10);

            // 读取性能限制配置
            long delayBetweenBatchesMs = getConfig().getLong("performance.delay-between-batches-ms", 100);
            long delayBetweenChunksMs = getConfig().getLong("performance.delay-between-chunks-ms", 20);
            int gcEveryChunks = getConfig().getInt("performance.gc-every-chunks", 50);

            // 配置清理任务
            cleaningTask.configure(world, startX, startY, startZ, endX, endY, endZ,
                    parallel, tiling, fastMode, blockRules, entityCleanup, entityTypes,
                    resumeEnabled, resumeSaveEvery, resumeFile, skipUngeneratedChunks,
                    memoryProtectionEnabled, minFreeMemoryPercent, waitOnLowMemoryMs, maxMemoryRetries,
                    delayBetweenBatchesMs, delayBetweenChunksMs, gcEveryChunks);

            // 设置区域大小
            cleaningTask.setRegionSize(regionX, regionY, regionZ);

            getLogger().info(String.format("已加载配置: 世界=%s, 区域=%dx%dx%d, 范围=(%d,%d,%d)->(%d,%d,%d), 跳过未生成区块=%s",
                    worldName, regionX, regionY, regionZ, startX, startY, startZ, endX, endY, endZ, 
                    skipUngeneratedChunks ? "开启" : "关闭"));
            getLogger().info(String.format("内存保护: %s | 性能限制: 批次延迟=%dms, 区块延迟=%dms, GC频率=%d区块",
                    memoryProtectionEnabled ? "已启用" : "已禁用", delayBetweenBatchesMs, delayBetweenChunksMs, gcEveryChunks));

            return true;
        } catch (Exception e) {
            getLogger().severe(languageManager.getMessage("error.config_load_failed") + " " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 从配置构建方块替换规则
     */
    private Map<com.sk89q.worldedit.world.block.BlockState, BlockType[]> buildBlockRulesFromConfig() {
        Map<com.sk89q.worldedit.world.block.BlockState, List<BlockType>> grouped = new HashMap<>();

        List<?> list = getConfig().getList("blocks");
        if (list != null) {
            for (Object o : list) {
                String originName = null;
                String targetName = null;

                if (o instanceof org.bukkit.configuration.ConfigurationSection) {
                    org.bukkit.configuration.ConfigurationSection sec = (org.bukkit.configuration.ConfigurationSection) o;
                    originName = sec.getString("origin");
                    targetName = sec.getString("target");
                } else if (o instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = (Map<String, Object>) o;
                    Object ov = map.get("origin");
                    Object tv = map.get("target");
                    originName = ov == null ? null : ov.toString();
                    targetName = tv == null ? null : tv.toString();
                }

                if (originName == null || targetName == null) continue;

                Material om = Material.getMaterial(originName.toUpperCase(Locale.ROOT));
                Material tm = Material.getMaterial(targetName.toUpperCase(Locale.ROOT));
                if (om == null || tm == null) {
                    getLogger().warning("Invalid material: origin=" + originName + ", target=" + targetName);
                    continue;
                }

                BlockType ob = BlockTypes.get(om.name().toLowerCase(Locale.ROOT));
                BlockType tb = BlockTypes.get(tm.name().toLowerCase(Locale.ROOT));
                if (ob == null || tb == null) continue;

                com.sk89q.worldedit.world.block.BlockState targetState = tb.getDefaultState();
                grouped.computeIfAbsent(targetState, k -> new ArrayList<>()).add(ob);
            }
        }

        // 转换为数组格式
        Map<com.sk89q.worldedit.world.block.BlockState, BlockType[]> compiled = new HashMap<>();
        for (Map.Entry<com.sk89q.worldedit.world.block.BlockState, List<BlockType>> entry : grouped.entrySet()) {
            List<BlockType> origins = entry.getValue();
            if (!origins.isEmpty()) {
                compiled.put(entry.getKey(), origins.toArray(new BlockType[0]));
            }
        }

        return compiled;
    }

    /**
     * 从配置构建实体类型集合
     */
    private Set<EntityType> buildEntityTypesFromConfig() {
        Set<EntityType> types = new HashSet<>();
        List<String> ets = getConfig().getStringList("entities.types");
        if (ets != null) {
            for (String s : ets) {
                if (s == null) continue;
                String key = s.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
                try {
                    EntityType et = EntityType.valueOf(key);
                    types.add(et);
                } catch (IllegalArgumentException ex) {
                    getLogger().warning("Unknown entity type: " + s);
                }
            }
        }
        return types;
    }

    /**
     * 获取 WorldEdit 世界对象
     */
    private World getWorldFromFaweOrBukkit(String worldName) {
        try {
            // 尝试使用 FAWE API
            Class<?> faweApi = Class.forName("com.fastasyncworldedit.core.FaweAPI");
            Object weWorld = faweApi.getMethod("getWorld", String.class).invoke(null, worldName);
            if (weWorld instanceof World) {
                return (World) weWorld;
            }
        } catch (Throwable ignored) {
            // FAWE 不可用，使用 Bukkit 适配器
        }

        org.bukkit.World bw = Bukkit.getWorld(worldName);
        return bw == null ? null : BukkitAdapter.adapt(bw);
    }
}
