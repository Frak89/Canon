# AmpyEOS Camera Assistant — Pixel 7 Pro + Canon EOS R6 Mark II

Progetto Android nativo per **Android 17 / Pixel 7 Pro**, sviluppato in Java, con suggerimenti fotografici in tempo reale **interamente sul telefono**, inclusa AI on-device ML Kit per riconoscere volti e oggetti.

## Rev 0.1 — funzionalità implementate
- **PIXEL LIVE**: CameraX con preview live, lettura YUV luma a campionamento ridotto e suggerimenti di luce, contrasto, clipping, microcontrasto/possibile mosso e composizione.
- **CANON LIVE (sperimentale)**: connessione Wi-Fi via Canon CCAPI HTTP, riconoscimento versione, informazioni dispositivo e tentativo di avvio Live View JPEG (polling via `flip`); ogni frame riconosciuto viene analizzato con lo stesso motore locale.
- **LINK**: indirizzo IP/porta manuale oppure rilevamento SSDP/UPnP ispirato ad AmpyLink (senza scandire indirizzi a caso).
- **SCATTA**: comando CCAPI inviato **solo su pressione esplicita**. Non modifica mai il firmware Canon, non tenta sblocco o installazioni.
- **AI LOCALE**: ML Kit Object Detection + Face Detection con modelli integrati, analisi soggetto e suggerimenti dei terzi su Pixel e JPEG Canon quando disponibili.
- **OVERLAY**: griglia dei terzi e composizione; punteggio di qualità *euristico*; letture e suggerimenti con soglie modificabili nel codice.
- **LOG** di collegamento visibile sul display.
- **CI**: GitHub Actions per produrre `app-debug.apk`.

### Importante: cosa NON è
- Non è ancora un porting di Magic Lantern né esegue Lua sulla EOS.
- Il riconoscimento di volti/oggetti usa ML Kit on-device; il punteggio di luce e nitidezza resta una stima euristica, non una valutazione artistica e non un modello generativo.
- I consigli sullo schermo non dimostrano che una foto sia artisticamente bella: sono suggerimenti tecnici basati sul frame.
- **PTP/IP e Bluetooth BLE di AmpyLink non sono ancora integrati**: in questa fase il collegamento è CCAPI. Lo sviluppo potrà riutilizzare quelle componenti dopo test isolati, evitando duplicazioni del servizio BLE.
- La compatibilità CCAPI è **da verificare su fotocamera reale**; porta e capacità dipendono da firmware e configurazione Canon.

## Requisiti
- JDK 17, Android Studio recente, Android SDK 36
- Android Gradle Plugin 8.9.2, Gradle 8.11.1
- Pixel 7 Pro (test di destinazione), Android 11+ (minSdk 30)
- CameraX 1.6.2

### Build
Apri la cartella del repository in Android Studio; sincronizza Gradle; esegui `:app:assembleDebug`. In alternativa vai su Actions → Android debug APK → Run workflow, quindi scarica `AmpyEOS-debug-apk` dagli artifact. La compilazione CI va ancora verificata.

## Primo avvio
1. Concedi autorizzazione fotocamera. Parte Pixel Live senza login o cloud.
2. Tieni l'inquadratura ferma e leggi il suggerimento; le soglie sono solo orientative.
3. Per EOS, abilita CCAPI secondo istruzioni Canon, collega Canon e Pixel sulla stessa rete (o alla rete fornita dalla Canon), premi **CANON**, inserisci `192.168.x.x:8080` (sostituisci con IP e porta visualizzati dalla fotocamera).
4. Se l'anteprima non parte, l'app visualizza l'errore: alcune configurazioni richiedono autenticazione o un metodo Live View diverso. Ripassa a **PIXEL**.
5. Il pulsante **SCATTA CANON** invia uno scatto solo se connesso, e solo su tua richiesta.

## Architettura
- `MainActivity.java`: UI, lifecycle, preview, controllo modalità, loop Live View.
- `PhotoAnalyzer.java`: analisi a basso costo dei frame CameraX e JPEG.
- `CanonCcapi.java`: client HTTP con limite dimensioni e timeout, start/stop Live View.
- `CanonDiscovery.java`: ricerca SSDP limitata alla LAN; non è pairing BLE.
- `GuideOverlay.java`: griglia terzi.
- `.github/workflows/android.yml`: build CI.

## Sicurezza
Nessuna modifica firmware, nessun overclock, nessun upload al cloud. La connessione HTTP Canon è in chiaro se attivata in CCAPI: usala su una rete fidata; alcuni modelli richiedono pairing o HTTP Digest/HTTPS non ancora implementati.

### AmpyLink
Progetto di ispirazione: https://github.com/Frak89/AmpyLink — ha gestione BLE, SSDP/UPnP e PTP/IP. AmpyEOS mantiene **una propria sessione CCAPI** per non avviare servizi AmpyLink né interferire con la sua galleria/stampa.

## Roadmap
- Rev0.2: autenticazione Canon (quando richiesta), capability matrix, stabilità Live View con fallback multipart/RTP e test dispositivo.
- Rev0.3: modelli opzionali TFLite per categorie più ricche (animali/occhi/scena), orizzonte, esposizione e generi fotografici.
- Rev0.4: profili (ritratto, paesaggio, astro, street) e checklist automatizzate.
- Rev0.5: monitoraggio batteria/SD, benchmark Canon non invasivi, automazioni autorizzate.
- Rev1.0: eventuale runtime Lua **solo se** viene dimostrato un loader sicuro e compatibile sulla EOS R6 Mark II.

Codice GPL-3.0-or-later; componenti Canon e AmpyLink restano dei rispettivi autori.
