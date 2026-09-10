# Model Acquisition

Download the following model and place it in this directory (`mobile/app/src/main/assets/`):

- **Filename**: `qwen2.5-coder-1.5b-instruct-q4_k_m.gguf`
- **Source**: `https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf`
- **Size**: ~1.12 GB
- **License**: Apache 2.0 / Qwen License

When the app launches, it will copy this file from the APK assets to the internal `filesDir` so `llama.cpp` can read it directly.
