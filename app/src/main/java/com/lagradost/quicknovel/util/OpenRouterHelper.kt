package com.lagradost.quicknovel.util

import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.OPENROUTER_DEFAULT_PROMPT
import com.lagradost.quicknovel.OPENROUTER_ENDPOINT
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

object OpenRouterHelper {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Calls the OpenRouter chat completions endpoint with the selected text and
     * surrounding paragraph as context. Throws on network error or non-200 response.
     */
    suspend fun explain(
        selected: String,
        paragraph: String,
        apiKey: String,
        model: String,
        promptTemplate: String = OPENROUTER_DEFAULT_PROMPT,
        reasoning: Boolean = false
    ): String {
        val fullPrompt = buildPrompt(promptTemplate, selected, paragraph)

        val body = """
            {
              "model": ${jsonString(model)},
              "messages": [
                { "role": "user", "content": ${jsonString(fullPrompt)} }
              ],
              "reasoning": { "enabled": $reasoning }
            }
        """.trimIndent()

        val response = MainActivity.app.post(
            url = OPENROUTER_ENDPOINT,
            headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            ),
            requestBody = body.toRequestBody(jsonMediaType)
        )

        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.text.take(200)}")
        }

        // Parse choices[0].message.content from the response JSON
        // Using simple string parsing to avoid pulling in extra deps
        val text = response.text
        return extractContent(text)
            ?: throw Exception("Unexpected API response format")
    }

    private fun buildPrompt(template: String, selected: String, paragraph: String): String {
        return "You are a language assistant in an ebook reader. You have been provided a snippet of text:\n\n$paragraph\n\n$template\n\nThe user is asking about:\n\n$selected"
    }

    /**
     * Escapes a string for inclusion as a JSON string value.
     */
    private fun jsonString(s: String): String {
        val escaped = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    /**
     * Extracts the content field from choices[0].message.content in an OpenAI-compatible
     * JSON response. Uses Jackson (already in the project) for robust parsing.
     */
    private fun extractContent(json: String): String? {
        return try {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper()
            val root = mapper.readTree(json)
            root["choices"]?.get(0)?.get("message")?.get("content")?.asText()
        } catch (e: Exception) {
            null
        }
    }
}
