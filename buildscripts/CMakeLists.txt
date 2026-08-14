cmake_minimum_required(VERSION 3.5)

# https://github.com/libjpeg-turbo/libjpeg-turbo/releases
set(LIBJPEG_TURBO_VERSION 1.5.3)
set(LIBJPEG_TURBO_HASH SHA256=b24890e2bb46e12e72a79f7e965f409f4e16466d00e1dd15d93d73ee6b592523)

# http://www.libpng.org/pub/png/libpng.html
set(LIBPNG_VERSION 1.6.37)
set(LIBPNG_HASH SHA256=505e70834d35383537b6491e7ae8641f1a4bed1876dbfe361201fc80868d88ca)

# https://www.freetype.org/index.html#news
set(FREETYPE2_VERSION 2.10.4)
set(FREETYPE2_HASH SHA256=5eab795ebb23ac77001cfb68b7d4d50b5d6c7469247b0b01b2c953269f658dac)

# https://github.com/kcat/openal-soft/releases
set(OPENAL_VERSION 1.19.1)
set(OPENAL_HASH SHA256=9f3536ab2bb7781dbafabc6a61e0b34b17edd16bd6c2eaf2ae71bc63078f98c7)

# http://www.boost.org/users/download/
set(BOOST_VERSION 1.69.0)
set(BOOST_HASH SHA256=8f32d4617390d1c2d16f26a27ab60d97807b35440d45891fa340fc2648b04406)

# https://www.ffmpeg.org/download.html
set(FFMPEG_VERSION 4.4)
set(FFMPEG_HASH SHA256=42093549751b582cf0f338a21a3664f52e0a9fbe0d238d3c992005e493607d0e)

# https://www.libsdl.org/download-2.0.php
set(SDL2_VERSION 2.0.12)
set(SDL2_HASH SHA256=349268f695c02efbc9b9148a70b85e58cefbbf704abd3e91be654db7f1e2c863)

# https://github.com/bulletphysics/bullet3/releases
set(BULLET_VERSION 3.17)
set(BULLET_HASH SHA256=baa642c906576d4d98d041d0acb80d85dd6eff6e3c16a009b1abf1ccd2bc0a61)

# https://github.com/MyGUI/mygui/releases
set(MYGUI_VERSION 3.4.0)
set(MYGUI_HASH SHA256=d1d5f294670ae71f7200ed4b30859018281d8cfd45d6a38d18b97a4aba604c42)

# https://github.com/sisah2/ng_gl4es/releases
set(GL4ES_VERSION 04d3acdd2f7b06085d23ae225ae977b2b7429b98)
# set(GL4ES_HASH SHA256=b565e717c7d192e936bda25f3cb90ad8db398af56414ec08294b6716574c1a6d)

# https://github.com/OpenMW/osg/releases
set(OSG_VERSION ada34a6d38772f684aa70d2eb76549a33edc9a5f)
#set(OSG_HASH SHA256=62ad80311e9bb96c47b38322d8871118cd9abbc152fd97c7aea67a2b4014601d)

# https://github.com/lz4/lz4/releases
set(LZ4_VERSION 1.9.3)
set(LZ4_HASH SHA256=030644df4611007ff7dc962d981f390361e6c97a34e5cbc393ddfbe019ffe2c1)


set(BUILD_JOBS "1" CACHE STRING "Maximum parallel jobs for nested Android builds")
if(NOT BUILD_JOBS MATCHES "^[1-9][0-9]*$")
	message(FATAL_ERROR "BUILD_JOBS must be a positive integer, got: ${BUILD_JOBS}")
endif()


# End of configurable options

project(arenamw-android)

# ArenaMW source can be overridden from the command line, for example:
#   cmake ... -DARENAMW_REPOSITORY=/absolute/path/to/ArenaMW -DARENAMW_GIT_TAG=main
set(ARENAMW_REPOSITORY "https://github.com/pporsilkde/AMW.git" CACHE STRING "ArenaMW Git repository or local source path")
set(ARENAMW_GIT_TAG "main" CACHE STRING "ArenaMW branch, tag or commit")

