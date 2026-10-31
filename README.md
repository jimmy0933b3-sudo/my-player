# My Player

原生 Android 圖片與影片瀏覽器。

- 在同一個媒體庫查看手機照片與影片：全部／圖片／影片篩選、右上角按鈕展開檔名搜尋，收起即清除搜尋條件。
- 預設縮圖網格，亦可切換清單檢視；建立日期與檔案大小，升冪／降冪排序。建立時間排序時會依本機日期自動分群，同一天集中顯示，網格與清單都適用。
- 全螢幕檢視時上下滑動切換上一個／下一個，影片左右滑動快轉及倒退，圖片與影片都支援雙指縮放。影片進度條採細線與小滑塊，仍可直接拖曳定位。
- 使用 Android MediaStore 讀取授權內容，不上傳圖片或影片。
- Android 14+ 支援只授權指定照片與影片，以及重新選取範圍。
- GitHub Actions 對 main 建置、簽章及發佈 Release APK；確認新檔上傳成功後才移除舊 Release。

## GitHub Actions Secrets

使用既有的四項 Actions Secrets：

| Name | Description |
| --- | --- |
| `KEYSTORE_BASE64` | 既有 Android 簽章 keystore 的 Base64 |
| `KEYSTORE_PASSWORD` | KeyStore 密碼 |
| `KEY_ALIAS` | 金鑰別名 |
| `KEY_PASSWORD` | 私鑰密碼 |

簽署用的檔案只會還原到 runner 暫存目錄，不提交到 Git。若任一 Secret 不存在則發布失敗，**不會**使用臨時測試金鑰取代既有簽章。

## Build

安裝 JDK 17、Android SDK 35、Gradle 8.11.1 後執行：

```bash
gradle :app:assembleDebug
```

在 GitHub 上每次推送 `main` 都會觸發 Release 工作流程。APK 下載請見 [Releases](https://github.com/jimmy0933b3-sudo/my-player/releases/latest)。
