cmake_minimum_required(VERSION 3.22.1)

project("ngdroid")

# Build JNI bridge shared library
add_library(ngspice_jni SHARED
    ngspice_jni.cpp
)

find_library(log-lib log)

target_link_libraries(ngspice_jni
    ${log-lib}
    ${CMAKE_DL_LIBS}
)
