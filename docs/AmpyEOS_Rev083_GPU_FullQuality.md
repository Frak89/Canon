# AmpyEOS v0.8.3 — Accelerazione Vulkan, qualità fotografica e Qwen residente

## Motivazione

Nella v0.8.0 l'encoder multimodale Qwen3-VL del Pixel 7 Pro rimaneva occupato oltre **1.208 secondi**. Il percorso nativo aveva due colli di bottiglia: GPU esplicitamente disabilitata (`n_gpu_layers=0` e `mtmd.use_gpu=false`) e ricaricamento completo dei GGUF a ogni analisi.

La strategia della v0.8.3 **non riduce arbitrariamente la foto a 224/320 pixel** e non limita il budget di patch visive a 128–192. Il lavoro deve essere accelerato, non reso meno accurato tramite un'anteprima minuscola.

## Modifiche

- **Vulkan su Android arm64**, destinato alla GPU Mali-G710 del Pixel 7 Pro: backend ggml-vulkan compilato da sorgente llama.cpp bloccato a commit specifico, con glslc/SPIR-V e header Vulkan-Hpp nel workflow. Se il driver o l'inizializzazione GPU falliscono, l'engine registra il problema e ritenta con CPU per evitare un crash silenzioso.
- **Offload di Qwen e proiettore:** `n_gpu_layers=99` quando la GPU Vulkan è presente; `mtmd_context_params.use_gpu=true` per il motore multimodale, non soltanto per il generatore di testo.
- **Cache nativa del modello:** conserva GGUF Qwen, contesto e mmproj in memoria dopo una valutazione riuscita; controlla identità delle due sorgenti tramite `stat()` e azzera la memoria KV fra foto indipendenti. Un comando di rilascio esplicito permette di liberare la RAM; il motore resta nell'app, senza server.
- **Qualità visiva preservata:** il livello Java non forza una fotografia a 320/448/640 pixel nella modalità approfondita; il parser MTMD usa i limiti nativi del modello anziché un tetto artificiale introdotto dall'app. L'immagine originale del telefono rimane intatta.
- **Diagnostica reale:** fasi di caricamento, GPU Vulkan o fallback CPU, dimensioni della bitmap ricevuta e tempo effettivo dell'encoder; nessuna percentuale inventata.
- **Annullamento / watchdog:** le elaborazioni CPU e GPU possono essere interrotte cooperativamente; un'operazione GPU lunga potrebbe richiedere ulteriore tempo prima di interrompersi. Il limite di sicurezza non è una riduzione di qualità, ma un errore esplicito in caso di mancato completamento.

## Distinzioni importanti

La cartella condivisa `Download/AmpyEOS/Qwen3-VL` conserva i file GGUF tra gli aggiornamenti. **Caricare il modello in memoria è diverso dallo scaricarlo da Internet**: la cache qui evita parte dei caricamenti ripetuti durante una stessa sessione dell'app.

La conversione JPEG iniziale dell'editor utilizza attualmente una bitmap di lavoro campionata per l'interfaccia. Questa revisione elimina l'ulteriore riduzione forzata dentro il modulo Qwen, **ma non equivale ancora a una pipeline completa di editing/export RAW a 24 megapixel**. La conservazione di tutti i pixel nella pipeline editor/RAW è un problema distinto che richiede un renderer full-resolution dedicato.

L'inferenza Qwen può restare costosa anche su Vulkan: memoria, driver della GPU e gestione delle patch dinamiche influenzano le prestazioni. **La compilazione GitHub non prova la velocità sull'hardware**.

## Verifiche da eseguire sul Pixel reale

1. Avvia Qwen3-VL e verifica che la diagnostica riporti **GPU Vulkan**; se indica **fallback CPU**, raccogli il messaggio di errore.
2. Prova prima uno scatto normale, poi un'immagine con dettagli piccoli e confronta i risultati. Registra il tempo di caricamento e dell'encoder.
3. Ripeti l'analisi con una seconda foto senza chiudere Studio Pro: dovrebbe comparire **modello già caricato** e non ricaricare 1,55 GB.
4. Prova FERMA ANALISI, l'uscita dall'editor, il monitoraggio della temperatura e del consumo RAM.
5. Confronta il soggetto Qwen con il contenuto reale; il modello non deve inventare dettagli né assegnare un 9/10 basandosi sul contrasto.
6. Conferma che nessuna foto sia inviata a server: i soli URL permessi dai percorsi AI sono per scaricare i modelli.

Il miglioramento prestazionale non viene dichiarato concluso finché non supera prove reali di latenza, precisione e stabilità sul Pixel 7 Pro.
