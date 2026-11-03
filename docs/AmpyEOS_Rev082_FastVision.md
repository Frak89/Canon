# AmpyEOS v0.8.2 — Quick Vision & bounded CPU inference

## What was broken

On the Pixel 7 Pro, the Qwen3-VL 2B CPU-only vision encoder could remain at
"Analisi dell'immagine sul Pixel (encoder vision)" for 1,208 seconds (>20 minutes),
with no output tokens generated. This is unacceptable for photographic work.

This was **not another download**: Qwen was processing the photograph locally.
The previous native implementation used the default image-token budget inherited
from the model and only checked cancellation between large native stages.

## Changes

- **ANALISI RAPIDA LOCALE** (immediata): analisi fotografia con ML Kit,
  misurazioni dei pixel, composizione geometrica e MUSIQ se già eseguito.
  **Non carica Qwen** e non produce voti artistici.
- **Qwen approfondito**: 320px long side, capped ~131 image tokens,
  880 output tokens, **60s cooperative CPU budget**.
- **Confronto Qwen**: 384px long side, capped ~188 image tokens,
  650 output tokens, **60s cooperative CPU budget**.
- La modalità precedente "Qwen rapido" rimane come metodo interno
  non esposto nella UI, con una preview ridotta; la UI ora privilegia
  la diagnosi locale realmente leggera.
- **Native image preprocessor limit:** \`mtmd_context_params.image_min_tokens=16\` and
  \`image_max_tokens\` computed from the actual input dimensions; the old code only
  resized the Android Bitmap, allowing the vision preprocessor to do additional work.
- **Interruptible work:** \`llama_context_params.abort_callback\` for CPU graph execution,
  plus the MTMD \`cb_eval\` hook periodically checking cancel/time budget while the
  image tower is computing. The UI also issues cancellation once its deadline is hit.
- **Truthful timer:** shows real elapsed time and configured time budget, not
  "100% sul Pixel" which could be mistaken for processing progress.
- **Failure is explicit:** if the budget expires or the image tower does not return
  usable JSON, no photo score is invented. MUSIQ, ML Kit and the existing
  composition guides remain usable offline.

### Important limits

The native cancellation is **cooperative**. An individual long-running CPU
kernel or a model-load operation may not stop at the exact requested deadline.
However, the C++ computation callbacks now provide mid-stage interruption
opportunities that were previously absent.

A 2-billion-parameter multimodal model on the Tensor G2 CPU may remain too
slow even with these changes. **No minimum analysis speed is promised** until
real Pixel measurements are available. If the 90-second Quick profile repeatedly
expires, native Qwen3-VL is unsuitable as the default interactive assistant on
this device. A smaller, optimized vision-capable model or hardware-accelerated
runtime should replace it, rather than extending the timeout indefinitely.

The lower resolution may reduce recognition accuracy for small subjects, text,
fine patterns and distant objects. The full-resolution photo remains unchanged
and never leaves the device for analysis.

## Acceptance tests

1. Start **ANALISI RAPIDA LOCALE** on the Venice boat photo:
   confirm it returns technical observations independently of the Qwen GGUF
   and without claiming to recognize the boat unless the object model detects it.
   Do not label its output as the Qwen multimodal verdict.
2. Press **FERMA ANALISI** during "encoder vision": verify that callback begins
   cancellation and the UI eventually returns to idle without a process crash.
3. In Quick analysis without internet access, check that no HTTP call is made
   to send photographs. Models in Download/AmpyEOS/Qwen3-VL must be reused.
4. Open a photo with tiny text: accept that Quick may be less accurate. Use Deep
   only when meaningful; time budget is 180s.
5. If quick timeout occurs, app should show **no invented numerical 9/10**;
   fall back to MUSIQ and the technical visual report.
6. Monitor Pixel memory and temperature on repeated analyses.
7. Run Qwen Deep only if needed; check its ~60-second budget and cooperative
   cancellation even when the encoder stalls; do not assume hard preemption.

## Practical recommendation

Use **MUSIQ** for fast learned perceptual quality and the local composition
tools for interactive feedback. Run **Qwen Quick** as an optional commentary,
not an always-on assistant until tested on Tensor G2. Even 90 seconds is a cap,
not a target speed.
