package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "PmAiBrainRepo"

enum class AiBrainMode(
    val label: String,
    val subtitle: String,
    val systemPrompt: String
) {
    OMNI_BRAIN(
        label = "PM Brain",
        subtitle = "All-in-one Gemini brain for chat, reasoning & phone control",
        systemPrompt = "You are PM AI, an intelligent Android 16 phone assistant and problem-solving AI brain. " +
            "You talk naturally in English, Hindi, or Hinglish matching the user's language. " +
            "Be crisp, warm, accurate, and genuinely helpful—like a fusion of Gemini, ChatGPT, Siri, and Folax."
    ),
    PROBLEM_SOLVER(
        label = "Problem Solver",
        subtitle = "Step-by-step math, science, coding & troubleshooting",
        systemPrompt = "You are PM AI Problem Solver. Break down complex questions, math, code, and real-world Android/tech problems into clear, numbered, actionable steps. Explain simply and verify edge cases."
    ),
    HINGLISH_BUDDY(
        label = "Hindi & Hinglish",
        subtitle = "Natural desi conversational assistant & translator",
        systemPrompt = "You are PM AI, a friendly and smart Indian AI assistant. Reply primarily in natural Hinglish or Hindi (unless the user writes pure English), keep explanations crystal clear, practical, and friendly."
    ),
    CODE_PRO(
        label = "Code & Tech",
        subtitle = "Kotlin, Android 16, Python, debugging & architecture",
        systemPrompt = "You are PM AI Code Architect. Provide clean, production-ready code snippets, debugging insights, and concise technical explanations."
    )
}

enum class PhoneActionType {
    NONE,
    SCROLL_DOWN,
    SCROLL_UP,
    SCROLL_LEFT,
    SCROLL_RIGHT,
    AUTO_SCROLL_START,
    AUTO_SCROLL_STOP,
    NAV_HOME,
    NAV_BACK,
    NAV_RECENTS,
    OPEN_NOTIFICATIONS,
    OPEN_QUICK_SETTINGS,
    TAKE_SCREENSHOT,
    READ_SCREEN,
    FLASHLIGHT_ON,
    FLASHLIGHT_OFF,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE_MEDIA,
    MEDIA_PLAY_PAUSE,
    MEDIA_NEXT,
    CALL_NUMBER,
    SEND_SMS,
    SEND_WHATSAPP,
    OPEN_APP,
    SET_ALARM,
    OPEN_WIFI_SETTINGS,
    OPEN_BLUETOOTH_SETTINGS,
    SET_CALL_MODE_QUIET,
    SET_CALL_MODE_SPAM_GUARD,
    OPEN_CONTROL_TAB,
    OPEN_CALL_SHIELD_TAB,
    OPEN_AI_STUDIO_TAB,
    GENERATE_IMAGE,
    ENABLE_ALWAYS_ACTIVE,
    DISABLE_ALWAYS_ACTIVE,
    ADD_CALL_VIP_RULE,
    ADD_CALL_BLOCK_RULE
}

data class ParsedPhoneCommand(
    val actionType: PhoneActionType,
    val phoneNumber: String = "",
    val messageBody: String = "",
    val appName: String = "",
    val alarmHour: Int = 7,
    val alarmMinute: Int = 0,
    val spokenConfirmation: String = ""
)

class PmAiBrainRepository {

