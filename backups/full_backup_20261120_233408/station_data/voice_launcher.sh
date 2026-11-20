#!/system/bin/sh

export PATH=/system/bin:/system/xbin:$PATH
export ANDROID_ROOT=/system
export ANDROID_DATA=/data

# -T 1 means only process new log lines from now on
logcat -T 1 -s MordoviaPageImpl | while read -r line; do
    case "$line" in
        *"%D0%BA%D0%BE%D0%B4%D0%B8"*|*"kodi"*|*"Kodi"*|*"%D0%BA%D0%BE%D0%B4%D1%8B"*|*"cody"*|*"%D0%BA%D0%B8%D0%BD%D0%BE"*|*"коди"*|*"кино"*)
            am start -n org.xbmc.kodi/.Splash >/dev/null 2>&1
            sleep 2
            ;;
        *"%D0%B4%D0%BE%D0%BC%D0%BE%D0%B9"*|*"домой"*)
            # If home requested, close Kodi
            pkill -9 org.xbmc.kodi >/dev/null 2>&1
            am start -n ru.yandex.quasar.app/.splash.SplashActivity >/dev/null 2>&1
            sleep 2
            ;;
    esac
done
