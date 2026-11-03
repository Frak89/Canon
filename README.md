# AmpyEOS 0.8.0 — Studio Pro, Qwen Vision e Auto Raddrizza

App Android per Pixel 7 Pro e assistente remoto Canon EOS R6 Mark II.

## Novità v0.8.0 — interfaccia professionale e foto automaticamente dritte

- **Editor semplificato:** sette pannelli espandibili con controlli responsive su **due colonne**: Scatto & Raddrizzamento, Maestro Qwen, Risultati, Editor, Color Lab, Classroom e Modelli. Non c'è più una barra orizzontale interminabile.
- **RADDRIZZAMENTO ALL'APERTURA · ON:** impostazione attiva per default, mantenuta tra gli avvii. L'editor legge prima i dati EXIF Orientation, poi cerca strutture orizzontali/verticali in più zone del frame e corregge lievi inclinazioni attendibili. Se trova solo una diagonale o non può stabilire l'orizzonte, evita correzioni arbitrarie.
- **RADDRIZZA ADESSO** ripete il controllo sull'attuale copia di lavoro; **CORREZIONE FINE ±10°** offre anteprima in tempo reale e rotazione regolabile a passi di 0,1°. La foto sorgente non viene mai sovrascritta.
- All'apertura il look resta **NATURALE** e la griglia turchese della Virtual Classroom rimane nascosta fino all'avvio della lezione.
- **Qwen offline più pratico:** analisi rapida, approfondita e confronto originale/proposta, spiegazioni narrative e avvertenze; monitoraggio delle fasi native e diagnostica locale della RAM/temperatura. L'analisi usa una coda separata dai render dell'editor per mantenere attivi gli altri comandi.
- **MUSIQ ONNX /100** rimane un indicatore neurale di qualità percettiva, distinto da qualunque giudizio artistico. L'app non assegna automaticamente 9/10 a fotografie senza soggetto soltanto perché correttamente esposte.
- Nessuna foto inviata per analisi su server o servizi esterni; Internet serve ai download espliciti dei modelli e alle funzioni fotografiche Canon su rete locale.

**Guida completa e limiti del raddrizzamento:** [docs/AmpyEOS_Rev08_Studio_UI_AutoLevel.md](docs/AmpyEOS_Rev08_Studio_UI_AutoLevel.md)

**Verifiche:** l'esito della compilazione Android è controllato in GitHub Actions. La valutazione effettiva di linee inclinate e la qualità dell'inferenza Qwen devono essere verificate ancora con un set di scatti reali sul Pixel 7 Pro. Il modulo non promette che ogni fotografia contenga un orizzonte misurabile.

## Rev0.7 — Modello visivo serio, completamente dentro l'APK

**Novità:** Qwen3-VL 2B Instruct (GGUF Q4_K_M) con proiettore visivo Q8_0 viene elaborato da **llama.cpp + libmtmd compilati come libreria JNI ARM64**. Non devi inviare immagini a server esterni, a un PC o a Termux. Le vecchie API HTTP di analisi fotografica sono disabilitate.

In `EDIT / AULA` usa:
- **SCARICA QWEN 2B (1,55 GB)** — download esplicito degli unici due modelli necessari da Hugging Face, ripresa su 4G/5G o Wi-Fi, hash SHA-256 verificati
- **IMPORTA GGUF DA DOWNLOAD** — scegli manualmente entrambi i file ufficiali dalla cartella Download del Pixel
- **SOSPENDI DOWNLOAD** — interrompe senza perdere file parziali
- **ANALIZZA QWEN OFFLINE** — passa direttamente i pixel al motore JNI e visualizza una critica con soggetto, composizione, luce e miglioramenti, senza HTTP

Non esiste più una configurazione di server locale obbligatoria per questa modalità. L'interfaccia continua a non attribuire voti artistici dai soli istogrammi o da MobileNet. In caso di errore del runtime o descrizione insufficiente, il voto rimane **non valutabile**.

**Guida privacy, pesi, dimensioni, memoria e limiti:** [docs/AmpyEOS_Rev07_Private_Vision.md](docs/AmpyEOS_Rev07_Private_Vision.md)

