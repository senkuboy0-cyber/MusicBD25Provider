cloudstream {
    setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/senkuboy0-cyber/MusicBD25Provider")
    version = 2
    description = "MusicBD25 - Viral Video Site (Bengali & World Trending)"
    authors = listOf("senkuboy0-cyber")
    language = "bn"
    tvTypes = listOf("Others")
    iconUrl = "https://musicbd25.site/favicon.ico"
}

android {
    namespace = "com.musicbd25"
}
