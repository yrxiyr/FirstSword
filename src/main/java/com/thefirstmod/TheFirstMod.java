package com.thefirstmod;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(TheFirstMod.MODID)
public class TheFirstMod
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "thefirstmod";
    // Directly reference a slf4j logger
    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Items which will all be registered under the "thefirstmod" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "thefirstmod" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    // Create a Deferred Register to hold entity types
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    // 初锋（first_sword）：以钻石剑为基底，右键召唤彩虹闪电
    public static final DeferredItem<FirstSwordItem> FIRST_SWORD = ITEMS.registerItem(
            "first_sword",
            props -> new FirstSwordItem(Tiers.DIAMOND, props
                    // 面板伤害 = 玩家基础 1 + 这里的 10 + 钻石材质加成 3 = 14
                    .attributes(SwordItem.createAttributes(Tiers.DIAMOND, 10.0F, -2.2F))));

    // 纯视觉的彩虹闪电实体
    public static final DeferredHolder<EntityType<?>, EntityType<RainbowLightning>> RAINBOW_LIGHTNING =
            ENTITY_TYPES.register("rainbow_lightning", () -> EntityType.Builder
                    .of(RainbowLightning::new, MobCategory.MISC)
                    .noSave()
                    .sized(0.0F, 0.0F)
                    .clientTrackingRange(16)
                    .updateInterval(Integer.MAX_VALUE)
                    .build("rainbow_lightning"));

    // 流星：长按右键蓄力后落下的攻击
    public static final DeferredHolder<EntityType<?>, EntityType<RainbowMeteor>> RAINBOW_METEOR =
            ENTITY_TYPES.register("rainbow_meteor", () -> EntityType.Builder
                    .of(RainbowMeteor::new, MobCategory.MISC)
                    .noSave()
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .build("rainbow_meteor"));

    // Creates a creative tab with the id "thefirstmod:first_tab", placed before the combat tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FIRST_TAB = CREATIVE_MODE_TABS.register("first_tab", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.thefirstmod")) //The language key for the title of your CreativeModeTab
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> FIRST_SWORD.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(FIRST_SWORD.get()); // Add the sword to the tab. For your own tabs, this method is preferred over the event
            }).build());

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public TheFirstMod(IEventBus modEventBus, ModContainer modContainer)
    {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so entity types get registered
        ENTITY_TYPES.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (TheFirstMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");

        if (Config.logDirtBlock)
            LOGGER.info("DIRT BLOCK >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));

        LOGGER.info(Config.magicNumberIntroduction + Config.magicNumber);

        Config.items.forEach((item) -> LOGGER.info("ITEM >> {}", item.toString()));
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }
}
