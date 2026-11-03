# AmpyEOS v0.8.4 — Download Qwen reale in background e test caricamento GGUF

## Problemi verificati nel codice precedente

1. Il trasferimento dei modelli era affidato a QwenModelManager costruito dentro EditorActivity. EditorActivity.onDestroy() chiamava QwenModelManager.close(), che interrompeva il worker del download. Chiudendo o sostituendo la schermata, il trasferimento veniva annullato.
2. L'interfaccia non mostrava né i singoli file scritti in Download né la dimensione dei parziali .part. Il messaggio generico "modello non disponibile" non distingue un download in corso da un file non accessibile.
3. La presenza di un content:// URI dopo IMPORTA GGUF non garantiva che l'Android provider restituisse un file realmente seekable e mappabile da llama.cpp. In questi casi il modello era segnato come pronto ma l'inizializzazione JNI falliva.

## Novità implementate

- **QwenDownloadService** è un vero foreground service Android con tipo **dataSync**, notifiche permanenti, progresso in MiB/% e pulsante SOSPENDI. Non appartiene all'Activity: navigando in altre app o spegnendo lo schermo non viene chiuso da EditorActivity.onDestroy().
- Il servizio viene avviato **esplicitamente con AVVIA IN BACKGROUND**, e scarica soltanto i file del modello ufficiale, mai immagini dell'utente.
- I file sono visibili in **Memoria interna > Download > AmpyEOS > Qwen3-VL** tramite MediaStore.Downloads:
  - Qwen3VL-2B-Instruct-Q4_K_M.gguf — 1.107.409.952 byte
  - mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf — 445.053.216 byte
- Fino alla verifica SHA-256 l'estensione è **.gguf.part**. Se interrompi il trasferimento, i byte già scaricati restano sul telefono; alla successiva pressione di AVVIA il server viene interrogato con **HTTP Range** per riprendere il download.
- **FILE QWEN IN DOWNLOAD** mostra se i due GGUF finali e i due .part esistono, i MiB effettivamente presenti e gli errori di MediaStore. L'app non confonde più i modelli non visibili con quelli cancellati.
- **TEST CARICAMENTO QWEN** apre il modello e il proiettore tramite Android ParcelFileDescriptor, legge dimensioni fisiche (fstat) e header GGUF (pread), verificando che il provider offra un vero file locale leggibile casualmente; segnala un eventuale provider cloud non compatibile. Non è ancora un test dell'intera inferenza Qwen, ma identifica immediatamente problemi di file incompleto o descriptor.
- EditorActivity osserva lo stato del servizio tramite SharedPreferences: un avanzamento non viene perso se ricrei l'interfaccia. Quando il servizio conclude, la UI aggiorna i descrittori del modello.
- SHA-256 fissati alla sorgente ufficiale Hugging Face Qwen. I modelli validi già scaricati non vengono riscaricati. Non esiste un server remoto fotografico.
- **Qualità immagine:** nessuna riduzione della risoluzione fotografica in questo intervento. Qwen advanced conserva i pixel disponibili nella bitmap di lavoro.

## Limiti Android

Su Android 15+ il foreground service dataSync ha un limite totale di **6 ore per 24 ore** in background. Un'app non può promettere download eterni o immunità dai risparmi energetici o da kill espliciti dell'utente. Se il sistema interrompe il trasferimento, i file .part restano su Download per ripresa manuale.

La notifica può richiedere il permesso POST_NOTIFICATIONS su Android 13+: AmpyEOS lo richiede quando inizi il trasferimento. Se lo neghi, Android può ridurre la visibilità della notifica, pur mantenendo il servizio foreground nelle condizioni supportate.

## Test sul Pixel 7 Pro

1. Senza reinstallare o eliminare l'app precedente, aggiorna v0.8.4 se la firma APK coincide.
2. Apri STUDIO PRO > MODELLI IA > FILE QWEN IN DOWNLOAD. Verifica i due nomi o i file .part. Se già esistono, **non** premere SCARICA: usa IMPORTA GGUF DA DOWNLOAD.
3. Premi TEST CARICAMENTO QWEN. Con entrambi i file completi deve indicare GGUF e mmproj leggibili. Se non sono riconosciuti, usa IMPORTA GGUF DA DOWNLOAD due volte.
4. Per un file mancante, premi SCARICA QWEN SOLO SE MANCA > AVVIA IN BACKGROUND e autorizza le notifiche.
5. Esci dalla schermata o spegni lo schermo. Verifica lo stato dalla notifica di Android. Il servizio deve continuare indipendentemente dall'editor, nei limiti delle restrizioni di sistema.
6. Apri Files > Download > AmpyEOS > Qwen3-VL: visualizza i parziali .gguf.part durante il trasferimento e i file .gguf dopo verifica e rinomina.
7. Avvia QWEN PROFONDO quando i due GGUF sono pronti. L'analisi non deve confondere un problema di file con l'eventuale lentezza del modello.

## Verifica

La CI compila Java, C++ e l'APK. **Non** prova direttamente MediaStore, Service e JNI su un Pixel fisico: sono necessarie le prove reali elencate sopra per confermare il comportamento effettivo.
