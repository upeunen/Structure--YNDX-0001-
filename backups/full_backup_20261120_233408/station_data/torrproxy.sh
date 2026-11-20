#!/system/bin/sh
IFS= read -r req_line || exit 0
clean_req=$(printf '%s' "$req_line" | /system/bin/busybox-smp tr -d '\r')
method=${clean_req%% *}
rest=${clean_req#* }
path=${rest%% *}

if [ "$method" = "HEAD" ] && [ "${path#/api/stream/}" != "$path" ]; then
    req_range=""
    while IFS= read -r hdr; do
        hdr_clean=$(printf '%s' "$hdr" | /system/bin/busybox-smp tr -d '\r')
        [ -z "$hdr_clean" ] && break
        case "$hdr_clean" in
            Range:*|range:*) req_range=${hdr_clean#*: };;
        esac
    done

    range_arg="0-0"
    status_line="HTTP/1.1 200 OK"
    if [ -n "$req_range" ]; then
        range_arg="${req_range#bytes=}"
        status_line="HTTP/1.1 206 Partial Content"
    fi

    hdrs=$(curl -sD - --max-time 8 -r "$range_arg" "http://127.0.0.1:8091$path" -o /dev/null)
    cr=$(printf '%s\n' "$hdrs" | /system/bin/busybox-smp grep -i '^Content-Range:' | /system/bin/busybox-smp head -n 1 | /system/bin/busybox-smp tr -d '\r')
    ct=$(printf '%s\n' "$hdrs" | /system/bin/busybox-smp grep -i '^Content-Type:' | /system/bin/busybox-smp head -n 1 | /system/bin/busybox-smp tr -d '\r')
    total=${cr##*/}
    if [ -z "$total" ] || [ "$total" = "*" ]; then
        printf 'HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n'
        exit 0
    fi
    [ -z "$ct" ] && ct='Content-Type: application/octet-stream'

    printf '%s\r\n' "$status_line"
    printf 'Accept-Ranges: bytes\r\n'
    printf '%s\r\n' "$ct"
    if [ -n "$req_range" ]; then
        cl=$(printf '%s\n' "$hdrs" | /system/bin/busybox-smp grep -i '^Content-Length:' | /system/bin/busybox-smp head -n 1 | /system/bin/busybox-smp tr -d '\r')
        [ -n "$cl" ] && printf '%s\r\n' "$cl"
        [ -n "$cr" ] && printf '%s\r\n' "$cr"
    else
        printf 'Content-Length: %s\r\n' "$total"
    fi
    printf 'Connection: close\r\n\r\n'
    exit 0
fi

if [ "$method" = "GET" ] && [ "${path#/api/stream/}" != "$path" ]; then
    hdrs_out="$clean_req\r\n"
    while IFS= read -r hdr; do
        hdr_clean=$(printf '%s' "$hdr" | /system/bin/busybox-smp tr -d '\r')
        [ -z "$hdr_clean" ] && break
        case "$hdr_clean" in
            If-Unmodified-Since:*|if-unmodified-since:*|If-Match:*|if-match:*|If-Range:*|if-range:*|If-Modified-Since:*|if-modified-since:*|Connection:*|connection:*) ;;
            *) hdrs_out="${hdrs_out}${hdr_clean}\r\n";;
        esac
    done
    hdrs_out="${hdrs_out}Connection: close\r\n\r\n"
    printf '%b' "$hdrs_out" | /system/bin/busybox-smp nc 127.0.0.1 8091
    exit 0
fi

(printf '%s\n' "$req_line"; cat) | /system/bin/busybox-smp nc 127.0.0.1 8091

