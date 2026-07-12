package com.example.ai

import com.example.BuildConfig
import com.example.data.Macro
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.data.SettingsManager

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(val contents: List<Content>, val systemInstruction: Content? = null)

@JsonClass(generateAdapter = true)
data class Content(val parts: List<Part>)

@JsonClass(generateAdapter = true)
data class Part(val text: String)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(val candidates: List<Candidate>?)

@JsonClass(generateAdapter = true)
data class Candidate(val content: Content?)

@JsonClass(generateAdapter = true)
data class ParameterReplacement(val originalText: String, val newText: String)

@JsonClass(generateAdapter = true)
data class AutonomousAction(
    val actionType: String, // "TAP", "TYPE", "SCROLL_UP", "SCROLL_DOWN", "HOME", "BACK", "DONE"
    val targetText: String? = null,
    val targetX: Int? = null,
    val targetY: Int? = null,
    val textToType: String? = null,
    val reason: String = ""
)

@JsonClass(generateAdapter = true)
data class GeminiIntentResponse(val macroId: Int?, val parameterReplacements: List<ParameterReplacement>?)

interface GeminiApiService {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val service: GeminiApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeminiApiService::class.java)
    }
}

object GeminiHelper {
    suspend fun analyzeCommand(command: String, macros: List<Macro>): GeminiIntentResponse? = withContext(Dispatchers.IO) {
        val apiKey = SettingsManager.getApiKey().ifEmpty { BuildConfig.GEMINI_API_KEY }
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") return@withContext null
        val model = SettingsManager.getSelectedModel()
        
        val macroListStr = macros.joinToString("\n") { "ID: ${it.id}, Phrase: '${it.voicePhrase}'" }
        
        val systemPrompt = """
            You are an AI assistant that analyzes a user's voice command to control an Android device via accessibility macros.
            Available macros:
            $macroListStr
            
            The user wants to do: "$command".
            Determine which macro ID matches the intent.
            The user might ask to do the SAME action but with a DIFFERENT parameter (e.g. they say "Open Facebook" instead of "Open WhatsApp").
            Reply with ONLY a JSON object:
            {
                "macroId": <ID of the best matching macro, or null if none match>,
                "parameterReplacements": [
                   { "originalText": "WhatsApp", "newText": "Facebook" }
                ]
            }
        """.trimIndent()
        
        val request = GenerateContentRequest(
            contents = listOf(Content(listOf(Part("Analyze this command: $command")))),
            systemInstruction = Content(listOf(Part(systemPrompt)))
        )
        
        try {
            val response = RetrofitClient.service.generateContent(model, apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            
            // Basic JSON extraction if model adds markdown
            val cleanJson = jsonText?.replace("```json", "")?.replace("```", "")?.trim()
            
            if (cleanJson != null) {
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(GeminiIntentResponse::class.java)
                return@withContext adapter.fromJson(cleanJson)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun decideNextAutonomousAction(goal: String, screenHierarchy: String): AutonomousAction? = withContext(Dispatchers.IO) {
        val apiKey = SettingsManager.getApiKey().ifEmpty { BuildConfig.GEMINI_API_KEY }
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") return@withContext null
        val model = SettingsManager.getSelectedModel()
        
        val systemPrompt = """
            You are an autonomous AI Agent ("Jarvis") controlling an Android device.
            Your ultimate goal: "$goal".
            You will be given a text representation of the current screen hierarchy.
            Look at the screen elements and decide the NEXT logical action to achieve the goal.
            
            Action Types:
            - OPEN_APP (requires targetText to be the exact app name like "WhatsApp", "YouTube", "Settings". Use this first if the goal requires opening an app that is not currently on screen.)
            - TAP (requires targetText OR targetX and targetY. You can extract center coordinates from the provided Bounds)
            - TYPE (requires textToType: the text to type, usually after tapping a text field)
            - SCROLL_UP (to scroll down the content by swiping up)
            - SCROLL_DOWN (to scroll up the content by swiping down)
            - HOME (to go to home screen)
            - BACK (to go back)
            - DONE (if the goal is achieved)
            
            Reply ONLY with a valid JSON object matching this schema:
            {
                "actionType": "TAP",
                "targetText": "Submit",
                "targetX": 500,
                "targetY": 800,
                "textToType": null,
                "reason": "Tapping the submit button to proceed."
            }
        """.trimIndent()
        
        val request = GenerateContentRequest(
            contents = listOf(Content(listOf(Part("Current Screen Hierarchy:\n$screenHierarchy")))),
            systemInstruction = Content(listOf(Part(systemPrompt)))
        )
        
        try {
            val response = RetrofitClient.service.generateContent(model, apiKey, request)
            val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            
            val cleanJson = jsonText?.replace("```json", "")?.replace("```", "")?.trim()
            
            if (cleanJson != null) {
                val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
                val adapter = moshi.adapter(AutonomousAction::class.java)
                return@withContext adapter.fromJson(cleanJson)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }
}
