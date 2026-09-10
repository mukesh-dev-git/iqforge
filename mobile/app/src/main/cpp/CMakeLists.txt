cmake_minimum_required(VERSION 3.22.1)

project("llama-android")

set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

# NPU Integration Pipeline (Issue #5)
# This is currently disabled (OFF) to preserve the CPU safety net for Hour 0.
# On-site (Hour 2-8), we will switch GGML_QNN to ON if we receive the QNN SDK.
set(GGML_QNN OFF CACHE BOOL "Enable Qualcomm Neural Network SDK (Hexagon NPU) backend" FORCE)

add_subdirectory(llama.cpp EXCLUDE_FROM_ALL)

add_library(
        llama-android
        SHARED
        LlamaEngine.cpp)

find_library(
        log-lib
        log)

target_link_libraries(
        llama-android
        llama
        ${log-lib})

target_include_directories(llama-android PRIVATE
        llama.cpp/include
        llama.cpp/ggml/include)

if(GGML_QNN)
    # The QNN backend requires linking the QNN system libraries provided on the iQOO 15
    target_compile_definitions(llama-android PRIVATE USE_QNN_BACKEND)
    # target_link_libraries(llama-android QnnSystem QnnCpu QnnHtp) # Uncomment on-site when SDK is present
endif()
