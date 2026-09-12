package com.iqforge.chat

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

enum class AttachmentKind { CAMERA, PHOTO, FILE }

data class ChatAttachment(
    val id: String,
    val name: String,
    val kind: AttachmentKind,
    val content: String
)

/** Turns camera, photo-picker, and document-picker results into model-ready text context. */
class ChatAttachmentService {
    suspend fun fromCamera(bitmap: Bitmap): ChatAttachment {
        val text = recognize(InputImage.fromBitmap(bitmap, 0))
        return requireText(
            ChatAttachment(
                id = "camera-${System.currentTimeMillis()}",
                name = "Camera capture",
                kind = AttachmentKind.CAMERA,
                content = text
            )
        )
    }

    /** OCR a full-resolution image returned by the device's camera application. */
    suspend fun fromCamera(context: Context, uri: Uri): ChatAttachment {
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
        val text = recognize(image)
        return requireText(
            ChatAttachment(
                id = "camera-${System.currentTimeMillis()}",
                name = "Camera scan",
                kind = AttachmentKind.CAMERA,
                content = text
            )
        )
    }

    suspend fun fromPhoto(context: Context, uri: Uri): ChatAttachment {
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
        val text = recognize(image)
        return requireText(
            ChatAttachment(
                id = uri.toString(),
                name = displayName(context, uri) ?: "Photo",
                kind = AttachmentKind.PHOTO,
                content = text
            )
        )
    }

    suspend fun fromFile(context: Context, uri: Uri): ChatAttachment = withContext(Dispatchers.IO) {
        val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
            val result = StringBuilder()
            val buffer = CharArray(4_096)
            while (result.length < MAX_ATTACHMENT_CHARS) {
                val count = reader.read(buffer, 0, minOf(buffer.size, MAX_ATTACHMENT_CHARS - result.length))
                if (count <= 0) break
                result.append(buffer, 0, count)
            }
            result.toString()
        } ?: throw IOException("The selected file could not be opened.")

        requireText(
            ChatAttachment(
                id = uri.toString(),
                name = displayName(context, uri) ?: "Code file",
                kind = AttachmentKind.FILE,
                content = content
            )
        )
    }

    private suspend fun recognize(image: InputImage): String = suspendCancellableCoroutine { continuation ->
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result.text.take(MAX_ATTACHMENT_CHARS))
            }
            .addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
            .addOnCompleteListener { recognizer.close() }
        continuation.invokeOnCancellation { recognizer.close() }
    }

    private fun requireText(attachment: ChatAttachment): ChatAttachment {
        if (attachment.content.isBlank()) {
            throw IOException("No readable text or code was found in ${attachment.name}.")
        }
        return attachment
    }

    private fun displayName(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private companion object {
        const val MAX_ATTACHMENT_CHARS = 48_000
    }
}
