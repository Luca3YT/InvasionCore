# 引き継ぎメモ（2026-10-21）

前任者（MAIOTA 氏）から引き継いだ時点の状態と、再開発を始めるための情報をまとめる。
ゲーム仕様そのものは [README.md](../README.md) にある。

---

## 1. 引き継ぎ時点の状態

- 開発期間: 2026-08-04 の1日で 11 コミット（前任者が Claude と共同で実装）。以降の更新なし。
- 規模: Java 45 ファイル・約 9,500 行。テストコードなし。
- ブランチ: `develop`（作業用）と `main`。リモートは未設定。
- 実装はほぼ完成しており、サーバー側の動作は実機で一通り確認済み。
  **プレイヤーが実際に触る部分（GUI・設置物・パーク・報酬受け取り・表示類）はクライアント未接続のため未検証。**

### このPCでの確認結果（2026-10-21）

| 項目 | 結果 |
|---|---|
| ビルド | ✅ `.\build.cmd package` で成功（JDK 26 で `--release 25` コンパイル、IntelliJ 2026.2.1 同梱の Maven 3.9.16） |
| サーバー起動 | ✅ `run/` の Paper 26.2 build 92 を Java 26 で起動。プラグインが例外なく有効化・無効化された |
| データ読み込み | ✅ 前任者の検証データ（ネクサス1基・強化ブロック137・トラップ13・プレイヤー2）を読み込めた |
| コンソール操作 | ✅ `invasion admin list` / `info` が応答 |

### 引き継ぎで直したもの

- `build.cmd`: 前任者のPC固有パス（`C:\tools\jdk-25.0.4+7` 等）の決め打ちをやめ、JDK と Maven を自動検出するようにした。
  `build.local.cmd`（gitignore 済み）で上書きできる。
- `run/start.cmd`（ローカルのみ・git 管理外）: 同じく JDK を自動検出するようにした。
- `.gitattributes`: `.cmd` を CRLF に固定。
- README: 強化ティアの耐久値が古いまま（140〜3200）だったので現行値（250〜3600）に修正。
- `CLAUDE.md` を追加（開発時の規約とハマりどころ）。

---

## 2. 開発環境のセットアップ

必要なもの: **JDK 25 以上**、**Maven 3.9 以上**（IntelliJ 同梱でよい）、git。

1. JDK: IntelliJ の「Project Structure → SDK → Download JDK」で入れたもの（`~/.jdks`）が自動で使われる。
2. Maven: PATH に無ければ IntelliJ 同梱のものが自動で使われる。
3. ビルド: `.\build.cmd package`
4. 自動検出が外れる場合は `build.local.cmd` を作る:
   ```bat
   set "JAVA_HOME=C:\path\to\jdk-25"
   set "MVN=C:\path\to\apache-maven\bin\mvn.cmd"
   ```

IntelliJ で開く場合は `pom.xml` をプロジェクトとして開き、Project SDK を JDK 25 以上にする。

### 検証サーバー（`run/`）

`run/` は git 管理外。新しいメンバーは以下で用意する:

