# InvasionCore — 開発メモ（Claude / 開発者向け）

Paper 26.2 用のネクサス防衛プラグイン。ゲーム仕様は [README.md](README.md)、
引き継ぎ時点の状況と今後の候補は [docs/handoff.md](docs/handoff.md) を参照。

## ビルドと実行

- ビルド: `.\build.cmd package` → `target/InvasionCore-0.1.0-SNAPSHOT.jar`
  - JDK 25 以上と Maven を自動検出する（`~/.jdks`、`C:\tools`、Adoptium、PATH、IntelliJ 同梱 Maven）。
  - 検出できない環境では、リポジトリ直下に `build.local.cmd`（gitignore 済み）を置いて
    `set "JAVA_HOME=..."` / `set "MVN=..."` を書く。
- 検証サーバー: `run/`（gitignore 済み・各自ローカル）。jar を `run/plugins/` にコピーして `run/start.cmd`。
  - サーバーポートは 25599。コンソールから `invasion admin ...` が打てる。
- テストコードは無い。動作確認は実サーバーで行う。

## アーキテクチャの要点

- `InvasionCore.java` が全サービスを手で組み立てる（DI コンテナ無し）。依存はコンストラクタ注入。
- 定期処理は全てメインスレッドの `runTaskTimer`。AI は毎tick呼ばれるが内部でバケット分割。
- `WaveEngine` がフェーズ遷移（DORMANT → PREPARATION ⇄ BLOOD_MOON → BROKEN）の唯一の持ち主。
  AI 層は `NexusCombatSink` 越しにしかネクサスへダメージを入れない。
- 永続化は `plugins/InvasionCore/*.yml`。各サービスが自分のファイルを load/save し、5分ごとと無効化時に保存。
  `Nexus` の `transient` フィールド（現在ウェーブ等）は永続化しない。

## コード規約（既存コードに合わせる）

- バランス値は `config.yml` → `InvasionConfig` 経由で読む。コードに数値を直書きしない。
- PDC の `NamespacedKey` は `util/Keys` に集約。独自アイテムの判定は表示名ではなく PDC の ITEM_ID。
- プレイヤー向けメッセージは `util/Text` のヘルパで色付けする。日本語。
  繰り返し発生する軽いフィードバックはチャットではなくアクションバーへ（チャット流れ防止）。
- GUI の識別は `InvasionHolder`。タイトル文字列で判定しない。
- コメント・Javadoc は日本語で「なぜそうしているか」を書く。
- `.cmd` ファイルは ASCII のみ・CRLF（cmd.exe が OEM コードページで読むため）。
- コミットメッセージは英語の `type: summary`（feat / fix / balance / docs）＋本文で理由を説明する既存の流儀。
- ブランチ: 作業は `develop`、`main` へ PR。

## ハマりどころ

- Paper 26.2 は Java 25 必須。PATH の古い java だと起動もビルドもできない。
- Windows では `-Dstdout.encoding=UTF-8` が無いと日本語ログが全て文字化けする。
- `block.getState()` はスナップショットを返す。容器の中身を書き換えるときは `getState(false)`。
- 侵略者が壊したブロックの記録（`BreachLog`）はメモリのみ。再起動を跨ぐと `restore` できない。
