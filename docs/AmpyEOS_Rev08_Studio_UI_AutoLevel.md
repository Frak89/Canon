# AmpyEOS Rev0.8 — Studio Pro, raddrizzamento automatico e analisi IA locale

## Obiettivo

Rev0.8 rende l'editor fotografico più usabile su Pixel 7 Pro e più accurato nel distinguere correzioni geometriche, indicatori tecnici e giudizi dell'IA. Non cambia il firmware della Canon EOS R6 Mark II.

## Interfaccia: sette sezioni espandibili

La vecchia barra con decine di pulsanti orizzontali è sostituita da pannelli verticali con pulsanti in **due colonne**, dimensionati allo schermo. Le sezioni rimangono cliccabili anche quando chiuse:

1. **SCATTO & RADDRIZZAMENTO:** APRI FOTO, AUTO RADDRIZZA APERTURA ON/OFF, RADDRIZZA ADESSO, SALVA COPIA, RIPRISTINA ORIGINALE, ANALISI VISIVA
2. **MAESTRO IA:** ANALISI RAPIDA QWEN, ANALISI PROFONDA QWEN, CONFRONTA ORIGINALE/EDIT, FERMA ANALISI, VALUTA QUALITÀ MUSIQ, DIAGNOSTICA QWEN
3. **RISULTATO & SPIEGAZIONE:** pannello inizialmente chiuso per non coprire i comandi; si apre automaticamente quando il modello Qwen produce un risultato
4. **EDITOR · COMPOSIZIONE & ESPORTAZIONE:** varianti, auto crop, taglio, resize, specchio, rotazione, confronto, guide e **CORREZIONE FINE ±10°**
5. **COLOR LAB:** look, esposizione simulata, contrasto e saturazione; espandibile a scelta
6. **VIRTUAL CLASSROOM:** passaggi narrati, TTS italiano, animazioni e lezione automatica, aperta solo su richiesta
7. **MODELLI IA:** installazione, download e importazione GGUF Qwen3-VL, ONNX MUSIQ, MobileNet e impostazioni rete

Il tema rispetta gli stati già richiesti: **menta chiaro = toggle ON; ardesia scuro = OFF; blu = azione; scuro attenuato = non disponibile**. Anche il testo indica gli stati ON/OFF per accessibilità.

## Auto raddrizzamento all'apertura

La preferenza **AUTO RADDRIZZA APERTURA è ON per impostazione predefinita**, persistente nelle impostazioni Android di AmpyEOS.

La pipeline di apertura:

1. Decodifica il JPEG/PNG campionato per anteprima.
2. Se presente, legge EXIF Orientation (rotazione 90/180/270°, mirror, transpose) e applica l'orientamento corretto alla copia in memoria.
3. `AutoLevel` valuta i gradienti della foto in 256×192 pixel e cerca famiglie di linee vicine a verticale e orizzontale.
4. Per proporre un roll, richiede una concentrazione di orientamenti coerenti in **almeno tre regioni** della foto, confidenza minima 62%, inclinazione da 0,75° a 5°.
5. Se coerente, `AutoOptimizer.rotateAndCrop` compensa l'angolo e applica un crop di copertura per evitare triangoli neri.
6. Se non coerente, **non** altera la foto: la prospettiva di un ponte, tetto diagonale o corrimano può essere intenzionale.
7. La copia originale decodificata resta accessibile tramite MOSTRA ORIGINALE e RIPRISTINA, e l'immagine sorgente nel telefono non viene modificata.

Il rilevamento è un algoritmo fotografico, **non riconoscimento affidabile di un vero orizzonte**. L'utente può premere RADDRIZZA ADESSO o CORREZIONE FINE per confrontare e intervenire.

### Correzione fine manuale

**CORREZIONE FINE ±10°** apre una finestra con anteprima e slider ogni 0,1°. La preview è a risoluzione ridotta; la correzione accettata viene renderizzata in background sulla copia di lavoro. Il crop evita bordi neri. ANNULLA conserva la foto precedente.

## Foto pulita alla prima apertura

La vecchia `showStep(0)` faceva apparire immediatamente la **griglia turchese della lezione anche quando l'utente non aveva avviato Classroom**. Ora il caricamento nasconde le guide, lascia il look NATURALE e conserva soltanto la correzione geometrica esplicitamente abilitata. Le guide appaiono solo quando si attiva la lezione o OVERLAY TECNICO.

## Qwen: rapido, profondo, confronto

Qwen3-VL 2B Instruct e relativo mmproj rimangono **locali al Pixel**.

- **Analisi rapida:** preview max 448 px sul lato lungo, fino a 520 token generati
- **Analisi profonda:** preview max 640 px sul lato lungo, fino a 1100 token
- **Confronto:** canvas affiancato ORIGINALE/PROPOSTA, preview 768 px, fino a 740 token; il modello deve descrivere vantaggi, svantaggi, eventuale peggioramento, preferenza e incertezze, senza inventare punteggi estetici

La richiesta Qwen usa una coda di lavoro distinta dai render dell'editor per preservarne la reattività. Un avviso di memoria Android o severa temperatura impedisce l'avvio nelle condizioni peggiori.

### Diagnostica

Pulsante **DIAGNOSTICA QWEN**: mostra fasi reali, tempo di inferenza, RAM disponibile, livello termico Android e ragione di eventuale errore. Il log non registra pixel, foto, EXIF, nomi dei file, richieste né risposte del modello.

## Validazione e limiti

- La compilazione APK Android verifica Java, libreria JNI e script nativi, ma **non verifica da sola che la qualità dei consigli di Qwen sia buona**.
- Il sistema non promette di raddrizzare qualunque scena: se mancano riferimenti geometrici affidabili, rimane invariata e la correzione fine permette l'intervento manuale.
- L'import foto usa bitmap di lavoro campionati a circa 2300 px sul lato lungo. Le esportazioni sono derivate da questa bitmap, **non recuperano automaticamente la risoluzione originale di un RAW Canon**.
- Una scena volutamente inclinata, un ponte diagonale o una composizione astratta non devono essere penalizzati per l'assenza di un orizzonte.
- Le foto non sono inviate a server per l'analisi. Internet rimane disponibile per il download esplicito dei modelli e CCAPI Canon.

### Prove su Pixel 7 Pro richieste

Aprire foto JPEG con diversi EXIF Orientation, un paesaggio con orizzonte inclinato di circa 2°, una foto con ponte diagonale, una foto con pareti simmetriche, e una scena priva di linee. Verificare che la griglia non compaia all'apertura, i pulsanti siano visibili e il raddrizzamento sia reversibile. Dopo la build, testare separatamente Qwen e MUSIQ con file di modello installati.
