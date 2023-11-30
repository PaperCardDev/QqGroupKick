package cn.paper_card.qq_group_kick;

import cn.paper_card.qq_group_access.api.GroupMember;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface QqGroupKickApi {
    record KickInfo(
            GroupMember groupMember,
            String playerName,
            OfflinePlayer player,
            String reason,
            long extra
    ) {
    }

    @NotNull List<KickInfo> generateNotBind(int max) throws Exception;

    @NotNull List<KickInfo> generateOneDayPlayer(int max) throws Exception;

    @NotNull List<KickInfo> generateLowLevelMembers(int level) throws Exception;
}
