#!/system/bin/sh
export CLASSPATH=/data/local/tmp/station_media.dex
export ANDROID_ROOT=/system
export ANDROID_DATA=/data

while true; do
    if ! /system/bin/busybox-smp pgrep -f 'ru.yandex.station.media.StationMediaTool monitor' >/dev/null 2>&1; then
        if [ -f /data/local/tmp/station_media.dex ]; then
            /system/bin/busybox-smp setsid app_process /data/local/tmp ru.yandex.station.media.StationMediaTool monitor </dev/null >>/data/torrserver/voice_monitor.log 2>&1 &
        fi
    fi
    sleep 3
done
