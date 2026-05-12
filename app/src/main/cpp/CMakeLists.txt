cmake_minimum_required(VERSION 3.18.1)
project("fftnative")

add_library(fftnative SHARED fftnative.cpp)

find_library(log-lib log)

target_link_libraries(fftnative ${log-lib})
