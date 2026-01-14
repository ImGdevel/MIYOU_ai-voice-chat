package com.study.webflux.rag.infrastructure.common.constants;

public class DialogueConstants {

	public static class Supertone {
		public static final String BASE_URL = "https://supertoneapi.com";
		public static final String MODEL = "sona_speech_1";

		public static class Voice {
			public static final String ADAM_ID = "2c5f135cb33f49a2c8882d";

			// 면접관용 보이스 (Supertone)
			public static final String INTERVIEWER_MALE_ID = "4653d63d07d5340656b6bc";
			public static final String INTERVIEWER_FEMALE_1_ID = "fd15ad31caa16bd021f01d";
			public static final String INTERVIEWER_FEMALE_2_ID = "1f6b70f879da125bfec245";
		}

		public static class Language {
			public static final String KOREAN = "ko";
			public static final String ENGLISH = "en";
			public static final String JAPANESE = "ja";
		}

		public static class Style {
			public static final String NEUTRAL = "neutral";
			public static final String HAPPY = "happy";
		}

		public static class OutputFormat {
			public static final String WAV = "wav";
			public static final String MP3 = "mp3";
		}
	}
}
