# AmpyEOS v0.8.1 — Qwen scaricato una volta sola

## Problema della v0.8.0

I modelli pesanti erano scritti in `getFilesDir()/ampyeos_qwen_vision/`. La cartella è privata: Android la conserva durante gli **aggiornamenti installati sopra la stessa applicazione**, ma la cancella con una **disinstallazione**, con l'eliminazione dei dati dell'app o con alcuni reset. Questo spiegava i download ripetuti quando si reinstallava l'APK.

## Soluzione in v0.8.1

La cartella **permanente visibile nel gestore file** è:

`Memoria interna / Download / AmpyEOS / Qwen3-VL /`

Contiene:
- `Qwen3VL-2B-Instruct-Q4_K_M.gguf`
- `mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf`

**Come funziona:** Android MediaStore.Downloads salva i modelli nell'archivio condiviso, senza permessi "tutti i file". I GGUF in questa cartella non vengono cancellati quando disinstalli AmpyEOS, a differenza della vecchia cartella privata. Quando apri l'editor, AmpyEOS verifica i file già presenti e li riutilizza **senza fare richieste a Hugging Face**, se completi e con SHA-256 corretti.

La verifica iniziale può richiedere tempo, perché rilegge i file per verificarne gli hash: questo **non è un download Internet**.

L'inferenza JNI di llama.cpp riceve due descrittori di file Android e usa i percorsi `/proc/self/fd/...` mentre i file sono aperti. Così non servono due copie da 1,55 GB in memoria privata: l'APK carica i pesi direttamente da Download.

## Aggiornamento dalla v0.8.0

1. **Installa v0.8.1 sopra la versione precedente, senza disinstallarla**, perché la vecchia copia privata potrebbe non essere ancora salvata in Download.
2. Alla prima apertura di Color Lab, l'app verifica i file privati e prova a copiarli automaticamente in `Download/AmpyEOS/Qwen3-VL`.
3. Se sul telefono manca spazio per la copia, la vecchia versione resta funzionante, ma vedrai l'avviso **NON DISINSTALLARE**. Libera almeno quanto necessario (fino a circa 1,2 GB temporanei) e premi **SALVA MODELLI IN DOWNLOAD** dalla sezione 07.
4. Quando la copia pubblica viene verificata, l'app può eliminare **la sola copia privata obsoleta**, risparmiando spazio. I file in Download non vengono eliminati automaticamente.

## Dopo disinstallazione/reinstallazione

I modelli in Download **rimangono sul telefono**, ma Android potrebbe trattarli come file creati dalla precedente installazione. In quel caso la nuova installazione non può sempre leggerli automaticamente a causa di Scoped Storage. Non serve riscaricarli:

1. Apri **MODELLI IA → IMPORTA GGUF DA DOWNLOAD**.
2. Seleziona `Qwen3VL-2B-Instruct-Q4_K_M.gguf` già presente in Download.
3. Ripeti con `mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf`.
4. Android concede l'accesso ai file selezionati e l'app lo conserva per gli avvii successivi.

Questa operazione **non copia 1,55 GB e non scarica nulla**: i file vengono letti, verificati e poi utilizzati tramite i descrittori Android. Non spostare o eliminare i GGUF collegati senza ricollegarli.

## Download interrotto

Un download nuovo crea nella cartella pubblica il file `.gguf.part`. Quando il server accetta HTTP Range, AmpyEOS continua dai byte già scaricati. Al completamento controlla la dimensione totale, SHA-256 e firma GGUF; solo dopo rinomina il file `.part` al nome definitivo `.gguf`.

I trasferimenti possono usare Wi-Fi o rete mobile **solo dopo un'azione esplicita**. Le foto non vengono inviate in Internet durante l'analisi.

## Casi di perdita reale

I file in Download **non** resistono alla cancellazione manuale della cartella, alla formattazione del telefono, all'eliminazione della memoria condivisa o ad altri interventi che cancellano i file pubblici. Dopo l'aggiornamento controlla la presenza dei due GGUF nel gestore file prima di eliminare la vecchia installazione.

## Test necessari sul Pixel 7 Pro

1. Aggiornamento v0.8.0 → v0.8.1 senza disinstallare, con modello già presente: salvataggio pubblico e SHA corretti, senza traffico dati.
2. Chiudi e riapri l'app: nessun download; Qwen pronto.
3. Avvia Qwen Offline: accesso ai GGUF tramite descrittori file con mapping nativo funzionante.
4. Interrompi un trasferimento: estensione `.part` conservata, ripresa corretta.
5. Disinstalla e reinstalla: i due file restano in Download; se Android ne blocca l'accesso, ricollegali con il selettore.
6. Confronta spazio occupato prima/dopo: eliminazione cache privata soltanto quando è stata verificata la copia pubblica.
7. Verifica che i file di foto o EXIF non siano mai parte delle richieste di download.

La build CI conferma soltanto compilazione Java/JNI e disponibilità APK; **non** dimostra da sola il corretto comportamento di MediaStore e llama.cpp con i file reali sul dispositivo.
