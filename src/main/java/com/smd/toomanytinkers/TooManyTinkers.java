package com.smd.toomanytinkers;

import com.smd.toomanytinkers.proxy.IProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = Reference.MOD_ID, name = Reference.MOD_NAME, version = Reference.VERSION,
        clientSideOnly = true,
        dependencies = "required-after:tconstruct;after:conarm;after:jei")
public class TooManyTinkers {

    public static final Logger LOGGER = LogManager.getLogger(Reference.MOD_NAME);

    @SidedProxy(modId = Reference.MOD_ID,
                clientSide = "com.smd.toomanytinkers.proxy.ClientProxy",
                serverSide = "com.smd.toomanytinkers.proxy.CommonProxy")
    public static IProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit();
        LOGGER.info("{}：原生材质与模型的按需渲染缓存已启用", Reference.MOD_NAME);
    }

}
