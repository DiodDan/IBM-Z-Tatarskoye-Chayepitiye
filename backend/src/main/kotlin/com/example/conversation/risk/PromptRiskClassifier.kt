package com.example.conversation.risk

import org.springframework.stereotype.Component

/**
 * The classifier's verdict on one USER prompt.
 * @param score 0 (harmless) to 1 (high risk)
 * @param reason short human-readable explanation shown to the admin, e.g. "asks for weapon instructions"
 */
data class PromptRiskAssessment(val score: Double, val reason: String? = null)

/**
 * Scores how risky a USER prompt is. The result is stored on the user message
 * (`message.prompt_risk`, `message.prompt_risk_reason`). It is admin-only data:
 * the chat API never sends it to users; the admin page shows it.
 *
 * Return null when the prompt was not (or could not be) classified; the admin page then shows "Pending".
 * To plug in the real classifier: implement this interface as a @Component and delete
 * UnscoredPromptRiskClassifier (only one bean may exist).
 */
interface PromptRiskClassifier {
    fun classify(prompt: String): PromptRiskAssessment?
}

/** Placeholder until the classifier is ready: every prompt is left unscored. */
@Component
class UnscoredPromptRiskClassifier : PromptRiskClassifier {
    override fun classify(prompt: String): PromptRiskAssessment? = null
}
