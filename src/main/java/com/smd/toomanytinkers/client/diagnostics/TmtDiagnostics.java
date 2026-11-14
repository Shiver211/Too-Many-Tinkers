package com.smd.toomanytinkers.client.diagnostics;

import com.google.gson.GsonBuilder;
import com.smd.toomanytinkers.Reference;
import com.smd.toomanytinkers.client.armor.TmtArmorTextures;
import com.smd.toomanytinkers.client.model.NativeToolModelCaches;
import com.smd.toomanytinkers.client.model.TmtLazyMaterialModel;
import com.smd.toomanytinkers.client.model.TmtMaskModelCache;
import com.smd.toomanytinkers.client.model.TableItemModels;
import com.smd.toomanytinkers.client.render.TableItemRenderer;
import com.smd.toomanytinkers.client.render.NativeMaterialTextures;
import com.smd.toomanytinkers.client.render.TmtNativeQuadRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.registry.IRegistry;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.materials.Material;
import slimeknights.tconstruct.library.tools.IToolPart;
import slimeknights.tconstruct.library.tools.ToolCore;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 按命令采集；正常绘制不遍历材质、模型或缓存。 */
public final class TmtDiagnostics {
    private static final Map<String, Integer> MODEL_CLASSES = new TreeMap<>();
    private static FrameSamples frames;
    private static boolean profiling;
    private static long previousFrame;

    private TmtDiagnostics() { }

    public static void captureModels(IRegistry<ModelResourceLocation, IBakedModel> registry) {
        MODEL_CLASSES.clear();
        for (ModelResourceLocation location : registry.getKeys()) {
            IBakedModel model = registry.getObject(location);
            if (model != null) MODEL_CLASSES.merge(model.getClass().getName(), 1, Integer::sum);
        }
    }

    public static void startProfile() {
        frames = new FrameSamples(18000);
        profiling = true;
        previousFrame = 0;
    }

    public static void stopProfile() { profiling = false; }

    public static void frame() {
        if (!profiling) return;
        long now = System.nanoTime();
        if (previousFrame != 0) frames.add(now - previousFrame);
        previousFrame = now;
    }

    public static String summary() {
        return NativeMaterialTextures.stats() + " | " + TmtLazyMaterialModel.stats()
                + " | " + TmtNativeQuadRenderer.stats() + " | " + TmtMaskModelCache.stats()
                + " | " + NativeToolModelCaches.stats() + " | " + TmtArmorTextures.stats()
                + " | " + TableItemModels.stats() + " | " + TableItemRenderer.stats();
    }

    public static Path dump() throws IOException {
        Minecraft minecraft = Minecraft.getMinecraft();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("time", LocalDateTime.now().toString());
        report.put("tmtVersion", Reference.VERSION);
        report.put("javaVersion", System.getProperty("java.version"));
        report.put("processId", ProcessHandle.current().pid());
        report.put("heapUsedBytesAtSnapshot", ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());
        report.put("heapMaxBytes", Runtime.getRuntime().maxMemory());
        report.put("heapNote", "当前堆使用量，未强制 GC，不等于 GC 后存活堆。GPU 字节是自有纹理分配估算，不是驱动显存。模型估算不是总可达对象内存。");
        report.put("textures", NativeMaterialTextures.stats());
        report.put("models", TmtLazyMaterialModel.stats());
        report.put("quads", TmtNativeQuadRenderer.stats());
        report.put("masks", TmtMaskModelCache.stats());
        report.put("tableItems", TableItemModels.stats() + ", " + TableItemRenderer.stats());
        report.put("nativeToolCaches", NativeToolModelCaches.stats());
        report.put("armorTextures", TmtArmorTextures.stats());
        report.put("frameIntervalNote", "相邻 RenderTick START 间隔；包含 VSync、帧率限制和等待。超过 18000 帧只保留最近样本。");
        if (frames != null) report.put("frameIntervals", frames.summary());
        report.put("profiling", profiling);
        report.put("resourcePacks", new ArrayList<>(minecraft.gameSettings.resourcePacks));
        report.put("mipmapLevels", minecraft.gameSettings.mipmapLevels);
        report.put("displayWidth", minecraft.displayWidth);
        report.put("displayHeight", minecraft.displayHeight);
        report.put("registeredBakedModelClasses", MODEL_CLASSES);
        List<Object> materials = new ArrayList<>();
        for (Material material : TinkerRegistry.getAllMaterials()) {
            ModContainer owner = TinkerRegistry.getTrace(material);
            materials.add(Map.of("id", material.identifier, "class", material.getClass().getName(),
                    "renderInfo", material.renderInfo == null ? "null" : material.renderInfo.getClass().getName(),
                    "requiresNativeAtlas", NativeMaterialTextures.requiresNativeAtlas(material),
                    "registeredBy", owner == null ? "unknown" : owner.getModId()));
        }
        report.put("materials", materials);
        List<Object> items = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (item instanceof ToolCore || item instanceof IToolPart) {
                items.add(Map.of("id", String.valueOf(item.getRegistryName()), "class", item.getClass().getName()));
            }
        }
        report.put("toolAndPartItems", items);
        report.put("inventoryNote", "注册表快照，不表示全部材质组合已烘焙或画面已通过；包装模型内部类型和动态回调需结合实际样本核对。");
        Map<String, String> mods = new TreeMap<>();
        for (ModContainer mod : Loader.instance().getActiveModList()) mods.put(mod.getModId(), mod.getVersion());
        report.put("mods", mods);
        if (minecraft.player != null) {
            ItemStack stack = minecraft.player.getHeldItemMainhand();
            report.put("heldItem", stack.isEmpty() ? "empty" : Map.of(
                    "id", String.valueOf(stack.getItem().getRegistryName()),
                    "metadata", stack.getMetadata(),
                    "nbt", stack.hasTagCompound() ? stack.getTagCompound().toString() : "{}"));
        }
        Path folder = minecraft.gameDir.toPath().resolve("tmt-diagnostics");
        Files.createDirectories(folder);
        Path output = folder.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + ".json");
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8);
        return output;
    }
}
