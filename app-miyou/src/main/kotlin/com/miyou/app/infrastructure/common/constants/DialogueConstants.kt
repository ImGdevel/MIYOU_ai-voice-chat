package com.miyou.app.infrastructure.common.constants

object DialogueConstants {
    object Supertone {
        const val BASE_URL = "https://supertoneapi.com"
        const val MODEL = "sona_speech_1"

        object Voice {
            const val ADAM_ID = "2c5f135cb33f49a2c8882d"

            const val INTERVIEWER_MALE_ID = "4653d63d07d5340656b6bc"
            const val INTERVIEWER_FEMALE_1_ID = "fd15ad31caa16bd021f01d"
            const val INTERVIEWER_FEMALE_2_ID = "1f6b70f879da125bfec245"
        }

        object Language {
            const val KOREAN = "ko"
            const val ENGLISH = "en"
            const val JAPANESE = "ja"
        }

        object Style {
            const val NEUTRAL = "neutral"
            const val HAPPY = "happy"
        }

        object OutputFormat {
            const val WAV = "wav"
            const val MP3 = "mp3"
        }
    }
}
