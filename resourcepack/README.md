# InvasionCore のリソースパック

独自アイテムの見た目を、自分で描いた絵に差し替えるためのフォルダです。
ここに PNG を置いてスクリプトを実行すると、Minecraft に入れられるリソースパック（zip）ができます。

## 差し替えられるアイテム

| 置くファイル | アイテム | 元の見た目 |
|---|---|---|
| `textures/shard.png` | Shard（ネクサスの残滓） | アメジストの欠片 |
| `textures/recall.png` | 帰還の羅針盤 | 回復コンパス |
| `textures/hammer.png` | 強化ハンマー | 鉄の斧（手に持つと道具の持ち方になる） |

- 絵は **正方形の PNG**（16×16 がバニラと同じ大きさ。32×32 などでもよい）。背景は透明にする。
- 置かなかったアイテムは、元の見た目のままになります。
- 動く絵にしたいときは、縦に並べたコマの PNG と、同じ名前の `.png.mcmeta`（例: `shard.png.mcmeta`）を置きます。
  書き方はバニラのアニメーションと同じです。
- `pack.png`（このフォルダの直下、正方形）を置くと、パックの一覧のアイコンになります。

バニラのアメジストの欠片・回復コンパス・鉄の斧は、元の見た目のまま変わりません。
プラグインが独自アイテムにだけ付けている **CustomModelData**（`config.yml` の `custom-model-data`）を見て、
独自アイテムのときだけ絵を差し替えています。

## パックの作り方

1. 上の名前で PNG を `textures/` に置く。
2. リポジトリの直下で次を実行する（Minecraft 26.2 を一度起動したことがある PC で）。

   ```
   scripts\build-resourcepack.cmd
   ```

3. `target\InvasionCore-resourcepack.zip` ができる。

スクリプトは、手元の Minecraft 26.2 のゲームファイル（`%APPDATA%\.minecraft\versions\26.2\26.2.jar`）から
元の見た目の定義とパックのバージョンを読みます。別の場所にあるときは、jar のパスを引数で渡します。

## 配り方

できた zip は、次のどちらかで配ります。

- **サーバーから配る**：zip をどこかにアップロードして、`server.properties` の `resource-pack` にその URL、
  `resource-pack-sha1` に zip の SHA-1 を書く（`require-resource-pack=false` なら、断っても遊べる）。
- **参加者に入れてもらう**：zip を配って、各自 `.minecraft\resourcepacks` に入れてもらう。

## 注意

- `config.yml` の `custom-model-data` の値を変えたら、パックも作り直してください（スクリプトは `src/main/resources/config.yml` の値を読みます。サーバーの config.yml だけ変えた場合は、こちらも同じ値にしてから作る）。
- CustomModelData は、**この機能を入れた版より後に作られたアイテム** にだけ付いています。
  それより前に手に入れた Shard などは元の見た目のままで、新しく手に入れた Shard とは同じ欄にまとまりません（使うときはどちらも Shard として数えます）。
