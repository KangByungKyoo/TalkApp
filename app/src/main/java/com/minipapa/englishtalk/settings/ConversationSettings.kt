package com.minipapa.englishtalk.settings

data class CharacterConfig(
    val name: String,
    val age: Int,
    val levelLabel: String,
    val personalityLabel: String,
    val personality: String,
    val vocabulary: String,
    val sentenceStyle: String,
    val topics: String,
    val tone: String,
    val voiceId: String
)

enum class ConversationCharacter(val id: String, val config: CharacterConfig) {
    LILY("lily", CharacterConfig("Lily", 6, "유치원 수준 · 매우 쉬운 영어", "밝고 귀엽고 호기심이 많아요",
        "bright, sweet, playful and curious", "very easy kindergarten vocabulary",
        "short, simple sentences; avoid complex grammar and difficult words",
        "animals, toys, colors, food, family and play", "cheerful and innocent, like a friendly playmate", "shimmer")),
    EMMA("emma", CharacterConfig("Emma", 9, "초등학교 3학년 수준", "활발하고 친근하며 호기심이 많아요",
        "lively, friendly and curious", "everyday vocabulary at a third-grade elementary-school level",
        "natural short or medium-length sentences", "school, friends, hobbies, food, sports and travel",
        "bright and natural, like a friendly peer", "coral")),
    SOPHIA("sophia", CharacterConfig("Sophia", 13, "중학교 1학년 수준", "차분하고 친근하며 생각이 깊어요",
        "calm, friendly and thoughtful", "natural vocabulary at a first-year middle-school level",
        "more varied sentence structures, while staying clear and age-appropriate",
        "school life, music, movies, friends, feelings, interests and future dreams",
        "calm and natural, like a friendly teenage peer", "sage"));

    companion object {
        fun fromId(id: String?): ConversationCharacter = entries.firstOrNull { it.id == id } ?: EMMA
    }
}

enum class ResponseLength(val id: String, val label: String, val description: String) {
    SHORT("short", "짧게", "간결하고 자연스럽게"),
    NORMAL("normal", "보통", "조금 더 풍부하게"),
    DETAILED("detailed", "자세히", "충분한 표현과 설명");

    fun targetWords(character: ConversationCharacter): IntRange = when (character) {
        ConversationCharacter.LILY -> when (this) { SHORT -> 5..15; NORMAL -> 15..25; DETAILED -> 25..40 }
        ConversationCharacter.EMMA -> when (this) { SHORT -> 10..20; NORMAL -> 20..35; DETAILED -> 35..55 }
        ConversationCharacter.SOPHIA -> when (this) { SHORT -> 15..25; NORMAL -> 25..45; DETAILED -> 45..70 }
    }

    companion object {
        fun fromId(id: String?): ResponseLength = entries.firstOrNull { it.id == id } ?: SHORT
    }
}

data class ConversationSettings(
    val character: ConversationCharacter = ConversationCharacter.EMMA,
    val responseLength: ResponseLength = ResponseLength.SHORT
) {
    val summary: String get() = "${character.config.name} (${character.config.age}세) · ${responseLength.label}"
}
