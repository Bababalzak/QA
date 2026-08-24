cmake_minimum_required(VERSION 3.22.1)
project(jarvis_quest_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --- whisper.cpp (Milestone 2, local STT) -----------------------------
# We only need libwhisper — not its CLI tools, examples, server, or
# tests, all of which pull in extra deps that aren't relevant (or don't
# cross-compile cleanly) on Android.
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)
add_subdirectory(third_party/whisper.cpp whisper_build)

add_library(jarvis_whisper SHARED whisper_bridge.cpp)
target_link_libraries(jarvis_whisper PRIVATE whisper log android)
target_include_directories(jarvis_whisper PRIVATE third_party/whisper.cpp/include third_party/whisper.cpp/ggml/include)

# --- llama.cpp (Milestone 3, local Qwen inference) ---------------------
# Deliberately NOT built yet. This milestone's brief explicitly scopes to
# STT only ("Do NOT implement Qwen yet"). The llama.cpp submodule and
# llama_bridge.cpp JNI code from an earlier exploratory pass were removed
# rather than left half-wired in, so this build's actual scope matches
# what it claims to do. Re-add both when Milestone 3 starts — the earlier
# llama_bridge.cpp design (llama_model_load_from_file /
# llama_init_from_model / llama_chat_apply_template / streaming JNI
# callback per token) is documented in the project history and can be
# restored rather than redesigned from zero.