include(ExternalProject)

set(wrapper_command ${CMAKE_BINARY_DIR}/command_wrapper.sh)
set(ndk_triplet ${NDK_TRIPLET})
set(download_dir ${CMAKE_SOURCE_DIR}/downloads/)
set(prefix ${CMAKE_INSTALL_PREFIX})
set(host ${ndk_triplet})
set(android_platform android-${ANDROID_API})
set(app_abi ${ABI})

set(COMMON_CMAKE_ARGS
	-DCMAKE_TOOLCHAIN_FILE=${CMAKE_SOURCE_DIR}/toolchain/ndk/build/cmake/android.toolchain.cmake
	-DANDROID_ABI=${app_abi}
	-DANDROID_PLATFORM=${android_platform}
	-DANDROID_STL=c++_shared
	-DANDROID_CPP_FEATURES=rtti\ exceptions
	-DCMAKE_C_FLAGS=-DMYGUI_DONT_REPLACE_NULLPTR\ -I${prefix}/include/\ $ENV{CFLAGS}
	-DCMAKE_CXX_FLAGS=-DMYGUI_DONT_REPLACE_NULLPTR\ -I${prefix}/include/\ $ENV{CXXFLAGS}
	-DCMAKE_SHARED_LINKER_FLAGS=$ENV{LDFLAGS}
	-DCMAKE_BUILD_TYPE=${BUILD_TYPE}
	-DCMAKE_DEBUG_POSTFIX=
	-DCMAKE_INSTALL_PREFIX=${prefix}
	-DCMAKE_FIND_ROOT_PATH=${prefix}
)

set(COMMON_NDK_BUILD_FLAGS
	NDK_PROJECT_PATH=.
	APP_BUILD_SCRIPT=<SOURCE_DIR>/Android.mk
	APP_PLATFORM=${android_platform}
	APP_ABI=${app_abi}
	LOCAL_LDFLAGS=$ENV{LDFLAGS}
	-j4
)

set(COMMON_AUTOCONF_FLAGS
	--enable-static
	--disable-shared
	--prefix=${prefix}
)

# ffmpeg doesn't like --host, so sandwich it between adding --host to the flags
set(ffmpeg_configure_flags ${COMMON_AUTOCONF_FLAGS})
if (${BUILD_TYPE} STREQUAL "debug")
	# ffmpeg fails to compile if we have asm enabled and optimizations disabled, so disable both
	set(ffmpeg_configure_flags ${ffmpeg_configure_flags} --disable-asm --disable-optimizations)
endif()

if ((${ARCH} STREQUAL "x86_64") OR (${ARCH} STREQUAL "x86"))
	# ffmpeg fails to link with assembly optimizations on x86-64
	set(ffmpeg_configure_flags ${ffmpeg_configure_flags} --disable-asm)
endif()

set(COMMON_AUTOCONF_FLAGS
	--host=${host}
	${COMMON_AUTOCONF_FLAGS}
)

if (${ARCH} STREQUAL "arm")
	set(ffmpeg_configure_flags ${ffmpeg_configure_flags} --enable-thumb)
endif()

# on x86 enabling simd results in text relocations, which are banned on Android
set(libjpeg_turbo_flags "")
if (${ARCH} STREQUAL "x86")
	set(libjpeg_turbo_flags ${libjpeg_turbo_flags} --without-simd)
endif()

ExternalProject_Add(libjpeg-turbo
	URL https://sourceforge.net/projects/libjpeg-turbo/files/${LIBJPEG_TURBO_VERSION}/libjpeg-turbo-${LIBJPEG_TURBO_VERSION}.tar.gz/download
	https://github.com/xyzz/openmw-deps/releases/download/v0/libjpeg-turbo-${LIBJPEG_TURBO_VERSION}.tar.gz
	URL_HASH ${LIBJPEG_TURBO_HASH}
	DOWNLOAD_DIR ${download_dir}

	CONFIGURE_COMMAND ${wrapper_command} <SOURCE_DIR>/configure
	${COMMON_AUTOCONF_FLAGS}
	${libjpeg_turbo_flags}

	BUILD_COMMAND ${wrapper_command} $(MAKE) PROGRAMS=

	INSTALL_COMMAND ${wrapper_command} $(MAKE) install-libLTLIBRARIES install-data-am
)

