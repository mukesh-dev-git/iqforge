#include <jni.h>
#include <algorithm>
#include <string>
#include <vector>

#include "llama.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_iqforge_engine_NativeEngine_nativeGenerate(
        JNIEnv * env, jobject, jstring model_path, jstring prompt) {
    const char * path_chars = env->GetStringUTFChars(model_path, nullptr);
    const char * prompt_chars = env->GetStringUTFChars(prompt, nullptr);
    const std::string path(path_chars ? path_chars : "");
    const std::string input(prompt_chars ? prompt_chars : "");
    env->ReleaseStringUTFChars(model_path, path_chars);
    env->ReleaseStringUTFChars(prompt, prompt_chars);

    llama_backend_init();
    llama_model_params model_params = llama_model_default_params();
    // NPU DECISION POINT: n_gpu_layers = 0 means CPU-only.
    // If attempting the Hexagon swap on-site (Option A), CMake will define USE_QNN_BACKEND
    // and we will raise this number to offload graph computation to the NPU.
#ifdef USE_QNN_BACKEND
    model_params.n_gpu_layers = 99; // Offload fully to Hexagon NPU
#else
    model_params.n_gpu_layers = 0;  // CPU fallback
#endif
    llama_model * model = llama_model_load_from_file(path.c_str(), model_params);
    if (!model) return env->NewStringUTF("ERROR: unable to load GGUF model");

    const llama_vocab * vocab = llama_model_get_vocab(model);
    const int n_prompt = -llama_tokenize(vocab, input.c_str(), input.size(), nullptr, 0, true, true);
    if (n_prompt <= 0) {
        llama_model_free(model);
        return env->NewStringUTF("ERROR: prompt could not be tokenized");
    }
    std::vector<llama_token> tokens(n_prompt);
    if (llama_tokenize(vocab, input.c_str(), input.size(), tokens.data(), tokens.size(), true, true) < 0) {
        llama_model_free(model);
        return env->NewStringUTF("ERROR: tokenization failed");
    }

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = std::min<uint32_t>(4096, std::max<uint32_t>(2048, n_prompt + 512));
    context_params.n_batch = std::min<uint32_t>(context_params.n_ctx, tokens.size());
    llama_context * context = llama_init_from_model(model, context_params);
    if (!context) {
        llama_model_free(model);
        return env->NewStringUTF("ERROR: unable to create llama context");
    }

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());

    std::string output;
    llama_batch batch = llama_batch_get_one(tokens.data(), tokens.size());
    const int max_tokens = 256;
    llama_token next_token = 0;
    for (int position = 0; position + batch.n_tokens < n_prompt + max_tokens;) {
        if (llama_decode(context, batch) != 0) break;
        position += batch.n_tokens;
        next_token = llama_sampler_sample(sampler, context, -1);
        if (llama_vocab_is_eog(vocab, next_token)) break;
        std::vector<char> piece(256);
        const int count = llama_token_to_piece(vocab, next_token, piece.data(), piece.size(), 0, true);
        if (count > 0) output.append(piece.data(), count);
        batch = llama_batch_get_one(&next_token, 1);
    }

    llama_sampler_free(sampler);
    llama_free(context);
    llama_model_free(model);
    return env->NewStringUTF(output.c_str());
}
