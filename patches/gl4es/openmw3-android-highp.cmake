if(NOT DEFINED SOURCE_DIR)
    message(FATAL_ERROR "SOURCE_DIR is required")
endif()

set(_shaderconv "${SOURCE_DIR}/src/gl/shaderconv.c")
set(_fpe "${SOURCE_DIR}/src/gl/fpe_shader.c")
foreach(_file IN ITEMS "${_shaderconv}" "${_fpe}")
    if(NOT EXISTS "${_file}")
        message(FATAL_ERROR "NG-GL4ES source not found: ${_file}")
    endif()
endforeach()

# ArenaMW/OpenMW does substantial camera/world-space math in fragment shaders
# (water, fog, lighting). Openmw3 may still emit mediump built-in matrices when
# runtime highp detection is conservative. GLES3 devices support fragment highp;
# keep all translated OpenGL built-in matrices highp to avoid angle-dependent
# quantization/jitter when the camera rotates.
file(READ "${_shaderconv}" _src)
set(_before "${_src}")
string(REPLACE "ishighp = 0;" "ishighp = 1;" _src "${_src}")
string(REPLACE "ishighp=0;" "ishighp=1;" _src "${_src}")
string(REPLACE "hardext.highp ? gl4es_FogParametersSourceHighp : gl4es_FogParametersSource"
               "gl4es_FogParametersSourceHighp" _src "${_src}")
string(REPLACE "hardext.highp?gl4es_FogParametersSourceHighp:gl4es_FogParametersSource"
               "gl4es_FogParametersSourceHighp" _src "${_src}")
if(_src STREQUAL _before)
    string(FIND "${_src}" "gl4es_FogParametersSourceHighp" _has_highp)
    if(_has_highp EQUAL -1)
        message(FATAL_ERROR "ArenaMW: Openmw3 shader precision layout changed upstream")
    endif()
else()
    file(WRITE "${_shaderconv}" "${_src}")
endif()

file(READ "${_fpe}" _fpe_src)
set(_fpe_before "${_fpe_src}")
string(REPLACE "const char* fogp = hardext.highp ? \"highp\" : \"mediump\";"
               "const char* fogp = \"highp\";" _fpe_src "${_fpe_src}")
string(REPLACE "const char* fogp = hardext.highp?\"highp\":\"mediump\";"
               "const char* fogp = \"highp\";" _fpe_src "${_fpe_src}")
string(REPLACE "hardext.highp ? \"* gl_Fog.scale\" : \"/ (gl_Fog.end - gl_Fog.start)\""
               "\"* gl_Fog.scale\"" _fpe_src "${_fpe_src}")
string(REPLACE "hardext.highp?\"* gl_Fog.scale\":\"/ (gl_Fog.end - gl_Fog.start)\""
               "\"* gl_Fog.scale\"" _fpe_src "${_fpe_src}")
if(NOT _fpe_src STREQUAL _fpe_before)
    file(WRITE "${_fpe}" "${_fpe_src}")
endif()

message(STATUS "ArenaMW: forced highp matrix/fog precision in Sisah2/Openmw3 shader conversion")