ExternalProject_Add(libpng
	URL https://github.com/xyzz/openmw-deps/releases/download/v0/libpng-${LIBPNG_VERSION}.tar.xz
	http://prdownloads.sourceforge.net/libpng/libpng-${LIBPNG_VERSION}.tar.xz
	URL_HASH ${LIBPNG_HASH}
	DOWNLOAD_DIR ${download_dir}

	CONFIGURE_COMMAND ${wrapper_command} <SOURCE_DIR>/configure
	${COMMON_AUTOCONF_FLAGS}

	BUILD_COMMAND ${wrapper_command} $(MAKE) check_PROGRAMS= bin_PROGRAMS=

	INSTALL_COMMAND ${wrapper_command} $(MAKE) install check_PROGRAMS= bin_PROGRAMS=
)


ExternalProject_Add(freetype2
	URL https://download.savannah.gnu.org/releases/freetype/freetype-${FREETYPE2_VERSION}.tar.gz
	https://download-mirror.savannah.gnu.org/releases/freetype/freetype-${FREETYPE2_VERSION}.tar.gz
	https://downloads.sourceforge.net/project/freetype/freetype2/${FREETYPE2_VERSION}/freetype-${FREETYPE2_VERSION}.tar.gz
	URL_HASH ${FREETYPE2_HASH}
	DOWNLOAD_DIR ${download_dir}

	CONFIGURE_COMMAND ${wrapper_command} <SOURCE_DIR>/configure
	${COMMON_AUTOCONF_FLAGS}
	--with-png=no

	BUILD_COMMAND ${wrapper_command} $(MAKE)

	INSTALL_COMMAND ${wrapper_command} $(MAKE) install
)

ExternalProject_Add(openal
	URL https://github.com/kcat/openal-soft/archive/openal-soft-${OPENAL_VERSION}.tar.gz
	https://github.com/xyzz/openmw-deps/releases/download/v0/openal-soft-${OPENAL_VERSION}.tar.gz
	URL_HASH ${OPENAL_HASH}
	DOWNLOAD_DIR ${download_dir}

	PATCH_COMMAND patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/patches/openal-unset-compiler.patch

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
	-DALSOFT_EXAMPLES=OFF
	-DALSOFT_TESTS=OFF
	-DALSOFT_UTILS=OFF
	-DALSOFT_NO_CONFIG_UTIL=ON
	-DALSOFT_BACKEND_OPENSL=ON
	-DALSOFT_BACKEND_WAVE=OFF

	BUILD_COMMAND ${wrapper_command} $(MAKE)

	INSTALL_COMMAND ${wrapper_command} $(MAKE) install
)

string(REPLACE "." "_" BOOST_VERSION_UNDERSCORE ${BOOST_VERSION})
ExternalProject_Add(boost
	URL https://github.com/xyzz/openmw-deps/releases/download/v0/boost_${BOOST_VERSION_UNDERSCORE}.tar.bz2
	https://archives.boost.io/release/${BOOST_VERSION}/source/boost_${BOOST_VERSION_UNDERSCORE}.tar.bz2
	URL_HASH ${BOOST_HASH}
	DOWNLOAD_DIR ${download_dir}

	BUILD_IN_SOURCE 1

	CONFIGURE_COMMAND ${wrapper_command} <SOURCE_DIR>/bootstrap.sh
	--prefix=${prefix}

	BUILD_COMMAND ""

	INSTALL_COMMAND ${wrapper_command} ./b2
	-j4
	--with-filesystem
	--with-program_options
	--with-system
	--with-iostreams
	--ignore-site-config
	toolset=clang-android
	architecture=${BOOST_ARCH}
	address-model=${BOOST_ADDRESS_MODEL}
	cflags=$ENV{CFLAGS}
	cxxflags=$ENV{CXXFLAGS}
	variant=release
	target-os=android
	threading=multi
	threadapi=pthread
	link=static
	runtime-link=static
	install
	COMMAND ${host}-ranlib ${prefix}/lib/libboost_filesystem.a
	COMMAND ${host}-ranlib ${prefix}/lib/libboost_program_options.a
	COMMAND ${host}-ranlib ${prefix}/lib/libboost_system.a
	COMMAND ${host}-ranlib ${prefix}/lib/libboost_iostreams.a
)

