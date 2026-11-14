package com.smd.toomanytinkers.client.diagnostics;

import com.smd.toomanytinkers.TooManyTinkers;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.List;

public final class TmtCommand extends CommandBase {
    @Override public String getName() { return "tmt"; }
    @Override public String getUsage(ICommandSender sender) { return "/tmt stats | dump | profile start | profile stop"; }
    @Override public int getRequiredPermissionLevel() { return 0; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        try {
            if (args.length == 1 && args[0].equals("stats")) {
                String summary = TmtDiagnostics.summary();
                TooManyTinkers.LOGGER.info(summary);
                sender.sendMessage(new TextComponentString(summary));
            } else if (args.length == 1 && args[0].equals("dump")) {
                writeReport(sender);
            } else if (args.length == 2 && args[0].equals("profile") && args[1].equals("start")) {
                TmtDiagnostics.startProfile();
                sender.sendMessage(new TextComponentString("TMT 已开始记录帧间隔；完成场景后执行 /tmt profile stop。"));
            } else if (args.length == 2 && args[0].equals("profile") && args[1].equals("stop")) {
                TmtDiagnostics.stopProfile();
                writeReport(sender);
            } else {
                sender.sendMessage(new TextComponentString(getUsage(sender)));
            }
        } catch (IOException exception) {
            TooManyTinkers.LOGGER.error("无法写入 TMT 诊断报告", exception);
            throw new CommandException("无法写入 TMT 诊断报告，详情见日志。");
        }
    }

    private static void writeReport(ICommandSender sender) throws IOException {
        sender.sendMessage(new TextComponentString("TMT 诊断报告：" + TmtDiagnostics.dump().toAbsolutePath()));
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos target) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "stats", "dump", "profile");
        if (args.length == 2 && args[0].equals("profile")) return getListOfStringsMatchingLastWord(args, "start", "stop");
        return List.of();
    }
}
