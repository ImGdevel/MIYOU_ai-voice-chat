package com.study.webflux.rag.domain.dialogue.model;

public record PersonaId(
	String value
) {
	public static final PersonaId DEFAULT = new PersonaId("default");

	public PersonaId {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("personaId cannot be null or blank");
		}
		if (value.length() > 64) {
			throw new IllegalArgumentException("personaId too long");
		}
	}

	public static PersonaId of(String value) {
		return new PersonaId(value);
	}

	public static PersonaId defaultPersona() {
		return DEFAULT;
	}

	public static PersonaId ofNullable(String value) {
		return (value == null || value.isBlank()) ? DEFAULT : new PersonaId(value);
	}
}
