package cn.paper_card.qq_group_kick;

import cn.paper_card.qq_group_access.QqGroupAccessApi;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface QqGroupKickApi {
    record KickInfo(
            QqGroupAccessApi.GroupMember groupMember,
            String playerName,
            OfflinePlayer player,
            String reason
    ) {
    }

    @NotNull List<KickInfo> generateNotBind(int max) throws Exception;

    @NotNull List<KickInfo> generateOneDayPlayer(int max) throws Exception;
}
