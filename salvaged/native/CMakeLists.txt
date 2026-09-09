cmake_minimum_required(VERSION 3.22.1)

project("llama-android")

set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
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
