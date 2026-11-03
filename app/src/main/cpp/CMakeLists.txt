cmake_minimum_required(VERSION 3.22)
project(ampyeos_vision LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(NOT ANDROID_ABI STREQUAL "arm64-v8a")
    message(FATAL_ERROR "AmpyEOS local Qwen3-VL supports Pixel/arm64-v8a only")
endif()

set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_MTMD ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
set(LLAMA_SUBPROCESS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
set(MTMD_VIDEO OFF CACHE BOOL "" FORCE)

# Use Pixel 7 Pro Mali-G710 Vulkan compute for both the Qwen text decoder
# and the multimodal projector. CPU remains compiled as fallback.
set(GGML_VULKAN ON CACHE BOOL "Enable Android GPU acceleration" FORCE)
set(GGML_VULKAN_CHECK_RESULTS OFF CACHE BOOL "" FORCE)
set(GGML_VULKAN_SHADER_DEBUG_INFO OFF CACHE BOOL "" FORCE)
# In cross-compilation, NDK Vulkan headers/libraries are target-side while
# glslc/SPIR-V-Headers are installed on the Linux CI host.
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)

set(LLAMA_SRC "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp")
if(NOT EXISTS "${LLAMA_SRC}/CMakeLists.txt")
    message(FATAL_ERROR "Missing pinned llama.cpp. Execute bash scripts/fetch_llama_cpp.sh")
endif()
add_subdirectory("${LLAMA_SRC}" "${CMAKE_BINARY_DIR}/llama-src")

# Android NDK lacks Vulkan-Hpp C++ wrappers although it has Vulkan C API.
# Stage only Vulkan headers from Ubuntu CI (not host system headers).
set(AMPYEOS_VK_HPP "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/vulkan_headers")
if(EXISTS "${AMPYEOS_VK_HPP}/vulkan/vulkan.hpp")
    target_include_directories(ggml-vulkan PRIVATE "${AMPYEOS_VK_HPP}")
endif()

add_library(ampyeos-vision SHARED ampyeos_vision.cpp)
target_include_directories(ampyeos-vision PRIVATE
    "${LLAMA_SRC}/include"
    "${LLAMA_SRC}/common"
    "${LLAMA_SRC}/ggml/include"
    "${LLAMA_SRC}/tools/mtmd"
)
target_link_libraries(ampyeos-vision PRIVATE llama llama-common mtmd ggml-vulkan android log)
