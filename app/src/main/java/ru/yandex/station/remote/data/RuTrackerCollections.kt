package ru.yandex.station.remote.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RuTrackerCollections {

    // Каталог готовых подборок фильмов для витрины «Кинозал»
    val curatedMovies = listOf(
        // ================= НОВИНКИ =================
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
        MovieItem(
            id = "nov_5",
            title = "Гладиатор 2",
            year = "2024",
            quality = "1080p",
            size = "7.3 GB",
            seeds = 310,
            magnetUrl = "magnet:?xt=urn:btih:41a87e2bcf39810a9bb285e6402d8471b059ef3c&dn=Gladiator.II.2024.1080p",
            category = "Новинки",
            description = "Продолжение легендарного исторического эпоса Ридли Скотта."
        ),
        MovieItem(
            id = "nov_6",
            title = "Субстанция (The Substance)",
            year = "2024",
            quality = "1080p",
            size = "5.6 GB",
            seeds = 275,
            magnetUrl = "magnet:?xt=urn:btih:8bf71a64bc819e075daef4200bc7da89c7456d21&dn=The.Substance.2024.1080p",
            category = "Новинки",
            description = "Сенсационный боди-хоррор с Деми Мур о вечной молодости и цене славы."
        ),

        // ================= ТОП ФИЛЬМОВ =================
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
        MovieItem(
            id = "top_5",
            title = "Побег из Шоушенка",
            year = "1994",
            quality = "1080p",
            size = "9.5 GB",
            seeds = 540,
            magnetUrl = "magnet:?xt=urn:btih:51c36087d832b49bc527c02b292c30088c426214&dn=The.Shawshank.Redemption.1994",
            category = "Топ фильмов",
            description = "Энди Дюфрейн, обвиненный в убийстве, оказывается в суровой тюрьме Шоушенк."
        ),
        MovieItem(
            id = "top_6",
            title = "Аватар: Путь воды",
            year = "2022",
            quality = "1080p",
            size = "12.0 GB",
            seeds = 480,
            magnetUrl = "magnet:?xt=urn:btih:618b76ce8374d8e87ad6efcfbb43a6b2807eec5a&dn=Avatar.The.Way.Of.Water.1080p",
            category = "Топ фильмов",
            description = "Джейк Салли и Нейтири борются за защиту Пандоры от новой угрозы."
        ),

        // ================= СЕРИАЛЫ =================
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
        MovieItem(
            id = "ser_4",
            title = "Игра в кальмара (Squid Game)",
            year = "2021-2024",
            quality = "1080p",
            size = "15.0 GB",
            seeds = 380,
            magnetUrl = "magnet:?xt=urn:btih:3fa8e2025170d30eb9c5e533c3968652d88fb4dc&dn=Squid.Game.1080p",
            category = "Сериалы",
            description = "Сотни людей в долгах принимают приглашение на опасные детские игры с гигантским призом."
        ),

        // ================= КЛАССИКА =================
        MovieItem(
            id = "clas_1",
            title = "Иван Васильевич меняет профессию",
            year = "1973",
            quality = "1080p Отреставрировано",
            size = "4.2 GB",
            seeds = 510,
            magnetUrl = "magnet:?xt=urn:btih:89b3f026a7987ec98230559f91a6e92c286a5df4&dn=Ivan.Vasilievich.1080p",
            category = "Классика",
            description = "Инженер-изобретатель Тимофеев создал машину времени, соединившую XVI век и XX век."
        ),
        MovieItem(
            id = "clas_2",
            title = "Брат и Брат 2",
            year = "1997-2000",
            quality = "1080p Дилогия",
            size = "9.1 GB",
            seeds = 470,
            magnetUrl = "magnet:?xt=urn:btih:92f588a4e4776118b52f63f5ee748be38aa20d6f&dn=Brat.1.and.2.1080p",
            category = "Классика",
            description = "Культовая дилогия Алексея Балабанова о Даниле Багрове с Сергеем Бодровым."
        ),
        MovieItem(
            id = "clas_3",
            title = "Бриллиантовая рука",
            year = "1968",
            quality = "1080p Реставрация",
            size = "4.5 GB",
            seeds = 380,
            magnetUrl = "magnet:?xt=urn:btih:8c9f28df3b78523cf408f654aa301e749a909be2&dn=Brilliantovaya.Ruka.1080p",
            category = "Классика",
            description = "Комедия Леонида Гайдая о контрабандистах и скромном служащем Семене Семеновиче Горбункове."
        )
    )

    // Поиск по каталогу и обработка пользовательских запросов
    suspend fun searchMovies(query: String): List<MovieItem> = withContext(Dispatchers.Default) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext curatedMovies

        // 1. Если введена прямая magnet-ссылка
        if (q.startsWith("magnet:?", ignoreCase = true)) {
            val titleFromDn = extractDisplayNameFromMagnet(q)
            return@withContext listOf(
                MovieItem(
                    id = "custom_magnet_" + System.currentTimeMillis(),
                    title = titleFromDn.ifEmpty { "Фильм из Magnet ссылки" },
                    year = "Онлайн",
                    quality = "Auto HD",
                    size = "Онлайн",
                    seeds = 100,
                    magnetUrl = q,
                    category = "Пользовательские",
                    description = "Раздача добавлена по прямой ссылке"
                )
            )
        }

        // 2. Умный поиск по каталогу (разбивка на слова)
        val queryWords = q.lowercase().split("\\s+".toRegex()).filter { it.length >= 2 }
        val matches = curatedMovies.filter { movie ->
            val target = "${movie.title} ${movie.category} ${movie.year} ${movie.description}".lowercase()
            if (queryWords.isEmpty()) {
                target.contains(q.lowercase())
            } else {
                queryWords.all { word -> target.contains(word) } ||
                queryWords.any { word -> target.contains(word) }
            }
        }.sortedByDescending { movie ->
            // Приоритет фильмам, где запрос целиком входит в название
            if (movie.title.lowercase().contains(q.lowercase())) 10 else 1
        }

        if (matches.isNotEmpty()) {
            return@withContext matches
        }

        // 3. Если ничего не найдено в готовом каталоге, возвращаем карточку быстрого добавления по запросу
        listOf(
            MovieItem(
                id = "search_help_" + System.currentTimeMillis(),
                title = "Поиск «$q» на RuTracker",
                year = "Вставка",
                quality = "RuTracker",
                size = "Любой размер",
                seeds = 100,
                magnetUrl = "magnet:?xt=urn:btih:3fa8e2025170d30eb9c5e533c3968652d88fb4dc&dn=" + q.replace(" ", "."),
                category = "Поиск",
                description = "Скопируйте magnet-ссылку с RuTracker в браузере и нажмите «Вставить Magnet» для моментального запуска на ТВ."
            )
        )
    }

    private fun extractDisplayNameFromMagnet(magnet: String): String {
        val regex = Regex("dn=([^&]+)")
        val match = regex.find(magnet)
        val raw = match?.groupValues?.get(1) ?: return ""
        return try {
            java.net.URLDecoder.decode(raw, "UTF-8").replace(".", " ")
        } catch (_: Exception) {
            raw.replace(".", " ")
        }
    }
}
