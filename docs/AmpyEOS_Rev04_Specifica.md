# AmpyEOS Rev0.4 — Specifica operativa, tecnica e didattica

**Obiettivo:** trasformare il Pixel 7 Pro in un assistente fotografico che osserva la scena, spiega le scelte, prepara più elaborazioni locali di una fotografia, guida la composizione, permette modifiche manuali e salva sempre una copia indipendente dall'originale. L'utilizzo della Canon EOS R6 Mark II tramite CCAPI rimane sperimentale e richiede l'attivazione sulla fotocamera.

## Architettura

1. `MainActivity` — Pixel Live CameraX e Canon Live CCAPI; overlay animato e suggerimenti.
2. `PhotoAnalyzer` — istogramma, luminanza, clipping di preview, contrasto e dettaglio.
3. `SubjectAi` — ML Kit **on-device**: riconoscimento limitato di volti e categorie generiche di oggetti; non è una comprensione semantica universale.
4. `VisualInspector` — mappa dei dettagli/contorni 8×6, baricentro di energia, distribuzione destra-sinistra, vicinanza geometrica ai terzi, ricerca di strutture quasi orizzontali. **Non** è una vera mappa dei movimenti oculari né una diagnosi certa dell'orizzonte.
5. `VisualInspectorOverlay` — mostra i dati sull'immagine: mappa giallo/arancio, linee turchesi dei terzi, ritaglio giallo, soggetto ML Kit verde, candidata linea viola.
6. `PhotoReview` — valutazione e spiegazione del JPEG.
7. `AutoMasterEngine` — nove proposte non generative; confronto numerico delle metriche; mantiene l'originale se il beneficio stimato non è significativo.
8. `AutoOptimizer` — AutoTone moderato, AutoStraighten prudente, AutoResize.
9. `ColorLooks` — preview/rendering in naturale, B/N, B/N forte, vintage, cinema, morbido, vividi, matte.
10. `EditorActivity`, `BeforeAfterView`, `CropView` — editor, confronto e anteprima; `LessonOverlay` — animazione aula; Android TextToSpeech — lezione vocale italiana.

## Schermate

### A. Live Camera / Pixel

- **PIXEL LIVE:** streaming CameraX con consigli tecnici e AI su device.
- **CANON WIFI:** connessione CCAPI se attivata; Live View JPEG disponibile solo quando il firmware espone gli endpoint previsti.
- **CERCA WIFI:** ricerca SSDP/UPnP su LAN fidata.
- **BLE SCAN:** scoperta, non pairing/controllo BLE.
- **AMPYLINK:** scorciatoia all'app indipendente se presente.
- **STILE:** AUTO, RITRATTO, PAESAGGIO, STREET, NOTTE, MACRO.
- **SCATTA PIXEL:** salva JPEG in `Pictures/AmpyEOS`, offre ingresso alla Classroom.
- **SCATTA CANON:** solo con utente e funzione CCAPI disponibile.
- **REC CANON:** comando remoto sperimentale soltanto se l'endpoint viene pubblicato.
- **EDIT / AULA:** entra nell'editor e nella classe fotografica.
- **CLASSIFICA FOTO:** classifica fino a 15 foto locali con metrica tecnica, senza asserire una classifica artistica.
- **AUTO COMPOSER:** attiva/disattiva il rettangolo di suggerimento live.
- **AUTO LOOK:** attiva/disattiva i filtri live suggeriti.
- **VOCE MAESTRO:** lettura delle raccomandazioni, senza cloud.
- **DIAGNOSTICA:** funzioni CCAPI pubblicate, stato e latenza quando disponibili.

### B. Color Lab / Editor

- **APRI FOTO:** Android Storage Access Framework; ingresso JPEG/PNG gestiti dal decoder Android; RAW non garantito.
- **SCEGLI MIGLIORE:** classifica dei file selezionati.
- **MASTER AI AUTO:** nove varianti di colore/ritaglio, scelta secondo euristiche.
- **AUTO PRO COMPLETO:** variante, raddrizzamento solo se candidato affidabile, compressione tonale prudente e classe narrata automatica.
- **VERSIONI AI:** sceglie una delle versioni renderizzate, senza originale sovrascritto.
- **AUTO COMPONI:** ritaglio salienza e look; l'algoritmo non vede una storia o un'intenzione estetica.
- **AUTO RADDRIZZA:** cerca una linea quasi orizzontale. Se la stima è debole, **non applica** la rotazione.
- **AUTO TONI:** regola con delicatezza ombre e punti molto luminosi dei pixel **già presenti**.
- **AUTO RESIZE:** scelta tra originale, web max 2048 px, 1080×1080, 1080×1350, 1080×1920 e 1920×1080. Scelta tra riempi/crop e adatta con bande; è un'operazione della copia di lavoro.
- **ANALISI VISIVA:** rapporto di dati, limiti, interpretazioni e suggerimenti; possibilità di mostrare i dati sopra la foto e ascoltare una spiegazione.
- **OVERLAY TECNICO:** mostra/nasconde annotazioni e mappe.
- **RITAGLIA** + **APPLICA CROP:** trascinamento con rapporti libero/1:1/4:5/3:2/16:9.
- **RUOTA 90°**, **SPECCHIO:** interventi manuali.
- **PRIMA / DOPO:** alterna le due immagini.
- **SPLIT SCORREVOLE:** divisore drag originale/proposta.
- **SPIEGA:** confronta punteggio tecnico, anomalie, modifiche e istruzioni di ripresa.
- **SALVA COPIA:** JPEG distinto dall'originale in `Pictures/AmpyEOS`.
- **RIPRISTINA:** torna all'immagine di partenza.
- **LOOK** e tre cursori **ESPOSIZIONE SIMULATA**, **CONTRASTO**, **SATURAZIONE**: modifiche non distruttive fino a esportazione.

