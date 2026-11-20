#!/system/bin/sh

export PATH=/system/bin:/system/xbin:$PATH
export ANDROID_ROOT=/system
export ANDROID_DATA=/data

while true; do
    if ! ps | grep -q "org.xbmc.kodi"; then
        am start -n org.xbmc.kodi/.Splash >/dev/null 2>&1
    fi
    sleep 5
done
