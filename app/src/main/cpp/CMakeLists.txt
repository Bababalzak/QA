cmake_minimum_required(VERSION 3.22.1)
project(jarvis_quest_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --- whisper.cpp ------------------------------------------------------
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)
add_subdirectory(third_party/whisper.cpp whisper_build)

add_library(jarvis_whisper SHARED whisper_bridge.cpp)
target_link_libraries(jarvis_whisper PRIVATE whisper log android)
target_include_directories(jarvis_whisper PRIVATE third_party/whisper.cpp/include third_party/whisper.cpp/ggml/include)

# --- llama.cpp / Qwen3 local inference -------------------------------
# Fetch the upstream runtime during CI so the repository does not need to
# carry a second large native source tree. Android builds only need the
# llama library; examples/tests/server are disabled.
include(FetchContent)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_CURL OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)

FetchContent_Declare(
    llama
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG master
    GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(llama)

add_library(jarvis_llama SHARED llama_bridge.cpp)
target_include_directories(jarvis_llama PRIVATE
    ${llama_SOURCE_DIR}/include
    ${llama_SOURCE_DIR}/ggml/include
)
target_link_libraries(jarvis_llama PRIVATE llama log android)