### C. Virtual Classroom

- **◀ INDIETRO**, **AVANTI ▶** — sposta la lezione.
- **PLAY / PAUSA** — controlla annotazioni animate.
- **VOCE ON/OFF** — TextToSpeech italiano.
- **LEZIONE AUTOMATICA** — cinque scene animate/narrate e confronto alla fine.

**Lezione 1: osservazione:** racconta cosa è effettivamente rilevato (presenza di volto/oggetto, e/o dettagli); dichiara quando non sa identificare un soggetto.

**Lezione 2: luce e tecnica:** ombre, alte luci, luminanza, texture. Il colore rosso/blu/bianco della mappa non equivale a una misura RAW del sensore.

**Lezione 3: composizione:** mostra e motiva ritaglio, geometria e distribuzione dei dettagli, senza assumere che la regola dei terzi sia obbligatoria.

**Lezione 4: colore:** riproduce i cambiamenti dei profili e descrive la differenza.

**Lezione 5: verdetto:** confronto interattivo, motivi di miglioramento, cosa non si può recuperare e come ripetere la fotografia.

## Punteggi: formula verificabile, non giudizio artistico

- **Tecnico preview (0–100):** `PhotoAnalyzer` parte da 90, penalizza luminosità estrema, clipping molto elevato, ombre chiuse, microcontrasto basso e contrasto debole. Non è una rete estetica addestrata.
- **Distribuzione dei contorni (0–100):** `balanceScore = round(100 * (1 - |L-R|/(L+R)))`. È una misura di equilibrio di energia del gradiente, non il bilanciamento artistico del soggetto.
- **Vicinanza del centro dettagli ai terzi (0–100):** `thirdsScore = round(100 * max(0, 1 - distanzaMinima/.40))` con coordinate normalizzate, distanza euclidea verso quattro intersezioni dei terzi.
- **Indice compositivo indicativo (0–100):** `round(.60*balanceScore+.40*thirdsScore)`. È mostrato **separato** dal punteggio tecnico, proprio per non confonderli.
- **Varianti:** `AutoMasterEngine.quality` parte dal punteggio tecnico, penalizza maggior clipping/ombre, ritaglio che perde contesto e stilizzazione eccessiva. L'originale può vincere.
- **Voto 1–10:** visualizzazione arrotondata della stima tecnica. Un punteggio 10/10 non significa che un fotografo giudicherebbe quella versione migliore, né dà accesso al significato della scena.

## Flusso completo

1. Scatta con Pixel oppure importa una fotografia.
2. Decodifica locale, analisi tecnica, analisi della distribuzione spaziale, ML Kit per volto/oggetto se disponibile.
3. L'utente apre **ANALISI VISIVA**, visualizza evidenziazioni e può ascoltare la diagnosi.
4. Con **AUTO PRO COMPLETO**, l'app elabora più varianti, sceglie con metrica esplicita, può raddrizzare una struttura se affidabile, applica lievi compensazioni tonali.
5. La **Virtual Classroom** anima osservazione, luce, ritaglio, look e verdetto.
6. L'utente può modificare crop/filtri/resizing, verificare lo split e tornare all'originale.
7. **SALVA COPIA** genera un file JPEG indipendente in galleria.

## Limiti dichiarati

- Non esegue Python/Lua sulla EOS R6 II e non modifica il firmware Canon.
- Non è un motore AI generativo di immagini e non sposta fisicamente la fotocamera.
- AutoResize può richiedere aumento del numero di pixel per un formato social; questo non inventa dettagli reali.
- In Rev0.4 l'editor importa per efficienza un'immagine campionata fino a circa 2300 px per lato lungo: il JPEG esportato non equivale al RAW o al JPEG originale a piena risoluzione. In una versione successiva è previsto rendering da sorgente piena risoluzione con pipeline a tile.
- La ricerca di una linea orizzontale è euristica; non corregge in modo affidabile la prospettiva.
- Non esiste una mappa certificata dei punti di attenzione dell'occhio umano: la heatmap mostra **energia dei contorni**.
- La voce è TTS locale Android se un motore italiano è installato e disponibile.
- La compatibilità della connessione Canon dipende dai metodi CCAPI reali; il test in CI non equivale a test sul corpo fisico.

## Prove di accettazione

1. `gradle :app:assembleDebug` riesce e carica artifact APK su GitHub Actions.
2. Pixel Live apre la camera con permessi espliciti; nessun crash.
3. Editor importa JPG/PNG, non altera file originali.
4. Overlay tecnico corrisponde ai pixel presentati senza distorsioni.
5. AutoStraighten non ruota quando confidenza è bassa.
6. AutoResize produce esattamente le dimensioni richieste per i preset social.
7. Auto Pro conserva originale, stampa un rapporto verificabile e permette prima/dopo.
8. Lezione con TTS spiega anche limiti e ripresa successiva.
9. Nessuna operazione sul firmware EOS e nessuno scatto remoto senza richiesta utente.
10. Per passare da build riuscita a "testato", eseguire prove su Pixel 7 Pro reale e Canon EOS R6 Mark II.
