# AmpyEOS 0.4.0 — Technical Photography Master Classroom

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

### Rev0.4 · Analisi visiva e correzioni automatiche
- **AUTO PRO COMPLETO**: rendering migliore secondo metriche tecniche locali, AutoStraighten prudente e AutoTone JPEG (senza recupero RAW), avvio lezione automatica.
- **ANALISI VISIVA**: luminanza, ombre, zone chiare, dettagli, bilanciamento sinistra-destra, distanza dei contorni dai punti dei terzi, possibile linea quasi orizzontale, spiegazioni verificabili.
- **OVERLAY TECNICO**: heatmap arancio delle strutture, griglia turchese, ritaglio giallo, possibile linea viola, regione verde se ML Kit rileva un volto/oggetto.
- **AUTO RADDRIZZA**: evita interventi se la linea candidata è troppo incerta; non è una vera correzione prospettica.
- **AUTO TONI**: curva prudente pixel-space per schiarire ombre e attenuare zone molto luminose, senza inventare dettagli.
- **AUTO RESIZE**: 2048px web e formati 1080×1080, 1080×1350, 1080×1920, 1920×1080 con scelta riempi/letterbox.
- **Riconoscimento oggetti/volti sulle foto importate**: inferenza ML Kit integrata sul Pixel, non invio cloud.
- Specifica completa (schermate, pulsanti, algoritmi, limiti, prove): [docs/AmpyEOS_Rev04_Specifica.md](docs/AmpyEOS_Rev04_Specifica.md).

### Master AI Auto Render (Rev0.3)
- **MASTER AI AUTO** produce 9 proposte locali a partire dalla foto: originale, esposizione bilanciata, composizione guidata, bianco e nero, bianco e nero ad alto contrasto, cinema, vintage, ritratto morbido e colori intensi.
- Il selettore valuta luce, ombre, clipping, dettaglio, contrasto, e penalizza tagli eccessivi/look aggressivi. Il migliore viene renderizzato in automatico, senza modificare il file originale.
- **VERSIONI AI** consente di selezionare qualsiasi rendering alternativo, senza inviare dati a un server.
- **SPLIT SCORREVOLE**: confronto originale/proposta con linea trascinabile sullo schermo.
- **LEZIONE AUTOMATICA**: cinque passaggi animati con voce TTS italiana, narrazione dello scatto originale, correzione, motivazione delle scelte, confronto e verdetto finale.
- **VERDETTO MOTIVATO** e suggerimenti per rifare lo scatto, con indicazione del punteggio tecnico stimato; un 10/10 non equivale a una fotografia artisticamente perfetta.
- L'ottimizzatore `AutoMasterEngine` è un motore euristico locale di rendering e confronto, **non** un modello generativo o una rete neurale estetica. Il riconoscimento volti/oggetti in Live View usa ML Kit.

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

## Stato della v0.3
La build viene eseguita da GitHub Actions. Compilazione non significa test hardware: CameraX, SDK Canon CCAPI e TTS devono essere provati sul Pixel 7 Pro e sulla EOS R6 II. Un ritaglio automatico non può recuperare dettagli persi, né modificare la composizione reale dello scatto già effettuato.