ExternalProject_Add(ffmpeg
	URL http://ffmpeg.org/releases/ffmpeg-${FFMPEG_VERSION}.tar.bz2
	https://github.com/xyzz/openmw-deps/releases/download/v0/ffmpeg-${FFMPEG_VERSION}.tar.bz2
	URL_HASH ${FFMPEG_HASH}
	DOWNLOAD_DIR ${download_dir}

	CONFIGURE_COMMAND ${wrapper_command} <SOURCE_DIR>/configure
	${ffmpeg_configure_flags}
	--target-os=android
	--enable-cross-compile
	--cross-prefix=${host}-
	--cc=${host}-clang
	--arch=${ARCH}
	--cpu=${FFMPEG_CPU}
	--enable-version3
	--enable-pic
	--disable-everything
	--disable-doc
	--disable-programs
	--disable-autodetect
	--disable-iconv
	--enable-decoder=mp3
	--enable-demuxer=mp3
	--enable-decoder=bink
	--enable-decoder=binkaudio_rdft
	--enable-decoder=binkaudio_dct
	--enable-demuxer=bink
	--enable-demuxer=wav
	--enable-decoder=pcm_*
	--enable-decoder=vp8
	--enable-decoder=vp9
	--enable-decoder=opus
	--enable-decoder=vorbis
	--enable-demuxer=matroska
	--enable-demuxer=ogg

	BUILD_COMMAND ${wrapper_command} $(MAKE)

	INSTALL_COMMAND ${wrapper_command} $(MAKE) install
)

ExternalProject_Add(sdl2
	URL https://www.libsdl.org/release/SDL2-${SDL2_VERSION}.tar.gz
	https://github.com/xyzz/openmw-deps/releases/download/v0/SDL2-${SDL2_VERSION}.tar.gz
	URL_HASH ${SDL2_HASH}
	DOWNLOAD_DIR ${download_dir}

	PATCH_COMMAND patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/patches/sdl2-android-120hz-gyroscope.patch

	CONFIGURE_COMMAND ""

	BUILD_COMMAND ${wrapper_command} ndk-build
	${COMMON_NDK_BUILD_FLAGS}

	INSTALL_COMMAND mkdir -p ${prefix}/lib/
	COMMAND cp libs/${app_abi}/libSDL2.so ${prefix}/lib/
	COMMAND cp libs/${app_abi}/libhidapi.so ${prefix}/lib/
	COMMAND cp -r <SOURCE_DIR>/include ${prefix}/
)

ExternalProject_Add(bullet
	URL https://github.com/bulletphysics/bullet3/archive/${BULLET_VERSION}.tar.gz
	https://github.com/xyzz/openmw-deps/releases/download/v0/bullet3-${BULLET_VERSION}.tar.gz
	URL_HASH ${BULLET_HASH}
	DOWNLOAD_DIR ${download_dir}

	PATCH_COMMAND patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/patches/bullet-fix-cmake.patch

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
	-DBUILD_BULLET2_DEMOS=OFF
	-DBUILD_CPU_DEMOS=OFF
	-DBUILD_UNIT_TESTS=OFF
	-DBUILD_EXTRAS=OFF
	-DUSE_DOUBLE_PRECISION=ON
	-DBULLET2_MULTITHREADING=ON

	BUILD_COMMAND ${wrapper_command} $(MAKE)

	INSTALL_COMMAND $(MAKE) install
)

