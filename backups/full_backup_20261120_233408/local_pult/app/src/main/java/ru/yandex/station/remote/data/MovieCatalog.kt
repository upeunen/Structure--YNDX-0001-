package ru.yandex.station.remote.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

data class MovieReview(
    val author: String,
    val date: String,
    val text: String
)

data class MovieCatalogItem(
    val id: Long,
    val title: String,
    val originalTitle: String,
    val overview: String,
    val posterUrl: String,
    val backdropUrl: String = "",
    val releaseYear: String,
    val rating: Double,
    val genre: String = "",
    val seeds: Int = 0,
    val director: String = "",
    val country: String = "",
    val cast: String = "",
    val voiceActing: String = "",
    val torrentSize: String = ""
)

data class MovieSection(
    val id: String,
    val title: String,
    val movies: List<MovieCatalogItem>
)

object MovieCatalogService {

    private val builtinMovies: List<MovieCatalogItem> = listOf(
        MovieCatalogItem(
            id = 1L,
            title = "Дюна: Часть вторая",
            originalTitle = "Dune: Part Two",
            overview = "Герцог Пол Атрейдес объединяется с фременами, чтобы отомстить заговорщикам, уничтожившим его семью.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BNTc0YmQxMjEtODI5MC00NjFiLTlkMWUtOGQ5NjFmYWUyZGJhXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2024",
            rating = 8.6,
            genre = "trending"
        ),
        MovieCatalogItem(
            id = 2L,
            title = "Оппенгеймер",
            originalTitle = "Oppenheimer",
            overview = "История жизни американского физика Роберта Оппенгеймера, руководителя Манхэттенского проекта.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BN2JkMDc5MGQtZjg3YS00NmFiLWIyZmQtZTJmNTM5MjVmYTQ4XkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2023",
            rating = 8.9,
            genre = "trending"
        ),
        MovieCatalogItem(
            id = 3L,
            title = "Гладиатор 2",
            originalTitle = "Gladiator II",
            overview = "Спустя годы после гибели Максимуса повзрослевший Луций вынужден выйти на арену Колизея.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BMWYzZTM5ZGQtOGE5My00NmM2LWFlMDEtMGNjYjdmOWM1MzA1XkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2024",
            rating = 7.4,
            genre = "trending"
        ),
        MovieCatalogItem(
            id = 4L,
            title = "Дэдпул и Росомаха",
            originalTitle = "Deadpool & Wolverine",
            overview = "Уэйд Уилсон объединяется с Росомахой ради спасения своей родной вселенной.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BZTk5ODY0MmQtMzA3Ni00NGY1LThiYzItZThiNjFiNDM4MTM3XkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2024",
            rating = 7.8,
            genre = "trending"
        ),
        MovieCatalogItem(
            id = 5L,
            title = "Граф Монте-Кристо",
            originalTitle = "The Count of Monte-Cristo",
            overview = "Молодой Эдмон Дантес несправедливо осужден и жаждет отмщения.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BYWQ2ZTI4ODYtZmRkOS00NzlmLWIyMGItNTEzZmMwNmUxZDRkXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2024",
            rating = 8.0,
            genre = "trending"
        ),
        MovieCatalogItem(
            id = 6L,
            title = "Падение империи",
            originalTitle = "Civil War",
            overview = "Группа журналистов едет через охваченную войной Америку к Белому дому.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BYTkzMjc0YzgtY2E0Yi00NDBlLWI0MWUtODY1ZjExMDAyOWZiXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2024",
            rating = 7.1,
            genre = "trending"
        ),
        MovieCatalogItem(
            id = 7L,
            title = "Фуриоса: Хроники Безумного Макса",
            originalTitle = "Furiosa: A Mad Max Saga",
            overview = "История юной воительницы Фуриосы в жестоком постапокалиптическом мире Пустоши.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BNTcwYWE1NTYtOWNiYy00NzY3LWIwY2MtNjJmZDkxNDNmOWE1XkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2024",
            rating = 7.6,
            genre = "now_playing"
        ),
        MovieCatalogItem(
            id = 8L,
            title = "Годзилла и Конг: Новая империя",
            originalTitle = "Godzilla x Kong: The New Empire",
            overview = "Два титана объединяются против колоссальной угрозы из глубин Полой Земли.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BMTY0N2MzODctY2ExYy00OWYxLTkyNDItMTVhZGIxZjliZjU5XkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2024",
            rating = 6.8,
            genre = "now_playing"
        ),
        MovieCatalogItem(
            id = 9L,
            title = "Планета обезьян: Новое царство",
            originalTitle = "Kingdom of the Planet of the Apes",
            overview = "Спустя много поколений после Цезаря молодой шимпанзе отправляется в опасное путешествие.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BZDRlZTc3YTItOTk3Yi00NmU4LWFiOGUtNjgwMDZjNjIzNTU1XkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2024",
            rating = 7.0,
            genre = "now_playing"
        ),
        MovieCatalogItem(
            id = 10L,
            title = "Битлджус Битлджус",
            originalTitle = "Beetlejuice Beetlejuice",
            overview = "После семейной трагедии семья Дитц возвращается в Уинтер-Ривер.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BNTQ4Y2MzY2MtYzQyMS00ZTQ3LWIzZGQtNjVjYzgwYzA1MDkzXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2024",
            rating = 6.8,
            genre = "now_playing"
        ),
        MovieCatalogItem(
            id = 11L,
            title = "Каскадеры",
            originalTitle = "The Fall Guy",
            overview = "Опытный каскадер берется за поиски пропавшей звезды крупнобюджетного блокбастера.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BM2U0MTJiYTItMjNiZS00MzU4LTkxYTAtYTU0ZGY1ODJhMjRhXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2024",
            rating = 6.9,
            genre = "now_playing"
        ),
        MovieCatalogItem(
            id = 12L,
            title = "Интерстеллар",
            originalTitle = "Interstellar",
            overview = "Группа исследователей отправляется сквозь червоточину в поисках нового дома для человечества.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BYzdjMDAxZGItMjI2My00ODA1LTlkNzItOWFjMDU5ZDJlYWY3XkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2014",
            rating = 8.7,
            genre = "scifi"
        ),
        MovieCatalogItem(
            id = 13L,
            title = "Начало",
            originalTitle = "Inception",
            overview = "Вор, крадущий секреты во сне, берется внедрить мысль в чужое подсознание.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BMjAxMzY3NjcxNF5BMl5BanBnXkFtZTcwNTI5OTM0Mw@@._V1_.jpg",
            releaseYear = "2010",
            rating = 8.8,
            genre = "scifi"
        ),
        MovieCatalogItem(
            id = 14L,
            title = "Матрица",
            originalTitle = "The Matrix",
            overview = "Хакер Нео узнает правду о том, что привычная реальность — лишь иллюзия машин.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BN2NmN2VhMTQtMDNiOS00NDlhLTliMjgtODE2ZTY0ODQyNDRhXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "1999",
            rating = 8.7,
            genre = "scifi"
        ),
        MovieCatalogItem(
            id = 15L,
            title = "Бегущий по лезвию 2049",
            originalTitle = "Blade Runner 2049",
            overview = "Офицер Кей раскрывает тайну, способную повергнуть общество в хаос.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BNzA1Njg4NzYxOV5BMl5BanBnXkFtZTgwODk5NjU3MzI@._V1_.jpg",
            releaseYear = "2017",
            rating = 8.0,
            genre = "scifi"
        ),
        MovieCatalogItem(
            id = 16L,
            title = "Марсианин",
            originalTitle = "The Martian",
            overview = "Астронавт Марк Уотни остался один на Марсе и борется за выживание.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BMTc2MTQ3MDA1Nl5BMl5BanBnXkFtZTgwODA3OTI4NjE@._V1_.jpg",
            releaseYear = "2015",
            rating = 8.0,
            genre = "scifi"
        ),
        MovieCatalogItem(
            id = 17L,
            title = "Прибытие",
            originalTitle = "Arrival",
            overview = "Лингвист Луиза Бэнкс пытается расшифровать язык прибывших на Землю пришельцев.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BMTExMzU0ODcxNDheQTJeQWpwZ15BbWU4MDE1OTI4MzAy._V1_.jpg",
            releaseYear = "2016",
            rating = 7.9,
            genre = "scifi"
        ),
        MovieCatalogItem(
            id = 18L,
            title = "Властелин колец: Братство Кольца",
            originalTitle = "The Lord of the Rings: The Fellowship of the Ring",
            overview = "Хоббит Фродо Бэггинс отправляется в Мордор, чтобы уничтожить Единое Кольцо.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BNzIxMDQ2YTctNDY4MC00ZTRhLTk4ODQtMTVlOWY4NTdiYmMwXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2001",
            rating = 8.9,
            genre = "adventure"
        ),
        MovieCatalogItem(
            id = 19L,
            title = "Властелин колец: Возвращение короля",
            originalTitle = "The Lord of the Rings: The Return of the King",
            overview = "Финальная битва за Средиземье у стен Минас-Тирита.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BMTZkMjBjNWMtZGI5OC00MGU0LTk4ZTItODg2NWM3NTVmNWQ4XkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2003",
            rating = 9.0,
            genre = "adventure"
        ),
        MovieCatalogItem(
            id = 20L,
            title = "Хоббит: Нежданное путешествие",
            originalTitle = "The Hobbit: An Unexpected Journey",
            overview = "Бильбо Бэггинс отправляется в эпический поход к Одинокой Горе.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BMTcwNTE4MTUxMl5BMl5BanBnXkFtZTcwMDIyODM4OA@@._V1_.jpg",
            releaseYear = "2012",
            rating = 7.8,
            genre = "adventure"
        ),
        MovieCatalogItem(
            id = 21L,
            title = "Пираты Карибского моря: Проклятие Черной жемчужины",
            originalTitle = "Pirates of the Caribbean: The Curse of the Black Pearl",
            overview = "Капитан Джек Воробей возвращает свой корабль и спасает губернаторскую дочку.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BNDhlMzEyNzItMTA5Mi00YWRhLThlNTktYTQyMTA0MDIyNDEyXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2003",
            rating = 8.1,
            genre = "adventure"
        ),
        MovieCatalogItem(
            id = 22L,
            title = "Аватар: Путь воды",
            originalTitle = "Avatar: The Way of Water",
            overview = "Джейк Салли и Нейтири защищают свою семью на бескрайних рифах Пандоры.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BNWI0Y2NkOWEtMmM2OC00MjQ3LWI1YzItZGQxYzQ3NzI4NWZmXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2022",
            rating = 7.6,
            genre = "adventure"
        ),
        MovieCatalogItem(
            id = 23L,
            title = "Побег из Шоушенка",
            originalTitle = "The Shawshank Redemption",
            overview = "Энди Дюфрейн несправедливо осужден на пожизненное заключение в мрачной тюрьме.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BMDAyY2FhYjctNDc5OS00MDNlLThiMGUtY2UxYWVkNGY2ZjljXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "1994",
            rating = 9.3,
            genre = "drama"
        ),
        MovieCatalogItem(
            id = 24L,
            title = "Зеленая миля",
            originalTitle = "The Green Mile",
            overview = "История надзирателя блока смертников и гиганта Джона Коффи, обладающего даром исцеления.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BMTUxMzQyNjA5MF5BMl5BanBnXkFtZTYwOTU2NTY3._V1_.jpg",
            releaseYear = "1999",
            rating = 8.6,
            genre = "drama"
        ),
        MovieCatalogItem(
            id = 25L,
            title = "Форрест Гамп",
            originalTitle = "Forrest Gump",
            overview = "Необыкновенная жизнь добродушного парня из Алабамы, ставшего свидетелем истории США.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BNDYwNzVjMTItZmU5YS00YjQ5LTljYjgtMjY2NDVmYWMyNWFmXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "1994",
            rating = 8.8,
            genre = "drama"
        ),
        MovieCatalogItem(
            id = 26L,
            title = "Бойцовский клуб",
            originalTitle = "Fight Club",
            overview = "Страдающий бессонницей клерк создает подпольный клуб с продавцом мыла Тайлером.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BOTgyOGQ1NDItNGU3Ny00MjU3LTg2YWEtNmEyYjBiMjI1Y2M5XkEyXkFqcGc@._V1_.jpg",
            releaseYear = "1999",
            rating = 8.8,
            genre = "drama"
        ),
        MovieCatalogItem(
            id = 27L,
            title = "Престиж",
            originalTitle = "The Prestige",
            overview = "Два фокусника-иллюзиониста ведут смертельную войну за секрет идеального трюка.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BZWU0NWZhYzMtZmNlYy00MjA1LTgyZWEtYmNjNzdjYmRkOTkzXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2006",
            rating = 8.5,
            genre = "drama"
        ),
        MovieCatalogItem(
            id = 28L,
            title = "1+1",
            originalTitle = "The Intouchables",
            overview = "Богатый аристократ нанимает в сиделки бывшего заключенного с улицы.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BMTYxNDA3MDQwNl5BMl5BanBnXkFtZTcwNTU4Mzc1Nw@@._V1_.jpg",
            releaseYear = "2011",
            rating = 8.5,
            genre = "drama"
        ),
        MovieCatalogItem(
            id = 29L,
            title = "Джентльмены",
            originalTitle = "The Gentlemen",
            overview = "Американец пытается продать свою высокодоходную марихуановую империю в Лондоне.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BMzM0YzFhMTYtYjZkNS00MWRmLTg2ZDgtY2MzYjQ5ODVmOTI1XkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2020",
            rating = 7.8,
            genre = "comedy"
        ),
        MovieCatalogItem(
            id = 30L,
            title = "Достать ножи",
            originalTitle = "Knives Out",
            overview = "Детектив Бенуа Бланк расследует смерть богатого автора детективных романов.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BZDU5ZTRkYmItZjg0Mi00ZTQwLThjMWItNWM3MTMxMzVjZmVjXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2019",
            rating = 7.9,
            genre = "comedy"
        ),
        MovieCatalogItem(
            id = 31L,
            title = "Волк с Уолл-стрит",
            originalTitle = "The Wolf of Wall Street",
            overview = "История взлета и падения дерзкого нью-йоркского брокера Джордана Белфорта.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BMjIxMjgxNTk0MF5BMl5BanBnXkFtZTgwNjIyOTg2MDE@._V1_.jpg",
            releaseYear = "2013",
            rating = 8.2,
            genre = "comedy"
        ),
        MovieCatalogItem(
            id = 32L,
            title = "Зеленая книга",
            originalTitle = "Green Book",
            overview = "Утонченный джазовый пианист нанимает простоватого вышибалу водителем в турне по Югу США.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BNDU5YTNmMmItN2QxNy00OGQ0LTg5MTctNzFmYjEzZjcwN2UwXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2018",
            rating = 8.2,
            genre = "comedy"
        ),
        MovieCatalogItem(
            id = 33L,
            title = "Мальчишник в Вегасе",
            originalTitle = "The Hangover",
            overview = "Трое друзей просыпаются в разгромленном номере отеля и не могут найти жениха.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BNDI2MzBhNzgtOWYyOS00NDM2LWE0OGYtOGQ0M2FjMTI2NTllXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2009",
            rating = 7.7,
            genre = "comedy"
        ),
        MovieCatalogItem(
            id = 34L,
            title = "Темный рыцарь",
            originalTitle = "The Dark Knight",
            overview = "Бэтмен поднимает ставки в войне с криминалом, но сталкивается с Джокером.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BMTMxNTMwODM0NF5BMl5BanBnXkFtZTcwODAyMTk2Mw@@._V1_.jpg",
            releaseYear = "2008",
            rating = 9.0,
            genre = "action"
        ),
        MovieCatalogItem(
            id = 35L,
            title = "Джон Уик 4",
            originalTitle = "John Wick: Chapter 4",
            overview = "Джон Уик находит способ победить Правление Кланов в Париже, Берлине и Осаке.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BY2Q2ZmI5ZjUtNWVhMC00YzJkLTlmYjMtY2RmZDhkNzEzYjZhXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2023",
            rating = 7.7,
            genre = "action"
        ),
        MovieCatalogItem(
            id = 36L,
            title = "Топ Ган: Мэверик",
            originalTitle = "Top Gun: Maverick",
            overview = "Пит «Мэверик» Митчелл готовит выпускников для выполнения смертельно опасной миссии.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BMDBkZDNjMWEtOTdmMi00NmExLTg5MmMtNTFlYTJlNWY5YTdmXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2022",
            rating = 8.3,
            genre = "action"
        ),
        MovieCatalogItem(
            id = 37L,
            title = "Безумный Макс: Дорога ярости",
            originalTitle = "Mad Max: Fury Road",
            overview = "Макс и Фуриоса мчатся через пустыню от безжалостного тирана Несмертного Джо.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BZDRkODJhOTgtOTc1OC00NTgzLTk4NjItNDgxZDY4YjlmNDY2XkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2015",
            rating = 8.1,
            genre = "action"
        ),
        MovieCatalogItem(
            id = 38L,
            title = "Леон",
            originalTitle = "Léon: The Professional",
            overview = "Профессиональный киллер берет под опеку двенадцатилетнюю сироту Матильду.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BNGRkYTNhOWQtYmI0Ni00MjZhLWJmMzAtMTA2Mjg4NGNiNDU0XkEyXkFqcGc@._V1_.jpg",
            releaseYear = "1994",
            rating = 8.5,
            genre = "action"
        ),
        MovieCatalogItem(
            id = 39L,
            title = "Головоломка 2",
            originalTitle = "Inside Out 2",
            overview = "В голове повзрослевшей Райли появляются новые эмоции: Тревожность и Зависть.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BYWY3MDE2Y2UtOTE3Zi00MGUzLTg2MTItZjE1ZWVkMGVlODRmXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2024",
            rating = 7.7,
            genre = "animation"
        ),
        MovieCatalogItem(
            id = 40L,
            title = "Человек-паук: Паутина вселенных",
            originalTitle = "Spider-Man: Across the Spider-Verse",
            overview = "Майлз Моралес отправляется в мультивселенную, где встречает команду Пауков.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BNThiZjA3MjItZGY5Ni00ZmJhLWEwN2EtOTBlYTA4Y2E0M2ZmXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2023",
            rating = 8.6,
            genre = "animation"
        ),
        MovieCatalogItem(
            id = 41L,
            title = "Кот в сапогах 2: Последнее желание",
            originalTitle = "Puss in Boots: The Last Wish",
            overview = "Кот истратил восемь жизней из девяти и отправляется на поиски Звезды Желаний.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BMzg0MWUzMjctYjVlOS00NzVjLWIwZDMtNzg1YzNkYzdjNTMwXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2022",
            rating = 7.9,
            genre = "animation"
        ),
        MovieCatalogItem(
            id = 42L,
            title = "Тайна Коко",
            originalTitle = "Coco",
            overview = "Юный Мигель отправляется в красочную Страну Мертвых, чтобы разгадать семейную тайну.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BMDIyM2E2NTAtMzlhNy00ZGUxLWI1NjgtZDY5MzhiMDc5NGU3XkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2017",
            rating = 8.4,
            genre = "animation"
        ),
        MovieCatalogItem(
            id = 43L,
            title = "ВАЛЛ-И",
            originalTitle = "WALL-E",
            overview = "Робот-мусорщик встречает робота ЕВУ и отправляется в невероятное космическое приключение.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BMzk0ZGMzMDYtMDJkYS00NTdhLWJlZWUtMTdhOTJkOTM3NDUwXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2008",
            rating = 8.4,
            genre = "animation"
        ),
        MovieCatalogItem(
            id = 44L,
            title = "Дом Дракона",
            originalTitle = "House of the Dragon",
            overview = "История гражданской войны Таргариенов за Железный трон за 200 лет до событий Игры престолов.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BOWU2ZDA0M2EtNWEyYy00MWUwLWI4NjAtYzkxMDFjYzZkNGFiXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2022",
            rating = 8.4,
            genre = "tv_series"
        ),
        MovieCatalogItem(
            id = 45L,
            title = "Fallout",
            originalTitle = "Fallout",
            overview = "Спустя 200 лет после ядерной катастрофы наивная обитательница Убежища выходит на поверхность.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BYzE1MTZmY2ItMTgxNi00OGU5LTk4NTItMmY2ODFhYzhjZjRkXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2024",
            rating = 8.4,
            genre = "tv_series"
        ),
        MovieCatalogItem(
            id = 46L,
            title = "Пацаны",
            originalTitle = "The Boys",
            overview = "Отряд добровольцев борется с коррумпированными супергероями корпорации Vought.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BZjU4OWNiYzQtMzc1NS00NjZlLTgyYTctZWY4ZmEzMTkxYjA4XkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2019",
            rating = 8.7,
            genre = "tv_series"
        ),
        MovieCatalogItem(
            id = 47L,
            title = "Сёгун",
            originalTitle = "Shōgun",
            overview = "Английский штурман Джон Блэкторн терпит крушение у берегов феодальной Японии.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BZmJkMDRjYzEtMWI3Ny00OWE3LWJlNTItMGQ1MTQzMzc3NDY5XkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2024",
            rating = 8.7,
            genre = "tv_series"
        ),
        MovieCatalogItem(
            id = 48L,
            title = "Во все тяжкие",
            originalTitle = "Breaking Bad",
            overview = "Учитель химии Уолтер Уайт узнает о раке и решает варить мет ради будущего семьи.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BMzU5ZGYzNmQtMTdhYy00OGRiLTg0NmQtYjVjNzliZTg1ZGE4XkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2008",
            rating = 9.5,
            genre = "tv_series"
        ),
        MovieCatalogItem(
            id = 49L,
            title = "Чернобыль",
            originalTitle = "Chernobyl",
            overview = "Хроника катастрофы на Чернобыльской АЭС в апреле 1986 года и ликвидации ее последствий.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BNzU0OTI4YTQtNGQ1ZS00ZjA4LTg3MTMtZjkyZWNjN2RiZDJmXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "2019",
            rating = 9.3,
            genre = "tv_series"
        ),
        MovieCatalogItem(
            id = 50L,
            title = "Крестный отец",
            originalTitle = "The Godfather",
            overview = "Криминальная сага о сицилийской мафиозной семье Корлеоне в Нью-Йорке.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BNGEwYjgwOGQtYjg5ZS00Njc1LTk2ZGEtM2QwZWQ2NjdhZTE5XkEyXkFqcGc@._V1_.jpg",
            releaseYear = "1972",
            rating = 9.2,
            genre = "top_rated"
        ),
        MovieCatalogItem(
            id = 51L,
            title = "Криминальное чтиво",
            originalTitle = "Pulp Fiction",
            overview = "Несколько связанных между собой историй бандитов Винсента Веги и Джулса Виннфилда.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BYTViYTE3ZGQtNDBlMC00ZTAyLTkyODMtZGRiZDg0MjA2YThkXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "1994",
            rating = 8.9,
            genre = "top_rated"
        ),
        MovieCatalogItem(
            id = 52L,
            title = "Список Шиндлера",
            originalTitle = "Schindlers List",
            overview = "Немецкий промышленник Оскар Шиндлер спасает более тысячи польских евреев во время Холокоста.",
            posterUrl = "https://m.media-amazon.com/images/M/MV5BNjM1ZDQxYWUtMzQyZS00MTE1LWJmZGYtNGUyNTdlYjM3ZmVmXkEyXkFqcGc@._V1_.jpg",
            releaseYear = "1993",
            rating = 9.0,
            genre = "top_rated"
        ),
    )

