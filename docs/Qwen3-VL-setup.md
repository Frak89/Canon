# AmpyEOS — Verdetto fotografico con un vero modello visivo

## Perché la vecchia valutazione 9/10 era sbagliata

L'indice `PhotoAnalyzer` veniva calcolato partendo da 90 punti e
penalizzando ombre, alte luci, contrasto e dettaglio. **Non riconosceva cosa
rappresentasse la foto**; non poteva distinguere una fotografia intenzionale
da uno scatto accidentale. Il valore oggi rimane soltanto una diagnostica
di esposizione/contorni e NON viene più convertito in una valutazione 1–10.

Il classificatore predefinito ML Kit distingue cinque categorie generiche;
MobileNetV2/ImageNet classifica una classe dominante ma non comprende la
relazione fra soggetti, la narrativa né la composizione.

## Nuova modalità: Qwen-VL — analisi semantica visiva

L'editor contiene `CONFIGURA QWEN-VL` e `ANALIZZA SCENA CON QWEN-VL`.
Il modello multimodale deve descrivere oggetti visibili, soggetto, scena,
composizione, luce, palette, fattori positivi, problemi, modifiche e
come rifare lo scatto. Quando l'analisi è incompleta o incerta, **nessun
voto** viene pubblicato. Eventuali voti del modello restano interpretativi,
NON dimostrati oggettivamente. Le istruzioni penalizzano espressamente voti
9/10 basati solo su una foto esposta correttamente.

L'app non carica le foto su Internet: l'endpoint è vincolato al telefono
`http://127.0.0.1:8080` (porta configurabile); le immagini passano
soltanto al processo locale `llama-server`.

> **Stato reale:** il client Qwen-VL e la UI sono implementati, ma **llama.cpp
> e i modelli non sono compresi nell'APK**. Occorre avviare il server
> multimodale sul Pixel, per esempio con Termux. Non definire questa
> funzionalità «offline pronta all'uso» prima dei test sul dispositivo.

## Setup Termux sul Pixel 7 Pro

Servono diversi gigabyte di spazio libero per software, modelli e build.
Il modello ufficiale proposto è
[Qwen3-VL-2B-Instruct-GGUF](https://huggingface.co/Qwen/Qwen3-VL-2B-Instruct-GGUF),
con **modello testuale Q4_K_M + proiettore visivo mmproj Q8_0**. Non
confondere con il vecchio Qwen3-0.6B testuale, incapace di vedere.

In Termux, usa:

```bash
curl -fsSL https://raw.githubusercontent.com/Frak89/Canon/main/scripts/ampyeos_qwen_termux.sh -o ampyeos_qwen_termux.sh
bash ampyeos_qwen_termux.sh
```

Lo script:

1. Installa tool di compilazione in Termux e clona `ggml-org/llama.cpp`.
2. Compila `llama-server` per il dispositivo corrente.
3. Scarica il Qwen3-VL 2B Q4_K_M e il **proiettore visivo** ufficiale.
4. Avvia `llama-server` in ascolto soltanto su `127.0.0.1:8080`.
5. Lascia Termux aperto mentre AmpyEOS invia l'immagine al processo locale.

Verifica da una seconda sessione Termux:

```bash
curl -fsS http://127.0.0.1:8080/v1/models
```

Devi ricevere un elenco di modelli, non un errore di connessione.

In AmpyEOS apri una foto, premi `CONFIGURA QWEN-VL` (porta 8080),
poi `ANALIZZA SCENA CON QWEN-VL`.

Se il server non parte, il modello non supporta la visione, manca
`mmproj` oppure il JSON è incompleto, l'app **non deve inventare** il
risultato: visualizza l'errore e lascia il voto «Non valutato».

## Modello qualitativo MUSIQ via ONNX (sperimentale)

Una stima appresa della qualità percettiva è diversa dalla descrizione
semantica della scena. Il modulo `LearnedQuality` usa ONNX Runtime per
inferenza su un modello MUSIQ importato. Valuta aspetti fotografici
percettivi ma **non sostituisce Qwen-VL**, non determina il senso
della scena e non assegna automaticamente un valore artistico.

La compatibilità esatta dei file `musiq_model.onnx` e
`musiq_model.onnx.data`, con relativi input/output e normalizzazione,
deve essere confermata sul Pixel e contro il modello sorgente: la semplice
compilazione non è prova che l'inferenza produca voti validi.

## Limiti

- Il voto Qwen-VL è una valutazione interpretativa, non un fatto misurabile.
- Un modello può sbagliare identificazione o allucinare dettagli. Verifica
  sempre che citi elementi effettivamente visibili.
- Una foto astratta o minimalista non è automaticamente priva di valore.
- I modelli VLM di dimensione maggiore possono analizzare meglio ma
  richiedono più RAM, tempo ed energia. Qwen3-VL 2B è un compromesso.
- Non si risolvono carenze di comprensione semplicemente sostituendo
  TensorFlow Lite con un runtime diverso: conta il modello addestrato.
- Il processo Termux potrebbe venire sospeso dal risparmio batteria Android.

## Controlli prima di dichiarare il modulo «completo»

- Compilazione APK Android con Qwen client e ONNX Runtime.
- Test del download e caricamento di entrambi i GGUF su Pixel 7 Pro.
- Prova su almeno cinque classi di immagini: oggetti chiari, paesaggio,
  ritratto, scatto casuale e foto astratta intenzionale.
- Verifica che non appaiano 9/10 senza un'analisi della scena.
- Misura tempi, RAM, consumo batteria e comportamento in background.