ExternalProject_Add(NG-GL4ES
	URL https://github.com/sisah2/NG-GL4ES/archive/${GL4ES_VERSION}.tar.gz
	#URL_HASH ${GL4ES_HASH}
	DOWNLOAD_DIR ${download_dir}

	BUILD_IN_SOURCE 1

	# libglslang.a (prebuilt) ссылается на std::filesystem::__absolute —
	# в NDK r21e libc++ этого символа нет. Патчим CMakeLists.txt NG-GL4ES,
	# добавляя stub-файл с реализацией no-op, чтобы линкер был удовлетворён.
	# Используем .cpp (а не .S), т.к. NG-GL4ES не вызывает enable_language(ASM)
	# и .S-файл был бы тихо проигнорирован при сборке.
	PATCH_COMMAND
	  cp ${CMAKE_SOURCE_DIR}/patches/ng-gl4es/fs_stub.cpp <SOURCE_DIR>/fs_stub.cpp
	  COMMAND sh -c "grep -qF fs_stub.cpp <SOURCE_DIR>/CMakeLists.txt || echo 'target_sources(ng_gl4es PRIVATE fs_stub.cpp)' >> <SOURCE_DIR>/CMakeLists.txt"

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}

	BUILD_COMMAND ${wrapper_command} $(MAKE)

	# Do not rely on another dependency/cache having created prefix/include.
	# Copy the *contents* of NG-GL4ES/include so an existing gl4es directory
	# cannot accidentally produce prefix/include/gl4es/include/... .
	INSTALL_COMMAND mkdir -p ${prefix}/lib/ ${prefix}/include/gl4es/
	COMMAND cp libng_gl4es.so ${prefix}/lib/
	COMMAND cp <SOURCE_DIR>/libraries/${app_abi}/libspirv-cross-c-shared.so ${prefix}/lib/
	COMMAND cp -r <SOURCE_DIR>/include/. ${prefix}/include/gl4es/
	COMMAND cp -r <SOURCE_DIR>/include/. ${prefix}/include/
)

# Alias so existing targets depending on "gl4es" still resolve
add_custom_target(gl4es)
add_dependencies(gl4es NG-GL4ES)


ExternalProject_Add(mygui
	DEPENDS freetype2

	URL https://github.com/MyGUI/mygui/archive/MyGUI${MYGUI_VERSION}.tar.gz
	https://github.com/xyzz/openmw-deps/releases/download/v0/MyGUI${MYGUI_VERSION}.tar.gz
	URL_HASH ${MYGUI_HASH}
	DOWNLOAD_DIR ${download_dir}

	PATCH_COMMAND patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/patches/mygui-dont-set-debug-postfix.patch

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
	-DMYGUI_RENDERSYSTEM=ON
	-DMYGUI_BUILD_DEMOS=OFF
	-DMYGUI_BUILD_TOOLS=OFF
	-DMYGUI_BUILD_PLUGINS=OFF
	-DFREETYPE_FT2BUILD_INCLUDE_DIR=${prefix}/include/freetype2/

	-DMYGUI_STATIC=ON

	BUILD_COMMAND ${wrapper_command} $(MAKE)

	INSTALL_COMMAND ${wrapper_command} $(MAKE) install
)

ExternalProject_Add(lz4
	URL https://github.com/lz4/lz4/archive/v${LZ4_VERSION}.tar.gz
	https://github.com/xyzz/openmw-deps/releases/download/v0/v${LZ4_VERSION}.tar.gz
	URL_HASH ${LZ4_HASH}
	DOWNLOAD_DIR ${download_dir}

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>/build/cmake/
	${COMMON_CMAKE_ARGS}
	-DBUILD_STATIC_LIBS=ON
	-DBUILD_SHARED_LIBS=OFF

	BUILD_COMMAND ${wrapper_command} $(MAKE)

	INSTALL_COMMAND ${wrapper_command} $(MAKE) install
	)

