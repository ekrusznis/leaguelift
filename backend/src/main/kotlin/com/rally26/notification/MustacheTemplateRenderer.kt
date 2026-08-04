package com.rally26.notification

import com.github.mustachejava.DefaultMustacheFactory
import org.springframework.stereotype.Component
import java.io.StringWriter

/**
 * Thin wrapper around the `com.github.spullara.mustache.java` compiler (ADR-059) —
 * used only for the plain-text SMTP support-case-created email today
 * ([com.rally26.support.application.SupportCaseCreatedEmailHandler]). The four Resend
 * templates (verify/reset/invite/welcome) are unrelated: those are designed and hosted
 * in Resend's own dashboard and referenced by ID ([com.rally26.config.ResendTemplateProperties]),
 * not rendered here — this renderer is specifically for content this codebase owns
 * and ships as classpath resources.
 *
 * [DefaultMustacheFactory] resolves template names from the classpath by default,
 * which is why templates live under `src/main/resources/mail-templates/`.
 */
@Component
class MustacheTemplateRenderer {
	private val factory = DefaultMustacheFactory()

	fun render(templateClasspathName: String, context: Map<String, Any?>): String {
		val mustache = factory.compile(templateClasspathName)
		val writer = StringWriter()
		mustache.execute(writer, context).flush()
		return writer.toString()
	}
}