    fun loadBuiltinSections(): List<MovieSection> {
        val list = mutableListOf<MovieSection>()
        list.add(
            MovieSection(
                id = "trending",
                title = "🔥 Сейчас в тренде",
                movies = builtinMovies.filter { it.genre == "trending" }
            )
        )
        list.add(
            MovieSection(
                id = "now_playing",
                title = "⭐ Новинки кино",
                movies = builtinMovies.filter { it.genre == "now_playing" }
            )
        )
        list.add(
            MovieSection(
                id = "scifi",
                title = "🚀 Фантастика",
                movies = builtinMovies.filter { it.genre == "scifi" }
            )
        )
        list.add(
            MovieSection(
                id = "adventure",
                title = "🗺️ Приключения",
                movies = builtinMovies.filter { it.genre == "adventure" }
            )
        )
        list.add(
            MovieSection(
                id = "drama",
                title = "🎭 Драмы",
                movies = builtinMovies.filter { it.genre == "drama" }
            )
        )
        list.add(
            MovieSection(
                id = "comedy",
                title = "😂 Комедии",
                movies = builtinMovies.filter { it.genre == "comedy" }
            )
        )
        list.add(
            MovieSection(
                id = "action",
                title = "💥 Боевики",
                movies = builtinMovies.filter { it.genre == "action" }
            )
        )
        list.add(
            MovieSection(
                id = "animation",
                title = "🎨 Мультфильмы",
                movies = builtinMovies.filter { it.genre == "animation" }
            )
        )
        list.add(
            MovieSection(
                id = "tv_series",
                title = "📺 Популярные сериалы",
                movies = builtinMovies.filter { it.genre == "tv_series" }
            )
        )
        list.add(
            MovieSection(
                id = "top_rated",
                title = "🏆 Шедевры с высоким рейтингом",
                movies = builtinMovies.filter { it.genre == "top_rated" }
            )
        )
        return list
    }

