package work.maiotanet.invasioncore.command;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import work.maiotanet.invasioncore.util.Text;

import java.util.List;
import java.util.function.Supplier;

/**
 * /rule・/bug・/patchnote — 案内ページの URL をクリックできる形で出す。
 *
 * <p>視聴者が自分で打つコマンドなので、/invasion の下ではなく短い単独のコマンドにしている。
 * URL は配信ごとに差し替えられるよう config.yml から読む（reload 後も反映されるよう毎回読む）。</p>
 */
public final class LinkCommand implements BasicCommand {

    /** 1 行分。説明の文と、開く URL。 */
    public record Link(String label, Supplier<String> url) {
    }

    private final List<Link> links;

    public LinkCommand(List<Link> links) {
        this.links = links;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        for (Link link : links) {
            String url = link.url().get();
            sender.sendMessage(Text.prefixed(Text.of(link.label(), Text.GOOD)
                    .append(Text.of(url, Text.INFO)
                            .decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.openUrl(url))
                            .hoverEvent(HoverEvent.showText(Text.of("クリックでブラウザを開く", Text.MUTED))))));
        }
    }
}
