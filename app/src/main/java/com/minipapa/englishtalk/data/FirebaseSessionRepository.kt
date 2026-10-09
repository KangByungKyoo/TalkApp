package com.minipapa.englishtalk.data

import android.os.SystemClock
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class FirebaseSessionRepository : SessionRepository {
    override suspend fun createSession(): SessionCredentials {
        try {
            FirebaseApp.getInstance()
        } catch (_: IllegalStateException) {
            throw SessionCreationException("Firebase 설정이 없습니다. google-services.json을 추가해 주세요.")
        }
        return createConfiguredSession()
    }

    private suspend fun createConfiguredSession(): SessionCredentials {
        try {
            val auth = FirebaseAuth.getInstance()
            if (auth.currentUser == null) auth.signInAnonymously().await()
            val options = HttpsCallableOptions.Builder().setLimitedUseAppCheckTokens(true).build()
            val callable = FirebaseFunctions.getInstance("asia-northeast3")
                .getHttpsCallable("createRealtimeSession", options)
            val started = SystemClock.elapsedRealtime()
            val result = callable.call(emptyMap<String, Any>()).await()
            val elapsed = (SystemClock.elapsedRealtime() - started + 999) / 1000
            return parseSessionResponse(result.data, elapsed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: SessionCreationException) {
            throw e
        } catch (e: FirebaseFunctionsException) {
            val message = when (e.code) {
                FirebaseFunctionsException.Code.UNAUTHENTICATED -> "인증 또는 App Check 검증에 실패했습니다. Firebase 설정을 확인해 주세요."
                FirebaseFunctionsException.Code.PERMISSION_DENIED -> "이 앱의 요청이 허용되지 않았습니다. App Check 설정을 확인해 주세요."
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> "세션 요청 한도를 초과했습니다. 잠시 후 다시 시도해 주세요."
                FirebaseFunctionsException.Code.FAILED_PRECONDITION -> "서버 API 키 또는 모델 설정을 확인해야 합니다."
                FirebaseFunctionsException.Code.UNAVAILABLE, FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> "서버 또는 OpenAI에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."
                else -> "세션 생성에 실패했습니다. 서버 배포와 설정을 확인해 주세요."
            }
            throw SessionCreationException(message)
        } catch (_: Exception) {
            throw SessionCreationException("Firebase 로그인 또는 네트워크 요청에 실패했습니다. 익명 로그인과 네트워크 설정을 확인해 주세요.")
        }
    }
}