    fun getRandomSection(): MovieSection {
        val shuffled = builtinMovies.shuffled().take(15)
        return MovieSection(
            id = "random_" + System.currentTimeMillis(),
            title = "🎲 Случайная подборка",
            movies = shuffled
        )
    }

    fun findPoster(title: String, originalTitle: String = ""): String {
        val t = title.trim().lowercase()
        val orig = originalTitle.trim().lowercase()
        val found = builtinMovies.firstOrNull {
            it.title.lowercase() == t ||
            (orig.isNotBlank() && it.originalTitle.lowercase() == orig) ||
            (t.length >= 4 && it.title.lowercase().contains(t)) ||
            (orig.length >= 4 && it.originalTitle.lowercase().contains(orig))
        }
        return found?.posterUrl ?: ""
    }

    suspend fun fetchOnlinePoster(title: String, originalTitle: String = "", year: String = ""): String = withContext(Dispatchers.IO) {
        val local = findPoster(title, originalTitle)
        if (local.isNotBlank()) return@withContext local

        val query = originalTitle.ifBlank { title }.trim()
        if (query.isBlank()) return@withContext ""
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val u = URL("https://api.tvmaze.com/singlesearch/shows?q=$encoded")
            val conn = (u.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 4000
            }
            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val json = JSONObject(jsonStr)
                val imgObj = json.optJSONObject("image")
                val poster = imgObj?.optString("medium", "") ?: ""
                if (poster.isNotBlank()) {
                    return@withContext if (poster.startsWith("http://")) poster.replaceFirst("http://", "https://") else poster
                }
            } else {
                conn.disconnect()
            }
        } catch (_: Exception) {}
        ""
    }

    suspend fun fetchOnlineOverview(title: String, originalTitle: String = ""): String = withContext(Dispatchers.IO) {
        val query = originalTitle.ifBlank { title }.trim()
        if (query.isBlank()) return@withContext ""
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val u = URL("https://api.tvmaze.com/singlesearch/shows?q=$encoded")
            val conn = (u.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 3500
                readTimeout = 4000
            }
            if (conn.responseCode in 200..299) {
                val jsonStr = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val json = JSONObject(jsonStr)
                val summaryHtml = json.optString("summary", "")
                if (summaryHtml.isNotBlank()) {
                    return@withContext summaryHtml
                        .replace(Regex("<[^>]+>"), "")
                        .replace("&quot;", "\"")
                        .replace("&#039;", "'")
                        .replace("&amp;", "&")
                        .replace("&nbsp;", " ")
                        .trim()
                }
            } else {
                conn.disconnect()
            }
        } catch (_: Exception) {}
        ""
    }

    suspend fun loadMainSections(): List<MovieSection> = withContext(Dispatchers.IO) {
        // Витрина формируется исключительно из реальных раздач RuTracker
        emptyList()
    }

    suspend fun searchMovies(query: String): List<MovieCatalogItem> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()
        if (q.isBlank()) return@withContext emptyList()
        builtinMovies.filter {
            it.title.lowercase().contains(q) ||
            it.originalTitle.lowercase().contains(q) ||
            it.overview.lowercase().contains(q)
        }
    }
}
