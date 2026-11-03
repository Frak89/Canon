# AmpyEOS — firma APK stabile fra le compilazioni

## Perché alcune APK richiedono una disinstallazione

GitHub Actions compila una APK debug su un runner temporaneo. Senza una chiave di firma impostata, Gradle può generare un nuovo certificato debug a ogni esecuzione. Android impedisce di installare una versione con firma diversa sopra quella esistente: disinstallando, si perdono i vecchi file privati.

Dalla v0.8.1 i GGUF vengono salvati in `Download/AmpyEOS/Qwen3-VL` tramite MediaStore per non perderli con la disinstallazione. Una firma stabile permette invece di evitare la disinstallazione negli aggiornamenti futuri.

## Configurazione sicura (una volta sola)

Non pubblicare MAI la chiave di firma nel repository o negli artifacts GitHub.

1. Sul tuo computer genera una chiave:
   `keytool -genkeypair -v -keystore ampyeos-signing.jks -alias ampyeos -keyalg RSA -keysize 3072 -validity 10000`
2. Conserva keystore e password in un backup sicuro.
3. Codifica localmente il file Base64:
   - PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("ampyeos-signing.jks"))`
   - Linux/Termux: `base64 -w 0 ampyeos-signing.jks`
4. In GitHub vai su **Frak89/Canon → Settings → Secrets and variables → Actions → New repository secret** e crea:
   - `AMPYEOS_KEYSTORE_BASE64` = Base64 del keystore
   - `AMPYEOS_STORE_PASSWORD` = password del keystore
   - `AMPYEOS_KEY_ALIAS` = `ampyeos`
   - `AMPYEOS_KEY_PASSWORD` = password della chiave
5. Il workflow userà queste secrets per firmare le APK con lo stesso certificato senza conservare il file chiave nell'artifact. Se mancano, mantiene la firma debug predefinita con un avviso.

## Prima installazione della firma stabile

La prima APK firmata con la nuova chiave potrebbe richiedere ancora una disinstallazione se la tua versione precedente ha un certificato diverso.

**Prima di disinstallare**, salva i GGUF già presenti in `Download/AmpyEOS/Qwen3-VL`.

- Se puoi aggiornare direttamente alla v0.8.1 con la stessa firma, apri l'editor e lascia terminare la migrazione automatica.
- Se Android rifiuta l'aggiornamento per firma diversa, **non disinstallare ancora**. Connettiti via ADB con un PC ed esegui `scripts/backup_qwen_before_uninstall.ps1` (Windows PowerShell) oppure `scripts/backup_qwen_before_uninstall.sh` (Linux/Termux con ADB collegato).
- Gli script di recupero leggono i due GGUF dalla cartella privata della **vecchia APK debug** tramite `run-as`, li copiano nella cartella Download e verificano i rispettivi SHA-256 senza collegarsi a Internet.
- Dopo che entrambi i file sono stati verificati, puoi disinstallare se necessario e installare la nuova APK. Se Android non dà accesso automatico ai Downloads della precedente installazione, seleziona i file esistenti con **IMPORTA GGUF DA DOWNLOAD**.

Gli script richiedono che il vecchio APK sia debuggable e che ADB sia stato autorizzato sul Pixel. Non richiedono root o l'invio di foto.

## Controllo della firma

Il log GitHub Actions deve mostrare `Persistent signature configured`. Con `apksigner verify --print-certs` confronta gli SHA-256 dei certificati delle APK successive: devono coincidere. Non confondere la firma del certificato con l'hash dell'APK, che cambia normalmente fra versioni.

**Nota:** le quattro repository secrets devono essere aggiunte da te. L'app non può generarle né custodirle in GitHub automaticamente. Non incollare qui password, keystore o segreti.
