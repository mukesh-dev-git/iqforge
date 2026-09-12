# Model Acquisition

`NativeEngine` auto-detects whichever of these files is present (in this order — see
`ModelCatalog.kt`) and loads it, applying the correct prompt template automatically. You only
need **one** of these for the app to work; ship just the first for the default fast path.

Download the file, then place it here (`mobile/app/src/main/assets/`) **renamed to match the
exact filename below** — quantizers name their uploads inconsistently, so always rename after
downloading rather than assuming the file already matches.

## 1. Qwen2.5-Coder-1.5B — default, fast tier

- **Filename**: `qwen2.5-coder-1.5b-instruct-q4_k_m.gguf`
- **Source**: https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf
- **Size**: ~1.1 GB · **License**: Apache 2.0

## 2. Qwen2.5-Coder-3B — stretch option, better quality, still small

- **Filename**: `qwen2.5-coder-3b-instruct-q4_k_m.gguf`
- **Source**: https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf
- **Size**: ~2.1 GB · **License**: Apache 2.0

## 3. Phi-4-mini-instruct (3.8B) — alternative family, MIT licensed

- **Filename**: `phi-4-mini-instruct-q4_k_m.gguf`
- **Source**: https://huggingface.co/unsloth/Phi-4-mini-instruct-GGUF — check the repo's Files
  tab for the exact Q4_K_M filename (varies by upload) and rename to match above
- **Size**: ~2.49 GB · **License**: MIT

**When the app launches, it copies whichever file it found from APK assets to the internal
`filesDir` so `llama.cpp` can read it directly.**

## If you want to compare more than one at a time

`ModelCatalog.detectAvailable` only returns the *first* match — with two files bundled, only
the earlier one in the list loads. To actually A/B compare during the event, bundle one, test
it, then swap the asset file and reinstall rather than trying to ship all three at once (that
also roughly triples the APK size for no benefit — each model needs its own ~1–2.5GB slot).