set(OSG_COMMON
	-DOPENGL_PROFILE="GL2"
	-DDYNAMIC_OPENTHREADS=OFF
	-DDYNAMIC_OPENSCENEGRAPH=OFF
	-DBUILD_OSG_PLUGIN_OSG=ON
	-DBUILD_OSG_PLUGIN_DDS=ON
	-DBUILD_OSG_PLUGIN_TGA=ON
	-DBUILD_OSG_PLUGIN_BMP=ON
	-DBUILD_OSG_PLUGIN_JPEG=ON
	-DBUILD_OSG_PLUGIN_PNG=ON
	-DBUILD_OSG_PLUGIN_FREETYPE=ON
	-DJPEG_INCLUDE_DIR=${prefix}/include/
	-DPNG_INCLUDE_DIR=${prefix}/include/
	-DFREETYPE_DIR=${prefix}
	-DOSG_CPP_EXCEPTIONS_AVAILABLE=TRUE
	-DOSG_GL1_AVAILABLE=ON
	-DOSG_GL2_AVAILABLE=ON
	-DOSG_GL3_AVAILABLE=OFF
	-DOSG_GLES1_AVAILABLE=OFF
	-DOSG_GLES2_AVAILABLE=OFF
	-DOSG_GL_LIBRARY_STATIC=OFF
	-DOSG_GL_DISPLAYLISTS_AVAILABLE=ON
	-DOSG_GL_MATRICES_AVAILABLE=ON
	-DOSG_GL_VERTEX_FUNCS_AVAILABLE=ON
	-DOSG_GL_VERTEX_ARRAY_FUNCS_AVAILABLE=ON
	-DOSG_GL_FIXED_FUNCTION_AVAILABLE=ON
	-DBUILD_OSG_APPLICATIONS=OFF
	-DBUILD_OSG_PLUGINS_BY_DEFAULT=OFF
	-DBUILD_OSG_DEPRECATED_SERIALIZERS=OFF
	-DOPENGL_gl_LIBRARY=${prefix}/lib/libng_gl4es.so
)

set(OSG_PATCH
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/patches/osg/disable-polygon-offset.patch &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/patches/osg/remove-lib-prefix-from-plugins.patch &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/patches/osg/fix-freetype-include-dirs.patch &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/patches/osg/0001-Replace-Atomic-impl-with-std-atomic.patch &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/patches/osg/0002-BufferObject-make-numClients-atomic.patch &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/patches/osg/0004-IncrementalCompileOperation-wrap-some-stuff-in-atomi.patch &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/patches/osg/remove-zlib-dependency.patch &&
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/patches/osg/0005-CullSettings-make-inheritanceMask-atomic-to-silence-.patch &&
	# load-ng-gl4es.patch НЕ применяем — его единственный hunk (подмена
	# dlopen("libGL.so") → dlopen("ng_gl4es.so") в src/osg/GLExtensions.cpp)
	# уже включён в ng-gl4es.patch. Повторное применение роняет patch -N
	# с "Reversed (or previously applied)" и exit code ≠ 0.
	patch -d <SOURCE_DIR> -p1 -t -N < ${CMAKE_SOURCE_DIR}/patches/osg/ng-gl4es.patch
)

ExternalProject_Add(osg
	DEPENDS libjpeg-turbo libpng NG-GL4ES freetype2

	URL https://github.com/OpenMW/osg/archive/${OSG_VERSION}.tar.gz
	# https://github.com/xyzz/openmw-deps/releases/download/v0/OpenSceneGraph-${OSG_VERSION}.tar.gz
	# URL_HASH ${OSG_HASH}
	DOWNLOAD_DIR ${download_dir}

	PATCH_COMMAND ${OSG_PATCH}

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
	${OSG_COMMON}

	BUILD_COMMAND ${wrapper_command} $(MAKE)

	INSTALL_COMMAND mkdir -p <SOURCE_DIR>/3rdparty/build/libpng <SOURCE_DIR>/3rdparty/build/libjpeg <SOURCE_DIR>/3rdparty/build/freetype
	COMMAND $(MAKE) install
)

