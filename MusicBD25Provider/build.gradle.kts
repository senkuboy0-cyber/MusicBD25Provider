cloudstream {
    setRepo(System.getenv("GITHUB_REPOSITORY") ?: "https://github.com/senkuboy0-cyber/MusicBD25Provider")
    version = 1
    description = "MusicBD25 - Viral Video Site (Bengali & World Trending)"
    authors = listOf("senkuboy0-cyber")
    language = "bn"
    tvTypes = listOf("Others")
}

android {
    namespace = "com.musicbd25"
}
