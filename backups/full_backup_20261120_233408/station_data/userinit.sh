#!/system/bin/sh
# SSH Dropbear
pkill -9 dropbear 2>/dev/null
sleep 1
/data/local/bin/dropbear -r /data/local/etc/dropbear/dropbear_rsa_host_key -r /data/local/etc/dropbear/dropbear_ecdsa_host_key -r /data/local/etc/dropbear/dropbear_ed25519_host_key -p 22 -B

# Mobile Web Remote on port 8088
pkill -f 'tcpsvd.*8088' 2>/dev/null
/system/bin/busybox-smp setsid /system/bin/busybox-smp tcpsvd -vE 0.0.0.0 8088 /data/local/bin/web_server.sh </dev/null >/dev/null 2>&1 &

# TorrServer BitTorrent Streamer Watchdog on port 8090
pkill -f torrserver_service.sh 2>/dev/null
mkdir -p /data/torrserver
chmod -R 777 /data/torrserver
/system/bin/busybox-smp setsid /data/local/bin/torrserver_service.sh </dev/null >/dev/null 2>&1 &

# Voice Transmit Sound Alert Watchdog
pkill -f voice_beep_service.sh 2>/dev/null
pkill -f 'ru.yandex.station.media.StationMediaTool monitor' 2>/dev/null
/system/bin/busybox-smp setsid /data/local/bin/voice_beep_service.sh </dev/null >/dev/null 2>&1 &