**Stato:** l'APK viene compilato da GitHub Actions anche con il codice nativo llama.cpp. Inference reale, qualità delle valutazioni, consumo RAM e termico devono ancora essere testati sul Pixel 7 Pro.

## Rev0.6 · Basta voti artistici inventati

- **Eliminato 9/10 automatico** da luminosità, ombre o microcontrasto:
  ora quello è solo un indice **diagnostico tonale**, non una valutazione
  della qualità della foto, del soggetto o dell'intenzione.
- **Qwen3-VL** nell'editor: analizza i pixel dell'intera fotografia
  con un modello realmente multimodale, chiede soggetti, relazioni,
  composizione, luce, colore, problemi e verdetto motivato.
- **Fallimento esplicito** quando il modello locale non è avviato,
  non è visivo o restituisce un'analisi insufficiente: in questo caso
  il voto resta **Non valutata** e non viene simulato.
- Confronto delle versioni elaborate: `AutoMasterEngine` usa soltanto
  indicatori di resa tecnica e non pubblica più voti 1–10 fittizi.
- **Qwen-VL eseguito sul Pixel in Termux** con un vero modello GGUF
  + proiettore visivo mmproj e un server locale `127.0.0.1:8080`.
  La fotografia non viene inviata su Internet.
- **MUSIQ tramite ONNX Runtime**: modello appreso per qualità percettiva
  sperimentale, **separato** dal significato fotografico; serve importare
  i file modello e verificarne la compatibilità.
- [Setup Qwen-VL e limitazioni](docs/Qwen3-VL-setup.md).
  [Script Termux](scripts/ampyeos_qwen_termux.sh).

**Stato:** APK e client potranno essere compilati in CI; il runtime
Qwen-VL non è incluso nell'APK e non è stato ancora verificato
fisicamente sul Pixel. Il confronto artistico non deve essere dichiarato
affidabile senza una prova sulla scena reale.

## Rev0.6 — niente più voti fotografici inventati

La vecchia app iniziava da 90/100 e trasformava un indice di luminosità/contrasto in 9/10 anche quando la foto non aveva soggetto o senso compositivo. Questo comportamento è stato eliminato: gli indici `PhotoAnalyzer` e `AutoMasterEngine` sono esplicitamente **tecnici**, non voti artistici.

Sono ora presenti due percorsi indipendenti:
- **Qwen3-VL 2B Instruct via `llama.cpp` locale:** il pulsante **ANALIZZA SCENA CON QWEN-VL** nell'editor parla realmente della foto usando un modello multimodale, con descrizione di oggetti, composizione, criticità, suggerimenti e incertezza. **Non è incluso un runtime Qwen nell'APK**: richiede Qwen3-VL + proiettore `mmproj` caricati in un server sullo stesso Pixel tramite Termux, raggiungibile a `127.0.0.1:8080`. La porta è configurabile. Quando il modello non risponde, il voto rimane non valutabile.
- **MUSIQ con ONNX Runtime 1.31.1:** tramite **IMPORTA MUSIQ ONNX** carica da Download `musiq_model.onnx` e `musiq_model.onnx.data` dal repository `86Cao/IQA-ONNX-Models`; premi **VALUTA QUALITÀ MUSIQ** per calcolare un punteggio appreso di qualità percettiva, senza confonderlo con merito artistico. L'APK non include i pesi di MUSIQ.

L'analisi ML Kit e MobileNet rimane utile per riconoscimento di categorie, non per voti estetici. Il maestro della Virtual Classroom mostra ora il verdetto Qwen solo quando c'è stata un'analisi visiva reale.

**Guida completa:** [docs/AmpyEOS_Rev06_RealVision.md](docs/AmpyEOS_Rev06_RealVision.md)

> Nota sui test: compilazione e caricamento APK verificati in GitHub Actions. L'inferenza MUSIQ con i pesi importati e l'avvio di Qwen-VL su Pixel 7 Pro reale non sono ancora verificati in hardware. Qwen e MUSIQ possono comunque sbagliare: i loro risultati sono stime, non misure oggettive di valore artistico.

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
