package work.maiotanet.invasioncore.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import work.maiotanet.invasioncore.config.InvasionConfig;
import work.maiotanet.invasioncore.util.Text;

/**
 * /rule — ルール説明の URL を表示する。
 *
 * <p>視聴者が自分で打つコマンドなので、/invasion の下ではなく短い単独のコマンドにしている。
 * URL は配信ごとに差し替えられるよう config.yml の rules.url から読む。</p>
 */
public final class RuleCommand implements BasicCommand {

    private final InvasionConfig config;

    public RuleCommand(InvasionConfig config) {
        this.config = config;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        String url = config.rulesUrl();
        source.getSender().sendMessage(Text.prefixed(Text.of("ルール説明はこちら: ", Text.GOOD)
                .append(Text.of(url, Text.INFO)
                        .decorate(TextDecoration.UNDERLINED)
                        .clickEvent(ClickEvent.openUrl(url))
                        .hoverEvent(HoverEvent.showText(Text.of("クリックでブラウザを開く", Text.MUTED))))));
    }
}
