cmake_minimum_required(VERSION 3.16)
project(cubiomes_jna C)

set(CMAKE_C_STANDARD 99)
set(CMAKE_C_STANDARD_REQUIRED ON)

set(CUBIOMES_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../cubiomes-context/cubiomes")

add_library(cubiomes_jna SHARED
    cubiomes_jna.c
    ${CUBIOMES_DIR}/biomenoise.c
    ${CUBIOMES_DIR}/biomes.c
    ${CUBIOMES_DIR}/finders.c
    ${CUBIOMES_DIR}/generator.c
    ${CUBIOMES_DIR}/layers.c
    ${CUBIOMES_DIR}/noise.c
    ${CUBIOMES_DIR}/quadbase.c
    ${CUBIOMES_DIR}/util.c
)

target_include_directories(cubiomes_jna PRIVATE
    ${CUBIOMES_DIR}
)

if(NOT WIN32)
    target_link_libraries(cubiomes_jna PRIVATE m)
endif()
