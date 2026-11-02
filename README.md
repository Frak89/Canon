# AmpyEOS 0.5.3 — Verified Streaming Model Extraction

App Android per Pixel 7 Pro e assistente remoto Canon EOS R6 Mark II.

## Novità v0.5.3 · Correzione dell'estrazione TAR del modello AI

- Corretto l'errore reale `Modello AI non disponibile: Voce archivio fuori limiti` sul Pixel. La versione precedente imponeva un limite di 256 MiB anche a file di checkpoint e altri elementi **non necessari** dell'archivio, oltre a interrompere la scansione dopo soli 80 MiB estratti.
- Il nuovo `ModelArchive` scorre in streaming un archivio TAR.GZ, ignora gli altri file senza scriverli su disco e salva soltanto il file ufficiale `mobilenet_v2_1.0_224.tflite`; limite di estrazione complessivo 4 GiB come protezione, limite di 256 MiB applicato soltanto al modello TFLite estratto.
- La verifica del modello avviene comunque con SHA-256 pinning; file arbitrari Qwen GGUF non vengono interpretati come TFLite.
- La CI ora usa lo **stesso codice di estrazione Java di Android** sull'archivio TensorFlow reale, confronta byte per byte il TFLite con `tar` e conferma gli hash attesi. Non è sufficiente che l'app compili se la decompressione fallisce.
- Status separati per download del pacchetto, estrazione e controllo di integrità. L'importazione manuale dalla cartella Download usa lo stesso estrattore corretto.
- Anche su 4G/5G vale la regola `DATI MOBILI AI` impostata dall'utente. Rimangono validi cache offline e nessun invio di immagini.

## Novità v0.5.2 · Riparazione download + file dalla cartella Download

- Corretto l'errore visibile `Pacchetto supera il limite previsto`: il vecchio limite di download era **24 MiB**, troppo basso per l'archivio ufficiale. Ora vale **512 MiB** per l'archivio e **256 MiB** per il modello estratto (limiti anti-abuso per proteggere memoria e spazio disco).
- **IMPORTA MODELLO DA DOWNLOAD** in Color Lab/Virtual Classroom e **MODELLO DA DOWNLOAD** nel Live: selettore file Android `ACTION_OPEN_DOCUMENT`; consente scegliere un file `.tgz`, `.tar.gz`, `.tflite` oppure `ImageNetLabels.txt` da Download o da altri provider documenti.
- Non richiede accesso a tutta la memoria: Android concede l'accesso soltanto al file selezionato. Applica gli stessi SHA-256 del modello TensorFlow Lite e delle 1001 etichette ufficiali.
- Importazione può avvenire anche offline **se selezioni sia il modello sia le etichette**; se mancano le etichette tenta di scaricare soltanto il piccolo file `ImageNetLabels.txt`.
- Se hai già aperto una foto nell'editor, dopo l'installazione valida del modello la classifica automaticamente e mostra il risultato; dal Live usa l'ultimo frame Pixel disponibile.
- Corretto l'allineamento con la status bar su Android 15/16/17 (system insets) ed eliminati overlay sulla schermata vuota prima di aprire una foto. La preview originale viene impostata subito dopo la decodifica; immagini con valori quasi tutti neri generano un avviso.
- **Qwen**: il formato `.gguf` non viene installato dal classificatore TFLite; in questa release la priorità è ripristinare MobileNet affidabile. Un modello Qwen-VL multimodale richiede un runtime Vision Language separato, più oneroso in RAM.

### Installazione manuale
1. Scarica `mobilenet_v2_1.0_224.tgz` da https://storage.googleapis.com/download.tensorflow.org/models/tflite_11_05_08/mobilenet_v2_1.0_224.tgz
2. In Color Lab premi `IMPORTA MODELLO DA DOWNLOAD` e seleziona il file scaricato: sarà estratto e controllato localmente.
3. Se le etichette non sono state scaricate automaticamente, scarica `ImageNetLabels.txt` da https://storage.googleapis.com/download.tensorflow.org/data/ImageNetLabels.txt e importa anche quel file.
4. Apri una foto o premi `RICONOSCIMENTO EXTRA`. Dopo l'importazione l'analisi funziona offline.

**Nessun supporto per modelli TFLite arbitrari:** l'algoritmo dipende dal formato e dalla normalizzazione del MobileNet ufficiale, e rifiuta file diversi per evitare errori di incompatibilità.

## Novità v0.5.1 · Download AI automatico anche su rete mobile

