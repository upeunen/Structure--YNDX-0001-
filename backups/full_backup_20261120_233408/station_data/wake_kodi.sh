#!/system/bin/sh
export PATH=/system/bin:/system/xbin:$PATH
export ANDROID_ROOT=/system
export ANDROID_DATA=/data
am start -n org.xbmc.kodi/.Splash >/dev/null 2>&1