1. `run/` を作り、[PaperMC](https://papermc.io/downloads/paper) から Paper 26.2 の jar（前任者は build 92）を置く
2. 一度起動して `eula.txt` を生成し、内容を確認して自分で `eula=true` にする
3. `server.properties` の `server-port` を 25599 にする（前任者の設定に合わせる場合）
4. `target/InvasionCore-*.jar` を `run/plugins/` にコピーし、`run/start.cmd` で起動

> いま手元にある `run/` は前任者の検証環境をそのまま引き継いだもの。`ops.json` には前任者（MAIOTA）の
> アカウントが OP として登録されているので、自分のアカウントに置き換えること（`op <名前>` をコンソールで実行）。
> ワールドには崩壊状態のネクサスが1基残っている（`world -6,-56,-23`）。

---

## 3. コードの地図

```
InvasionCore.java          組み立てと寿命管理のみ（全サービスを new して配線）
├─ nexus/     ネクサス本体・登録簿・アップグレード         nexuses.yml
├─ wave/      7日周期とブラッドムーン、変調（WaveModifier）  ← フェーズ遷移の中心
├─ mob/       侵略Mob 13種＋特殊・ボス5種・独自AI・湧き
├─ defense/   ブロック強化ティア・タレット・トラップ          fortified.yml / turrets.yml / traps.yml
├─ economy/   Shard 通貨・貢献度按分・報酬テーブル
├─ perk/      パーク10種と常時効果                          players.yml
├─ heat/      ヒートマップ（叫喚体の発生源）
├─ stage/     GameStage（難易度指標）
├─ world/     徘徊ホード・空中投下・死亡バックパック・破壊ログ
├─ listener/  Bukkit イベントの受け口（ロジックは各サービスへ委譲）
├─ ui/        BossBar・サイドバー・GUI
├─ command/   /invasion（Paper の BasicCommand、plugin.yml 宣言なし）
├─ item/      独自アイテムとレシピ
└─ util/      PDC キー・テキスト装飾
```

定期処理（`InvasionCore#onEnable`）:

| 処理 | 間隔 |
|---|---|
| 侵略Mob AI | 毎tick（内部で `ai-buckets` 分割） |
| WaveEngine / ワールドイベント / サイドバー / パーク効果 | 1秒 |
| タレット | `turret-tick-interval`（既定 10tick） |
| トラップ | 10tick |
| 自動保存・記録掃除 | 5分 |

---

## 4. 未検証・既知の課題

### 未検証（クライアント接続して最初に確認すべきもの）

- ネクサス GUI（アップグレード・報酬受け取り・修理）、パーク画面
- 強化ハンマー・補修キット、タレットとトラップの設置・作動・弾薬消費
- 消耗ガジェット（閃光弾・救急キット・緊急バリア）
- BossBar とサイドバーの表示
- 貢献度按分と Shard 配布、`/invasion join` / `leave`
- 複数人同時プレイ、長時間運用時のメモリ

### 既知の制約（README「既知の制約」より）

- `BreachLog`（破壊ブロック記録）と、空中投下・バックパックの一時容器はメモリ上のみ。再起動で追跡が切れる。
- Folia 非対応。
- 7DTD の構造崩落・空腹/水分・トレーダーは対象外。

### 気づいた点

- Paper 26.2 build 92 は起動時に「2リリース・42ビルド遅れ」と警告される。最新版へ上げるかは要判断
  （`pom.xml` の `paper.version` と `paper-plugin.yml` の `api-version`、`run/` の jar を揃えて更新する）。
- `InvaderSpawner` で非推奨 API を使っているとコンパイラが警告している（動作に影響なし）。
- パッケージ名 `work.maiotanet.invasioncore`、`paper-plugin.yml` の `authors: MAIOTA` は前任者のもの。
  自分たちの名前空間に変えるかは要判断。変える場合、PDC キーの名前空間（プラグイン名由来）は変わらないので
  既存アイテムやデータは引き続き読める。
- 起動ログの「Java 25」は文言の決め打ち（実際の Java バージョンとは無関係）。

---

## 5. 「配信参加型」について

コード上は **複数人で1つのネクサスを守る協力型サーバープラグイン** で、視聴者がサーバーに入って
`/invasion join` で防衛に参加する形を想定している。
**配信サービス（Twitch / YouTube 等）との連携機能は入っていない。**
コメントや投げ銭でウェーブやボスを呼ぶような連動が欲しい場合は新規実装になる
（`/invasion admin spawn` / `boss` / `forcewave` / `airdrop` がコンソールから叩けるので、外部ツールから
RCON 経由で呼ぶのが最短ルート）。

---

## 6. 次にやることの候補

1. 自分のアカウントで検証サーバーに入り、「4. 未検証」の項目を一通り触る
2. 配信でどう使うか（視聴者参加の形・配信連動の有無）を決める
3. 実プレイでバランス調整（`config.yml` だけで完結する）
4. 必要ならリモートリポジトリ（GitHub 等）を作って共有する
