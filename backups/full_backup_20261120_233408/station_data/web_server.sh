#!/system/bin/sh
read req

export PATH=/system/bin:/system/xbin:$PATH
export ANDROID_ROOT=/system
export ANDROID_DATA=/data

TYPE="application/json"
BODY='{"status":"ok"}'

case "$req" in
    *"GET /api/key?k="*)
        KEY=$(echo "$req" | sed -n 's/^.*k=\([^ &]*\).*$/\1/p')
        case "$KEY" in
            up) input keyevent 19 ;;
            down) input keyevent 20 ;;
            left) input keyevent 21 ;;
            right) input keyevent 22 ;;
            ok) input keyevent 66 ;;
            back) input keyevent 4 ;;
            playpause) input keyevent 85 ;;
            volup) input keyevent 24 ;;
            voldown) input keyevent 25 ;;
        esac
        BODY='{"status":"ok","key":"'$KEY'"}'
        ;;
    *"GET /api/play?url="*)
        RAW_URL=$(echo "$req" | sed -n 's/^.*url=\([^ &]*\).*$/\1/p')
        URL=$(printf '%b' "${RAW_URL//%/\\x}" 2>/dev/null || echo "$RAW_URL")
        if [ -n "$URL" ]; then
            am start -a android.intent.action.VIEW -d "$URL" org.smarttube.stable >/dev/null 2>&1
        fi
        BODY='{"status":"ok","msg":"Видео запускается!"}'
        ;;
    *"GET /api/tap?x="*)
        X=$(echo "$req" | sed -n 's/^.*x=\([^ &]*\).*$/\1/p')
        Y=$(echo "$req" | sed -n 's/^.*y=\([^ &]*\).*$/\1/p')
        if [ -n "$X" ] && [ -n "$Y" ]; then
            input tap "$X" "$Y" >/dev/null 2>&1
        fi
        BODY='{"status":"ok","tap":"'$X','$Y'"}'
        ;;
    *"GET /api/youtube"*)
        am start -n org.smarttube.stable/com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity >/dev/null 2>&1
        BODY='{"status":"ok","msg":"SmartTube открыт"}'
        ;;
    *"GET /api/kodi"*)
        am force-stop org.smarttube.stable >/dev/null 2>&1
        am start -n org.xbmc.kodi/.Splash >/dev/null 2>&1
        BODY='{"status":"ok","msg":"Kodi открыт"}'
        ;;
    *"GET /api/home"*)
        am force-stop org.smarttube.stable >/dev/null 2>&1
        am start -n ru.yandex.quasar.app/.MainActivity >/dev/null 2>&1
        BODY='{"status":"ok","msg":"Главный экран Алисы"}'
        ;;
    *)
        TYPE="text/html; charset=utf-8"
        BODY='<!DOCTYPE html>