set(OPENMW_COMMON
	-DBUILD_BSATOOL:BOOL=OFF
	-DBUILD_NIFTEST:BOOL=OFF
	-DBUILD_ESMTOOL:BOOL=OFF
	-DBUILD_LAUNCHER:BOOL=OFF
	-DBUILD_MWINIIMPORTER:BOOL=OFF
	-DBUILD_ESSIMPORTER:BOOL=OFF
	-DBUILD_OPENMW:BOOL=ON
	-DBUILD_OPENCS:BOOL=OFF
	-DBUILD_WIZARD:BOOL=OFF
	-DBUILD_MYGUI_PLUGIN:BOOL=OFF
	-DBUILD_DOCS:BOOL=OFF
	-DBUILD_UNITTESTS:BOOL=OFF
	-DBUILD_BENCHMARKS:BOOL=OFF
	-DBUILD_WITH_CODE_COVERAGE:BOOL=OFF
	-DOPENMW_USE_SYSTEM_OSG:BOOL=ON
	-DOPENMW_USE_SYSTEM_MYGUI:BOOL=ON
	-DOPENMW_USE_SYSTEM_BULLET:BOOL=ON
	-DOPENMW_USE_SYSTEM_RECASTNAVIGATION:BOOL=OFF
	-DBOOST_STATIC:BOOL=ON
	-DOSG_STATIC:BOOL=TRUE
	-DMYGUI_STATIC:BOOL=TRUE
	-DOPENAL_INCLUDE_DIR=${prefix}/include/AL/
	-DBullet_INCLUDE_DIR=${prefix}/include/bullet/
	-DOPENGL_ES=OFF
	-DOPENGL_gl_LIBRARY=${prefix}/lib/libng_gl4es.so
	-DOPENGL_glx_LIBRARY=${prefix}/lib/libng_gl4es.so
	-DOPENGL_INCLUDE_DIR=${prefix}/include/gl4es/
	-DMyGUI_LIBRARY=${prefix}/lib/libMyGUIEngineStatic.a
)

set(ARENAMW_PATCH
    # AMW2-native build-time patch pipeline. The source checkout stays clean in
    # the repository; Android compatibility is applied only inside ExternalProject.
    bash ${CMAKE_SOURCE_DIR}/patches/openmw/apply-series.sh <SOURCE_DIR>
)

# Original Android dependency stack, reduced to the single-player requirements.
set(ANDROID_NATIVE_DEPENDENCIES
	boost openal osg mygui ffmpeg sdl2 bullet lz4
)

add_custom_target(android-dependencies DEPENDS ${ANDROID_NATIVE_DEPENDENCIES})

ExternalProject_Add(arenamw
	DEPENDS ${ANDROID_NATIVE_DEPENDENCIES}

	GIT_REPOSITORY ${ARENAMW_REPOSITORY}
	GIT_TAG ${ARENAMW_GIT_TAG}
	GIT_SHALLOW TRUE
	UPDATE_DISCONNECTED TRUE
	DOWNLOAD_DIR ${download_dir}

	PATCH_COMMAND ${ARENAMW_PATCH}

	CONFIGURE_COMMAND ${wrapper_command} cmake <SOURCE_DIR>
	${COMMON_CMAKE_ARGS}
	${OPENMW_COMMON}

	# Always invoke the nested ArenaMW build. The cached CMake/Make build tree
	# then decides which translation units are actually out of date, so unchanged
	# sources are not recompiled on subsequent CI runs.
	BUILD_ALWAYS TRUE

	# Single-player ArenaMW Android library only.
	BUILD_COMMAND ${wrapper_command} ${CMAKE_COMMAND} --build <BINARY_DIR> --target openmw --parallel ${BUILD_JOBS}
	BUILD_BYPRODUCTS <BINARY_DIR>/libopenmw.so

	INSTALL_COMMAND ""
)
