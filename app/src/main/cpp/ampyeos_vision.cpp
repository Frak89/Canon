#include <jni.h>
#include <android/log.h>
#include <atomic>
#include <chrono>
#include <cstdint>
#include <sys/stat.h>
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
    const int64_t area=static_cast<int64_t>(width)*height;
    if(width<=0||height<=0||width>8192||height>8192||
       area<=0||area>32000000LL||
       env->GetArrayLength(javaPixels)!=area)
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

struct FileStamp {
    uint64_t dev=0, ino=0, size=0;
    bool operator==(const FileStamp& other) const {
        return dev==other.dev && ino==other.ino && size==other.size;
    }
};
FileStamp file_stamp(const std::string& path) {
    struct stat st{};
    if(stat(path.c_str(),&st)!=0 || st.st_size<1)
        throw std::runtime_error("Qwen model descriptor is invalid or no longer readable");
    return FileStamp{static_cast<uint64_t>(st.st_dev),
                     static_cast<uint64_t>(st.st_ino),
                     static_cast<uint64_t>(st.st_size)};
}
struct Session {
    llama_model* model=nullptr;
    llama_context* context=nullptr;
    mtmd_context* vision=nullptr;
    common_chat_templates_ptr chat;
    RunDeadline deadline{};
    FileStamp languageStamp{},visionStamp{};
    bool gpu=false;
    ~Session(){
        chat.reset();
        if(vision)mtmd_free(vision);
        if(context)llama_free(context);
        if(model)llama_model_free(model);
    }
};
// Loading the model on EVERY photo was needlessly expensive. Keep it resident
// while AmpyEOS is alive, resetting the KV cache for independent analyses.
std::unique_ptr<Session> cached;

std::unique_ptr<Session> load_session(
    JNIEnv* env,jobject listener,const std::string& modelPath,
    const std::string& projectorPath,const FileStamp& language,
    const FileStamp& visionStamp,bool gpu,int seconds) {

    auto session=std::make_unique<Session>();
    session->gpu=gpu;
    session->languageStamp=language;
    session->visionStamp=visionStamp;
    session->deadline.until=std::chrono::steady_clock::now()+std::chrono::seconds(seconds);
    session->deadline.visited=0;
    const std::string backend=gpu?"Vulkan GPU Mali (accelerazione)":"CPU (fallback)";
    const std::string loading="Caricamento Qwen3-VL · "+backend;
    stage(env,listener,loading.c_str());
    auto params=llama_model_default_params();
    params.n_gpu_layers=gpu?99:0;
    session->model=llama_model_load_from_file(modelPath.c_str(),params);
    if(!session->model)
        throw std::runtime_error("Impossibile caricare il modello "+backend+
                                 "; controlla memoria e formato GGUF");

    auto cp=llama_context_default_params();
    cp.n_ctx=4096;
    cp.n_batch=512;
    cp.n_ubatch=256;
    cp.n_threads=4;
    cp.n_threads_batch=4;
    cp.abort_callback=abort_cpu;
    cp.abort_callback_data=&session->deadline;
    stage(env,listener,"Creo il contesto Qwen per l'inferenza");
    session->context=llama_init_from_model(session->model,cp);
    if(!session->context)
        throw std::runtime_error("Memoria insufficiente per inizializzare Qwen");

    auto mp=mtmd_context_params_default();
    mp.use_gpu=gpu;
    mp.n_threads=4;
    mp.warmup=false;
    // Preserve model-native image resolution. No forced 224/320 pixel preview
    // and no reduced vision-token cap: the user's photo retains its detail.
    mp.cb_eval=observe_vision;
    mp.cb_eval_user_data=&session->deadline;
    stage(env,listener,"Inizializzo il proiettore visivo "+backend);
    session->vision=mtmd_init_from_file(projectorPath.c_str(),session->model,mp);
    if(!session->vision || !mtmd_support_vision(session->vision))
        throw std::runtime_error("Proiettore Qwen-VL incompatibile con il modello");

    session->chat=common_chat_templates_init(session->model,"");
    if(!session->chat)
        throw std::runtime_error("Template Qwen non disponibile");
    return session;
}
} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_ampyeos_canon_NativeQwenVision_cancel(JNIEnv*,jclass) {
    stop_requested=true;
}