<html lang="ru">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
<title>Пульт Станции</title>
<style>
*{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
body{margin:0;padding:16px 12px;background:#0d0f14;color:#eee;font-family:-apple-system,BlinkMacSystemFont,Roboto,Helvetica,Arial,sans-serif;text-align:center;user-select:none}
.card{max-width:360px;margin:0 auto 16px;background:#181b24;padding:18px 16px;border-radius:20px;box-shadow:0 6px 20px rgba(0,0,0,0.5);border:1px solid #282d3c}
h1{font-size:18px;margin:0 0 12px 0;font-weight:700;color:#ffcc00}
.url-input{width:100%;padding:14px;border-radius:12px;background:#0d0f14;border:1px solid #363c4e;color:#fff;font-size:15px;outline:none;margin-bottom:10px}
.btn-red{width:100%;padding:14px;border:none;border-radius:12px;background:linear-gradient(135deg,#e50914,#b20710);color:#fff;font-size:16px;font-weight:700;cursor:pointer}
.btn-red:active{transform:scale(0.97)}
.dpad{position:relative;width:220px;height:220px;margin:10px auto}
.dbtn{position:absolute;background:#222634;border:1px solid #363d52;color:#f0f2f5;font-size:20px;display:flex;align-items:center;justify-content:center;cursor:pointer;border-radius:12px}
.dbtn:active{background:#ffcc00;color:#000}
.dup{top:0;left:75px;width:70px;height:65px;border-radius:16px 16px 8px 8px}
.ddown{bottom:0;left:75px;width:70px;height:65px;border-radius:8px 8px 16px 16px}
.dleft{top:75px;left:0;width:65px;height:70px;border-radius:16px 8px 8px 16px}
.dright{top:75px;right:0;width:65px;height:70px;border-radius:8px 16px 16px 8px}
.dok{top:75px;left:75px;width:70px;height:70px;background:#ffcc00;color:#111;border-radius:50%;font-weight:700;font-size:16px;border:none}
.row{display:flex;gap:8px;width:100%;margin-top:10px}
.btn-action{flex:1;padding:12px 6px;background:#222634;border:1px solid #363d52;color:#e0e4ec;border-radius:12px;font-size:14px;font-weight:600;cursor:pointer}
.btn-action:active{background:#ffcc00;color:#000}
.btn-home{background:#2a2216;border-color:#5c441b;color:#ffcc00}
.status{margin-top:10px;padding:8px;border-radius:8px;background:#15241b;color:#4ade80;font-size:13px;display:none}
</style>
</head>
<body>
<div class="card">
  <h1>▶ YouTube Видео</h1>
  <input type="text" id="urlInput" class="url-input" placeholder="Вставьте ссылку на видео...">
  <button class="btn-red" onclick="play()">Включить на телевизоре</button>
</div>

<div class="card">
  <h1>🎮 Сенсорный пульт</h1>
  <div class="dpad">
    <button class="dbtn dup" onclick="k(\x27up\x27)">▲</button>
    <button class="dbtn dleft" onclick="k(\x27left\x27)">◀</button>
    <button class="dbtn dok" onclick="k(\x27ok\x27)">ОК</button>
    <button class="dbtn dright" onclick="k(\x27right\x27)">▶</button>
    <button class="dbtn ddown" onclick="k(\x27down\x27)">▼</button>
  </div>
  <div class="row">
    <button class="btn-action" onclick="k(\x27back\x27)">↩ Назад</button>
    <button class="btn-action" onclick="k(\x27playpause\x27)">⏯ Пауза</button>
    <button class="btn-action" onclick="k(\x27voldown\x27)">🔉 -</button>
    <button class="btn-action" onclick="k(\x27volup\x27)">🔊 +</button>
  </div>
  <div class="row">
    <button class="btn-action" onclick="send(\x27/api/youtube\x27, \x27Открываю YouTube...\x27)">YouTube</button>
    <button class="btn-action" onclick="send(\x27/api/kodi\x27, \x27Открываю Kodi...\x27)">🎬 Кино</button>
    <button class="btn-action btn-home" onclick="send(\x27/api/home\x27, \x27Главный экран...\x27)">🏠 Алиса</button>
  </div>
  <div id="status" class="status"></div>
</div>

<script>
function k(key){
  if(navigator.vibrate) navigator.vibrate(20);
  fetch("/api/key?k=" + key);
}
function play(){
  var u = document.getElementById("urlInput").value.trim();
  if(!u){ alert("Вставьте ссылку!"); return; }
  send("/api/play?url=" + encodeURIComponent(u), "Запускаю видео...");
  document.getElementById("urlInput").value = "";
}
function send(url, msg){
  var s = document.getElementById("status");
  s.style.display = "block";
  s.innerText = msg;
  fetch(url).then(function(){
    setTimeout(function(){ s.style.display = "none"; }, 2500);
  });
}
</script>
</body>
</html>'
        ;;
esac

LEN=$(printf "%s" "$BODY" | wc -c)

printf "HTTP/1.1 200 OK\r\n"
printf "Content-Type: %s\r\n" "$TYPE"
printf "Content-Length: %s\r\n" "$LEN"
printf "Connection: close\r\n"
printf "Access-Control-Allow-Origin: *\r\n\r\n"
printf "%s" "$BODY"
