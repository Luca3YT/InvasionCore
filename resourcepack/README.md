# InvasionCore のリソースパック

独自アイテムの見た目を、自分で描いた絵に差し替えるためのフォルダです。
ここに PNG を置いてスクリプトを実行すると、Minecraft に入れられるリソースパック（zip）ができます。

## しくみ

1. サーバーの `plugins/InvasionCore/item_model.yml` で、アイテムごとに CustomModelData の値を決める
   （初めて起動したときは全部空白。空白のアイテムには何も付かない）。
2. プラグインは、その値を **設定したあとに手に入れたアイテム** に付ける。
3. リソースパックは、その値が付いたアイテムだけ、ここに置いた絵に差し替える。
   バニラのアメジストの欠片などは元の見た目のまま。

`item_model.yml` の書き方:

```yaml
recall: 1        # 数字 → 数値として付く（/give の custom_model_data={floats:[1]} と同じ）
shard: 2
hammer: "hammer" # "" で囲む → 文字列として付く（custom_model_data={strings:["hammer"]}）
nexus:           # 空白 → 付けない
```

書き換えたら `/invasion admin reload` で読み直します。前から持っているアイテムは変わりません。

## 差し替えられるアイテム

PNG の名前は `item_model.yml` の項目名と同じにします。

| item_model.yml の項目 / 置くファイル | アイテム | 元の見た目 |
|---|---|---|
| `nexus` / `textures/nexus.png` | ネクサス | ロードストーン |
| `hammer` / `textures/hammer.png` | 強化ハンマー（手に持つと道具の持ち方） | 鉄の斧 |
| `shard` / `textures/shard.png` | Shard（ネクサスの残滓） | アメジストの欠片 |
| `kit` / `textures/kit.png` | 応急補修キット | ハニカム |
| `turret_arrow` / `textures/turret_arrow.png` | 射撃タレット | ディスペンサー |
| `turret_flame` / `textures/turret_flame.png` | 火炎タレット | 溶鉱炉 |
| `turret_frost` / `textures/turret_frost.png` | 凍結タレット | 氷塊 |
| `turret_heal` / `textures/turret_heal.png` | 修復タレット | ビーコン |
| `spike` / `textures/spike.png` | スパイクトラップ | 鍾乳石 |
| `mine` / `textures/mine.png` | 爆裂地雷 | 重量感圧板（重） |
| `flash` / `textures/flash.png` | 閃光弾 | 雪玉 |
| `medkit` / `textures/medkit.png` | 救急キット | ガストの涙 |
| `barrier` / `textures/barrier.png` | 緊急バリア | 鉄のトラップドア |
| `recall` / `textures/recall.png` | 帰還の羅針盤 | 回復コンパス |

- 絵は **正方形の PNG**（16×16 がバニラと同じ大きさ。32×32 などでもよい）。背景は透明にする。
- 絵は持ち物の欄と手に持ったときの見た目だけが変わります。タレットやネクサスなどのブロックは、置くと元のブロックの見た目です。
- 絵を置いても、`item_model.yml` の値が空白のアイテムは差し替わりません（スクリプトが「空白」と知らせます）。
- 動く絵にしたいときは、縦に並べたコマの PNG と、同じ名前の `.png.mcmeta`（例: `shard.png.mcmeta`）を置きます。
- `pack.png`（このフォルダの直下、正方形）を置くと、パックの一覧のアイコンになります。

## パックの作り方

1. 上の名前で PNG を `textures/` に置き、サーバーの `item_model.yml` に値を書く。
2. リポジトリの直下で次を実行する（Minecraft 26.2 を一度起動したことがある PC で）。

   ```
   scripts\build-resourcepack.cmd
   ```

3. `target\InvasionCore-resourcepack.zip` ができる。

スクリプトは値を **手元のテストサーバーの `server\plugins\InvasionCore\item_model.yml`** から読みます
（無ければ `src\main\resources\item_model.yml` の雛形。全部空白）。本番のサーバーと値を同じにしておいてください。
元の見た目の定義とパックのバージョンは、手元の Minecraft 26.2 のゲームファイル
（`%APPDATA%\.minecraft\versions\26.2\26.2.jar`）から読みます。別の場所にあるときは、jar のパスを引数で渡します。

## 配り方

できた zip は、次のどちらかで配ります。

- **サーバーから配る**：zip をどこかにアップロードして、`server.properties` の `resource-pack` にその URL、
  `resource-pack-sha1` に zip の SHA-1 を書く（`require-resource-pack=false` なら、断っても遊べる）。
- **参加者に入れてもらう**：zip を配って、各自 `.minecraft\resourcepacks` に入れてもらう。

## 注意

- 値を付けたあとのアイテムと、前から持っているアイテムは、インベントリで同じ欄にまとまりません（どちらも同じアイテムとして使えます）。
- 自分で作ったパックや配布されているパックを使うときは、`item_model.yml` の値をそのパックが見ている値に合わせてください。