extern "C" JNIEXPORT void JNICALL
Java_com_ampyeos_canon_NativeQwenVision_releaseModels(JNIEnv*,jclass) {
    // Optional memory release. Do not execute from the Android UI thread:
    // an ongoing inference owns the lock.
    std::lock_guard<std::mutex> guard(inference_lock);
    stop_requested.store(true);
    cached.reset();
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_ampyeos_canon_NativeQwenVision_process(
    JNIEnv* env,jclass,jstring javaModel,jstring javaProjector,
    jintArray javaPixels,jint width,jint height,jstring javaPrompt,jint maxTokens,
    jint maxImageTokens,jint budgetSeconds,jobject listener) {
    std::lock_guard<std::mutex> guard(inference_lock);
    stop_requested=false;
    try {
        // maxImageTokens is reserved for ABI compatibility; zero means
        // preserving the default full-resolution vision pipeline.
        if(budgetSeconds<20||budgetSeconds>600)
            throw std::runtime_error("Tempo massimo di inferenza non valido");
        stage(env,listener,"Inizializzazione motore llama.cpp");
        std::call_once(initialization, [](){
            llama_backend_init();
            ggml_backend_load_all();
        });

        const std::string modelPath=read(env,javaModel);
        const std::string projectorPath=read(env,javaProjector);
        const std::string prompt=read(env,javaPrompt);
        std::vector<uint8_t> rgb=to_rgb(env,javaPixels,width,height);
        if(maxTokens<1||maxTokens>1800)
            throw std::runtime_error("Requested token count outside safety limits");

        stop_check();
        const FileStamp language=file_stamp(modelPath);
        const FileStamp projection=file_stamp(projectorPath);
        bool cacheHit=cached && cached->languageStamp==language &&
            cached->visionStamp==projection;
        if(!cacheHit) {
            cached.reset();
            const bool gpuAvailable=
                ggml_backend_dev_by_type(GGML_BACKEND_DEVICE_TYPE_GPU)!=nullptr;
            if(gpuAvailable) {
                try {
                    cached=load_session(env,listener,modelPath,projectorPath,
                        language,projection,true,budgetSeconds);
                }catch(const std::exception& gpuError){
                    __android_log_print(ANDROID_LOG_WARN,"AmpyEOS-Qwen",
                        "Vulkan GPU unavailable: %s; CPU fallback",gpuError.what());
                    cached.reset();
                    stage(env,listener,"Vulkan non inizializzato: fallback CPU");
                }
            }
            if(!cached)
                cached=load_session(env,listener,modelPath,projectorPath,
                    language,projection,false,budgetSeconds);
        }else {
            stage(env,listener,"Modello e proiettore già caricati in RAM: riutilizzo");
        }
        Session& session=*cached;
        session.deadline.until=std::chrono::steady_clock::now()+
            std::chrono::seconds(budgetSeconds);
        session.deadline.visited=0;
        llama_memory_clear(llama_get_memory(session.context),true);
        // Reassign the native callback's pointer to a LIVE cached deadline.
        llama_set_abort_callback(session.context,abort_cpu,&session.deadline);
        RunDeadline& deadline=session.deadline;
        const std::string activeBackend=session.gpu?
            "GPU Vulkan · foto con dettaglio originale":"CPU · foto con dettaglio originale";
        stage(env,listener,activeBackend.c_str());
        stop_check();
        stage(env,listener,"Preparazione immagine e istruzioni fotografiche");

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
        const std::string stageMessage=(session.gpu?
            "Encoder visivo Vulkan GPU":"Encoder visivo CPU")+
            std::string(" · risoluzione originale ")+
            std::to_string(width)+"x"+std::to_string(height)+
            " · controllo timeout "+std::to_string(budgetSeconds)+"s";
        stage(env,listener,stageMessage.c_str());

        llama_pos next=0;
        const auto visionStarted=std::chrono::steady_clock::now();
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
        const long visionMs=std::chrono::duration_cast<std::chrono::milliseconds>(
            std::chrono::steady_clock::now()-visionStarted).count();
        const std::string visionFinished="Encoder completato in "+
            std::to_string(visionMs)+" ms · generazione testo";
        stage(env,listener,visionFinished.c_str());
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
