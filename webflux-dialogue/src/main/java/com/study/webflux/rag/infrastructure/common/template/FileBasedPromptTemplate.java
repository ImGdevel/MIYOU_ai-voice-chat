package com.study.webflux.rag.infrastructure.common.template;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class FileBasedPromptTemplate {

	private static final List<String> TEMPLATE_EXTENSIONS = List.of(".md", ".txt");

	public String load(String templateName) {
		return load(templateName, Map.of());
	}

	public String load(String templateName, Map<String, String> variables) {
		try {
			ClassPathResource resource = resolveResource(templateName);
			String template = StreamUtils.copyToString(resource.getInputStream(),
				StandardCharsets.UTF_8);

			String result = template;
			for (Map.Entry<String, String> entry : variables.entrySet()) {
				result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
			}
			return result;
		} catch (IOException e) {
			throw new RuntimeException("Failed to load template: " + templateName, e);
		}
	}

	private ClassPathResource resolveResource(String templateName) throws IOException {
		for (String ext : TEMPLATE_EXTENSIONS) {
			ClassPathResource resource = new ClassPathResource("templates/" + templateName + ext);
			if (resource.exists()) {
				return resource;
			}
		}
		throw new IOException("Template not found for name: " + templateName);
	}
}
