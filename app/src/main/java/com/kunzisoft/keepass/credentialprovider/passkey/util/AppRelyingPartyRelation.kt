package com.kunzisoft.keepass.credentialprovider.passkey.util


import android.util.Log
import com.kunzisoft.encrypt.HashManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class AppRelyingPartyRelation {


    companion object {

        private val TAG: String = Companion::class.java.simpleName

        private val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()

        private val alphanumericChars = ('a'..'z') + ('A'..'Z') + ('0'..'9')

        private const val FINGERPRINT_LENGTH_IN_BYTE = 256 / 8
        fun isRelationValid(relyingParty: String, apkSigningCertificate: ByteArray?): Boolean {
            if (apkSigningCertificate == null || apkSigningCertificate.isEmpty()) {
                Log.w(TAG, "apkSigningCertificate is null or is empty")
                return false
            }

            try {
                // run the OK HTTP request in a separate thread, to prevent NetworkOnMainThreadException
                val executor = ThreadPoolExecutor(1,1,10, TimeUnit.SECONDS, ArrayBlockingQueue(1))
                val future = executor.submit(Callable { return@Callable requestAssetLink(relyingParty)})

                val json = future.get() ?: return false
                executor.shutdownNow()
                
                val validFingerprints = parseJsonSafe(json)
                val fingerprint = calculateFingerprint(apkSigningCertificate)


                return validFingerprints.any { validFingerprint -> validFingerprint.contentEquals(fingerprint) }
            } catch (exception: Exception) {
                Log.d(TAG, "a exception was thrown", exception)
                return false
            }
        }

        private fun requestAssetLink(relyingParty: String): String? {
            val validChars = alphanumericChars + '.' + '-'
            if (relyingParty.toCharArray().any { c -> validChars.contains(c).not() }) {
                Log.d(TAG, "relyingParty $relyingParty contains a forbidden character")
                return null
            }

            if (relyingParty.contains("..")) {
                Log.d(TAG, "relyingParty $relyingParty contains '..'")
                return null
            }

            val url = "https://$relyingParty/.well-known/assetlinks.json"
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                if (response.code != 200) return null

                val contentType = response.headers["Content-Type"]
                if (contentType == null || contentType.lowercase().startsWith("application/json").not()) return null

                return response.body?.string()
            }
        }

        fun calculateFingerprint(apkSigningCertificate: ByteArray?): ByteArray {
            return HashManager.hashSha256(apkSigningCertificate)
        }


        fun parseJsonSafe(json: String): List<ByteArray> {
            try {
                return parseJson(json)
            } catch (e: Exception) {
                Log.e(TAG, "parseJson throw an exception", e)
                return listOf()
            }
        }

        private fun parseJson(json: String): List<ByteArray> {

            val validChars = alphanumericChars +
                    ',' + ':' + '{' + '}' + ' ' + '[' + ']' + '\t' + '\n' + '"' + '_' + '/' + '.'

            json.toCharArray().forEach { c -> if (!validChars.contains(c)) print(c) }
            if (json.toCharArray().any { c -> validChars.contains(c).not() }) {
                Log.d(TAG, "json contains a forbidden character")
                return listOf()
            }

            val validFingerprints = mutableListOf<ByteArray>()

            val entryArray = JSONArray(json)

            for (i in 0..<entryArray.length()) {
                val entry = entryArray.get(i)

                var foundAllUrls = false
                var foundGetLoginCreds = false
                if (entry is JSONObject) {
                    val relations = entry.getJSONArray("relation")
                    for (j in 0..<relations.length()) {
                        val relation = relations.getString(j)

                        if (relation == "delegate_permission/common.handle_all_urls") {
                            foundAllUrls = true
                        } else if (relation == "delegate_permission/common.get_login_creds") {
                            foundGetLoginCreds = true
                        }
                    }


                    if (foundAllUrls && foundGetLoginCreds) {
                        Log.d(TAG, "found a relevant entry at index $i")
                        val target = entry.getJSONObject("target")
                        if (target.getString("namespace") == "android_app") {
                            val fingerprints = target.getJSONArray("sha256_cert_fingerprints")
                            for (k in 0..<fingerprints.length()) {
                                val fingerprint = fingerprints.getString(k).replace(":", "")
                                Log.d(TAG, "found a fingerprint")
                                val fingerprintByteArray = fingerprint.decodeHexToByteArray()
                                if (fingerprintByteArray.size == FINGERPRINT_LENGTH_IN_BYTE) {
                                    validFingerprints.add(fingerprintByteArray)
                                }
                            }
                        }
                    }
                }

            }
            Log.d(TAG, "found ${validFingerprints.size} fingerprints")
            return validFingerprints.toList()
        }

        private fun String.decodeHexToByteArray(): ByteArray {
            if (length % 2 != 0) {
                throw IllegalArgumentException("Must have an even length")
            }
            return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }

    }

}