#!/system/bin/sh
pkill -f 'udpsvd.* 9' 2>/dev/null
pkill -f 'udpsvd.* 7' 2>/dev/null
sleep 1
/system/bin/busybox-smp udpsvd -vE 0.0.0.0 9 /system/bin/sh /data/local/bin/wake_kodi.sh >/dev/null 2>&1 &
/system/bin/busybox-smp udpsvd -vE 0.0.0.0 7 /system/bin/sh /data/local/bin/wake_kodi.sh >/dev/null 2>&1 &
