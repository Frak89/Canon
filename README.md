# AmpyEOS 0.2.0 — Photo Intelligence & Virtual Classroom

App Android per Pixel 7 Pro e assistente remoto Canon EOS R6 Mark II.

## Implementato

### Pixel Live
- CameraX: preview e scatto JPEG salvato in `Pictures/AmpyEOS`.
- Analisi YUV locale: luminosità, percentuale alte luci / ombre, microcontrasto e istogramma da 32 bin.
- Rilevamento reale volti/oggetti con ML Kit sul dispositivo, senza invio immagini al cloud.
- Profili AUTO, RITRATTO, PAESAGGIO, STREET, NOTTE e MACRO.
- Consigli di fotografia motivati, aggiornati durante il Live View e lettura vocale opzionale.
- Overlay animato "Automated Composer": suggerisce in tempo reale dove mettere il soggetto e come ritagliare. **Non muove la fotocamera né modifica lo scatto RAW.**
- Color Look Advisor automatico: valuta colori/contrasto e sceglie un'anteprima tra naturale, B/N, B/N forte, vintage, cinema, soft, vividi e matte. La pipeline Android RenderEffect richiede Android 12+ e CameraX TextureView; eventuali limiti hardware si verificano sul dispositivo.

### Canon Live (sperimentale)
- Ricerca SSDP/UPnP e BLE (soltanto discovery), ispirata all'architettura AmpyLink.
- Connessione CCAPI via Wi-Fi quando attiva sulla Canon; identifica gli endpoint pubblicati dal dispositivo.
- Tentativo Live View JPEG con analisi; può richiedere fallback alternativo su diversi firmware.
- Scatto remoto soltanto con azione esplicita dell'utente, se pubblicato da CCAPI.
- Informazioni diagnostiche di rete e possibili capacità (batteria, impostazioni, ecc.).
- Scorciatoia per aprire AmpyLink se installata.
- **Nessuna modifica firmware, no overclock, nessuna esecuzione Lua sulla fotocamera, nessun pairing BLE automatico ancora.**

### Color Lab + Virtual Classroom
- Importa foto da Android, analizza e classifica fino a 15 scatti per qualità **tecnica stimata**.
- Spiega criticità e suggerimenti pratici per migliorare l'immagine.
- Ritaglio manuale a trascinamento con proporzioni libero / 1:1 / 4:5 / 3:2 / 16:9.
- Auto Composer: ritaglio proposto a partire dai contorni + look consigliato.
- Ruota 90°, specchia e modifica colore, contrasto, luminosità e saturazione.
- Anteprima prima/dopo, ripristino dell'originale, salvataggio di una nuova copia JPEG.
- **Virtual Classroom** a cinque passaggi: osservazione, luce, composizione, colore e confronto. Animazioni e puntatore in sovrimpressione, avanti/indietro/play/pausa, spiegazione testuale e vocale opzionale.
- Il 10/10 è una **scala euristica** di requisiti tecnici. Non dimostra valore artistico, esposizione RAW o estetica universale.
- Per immagini RAW, l'importazione richiede che il decoder Android riesca ad aprirle; altrimenti usare un JPEG anteprima o un editor RAW come AmpyRAW.

## Build
GitHub Actions > Android debug APK > artifact `AmpyEOS-debug-apk`.
Oppure `gradle :app:assembleDebug` usando JDK 17 e SDK Android 36.

**Test hardware**: codice compilato in CI quando il workflow è verde; il test sul Pixel fisico e su EOS R6 Mark II non è sostituibile da CI.
