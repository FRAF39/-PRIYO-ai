#include <jni.h>
#include <string>
#include <cstring>
#include <vector>
#include "llama.cpp/include/llama.h"

extern "C" JNIEXPORT jstring JNICALL
Java_com_priyo_codex_engine_LocalLlama_generate(JNIEnv* env, jobject, jstring jpath, jstring jprompt, jint max_tokens) {
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    const char* prompt = env->GetStringUTFChars(jprompt, nullptr);
    std::string result;
    llama_backend_init();
    llama_model_params mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    llama_model* model = llama_model_load_from_file(path, mp);
    if (!model) result = "Could not load GGUF model.";
    else {
        llama_context_params cp = llama_context_default_params();
        cp.n_ctx = 1024;
        cp.n_batch = 128;
        cp.n_threads = 2;
        cp.n_threads_batch = 2;
        llama_context* ctx = llama_init_from_model(model, cp);
        if (!ctx) result = "Could not create inference context.";
        else {
            const llama_vocab* vocab = llama_model_get_vocab(model);
            std::vector<llama_token> tokens(strlen(prompt) + 16);
            int n = llama_tokenize(vocab, prompt, (int)strlen(prompt), tokens.data(), (int)tokens.size(), true, true);
            if (n < 0) { tokens.resize(-n); n = llama_tokenize(vocab, prompt, (int)strlen(prompt), tokens.data(), (int)tokens.size(), true, true); }
            tokens.resize(n);
            llama_batch batch = llama_batch_get_one(tokens.data(), (int)tokens.size());
            if (llama_decode(ctx, batch) != 0) result = "Prompt evaluation failed.";
            else {
                for (int i = 0; i < max_tokens; ++i) {
                    auto sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
                    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(40));
                    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(0.9f, 1));
                    llama_sampler_chain_add(sampler, llama_sampler_init_temp(0.7f));
                    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
                    llama_token token = llama_sampler_sample(sampler, ctx, -1);
                    llama_sampler_free(sampler);
                    if (llama_vocab_is_eog(vocab, token)) break;
                    char piece[256]; int len = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
                    if (len > 0) result.append(piece, len);
                    batch = llama_batch_get_one(&token, 1);
                    if (llama_decode(ctx, batch) != 0) break;
                }
            }
            llama_free(ctx);
        }
        llama_model_free(model);
    }
    llama_backend_free();
    env->ReleaseStringUTFChars(jpath, path);
    env->ReleaseStringUTFChars(jprompt, prompt);
    return env->NewStringUTF(result.c_str());
}
