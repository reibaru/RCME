package com.reibaru.blockcache;

import com.mojang.logging.LogUtils;
import com.reibaru.blockcache.mixin.accessor.LevelRendererAccessor;
import com.reibaru.blockcache.mixin.accessor.RenderChunkAccessor;
import com.reibaru.blockcache.mixin.accessor.RenderChunkInfoAccessor;
import com.reibaru.blockcache.mixin.accessor.RenderChunkStorageAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;

@Mod(BlockCacheMod.MODID)
public class BlockCacheMod {

    public static final String MODID = "blockcache";
    public static final Logger LOGGER = LogUtils.getLogger();

    // Creative Tab
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister
            .create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<CreativeModeTab> BLOCKCACHE_TAB = CREATIVE_MODE_TABS.register("blockcache_tab",
            () -> CreativeModeTab.builder()
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> Minecraft.getInstance().player.getMainHandItem())
                    .displayItems((parameters, output) -> {
                    })
                    .build());

    // ================================
    // ★ Forge が呼ぶ正しいコンストラクタ
    // ================================
    public BlockCacheMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);
        CREATIVE_MODE_TABS.register(modEventBus);
        modEventBus.addListener(this::addCreative);

        // Forge EVENT_BUS（サーバー/クライアント共通）
        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            // 必要なら追加
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("HELLO from server starting");
    }

    // ================================
    // ★★★ キャッシュ保存イベント（追加） ★★★
    // ================================
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("[BlockCache] Server stopping, saving cache...");
        CacheManager.saveAll();
    }

    // ================================
    // クライアント側イベント
    // ================================
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }

    // ================================
    // ワールド読み込み後のキャッシュ処理
    // ================================
    @SubscribeEvent
    public static void onClientPlayerLoggedIn(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft mc = Minecraft.getInstance();
        LevelRenderer renderer = mc.levelRenderer;

        LOGGER.info("[BlockCache] World loaded! Loading cache...");
        CacheManager.loadCache();

        if (renderer == null) {
            LOGGER.warn("[BlockCache] levelRenderer is null on world load, skipping compiled chunk clear");
            return;
        }

        AtomicReference<?> storageRef = ((LevelRendererAccessor) renderer).blockcache$getRenderChunkStorage();

        if (storageRef == null) {
            LOGGER.warn("[BlockCache] renderChunkStorageRef is null, skipping compiled chunk clear");
            return;
        }

        Object storageObj = storageRef.get();
        if (storageObj == null) {
            LOGGER.warn(
                    "[BlockCache] renderChunkStorage is null inside AtomicReference, skipping compiled chunk clear");
            return;
        }

        var infos = ((RenderChunkStorageAccessor) storageObj).blockcache$getRenderChunks();
        if (infos == null || infos.isEmpty()) {
            LOGGER.warn("[BlockCache] renderChunks is empty, nothing to clear");
            return;
        }

        LOGGER.info("[BlockCache] Clearing compiled chunks from RenderChunkStorage.renderChunks...");

        for (var infoObj : infos) {
            if (infoObj == null)
                continue;

            var info = (RenderChunkInfoAccessor) infoObj;
            var chunk = info.blockcache$getChunk();
            if (chunk == null)
                continue;

            ((RenderChunkAccessor) chunk)
                    .blockcache$getCompiledRef()
                    .set(new ChunkRenderDispatcher.CompiledChunk());
        }
    }
}