    private val intentSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "actionType" to Schema.enumeration(
                    values = PhoneActionType.entries.map { it.name },
                    description = "The Android phone or in-app control action to execute, or NONE if this is a normal chat/question/problem-solving request."
                ),
                "phoneNumber" to Schema.string(
                    description = "Target phone number or contact name if calling, messaging, or adding a call rule, else empty string."
                ),
                "messageBody" to Schema.string(
                    description = "Drafted SMS/WhatsApp body, or image prompt if GENERATE_IMAGE, or rule label if adding a call rule, else empty string."
                ),
                "appName" to Schema.string(
                    description = "App name to open if actionType is OPEN_APP (e.g., YouTube, WhatsApp, Camera, Chrome, Maps, Settings), else empty string."
                ),
                "alarmHour" to Schema.integer(
                    description = "Alarm hour 0-23 if SET_ALARM, else 7."
                ),
                "alarmMinute" to Schema.integer(
                    description = "Alarm minute 0-59 if SET_ALARM, else 0."
                ),
                "spokenConfirmation" to Schema.string(
                    description = "Short 1-sentence confirmation in the user's language (English/Hindi/Hinglish) from PM AI."
                )
            )
        )
    }

    private val intentRouterModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = intentSchema
                temperature = 0.1f
            },
            systemInstruction = content {
                text(
                    "You are PM AI's Android 16 Intent Router. Analyze the user's natural language command (in English, Hindi, or Hinglish) " +
                        "and determine if they want to control the phone, scroll on-screen elements, send messages, handle calls, or interact with app features. " +
                        "Examples: 'scroll down', 'niche scroll karo' -> SCROLL_DOWN; 'scroll up', 'upar scroll karo' -> SCROLL_UP; " +
                        "'auto scroll chalu karo' -> AUTO_SCROLL_START; 'stop scrolling' -> AUTO_SCROLL_STOP; " +
                        "'torch on', 'flashlight jalao' -> FLASHLIGHT_ON; 'call 9876543210' -> CALL_NUMBER; " +
                        "'message Rahul that I am coming' -> SEND_SMS; 'send WhatsApp to 9876543210 hello' -> SEND_WHATSAPP; " +
                        "'open YouTube' -> OPEN_APP; 'meeting mode on karo / block calls' -> SET_CALL_MODE_QUIET; " +
                        "'read screen / screen pe kya hai' -> READ_SCREEN; " +
                        "'always active mode on karo / screen off me bhi active rakho' -> ENABLE_ALWAYS_ACTIVE; " +
                        "'open control tab' -> OPEN_CONTROL_TAB; 'open call shield' -> OPEN_CALL_SHIELD_TAB; " +
                        "'open studio' -> OPEN_AI_STUDIO_TAB; 'generate image of a futuristic robot' (put prompt in messageBody) -> GENERATE_IMAGE; " +
                        "'add VIP rule for Mom 9876543210' (put label in messageBody, number in phoneNumber) -> ADD_CALL_VIP_RULE; " +
                        "'block number 14099' (put number in phoneNumber) -> ADD_CALL_BLOCK_RULE. " +
                        "If the user is asking a general question, chatting, solving math/code, or translating, return NONE."
                )
            }
        )
    }

    private val generalModelCache = mutableMapOf<AiBrainMode, GenerativeModel>()

    private fun getBrainModel(mode: AiBrainMode): GenerativeModel {
        return generalModelCache.getOrPut(mode) {
            Firebase.ai.generativeModel(
                modelName = "gemini-3.8-flash",
                systemInstruction = content {
                    text(mode.systemPrompt)
                }
            )
        }
    }

    private val imageStudioModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-nano-banana-2.1",
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    suspend fun classifyPhoneIntent(userInput: String): ParsedPhoneCommand {
        return try {
            val response = withTimeout(30_000L) {
                intentRouterModel.generateContent(userInput)
            }
            val rawJson = response.text ?: return ParsedPhoneCommand(PhoneActionType.NONE)
            val json = JSONObject(rawJson)
            val typeStr = json.optString("actionType", PhoneActionType.NONE.name)
            val actionType = runCatching { PhoneActionType.valueOf(typeStr) }
                .getOrDefault(PhoneActionType.NONE)
            ParsedPhoneCommand(
                actionType = actionType,
                phoneNumber = json.optString("phoneNumber", ""),
                messageBody = json.optString("messageBody", ""),
                appName = json.optString("appName", ""),
                alarmHour = json.optInt("alarmHour", 7),
                alarmMinute = json.optInt("alarmMinute", 0),
                spokenConfirmation = json.optString("spokenConfirmation", "")
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Intent routing skipped/failed, falling back to chat", e)
            ParsedPhoneCommand(PhoneActionType.NONE)
        }
    }

    fun streamChatResponse(
        userMessage: String,
        historyPairs: List<Pair<String, String>>,
        mode: AiBrainMode,
        attachedBitmap: Bitmap? = null,
        extraScreenContext: String? = null
    ): Flow<String> = flow {
        val model = getBrainModel(mode)
        val promptText = if (!extraScreenContext.isNullOrBlank()) {
            "Current visible screen text:\n$extraScreenContext\n\nUser request: $userMessage"
        } else {
            userMessage
        }

        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            if (attachedBitmap != null) {
                val multimodalContent = content {
                    image(attachedBitmap)
                    text(promptText)
                }
                model.generateContentStream(multimodalContent).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            } else {
                val chatHistory: List<Content> = historyPairs.takeLast(12).map { (role, text) ->
                    content(role = role) { text(text) }
                }
                val chat = model.startChat(history = chatHistory)
                chat.sendMessageStream(promptText).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(accumulated.toString())
                    }
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("PM AI received an empty response from the model.")
        }
    }

    suspend fun generateQuickToolOutput(
        toolInstruction: String,
        inputText: String
    ): String {
        val model = getBrainModel(AiBrainMode.OMNI_BRAIN)
        val response = withTimeout(60_000L) {
            model.generateContent("$toolInstruction\n\nInput:\n$inputText")
        }
        return response.text?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalStateException("Model returned an empty response.")
    }

    suspend fun generateStudioImage(
        prompt: String,
        referenceBitmap: Bitmap? = null
    ): Bitmap {
        val response = withTimeout(60_000L) {
            if (referenceBitmap != null) {
                imageStudioModel.generateContent(
                    content {
                        image(referenceBitmap)
                        text(prompt)
                    }
                )
            } else {
                imageStudioModel.generateContent(prompt)
            }
        }
        val imagePart = response.candidates.firstOrNull()
            ?.content?.parts?.filterIsInstance<ImagePart>()
            ?.firstOrNull()
        return imagePart?.image
            ?: throw IllegalStateException("No image was returned by Gemini Nano Banana 2.1.")
    }
}
