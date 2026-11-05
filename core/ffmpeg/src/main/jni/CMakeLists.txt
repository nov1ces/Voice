cmake_minimum_required(VERSION 3.22.1)
project(ffmpegJNI C CXX)
set(CMAKE_CXX_STANDARD 11)

get_filename_component(ffmpeg_root "${CMAKE_CURRENT_SOURCE_DIR}/../../../build/ffmpeg/${ANDROID_ABI}" ABSOLUTE)
if(NOT EXISTS "${ffmpeg_root}/lib/libavcodec.a")
    message(FATAL_ERROR "Build FFmpeg first: ./scripts/build_ffmpeg.sh")
endif()

foreach(component avutil swresample avcodec)
    add_library(${component} STATIC IMPORTED)
    set_target_properties(${component} PROPERTIES
        IMPORTED_LOCATION "${ffmpeg_root}/lib/lib${component}.a")
endforeach()

add_library(ffmpegJNI SHARED ffmpeg_jni.cc)
target_include_directories(ffmpegJNI PRIVATE "${ffmpeg_root}/include")
target_link_libraries(ffmpegJNI PRIVATE avcodec swresample avutil android log m z)
target_link_options(ffmpegJNI PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-Bsymbolic")
