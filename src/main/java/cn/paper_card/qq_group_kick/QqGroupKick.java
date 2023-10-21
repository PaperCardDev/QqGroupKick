package cn.paper_card.qq_group_kick;

import cn.paper_card.group_root_command.GroupRootCommandApi;
import cn.paper_card.mirai.PaperCardMiraiApi;
import cn.paper_card.player_qq_bind.QqBindApi;
import cn.paper_card.qq_group_access.QqGroupAccessApi;
import cn.paper_card.sponsorship.SponsorshipApi;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class QqGroupKick extends JavaPlugin implements QqGroupKickApi {

    private QqGroupAccessApi qqGroupAccessApi = null;
    private QqBindApi qqBindApi = null;

    private SponsorshipApi sponsorshipApi = null;

    private PaperCardMiraiApi paperCardMiraiApi = null;

    private @Nullable QqBindApi getQqBindApi0() {
        final Plugin plugin = getServer().getPluginManager().getPlugin("PlayerQqBind");
        if (plugin instanceof final QqBindApi api) {
            return api;
        }
        return null;
    }

    private @Nullable PaperCardMiraiApi getPaperCardMiraiApi0() {
        final Plugin plugin = getServer().getPluginManager().getPlugin("PaperCardMirai");
        if (plugin instanceof final PaperCardMiraiApi api) {
            return api;
        }
        return null;
    }


    private @Nullable QqGroupAccessApi getQqGroupAccessApi0() {
        final Plugin plugin = getServer().getPluginManager().getPlugin("QqGroupAccess");
        if (plugin instanceof final QqGroupAccessApi api) {
            return api;
        }
        return null;
    }

    private @Nullable SponsorshipApi getSponsorshipApi0() {
        final Plugin plugin = getServer().getPluginManager().getPlugin("Sponsorship");
        if (plugin instanceof final SponsorshipApi api) {
            return api;
        }
        return null;
    }

    @Override
    public void onEnable() {
        final Plugin plugin = getServer().getPluginManager().getPlugin("GroupRootCommand");
        if (plugin instanceof final GroupRootCommandApi api) {
            api.addCommandForAdminMainGroup(new MainCommand(this));
            getLogger().info("已添加踢出名单命令");
        } else throw new NoSuchElementException("GroupRootCommand插件未安装！");

        this.qqBindApi = this.getQqBindApi0();
        this.paperCardMiraiApi = this.getPaperCardMiraiApi0();
        this.qqGroupAccessApi = this.getQqGroupAccessApi0();
        this.sponsorshipApi = this.getSponsorshipApi0();
    }

    @Nullable QqGroupAccessApi getQqGroupAccessApi() {
        return this.qqGroupAccessApi;
    }

    @Override
    public void onDisable() {
    }

    @Override
    public @NotNull List<KickInfo> generateNotBind(int max) throws Exception {
        if (this.qqGroupAccessApi == null) throw new Exception("QqGroupAccess插件未安装！");
        if (this.qqBindApi == null) throw new Exception("PlayerQqBind插件未安装！");

        final QqGroupAccessApi.GroupAccess mainGroupAccess = this.qqGroupAccessApi.createMainGroupAccess();

        final List<QqGroupAccessApi.GroupMember> allMembers = mainGroupAccess.getAllMembers();

        // 获取所有机器人号
        final HashSet<Long> botQqs;
        if (this.paperCardMiraiApi != null) {
            final List<Long> qqs = this.paperCardMiraiApi.getAccountStorage().queryAllQqs();
            botQqs = new HashSet<>(qqs);
        } else {
            botQqs = null;
        }

        final ArrayList<KickInfo> kickList = new ArrayList<>();

        final long current = System.currentTimeMillis();

        // 获取所有未绑定的
        for (QqGroupAccessApi.GroupMember member : allMembers) {
            final long qq = member.getQq();

            final QqBindApi.BindInfo bindInfo = this.qqBindApi.queryByQq(qq);
            if (bindInfo != null) continue; // 已经绑定

            // 忽略管理员
            if (member.getPermissionLevel() > 0) continue;

            // 忽略有群头衔的
            final String specialTitle = member.getSpecialTitle();
            if (specialTitle != null && !specialTitle.isEmpty()) continue;

            final long joinTime = member.getJoinTime() * 1000L;
            long dayNo = (current - joinTime) / (24 * 60 * 60 * 1000L);
            dayNo += 1;

            // 忽略没有到1周的
            if (dayNo <= 7) continue;

            // 忽略机器人号
            if (botQqs != null && botQqs.contains(qq)) continue;

            kickList.add(new KickInfo(
                    member,
                    "未绑定",
                    null,
                    "入群超过7天（%d天）没有绑定正版号，活跃等级：%d".formatted(dayNo, member.getActiveLevel())
            ));
        }

        // 排序
        kickList.sort(Comparator.comparingInt(o -> o.groupMember().getActiveLevel()));

        if (max <= 0) return kickList;

        // 人数限制
        final LinkedList<KickInfo> list2 = new LinkedList<>();
        int c = 0;
        for (final KickInfo kickInfo : kickList) {
            list2.add(kickInfo);
            ++c;
            if (c >= max) break;
        }

        return list2;
    }

    @Override
    public @NotNull List<KickInfo> generateOneDayPlayer(int max) throws Exception {
        if (this.qqGroupAccessApi == null) throw new Exception("QqGroupAccess插件未安装！");
        if (this.qqBindApi == null) throw new Exception("PlayerQqBind插件未安装！");

        final QqGroupAccessApi.GroupAccess mainGroupAccess = this.qqGroupAccessApi.createMainGroupAccess();

        final ArrayList<KickInfo> list = new ArrayList<>();

        // 获取所有机器人号
        final HashSet<Long> botQqs;
        if (this.paperCardMiraiApi != null) {
            final List<Long> qqs = this.paperCardMiraiApi.getAccountStorage().queryAllQqs();
            botQqs = new HashSet<>(qqs);
        } else {
            botQqs = null;
        }


        final long cur = System.currentTimeMillis();

        for (final QqGroupAccessApi.GroupMember member : mainGroupAccess.getAllMembers()) {

            final long qq = member.getQq();

            // 忽略管理
            if (member.getPermissionLevel() > 0) continue;

            // 忽略群头衔
            final String specialTitle = member.getSpecialTitle();
            if (specialTitle != null && !specialTitle.isEmpty()) continue;

            // 忽略未绑定
            final QqBindApi.BindInfo bindInfo = this.qqBindApi.queryByQq(member.getQq());
            if (bindInfo == null) continue;

            final UUID uuid = bindInfo.uuid();

            // 忽略有赞助记录的玩家
            if (this.sponsorshipApi != null) {
                final int count = this.sponsorshipApi.queryCount(uuid);
                if (count > 0) continue;
            }

            // 忽略机器人号
            if (botQqs != null && botQqs.contains(qq)) continue;

            final OfflinePlayer offlinePlayer = getServer().getOfflinePlayer(uuid);

            final long firstPlayed = offlinePlayer.getFirstPlayed();
            final long lastSeen = offlinePlayer.getLastSeen();

            final long oneDay = 24 * 60 * 60 * 1000L;

            // 一日游玩家
            if (lastSeen - firstPlayed < 3 * oneDay && cur - lastSeen > 14 * oneDay) {
                String name = offlinePlayer.getName();
                if (name == null) name = offlinePlayer.getUniqueId().toString();

                final long days = (cur - lastSeen) / oneDay;

                list.add(new KickInfo(
                        member,
                        name,
                        offlinePlayer,
                        "一日游玩家，已%d天未上线，%d天前入群，活跃等级：%d".formatted(
                                days,
                                (cur - member.getJoinTime() * 1000L) / oneDay,
                                member.getActiveLevel()
                        )
                ));
            }

        }

        // 排序
        list.sort((o1, o2) -> {
            final long l1 = o1.player().getLastSeen();
            final long l2 = o2.player().getLastSeen();
            return Long.compare(l1, l2);
        });

        if (max <= 0) return list;

        // 限制人数
        int c = 0;
        final LinkedList<KickInfo> list2 = new LinkedList<>();
        for (KickInfo kickInfo : list) {
            list2.add(kickInfo);
            ++c;
            if (c >= max) break;
        }
        return list2;
    }
}
