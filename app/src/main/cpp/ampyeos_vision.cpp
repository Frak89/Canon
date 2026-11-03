#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <algorithm>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>
#include <memory>

#include "llama.h"
#include "common.h"
#include "chat.h"
#include "mtmd.h"
#include "mtmd-helper.h"
#include "ggml-backend.h"

namespace {
std::mutex inference_lock;
std::once_flag initialization;
std::atomic_bool stop_requested{false};

// Timeout is evaluated inside the CPU computation callbacks, not just
// between UI stages. The Qwen image encoder can otherwise block for 20+ min.
struct RunDeadline {
    std::chrono::steady_clock::time_point until;
    uint64_t visited=0;
    bool exceeded() const {
        return std::chrono::steady_clock::now() >= until;
    }
};
bool abort_cpu(void* opaque) {
    auto* state=static_cast<RunDeadline*>(opaque);
    return stop_requested.load(std::memory_order_relaxed) ||
        (state && state->exceeded());
}
bool observe_vision(struct ggml_tensor*,bool ask,void* opaque) {
    auto* state=static_cast<RunDeadline*>(opaque);
    if(ask){
        // Observe every 16th node rather than forcing a costly callback
        // for every layer operation in the CPU-only image tower.
        return state && ((++state->visited & 15U)==0U);
    }
    return !abort_cpu(opaque);
}


/**
 * Report real native stages; percentage is shown only while token generation
 * actually runs. Everything before it is an indeterminate phase.
 */
void stage(JNIEnv* env,jobject listener,const char* name,int tokens=0,int tokenLimit=0) {
    if(listener==nullptr)return;
    jclass type=env->GetObjectClass(listener);
    if(type==nullptr)return;
    jmethodID method=env->GetMethodID(type,"onProgress","(Ljava/lang/String;II)V");
    if(method==nullptr){env->DeleteLocalRef(type);return;}
    jstring message=env->NewStringUTF(name);
    if(message!=nullptr){
        env->CallVoidMethod(listener,method,message,static_cast<jint>(tokens),
                            static_cast<jint>(tokenLimit));
        env->DeleteLocalRef(message);
    }
    env->DeleteLocalRef(type);
}
void stop_check() {
    if(stop_requested.load())
        throw std::runtime_error("Analisi Qwen annullata dall'utente");
}

void fail(JNIEnv* env,const std::string& message) {
    __android_log_print(ANDROID_LOG_ERROR,"AmpyEOS-Qwen","%s",message.c_str());
    jclass type=env->FindClass("java/lang/IllegalStateException");
    if(type!=nullptr)env->ThrowNew(type,message.c_str());
}
std::string read(JNIEnv* env,jstring str){
    if(!str)throw std::runtime_error("Missing model path or prompt");
    const char* data=env->GetStringUTFChars(str,nullptr);
    if(!data)throw std::runtime_error("Cannot decode JNI string");
    std::string result(data);
    env->ReleaseStringUTFChars(str,data);
    return result;
}
jstring utf8(JNIEnv* env,const std::string& str){
    jbyteArray bytes=env->NewByteArray(static_cast<jsize>(str.size()));
    if(!bytes)return nullptr;
    env->SetByteArrayRegion(bytes,0,static_cast<jsize>(str.size()),
                            reinterpret_cast<const jbyte*>(str.data()));
    jclass type=env->FindClass("java/lang/String");
    jmethodID init=env->GetMethodID(type,"<init>","([BLjava/lang/String;)V");
    jstring charset=env->NewStringUTF("UTF-8");
    jobject value=env->NewObject(type,init,bytes,charset);
    env->DeleteLocalRef(bytes);
    env->DeleteLocalRef(charset);
    return static_cast<jstring>(value);
}
std::vector<uint8_t> to_rgb(JNIEnv* env,jintArray javaPixels,int width,int height){
    if(width<=0||height<=0||width>2048||height>2048||
       env->GetArrayLength(javaPixels)!=width*height)
        throw std::runtime_error("Invalid image dimensions for on-device model");
    std::vector<jint> argb(static_cast<size_t>(width)*height);
    env->GetIntArrayRegion(javaPixels,0,width*height,argb.data());
    std::vector<uint8_t> rgb(argb.size()*3);
    for(size_t i=0;i<argb.size();i++){
        const uint32_t p=static_cast<uint32_t>(argb[i]);
        rgb[i*3+0]=static_cast<uint8_t>((p>>16)&255);
        rgb[i*3+1]=static_cast<uint8_t>((p>>8)&255);
        rgb[i*3+2]=static_cast<uint8_t>(p&255);
    }
    return rgb;
}
std::string token_to_utf8(const llama_vocab* vocabulary,llama_token token){
    std::vector<char> text(128);
    int count=llama_token_to_piece(vocabulary,token,text.data(),
                                   static_cast<int>(text.size()),0,true);
    if(count<0){
        text.resize(static_cast<size_t>(-count));
        count=llama_token_to_piece(vocabulary,token,text.data(),
                                   static_cast<int>(text.size()),0,true);
    }
    if(count<0)throw std::runtime_error("Could not decode Qwen output token");
    return std::string(text.data(),static_cast<size_t>(count));
}

struct Session {
    llama_model* model=nullptr;
    llama_context* context=nullptr;
    mtmd_context* vision=nullptr;
    common_chat_templates_ptr chat;
    ~Session(){
        chat.reset();
        if(vision)mtmd_free(vision);
        if(context)llama_free(context);
        if(model)llama_model_free(model);
    }
};
} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_ampyeos_canon_NativeQwenVision_cancel(JNIEnv*,jclass) {
    stop_requested=true;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ampyeos_canon_NativeQwenVision_process(
    JNIEnv* env,jclass,jstring javaModel,jstring javaProjector,
    jintArray javaPixels,jint width,jint height,jstring javaPrompt,jint maxTokens,
    jint maxImageTokens,jint budgetSeconds,jobject listener) {
    std::lock_guard<std::mutex> guard(inference_lock);
    stop_requested=false;
    try {
        if(maxImageTokens<16||maxImageTokens>512||budgetSeconds<20||budgetSeconds>300)
            throw std::runtime_error("Profilo di inferenza fuori limiti");
        RunDeadline deadline{
            std::chrono::steady_clock::now() +
            std::chrono::seconds(budgetSeconds)
        };
        stage(env,listener,"Inizializzazione motore llama.cpp");
        std::call_once(initialization, [](){
            llama_backend_init();
            ggml_backend_load_all();
        });

        const std::string modelPath=read(env,javaModel);
        const std::string projectorPath=read(env,javaProjector);
        const std::string prompt=read(env,javaPrompt);
        std::vector<uint8_t> rgb=to_rgb(env,javaPixels,width,height);
        if(maxTokens<1||maxTokens>1200)
            throw std::runtime_error("Requested token count outside safety limits");

        stop_check();
        stage(env,listener,"Caricamento modello Qwen 2B dalla memoria");
        Session session;
        auto params=llama_model_default_params();
        params.n_gpu_layers=0;
        session.model=llama_model_load_from_file(modelPath.c_str(),params);
        if(!session.model)
            throw std::runtime_error("Cannot load Qwen GGUF. Check model RAM/size.");
        stop_check();
        stage(env,listener,"Allocazione memoria e contesto Qwen");

        auto contextParams=llama_context_default_params();
        contextParams.n_ctx=2048;
        contextParams.n_batch=256;
        contextParams.n_ubatch=256;
        contextParams.abort_callback=abort_cpu;
        contextParams.abort_callback_data=&deadline;
        contextParams.n_threads=4;
        contextParams.n_threads_batch=4;
        session.context=llama_init_from_model(session.model,contextParams);
        if(!session.context)
            throw std::runtime_error("Cannot allocate Qwen context on this phone.");
        stop_check();
        stage(env,listener,"Caricamento proiettore visivo mmproj");

        auto imageParams=mtmd_context_params_default();
        imageParams.use_gpu=false;
        imageParams.n_threads=4;
        imageParams.warmup=false;
        // Qwen3-VL supports variable-resolution visual inputs.
        // Default model metadata can request many more patches than needed.
        imageParams.image_min_tokens=16;
        imageParams.image_max_tokens=maxImageTokens;
        imageParams.cb_eval=observe_vision;
        imageParams.cb_eval_user_data=&deadline;
        session.vision=mtmd_init_from_file(projectorPath.c_str(),session.model,imageParams);
        if(!session.vision||!mtmd_support_vision(session.vision))
            throw std::runtime_error("The image projector is missing or incompatible.");
        stop_check();
        stage(env,listener,"Preparazione immagine e istruzioni fotografiche");

        session.chat=common_chat_templates_init(session.model,"");
        if(!session.chat)
            throw std::runtime_error("The GGUF has no compatible chat template.");

        mtmd::bitmap image(width,height,rgb.data());
        if(!image.ptr)
            throw std::runtime_error("Failed to convert the image for multimodal inference.");

        const std::string marker=mtmd_default_marker();
        common_chat_msg user;
        user.role="user";
        user.content=marker+"\n"+prompt;
        const std::vector<common_chat_msg> history;
        const std::string formatted=common_chat_format_single(
            session.chat.get(),history,user,true,true);
        auto pos=formatted.find(marker);
        if(pos==std::string::npos)
            throw std::runtime_error("Qwen chat template did not preserve the vision marker.");

        const std::string leading=formatted.substr(0,pos);
        const std::string trailing=formatted.substr(pos+marker.size());
        mtmd_input_text firstText{leading.data(),leading.size(),false,true};
        mtmd_input_text lastText{trailing.data(),trailing.size(),false,true};
        mtmd_input_part leadingPart{&firstText,nullptr};
        mtmd_input_part picturePart{nullptr,image.ptr.get()};
        mtmd_input_part trailingPart{&lastText,nullptr};
        const mtmd_input_part* inputs[]={&leadingPart,&picturePart,&trailingPart};
        mtmd::input_chunks_ptr chunks(mtmd_input_chunks_init());
        if(!chunks||mtmd_tokenize_from_parts(session.vision,chunks.get(),inputs,3,true)!=0)
            throw std::runtime_error("Vision tokens could not be produced.");
        stop_check();
        stage(env,listener,"Encoder Qwen CPU · max "+std::to_string(maxImageTokens)+" token visivi · scadenza "+std::to_string(budgetSeconds)+"s");

        llama_pos next=0;
        if(mtmd_helper_eval_chunks(session.vision,session.context,chunks.get(),
                                   0,0,256,true,&next)!=0) {
            if(deadline.exceeded())throw std::runtime_error(
                "Encoder visivo troppo lento per la CPU del Pixel: tempo limite superato.");
            stop_check();
            throw std::runtime_error("Il processore visivo non ha completato l'analisi.");
        }
        if(deadline.exceeded())throw std::runtime_error(
            "Encoder visivo troppo lento per la CPU del Pixel: tempo limite superato.");
        stop_check();
        stage(env,listener,"Generazione del verdetto fotografico",0,maxTokens);

        llama_sampler* sampler=llama_sampler_chain_init(
            llama_sampler_chain_default_params());
        if(!sampler)throw std::runtime_error("Sampler allocation failed");
        llama_sampler_chain_add(sampler,llama_sampler_init_top_k(40));
        llama_sampler_chain_add(sampler,llama_sampler_init_temp(.16f));
        llama_sampler_chain_add(sampler,llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

        llama_batch batch=llama_batch_init(1,0,1);
        const llama_vocab* vocab=llama_model_get_vocab(session.model);
        std::string answer;
        try{
            for(int n=0;n<maxTokens&&!stop_requested;n++){
                if(deadline.exceeded())
                    throw std::runtime_error(
                        "Tempo limite di elaborazione Qwen superato: prova il profilo rapido.");
                llama_token token=llama_sampler_sample(sampler,session.context,-1);
                llama_sampler_accept(sampler,token);
                if(llama_vocab_is_eog(vocab,token))break;
                answer+=token_to_utf8(vocab,token);
                if(n%24==0)
                    stage(env,listener,"Generazione del verdetto fotografico",n+1,maxTokens);
                common_batch_clear(batch);
                common_batch_add(batch,token,next++,{0},true);
                if(llama_decode(session.context,batch)!=0)
                    throw std::runtime_error("Qwen token generation stopped unexpectedly");
            }
        }catch(...){
            llama_batch_free(batch);
            llama_sampler_free(sampler);
            throw;
        }
        llama_batch_free(batch);
        llama_sampler_free(sampler);
        if(stop_requested)throw std::runtime_error("Analysis cancelled");
        if(answer.empty())throw std::runtime_error("Qwen returned no description");
        stage(env,listener,"Verdetto generato: controllo dei dati e del JSON");
        return utf8(env,answer);
    }catch(const std::exception& e){
        fail(env,e.what());
        return nullptr;
    }
}
