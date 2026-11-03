# AmpyEOS Rev0.6 — valutazione fotografica basata su reti reali

## Il problema risolto

Rev0.5.3 convertiva un algoritmo di luminosità e microcontrasto in una valutazione da 1 a 10, partendo da 90/100. Per questo anche fotografie prive di un soggetto riconoscibile potevano ricevere 9/10. **Questa valutazione non aveva validazione estetica e non era affidabile.**

In Rev0.6, il voto di fotografia generale è **NON VALUTABILE** finché non è disponibile una visione semantica reale. L'indice dei pixel rimane, ma viene indicato esplicitamente come indice tecnico e non come merito fotografico.

## Due modelli per due domande differenti

### MUSIQ — qualità percettiva / immagine, ONNX Runtime 1.31.1

MUSIQ è un modello di Image Quality Assessment (IQA) che apprende da valutazioni umane delle fotografie. Non è lo stesso semplice calcolo dell'istogramma. AmpyEOS accetta il modello **MUSIQ convertito ONNX (224×224, single-scale)**, tramite ONNX Runtime nativo Android; legge i file da memoria privata dell'app.

Fonte: https://huggingface.co/86Cao/IQA-ONNX-Models

Scarica **entrambi** i file dal repository:
- `musiq_model.onnx`
- `musiq_model.onnx.data`

Nell'editor usa **IMPORTA MUSIQ ONNX** due volte (un file alla volta) e poi **VALUTA QUALITÀ MUSIQ** su una fotografia. La normalizzazione applicata è `(pixel/255 - 0.5)/0.5`, input `float32[1,3,224,224]` in ordine RGB, output scalare atteso nell'intervallo 0–100. Il modello è importato su richiesta; non è incluso nell'APK. L'app mostra esplicitamente errori di file incompleto o modello incompatibile. Non usare file ONNX di provenienza sconosciuta.

**Limitazione fondamentale:** anche MUSIQ può valutare positivamente una foto tecnicamente pulita ma banale. Il punteggio MUSIQ va interpretato come qualità percettiva apprendibile, **non voto sul significato della foto**.

### Qwen3-VL 2B Instruct — comprensione della fotografia, llama.cpp / GGUF

Qwen3-VL è multimodale: osserva gli elementi visibili e può discutere soggetto, composizione, linee, gerarchia visiva, luce e motivi per cui una fotografia potrebbe essere o non essere significativa.

Fonte modello: https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct-GGUF
Motore Android d'esempio con llama.cpp: https://github.com/arm-education/ai-portal-android-app-image-text-to-text

È necessario **un modello Q4_K_M** e un **projector `mmproj` Q8_0** corrispondente:
- `Qwen3VL-2B-Instruct-Q4_K_M.gguf` — circa 1.11 GB
- `mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf` — circa 445 MB

**Qwen3-0.6B-Q4_K_M.gguf solo testuale NON può esaminare fotografie.**

Questa revisione ha un connettore Android a un server vision *sullo stesso Pixel*, OpenAI-compatible, sulla porta locale `127.0.0.1:8080`. Il modello non è integrato nell'APK: serve un'istanza `llama-server` con Qwen3-VL e `mmproj`, ad esempio avviata da Termux. Non vengono inviate automaticamente foto a server remoti.

### Esempio comando quando llama-server è già disponibile nel terminale sul Pixel

```bash
llama-server \
  -m "$HOME/ampyeos-models/Qwen3VL-2B-Instruct-Q4_K_M.gguf" \
  --mmproj "$HOME/ampyeos-models/mmproj-Qwen3VL-2B-Instruct-Q8_0.gguf" \
  --host 127.0.0.1 --port 8080 \
  --ctx-size 4096 --threads 4 --no-mmproj-offload
```

Verifica dall'interfaccia Android/Termux: `curl http://127.0.0.1:8080/v1/models`.

**Attenzione:** il motore deve essere un'installazione di llama.cpp recente e compilata con supporto multimodale; l'APK AmpyEOS non installa o compila automaticamente `llama-server`. L'inferenza CPU di Qwen-VL può essere lenta, calda e richiedere parecchia RAM sul Pixel 7 Pro. Prova inizialmente con una sola foto, senza altre app pesanti.

## Come leggere un verdetto

| Colonna | Provenienza | Cosa significa |
| --- | --- | --- |
| Indice pixel | PhotoAnalyzer | luminosità, dettaglio, contrasto; **non** voto artistico |
| Qualità MUSIQ /100 | rete neurale ONNX | qualità percettiva stimata sulla foto |
| Critica Qwen-VL | modello visivo | descrizione, soggetto, scelte compositive e motivazione |
| Voto fotografia /10 | solo se Qwen-VL identifica la scena e produce ragioni | giudizio interpretativo e discutibile, non oggettivo |

Esempio: un'immagine del pavimento perfettamente esposta può ricevere un indice pixel alto e anche una qualità MUSIQ discreta, ma **non deve ottenere automaticamente un voto fotografico elevato**.

## Sicurezza

- Foto sulla macchina locale; Qwen-VL viene invocato solo con il server loopback 127.0.0.1 in questa revisione.
- I modelli scaricati/importati devono provenire dai repository di riferimento; non trattare file arbitrari come sicuri.
- I voti automatici basati su filtri e crop sono ritirati, sostituiti da confronto tecnico senza voto estetico.
- Un modello generativo può comunque interpretare male scene o attribuire significato inesistente; l'app deve sempre mostrare incertezze e spiegazioni.
- La compilazione GitHub Actions verifica il codice APK, ma non prova la qualità reale delle valutazioni o la compatibilità di ogni modello sul Pixel.

## Test suggeriti

Testare una serie comprendente: foto casuale senza soggetto chiaro, parete ben esposta, ritratto intenzionale, scena astratta geometrica, paesaggio e foto sfocata. Per ogni foto controllare che il voto non venga semplicemente dedotto dalla corretta esposizione; confrontare il racconto Qwen con oggetti realmente visibili. Confrontare diversi output del modello e verificare che ammetta incertezza.