- Download automatico dei modelli **su Wi-Fi oppure rete mobile 4G/5G**, senza dover connettere il Pixel a un hotspot.
- Toggle separato **DATI MOBILI AI · ON/OFF**, disponibile sia nella schermata Live sia nell'editor; predefinito ON per questa revisione come richiesto.
- Il toggle **DOWNLOAD EXTRA** / **DOWNLOAD AI AUTOMATICO** continua a governare l'automatismo in generale, indipendentemente dal tipo di rete.
- **Roaming escluso dal download automatico** per evitare addebiti inattesi; il comando manuale può funzionare anche in roaming (valuta il tuo piano tariffario).
- Dopo il primo download, il modello resta in memoria privata e l'identificazione può operare offline. Le immagini non vengono caricate su server; soltanto i file del modello sono scaricati.
- Il download consuma dati mobili; l'app mostra lo stato e conserva i controlli SHA-256, i limiti di dimensione e la pausa tra tentativi falliti.
- Se usi una VPN che non comunica all'app il tipo di rete sottostante, il download automatico potrebbe non avviarsi: resta disponibile il comando manuale.

## Novità v0.5.0 · Ricerca soggetti con download di modelli AI

- Quando il riconoscimento ML Kit non trova un soggetto utile, **su Wi-Fi o rete mobile autorizzata** l'app scarica automaticamente **solo il modello AI** aggiuntivo MobileNetV2 e le relative etichette, non carica foto o altri dati personali su alcun server.
- Modello ufficiale TensorFlow: `mobilenet_v2_1.0_224.tgz`, estratto in memoria privata dell'app e validato con SHA-256 del file TFLite. Etichette ufficiali ImageNet: 1001 voci incluso background, con SHA-256 verificato.
- Il modello occupa circa 14 MB estratti e permette di classificare il soggetto dominante in una delle **circa 1.000 classi** del dataset ImageNet. Il risultato è una stima probabilistica con soglia minima e alternative; non riconosce qualsiasi oggetto immaginabile né fornisce una nuova bounding box.
- Se non sono disponibili né Wi-Fi né una rete mobile consentita, viene mantenuta l'analisi ML Kit; il download può essere avviato anche manualmente con `RICONOSCIMENTO EXTRA` nell'editor o `RICONOSCI EXTRA` in Live.
- Pulsante **DOWNLOAD EXTRA · ON/OFF** nel Live e **DOWNLOAD AI AUTOMATICO · ON/OFF** nell'editor: controlla il download automatico. Un modello già scaricato funziona offline.
- Download limitato per dimensioni, HTTPS su host TensorFlow ufficiale, verifica SHA-256 prima di installare, cache privata, attesa di 30 minuti tra tentativi falliti, nessun download ripetuto a ogni frame.
- MobileNetV2 elabora l'intera foto o un fotogramma ridotto e può sbagliare. La classificazione non dimostra l'identità di una persona, il significato della scena o la qualità artistica.

Sorgenti ufficiali: https://storage.googleapis.com/download.tensorflow.org/models/tflite_11_05_08/mobilenet_v2_1.0_224.tgz e https://storage.googleapis.com/download.tensorflow.org/data/ImageNetLabels.txt

## Novità v0.4.1 · Il maestro spiega il soggetto + pulsanti semantici

- **SOGGETTO RILEVATO:** ML Kit descrive il riconoscimento realmente disponibile sulle foto (es. volto o categoria generica di oggetto), posizione alto/basso/sinistra/destra e area relativa. Se il modello non riconosce nulla, lo dice esplicitamente; non inventa il contenuto della scena.
- **SPIEGAZIONE VOCALE:** nella lezione iniziale e nell'analisi visiva il maestro racconta quale regione è stata rilevata, la sua posizione e come il posizionamento potrebbe influire sulla composizione. Su Pixel Live i suggerimenti di ML Kit diventano più precisi.
- **PULSANTI TOGGLE ATTIVI:** fondo **menta chiaro** con testo scuro e dicitura `ON`.
- **TOGGLE SPENTI:** fondo **ardesia scuro**, contorno visibile e dicitura `OFF`; restano cliccabili per attivarli.
- **AZIONI NORMALI (non-toggle):** fondo **blu vivo**, distinto dai toggle. I comandi senza una foto o senza funzione disponibile sono realmente disabilitati e diventano ancora più scuri, con testo attenuato.
- **Editor:** i comandi che richiedono una fotografia restano disabilitati finché non apri/scatti; `APPLICA CROP` si attiva soltanto durante la selezione del ritaglio, `VERSIONI AI` soltanto quando esistono le proposte renderizzate.
- **Accessibilità:** gli stati ON/OFF sono indicati anche nel testo e nella descrizione accessibile, non soltanto dal colore.

Il riconoscimento ML Kit è limitato a volti e a un piccolo insieme di categorie generiche di oggetti, non a una comprensione generale di qualsiasi scena. Le parti che parlano di estetica sono consigli, non fatti accertati.

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
