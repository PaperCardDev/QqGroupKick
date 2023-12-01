package cn.paper_card.qq_group_kick;

import cn.paper_card.qq_group_access.api.GroupAccess;
import cn.paper_card.qq_group_access.api.QqGroupAccessApi;
import cn.paper_card.qq_group_command.QqGroupCommand;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

class MainCommand extends QqGroupCommand.HasSub {

    private final QqGroupKick plugin;

    private List<QqGroupKickApi.KickInfo> kickList = null;


    public MainCommand(QqGroupKick plugin) {
        super("踢出名单");
        this.plugin = plugin;

        this.addSubCommand(new Generate());
        this.addSubCommand(new DoKick());
        this.addSubCommand(new View());
    }

    class Generate extends QqGroupCommand.HasSub {

        public Generate() {
            super("生成");
            this.addSubCommand(new NotBind());
            this.addSubCommand(new OneDayPlayer());
            this.addSubCommand(new LowLevelQq());
        }

        class NotBind extends QqGroupCommand {

            public NotBind() {
                super("未绑定");
            }

            @Override
            public @Nullable String[] execute(@NotNull String[] args,
                                              long sender, @NotNull String senderName,
                                              long groupId, @NotNull String groupName) {

                final String argNum = args.length > 0 ? args[0] : null;

                final int max;
                if (argNum == null) {
                    max = -1;
                } else {
                    try {
                        max = Integer.parseInt(argNum);
                    } catch (NumberFormatException e) {
                        return new String[]{"%s 不是正确的人数！".formatted(argNum)};
                    }
                }

                final List<QqGroupKickApi.KickInfo> list;

                try {
                    list = plugin.generateNotBind(max);
                } catch (Exception e) {
                    return new String[]{e.toString()};
                }

                kickList = list;

                return new String[]{"生成未绑定正版号玩家名单成功，人数：%d".formatted(list.size())};
            }
        }

        class OneDayPlayer extends QqGroupCommand {


            public OneDayPlayer() {
                super("一日游");
            }

            @Override
            public @Nullable String[] execute(@NotNull String[] args,
                                              long sender, @NotNull String senderName,
                                              long groupId, @NotNull String groupName) {

                final String argNum = args.length > 0 ? args[0] : null;

                final int max;
                if (argNum == null) {
                    max = -1;
                } else {
                    try {
                        max = Integer.parseInt(argNum);
                    } catch (NumberFormatException e) {
                        return new String[]{"%s 不是正确的人数！".formatted(argNum)};
                    }
                }

                final List<QqGroupKickApi.KickInfo> list;

                try {
                    list = plugin.generateOneDayPlayer(max);
                } catch (Exception e) {
                    return new String[]{e.toString()};
                }

                kickList = list;

                return new String[]{"生成一日游玩家名单成功，人数：%d".formatted(list.size())};
            }
        }

        class LowLevelQq extends QqGroupCommand {

            public LowLevelQq() {
                super("低等级QQ");
            }

            @Override
            public @Nullable String[] execute(@NotNull String[] args,
                                              long sender, @NotNull String senderName,
                                              long groupId, @NotNull String groupName) {

                final String levelStr = args.length > 0 ? args[0] : null;

                if (levelStr == null) {
                    return new String[]{"必须提供参数：QQ等级"};
                }

                final int level;

                try {
                    level = Integer.parseInt(levelStr);
                } catch (NumberFormatException e) {
                    return new String[]{"%s 不是正确的QQ等级".formatted(levelStr)};
                }

                final List<QqGroupKickApi.KickInfo> list;

                try {
                    list = plugin.generateLowLevelMembers(level);
                } catch (Exception e) {
                    plugin.handleException(e);
                    return new String[]{e.toString()};
                }

                kickList = list;

                return new String[]{"生成QQ等级低于%d的成员名单成功，人数：%d".formatted(level, list.size())};
            }
        }
    }

    class DoKick extends QqGroupCommand {

        public DoKick() {
            super("踢出");
        }

        @Override
        public @Nullable String[] execute(@NotNull String[] args,
                                          long sender, @NotNull String senderName,
                                          long groupId, @NotNull String groupName) {

            if (kickList == null) {
                return new String[]{"没有生成名单，请先生成名单"};
            }

            final List<QqGroupKickApi.KickInfo> list = kickList;
            kickList = null;

            final QqGroupAccessApi qqGroupAccessApi = plugin.getQqGroupAccessApi();
            if (qqGroupAccessApi == null) return new String[]{"QqGroupAccess插件未安装！"};

            final GroupAccess mainGroupAccess;

            try {
                mainGroupAccess = qqGroupAccessApi.createMainGroupAccess();
            } catch (Exception e) {
                plugin.handleException(e);
                return new String[]{e.toString()};
            }


            final AtomicInteger kicked = new AtomicInteger(0);
            final AtomicInteger notKicked = new AtomicInteger(0);

            final Consumer<ScheduledTask> task = new Consumer<>() {
                @Override
                public void accept(ScheduledTask task) {
                    if (list.isEmpty()) {
                        try {
                            mainGroupAccess.sendAtMessage(sender, "踢出任务已完成，成功踢出%d人，未能踢出%d人".formatted(
                                    kicked.get(), notKicked.get()
                            ));
                        } catch (Exception e) {
                            plugin.handleException(e);
                        }
                        return;
                    }

                    final QqGroupKickApi.KickInfo remove = list.remove(0);

                    // 发送理由
                    final String reason = remove.reason();
                    if (reason != null && !reason.isEmpty()) {
                        try {
                            mainGroupAccess.sendAtMessage(remove.groupMember().getQq(), "踢出理由：" + remove.reason());
                        } catch (Exception e) {
                            plugin.handleException(e);
                        }
                    }

                    try { // 踢出
                        remove.groupMember().kick(reason);
                        kicked.incrementAndGet();
                    } catch (Exception e) {
                        plugin.handleException(e);
                        try {
                            mainGroupAccess.sendNormalMessage(e.getCause().toString());
                        } catch (Exception ex) {
                            plugin.handleException(e);
                        }
                        notKicked.incrementAndGet();
                    }

                    plugin.getServer().getAsyncScheduler().runDelayed(plugin, this, 5, TimeUnit.SECONDS);
                }
            };

            plugin.getServer().getAsyncScheduler().runDelayed(plugin, task, 5, TimeUnit.SECONDS);


            return new String[]{"已启动踢出任务，请等待任务完成"};
        }
    }

    class View extends QqGroupCommand {

        public View() {
            super("查看");
        }

        @Override
        public @Nullable String[] execute(@NotNull String[] args,
                                          long sender, @NotNull String senderName,
                                          long groupId, @NotNull String groupName) {

            if (kickList == null) {
                return new String[]{"还没有生成名单，请先生成名单"};
            }

            StringBuilder builder = new StringBuilder();
            builder.append("\n序号 | QQ | 游戏名 | 理由\n");

            final LinkedList<String> reply = new LinkedList<>();

            int i = 1;

            for (final QqGroupKickApi.KickInfo kickInfo : kickList) {
                builder.append("\n%d | %s(%d) | %s | %s\n".formatted(
                        i,
                        kickInfo.groupMember().getNick(), kickInfo.groupMember().getQq(),
                        kickInfo.playerName(), kickInfo.reason()
                ));

                if (i % 16 == 0) {
                    reply.add(builder.toString());
                    builder = new StringBuilder();
                }
                ++i;
            }

            final String string = builder.toString();
            if (!string.isEmpty()) reply.add(string);

            return reply.toArray(new String[0]);
        }
    }

}
