package com.example.conversation.risk

import org.springframework.stereotype.Component

/**
 * Scores how risky a USER prompt is, from 0 (harmless) to 1 (high risk).
 * The score is stored on the user message (`message.prompt_risk`). It is admin-only data:
 * the chat API never sends it to users. The admin page will read it from the database.
 *
 * Return null when the prompt was not (or could not be) classified.
 * To plug in the real classifier: implement this interface as a @Component and delete
 * UnscoredPromptRiskClassifier (only one bean may exist).
 */
interface PromptRiskClassifier {
    fun score(prompt: String): Double?
}

/** Placeholder until the classifier is ready: every prompt is left unscored. */
@Component
class UnscoredPromptRiskClassifier : PromptRiskClassifier {
    override fun score(prompt: String): Double? = null
}
