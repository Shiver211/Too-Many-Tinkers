package com.smd.toomanytinkers.proxy;

import com.smd.toomanytinkers.client.RenderLifecycle;
import com.smd.toomanytinkers.client.diagnostics.TmtCommand;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;

public class ClientProxy implements IProxy {
    @Override
    public void preInit() {
        MinecraftForge.EVENT_BUS.register(new RenderLifecycle());
        ClientCommandHandler.instance.registerCommand(new TmtCommand());
    }
}
