cmake_minimum_required(VERSION 3.4.1)

add_library( # Sets the name of the library.
             vpn_security_lib

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             native-lib.cpp )

find_library( # Sets the name of the path variable.
              log-lib

              # Specifies the name of the NDK library that
              # you want CMake to locate.
              log )

target_link_libraries( # Specifies the target library.
                       vpn_security_lib

                       # Links the target library to the log library
                       # included in the NDK.
                       ${log-lib} )
