package ru.yandex.station.remote.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object RuTrackerCollections {

    // Каталог готовых подборок фильмов для витрины «Кинозал»
    val curatedMovies = listOf(
        // Новинки
        MovieItem(
            id = "nov_1",
            title = "Дюна: Часть вторая",
            year = "2024",
            quality = "1080p Full HD",
            size = "8.4 GB",
            seeds = 345,
            magnetUrl = "magnet:?xt=urn:btih:3fa8e2025170d30eb9c5e533c3968652d88fb4dc&dn=Dune.Part.Two.2024.1080p",
            category = "Новинки",
            description = "Продолжение фантастической саги Дени Вильнёва по роману Фрэнка Герберта."
        ),
        MovieItem(
            id = "nov_2",
            title = "Оппенгеймер",
            year = "2023",
            quality = "1080p Full HD",
            size = "11.2 GB",
            seeds = 280,
            magnetUrl = "magnet:?xt=urn:btih:618b76ce8374d8e87ad6efcfbb43a6b2807eec5a&dn=Oppenheimer.2023.1080p",
            category = "Новинки",
            description = "История жизни американского физика-теоретика Роберта Оппенгеймера."
        ),
        MovieItem(
            id = "nov_3",
            title = "Мастер и Маргарита",
            year = "2024",
            quality = "1080p",
            size = "4.8 GB",
            seeds = 410,
            magnetUrl = "magnet:?xt=urn:btih:89b3f026a7987ec98230559f91a6e92c286a5df4&dn=Master.i.Margarita.2024",
            category = "Новинки",
            description = "Москва, 1930-е годы. Известный писатель оказывается в центре литературного скандала."
        ),
        MovieItem(
            id = "nov_4",
            title = "Дэдпул и Росомаха",
            year = "2024",
            quality = "1080p Full HD",
            size = "6.1 GB",
            seeds = 520,
            magnetUrl = "magnet:?xt=urn:btih:75b0607e47e5b565ad71d2b8cf589133496ab3e1&dn=Deadpool.and.Wolverine.2024",
            category = "Новинки",
            description = "Безумный дуэт Дэдпула и Росомахи отправляется спасать мультивселенную."
        ),

        // Топ фильмов
        MovieItem(
            id = "top_1",
            title = "Интерстеллар",
            year = "2014",
            quality = "1080p Blu-ray",
            size = "10.4 GB",
            seeds = 610,
            magnetUrl = "magnet:?xt=urn:btih:92f588a4e4776118b52f63f5ee748be38aa20d6f&dn=Interstellar.2014.1080p",
            category = "Топ фильмов",
            description = "Команда исследователей отправляется через червоточину в поисках нового дома для человечества."
        ),
        MovieItem(
            id = "top_2",
            title = "Джентльмены",
            year = "2019",
            quality = "1080p Full HD",
            size = "7.8 GB",
            seeds = 390,
            magnetUrl = "magnet:?xt=urn:btih:3c4839cf9aa6a7e028b58a1bbce538ef877e64da&dn=The.Gentlemen.2019.1080p",
            category = "Топ фильмов",
            description = "Культовая криминальная комедия Гая Ричи с Мэттью Макконахи."
        ),
        MovieItem(
            id = "top_3",
            title = "Начало (Inception)",
            year = "2010",
            quality = "1080p",
            size = "9.2 GB",
            seeds = 450,
            magnetUrl = "magnet:?xt=urn:btih:db8d3c12651b14b14d87f5492976bc89d71b3e80&dn=Inception.2010.1080p",
            category = "Топ фильмов",
            description = "Кобб — искусный вор, лучший в опасном искусстве извлечения секретов из глубин подсознания."
        ),
        MovieItem(
            id = "top_4",
            title = "Зеленая миля",
            year = "1999",
            quality = "1080p HD",
            size = "8.1 GB",
            seeds = 320,
            magnetUrl = "magnet:?xt=urn:btih:8c9f28df3b78523cf408f654aa301e749a909be2&dn=The.Green.Mile.1999.1080p",
            category = "Топ фильмов",
            description = "Пол Эджкомб — начальник блока смертников в тюрьме «Холодная гора»."
        ),

        // Сериалы
        MovieItem(
            id = "ser_1",
            title = "Слово пацана. Кровь на асфальте",
            year = "2023",
            quality = "1080p Все серии",
            size = "14.2 GB",
            seeds = 430,
            magnetUrl = "magnet:?xt=urn:btih:d37b8849b380f2d48c8bcf6291a0c8b6b78ae073&dn=Slovo.Pacana.2023.1080p",
            category = "Сериалы",
            description = "Перестройка, конец 1980-х годов. Подростки и улицы Казани."
        ),
        MovieItem(
            id = "ser_2",
            title = "Чернобыль (HBO)",
            year = "2019",
            quality = "1080p Мини-сериал",
            size = "12.8 GB",
            seeds = 290,
            magnetUrl = "magnet:?xt=urn:btih:2e9b8f97b68631eb93a89078f4b069d31a89b701&dn=Chernobyl.2019.1080p",
            category = "Сериалы",
            description = "Хроника ликвидации катастрофы на Чернобыльской АЭС."
        ),
        MovieItem(
            id = "ser_3",
            title = "Во все тяжкие (Breaking Bad)",
            year = "2008-2013",
            quality = "1080p HD",
            size = "45.0 GB",
            seeds = 360,
            magnetUrl = "magnet:?xt=urn:btih:07ba8ecf93e3d489bcf281a80d5d4d7328bf3a07&dn=Breaking.Bad.Full",
            category = "Сериалы",
            description = "Уолтер Уайт, школьный учитель химии, начинает новую жизнь."
        ),

        // Классика
        MovieItem(
            id = "clas_1",
            title = "Иван Васильевич меняет профессию",
            year = "1973",
            quality = "1080p Отреставрировано",
            size = "4.2 GB",
            seeds = 510,
            magnetUrl = "magnet:?xt=urn:btih:7c65306e93e2b4d9b73081e7d23a48e9188bf20a&dn=Ivan.Vasilievich.1973.1080p",
            category = "Классика",
            description = "Инженер-изобретатель Тимофеев создал машину времени, соединившую века."
        ),
        MovieItem(
            id = "clas_2",
            title = "Операция «Ы» и другие приключения Шурика",
            year = "1965",
            quality = "1080p Ремастер",
            size = "3.9 GB",
            seeds = 480,
            magnetUrl = "magnet:?xt=urn:btih:19b48c3f769018e9a2b8e5c3e743a9b19e2c40d1&dn=Operaciya.Y.1965.1080p",
            category = "Классика",
            description = "Три новеллы о приключениях застенчивого студента Шурика."
        )
    )

    // Поиск по локальным подборкам или онлайн
    suspend fun searchMovies(query: String): List<MovieItem> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return@withContext curatedMovies

        // 1. Поиск по каталогу
        val matchedInCatalog = curatedMovies.filter {
            it.title.lowercase().contains(q) || it.category.lowercase().contains(q)
        }

        if (matchedInCatalog.isNotEmpty()) {
            return@withContext matchedInCatalog
        }

        // 2. Если введена magnet-ссылка
        if (q.startsWith("magnet:?")) {
            return@withContext listOf(
                MovieItem(
                    id = "custom_magnet_" + System.currentTimeMillis(),
                    title = "Фильм из Magnet ссылки",
                    year = "2024",
                    quality = "Auto HD",
                    size = "Онлайн",
                    seeds = 100,
                    magnetUrl = query.trim(),
                    category = "Пользовательские",
                    description = "Добавлено по прямой ссылке"
                )
            )
        }

        // 3. Онлайн поиск через открытый шлюз торрентов (TorLook)
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://api.torlook.info/api.php?key=guest&s=$encoded")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 3000
                readTimeout = 3000
            }
            if (conn.responseCode in 200..299) {
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonText)
                val resultsArray = root.optJSONArray("result") ?: JSONArray()
                val list = mutableListOf<MovieItem>()

                for (i in 0 until minOf(resultsArray.length(), 15)) {
                    val item = resultsArray.getJSONObject(i)
                    val title = item.optString("title", "Фильм")
                    val magnet = item.optString("magnet", "")
                    val size = item.optString("size", "1.5 GB")
                    val seeds = item.optInt("seeders", 50)
                    if (magnet.isNotEmpty()) {
                        list.add(
                            MovieItem(
                                id = "online_$i",
                                title = title,
                                quality = if (title.contains("1080")) "1080p" else if (title.contains("720")) "720p" else "HD",
                                size = size,
                                seeds = seeds,
                                magnetUrl = magnet,
                                category = "Результаты поиска",
                                description = "Найдено по запросу «$query»"
                            )
                        )
                    }
                }
                if (list.isNotEmpty()) {
                    return@withContext list
                }
            }
        } catch (_: Exception) {
            // В случае отсутствия внешней сети возвращаем базовый поиск
        }

        // Если ничего не найдено, возвращаем пустой список
        emptyList()
    }
}
