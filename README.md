# 私藏漫畫 ComicVault（Android 原生 App）

Kotlin + Jetpack Compose，minSdk 26（Android 8.0）以上。

## 功能
- **新增漫畫**：多選圖片、選整個資料夾（含子資料夾、隱藏資料夾）、匯入 ZIP/CBZ
- **自動依檔名排序**：`2.jpg < 10.jpg`、`第二話 < 第十話 < 第十一話`、全形數字、`第1話/003.jpg < 第2話/001.jpg`
- **隱藏相簿**：掃描 `.` 開頭資料夾與含 `.nomedia` 的資料夾；副檔名被改掉的檔案會讀檔頭辨識
- **相簿看不到 App 裡的圖**：圖片複製到 App 私有空間（`/data/data/…/files/comics`），可選「匯入後刪除原始檔」
- **閱讀器**：條漫上下捲動／左→右翻頁／日漫右→左；雙指縮放、雙擊放大；點左右翻頁；記住讀到哪頁；滑桿跳頁
- 長按漫畫：追加圖片（會跟原有頁面一起重新排序）、重新命名、刪除

## 編譯方式
**A. Android Studio**：File → Open 選這個資料夾 → 等 Gradle 同步完 → Run ▶（接手機或模擬器）。
要拿 APK：Build → Build App Bundle(s) / APK(s) → Build APK(s)。

**B. 不裝 Android Studio（GitHub Actions）**：把整個資料夾推到一個 GitHub repo，
Actions 會自動編譯，完成後到該次 workflow 頁面底下的 Artifacts 下載 `ComicVault-apk`，解壓縮後傳到手機安裝。

## 注意
- 有加密的保險箱（三星「安全資料夾」、部分品牌的加密隱藏相簿）其他 App 無法讀取，需先在原相簿 App 取消隱藏。
- 「掃描隱藏相簿」需要「所有檔案存取權」，一般匯入不需要任何權限。
- 解除安裝 App 會連同裡面的漫畫一起刪除。
