# AmpyEOS Rev0.7 — Qwen3-VL dentro l'APK Android, foto sempre sul Pixel

## Nuova architettura

- **Qwen3-VL 2B Instruct** multimodale, con file GGUF ufficiali di [Qwen](https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct-GGUF).
- **Motore di inferenza nativo:** `llama.cpp` + `libmtmd`, compilati in una libreria JNI `libampyeos-vision.so` ARM64 inclusa nell'APK. L'elaborazione delle foto avviene **all'interno del processo AmpyEOS**, senza chiamate HTTP, server web, cloud, computer remoto o Termux.
- **Modelli scaricati su richiesta** nell'archivio privato dell'app. Necessari:
  - `Qwen3VL-2B-Instruct-Q4_K_M.gguf` — 1.107.409.952 byte; SHA-256 `089d75c52f4b7ffc56ba998ffc50aae89fcafc755f9e7208aacca281dca6c2ae`
  - `mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf` — 445.053.216 byte; SHA-256 `f9a68fabba69c3b81e153367b2c7521030b0fa8bb0de400c9599c8e6725f9c82`
- Download cumulativo circa **1,55 GB** su Wi-Fi o 4G/5G, **solo dopo pressione esplicita dell'utente**, con trasferimenti parziali riprendibili e verifica SHA-256. Margine di spazio libero richiesto. Un file non corrispondente viene rifiutato.
- Alternativa **IMPORTA GGUF DA DOWNLOAD**: usa il selettore Android `ACTION_OPEN_DOCUMENT` per installare file precedentemente salvati, uno alla volta. Le foto non vengono mai incluse nei download.
- La build GitHub scarica il **sorgente llama.cpp** da un commit verificato con SHA-256 prima di compilarlo con NDK. Il codice llama.cpp è usato secondo licenza MIT.

## Dove sono i comandi

In **EDIT / AULA**:

- **SCARICA QWEN 2B (1,55 GB)**: mostra un avviso di consumo dati e scarica i due file ufficiali sul Pixel
- **IMPORTA GGUF DA DOWNLOAD**: installazione manuale dei due file GGUF ufficiali
- **SOSPENDI DOWNLOAD**: interrompe senza eliminare quanto già scaricato
- **ANALIZZA QWEN OFFLINE**: produce una spiegazione basata sull'immagine senza rete

Il pulsante di analisi è utilizzabile soltanto quando hai aperto una foto. Se il GGUF non è ancora installato, viene mostrato l'errore e non viene inventato un voto.

## Privacy verificabile

`NativeQwenVision` trasforma localmente la foto in una matrice di pixel e passa i dati alla libreria JNI. `ampyeos_vision.cpp` non usa socket, HTTP o altri trasporti di rete. La vecchia analisi fotografica via server in `LocalVisionReviewer.inspect` e `VisionCritic.analyze` è disabilitata esplicitamente.

**Nota importante:** AmpyEOS mantiene comunque il permesso Internet per scaricare **modelli AI** e comunicare con la fotocamera Canon via CCAPI. Il principio «foto non inviate» vale per i percorsi di analisi fotografica nativi; strumenti di rete associati ad altre funzioni non devono ricevere le immagini, salvo l'uso intenzionale della Canon su Wi-Fi locale.

## Qualità della valutazione

Il modello descrive cosa vede, soggetto, elementi visibili, composizione, relazione tra oggetti, luce, colore, problemi fotografici e miglioramenti. Una formula di luminosità non deve diventare un voto estetico. Se le descrizioni non sono specifiche o i dati sono contraddittori, il voto viene dichiarato **non valutabile**.

Un modello multimodale di 2 miliardi di parametri è più capace di un semplice classificatore ImageNet, ma può comunque sbagliare e produrre valutazioni discutibili. Non è una garanzia di fotografie 10/10.

## Vincoli hardware

Pixel 7 Pro: Android ARM64, inferenza principalmente CPU, 2 GB di file di pesi circa tra i due GGUF e strutture aggiuntive in memoria; lascia spazio libero e non avviare altre operazioni pesanti nello stesso momento. Le analisi possono richiedere un tempo significativo e scaldare il telefono. Un errore di memoria deve comparire chiaramente, senza crash silenziosi.

## Stato di validazione

La compilazione GitHub Actions verifica Java/C++ e il collegamento JNI, ma **non** misura la qualità delle spiegazioni su immagini reali e non dimostra da sola la compatibilità del modello sul Pixel. Verificare concretamente: download + SHA, inizializzazione nativa, memoria, temperatura, foto con soggetti comprensibili, immagini casuali, scene astratte, gestione dell'annullamento.
