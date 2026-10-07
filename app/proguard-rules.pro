# ─────────────────────────────────────────────────────────────────────────────
# Aurora · Reglas R8 para el release ofuscado (P-05, RNF-05)
# ─────────────────────────────────────────────────────────────────────────────
# Objetivo de camuflaje: que NINGÚN nombre interno del doble fondo quede legible
# en el DEX. Por eso aquí NO se añade ningún `-keep` sobre los paquetes sensibles
# (`core.security.Secure*`, `SessionManager`, `access…SecretCode*`, `questionnaire`,
# `GuidanceLevel`, `CalculateGuidanceLevelUseCase`, `records`…): R8 los renombra a
# `a`, `b`, `c`… por defecto al activar `isMinifyEnabled`.
#
# Room, Hilt, Firebase y Retrofit traen sus propias `consumer-rules.pro` dentro de
# sus AAR y el AGP las aplica automáticamente; por eso este archivo solo refuerza
# lo que esas reglas no cubren: el puente JNI de SQLCipher y los serializers de
# kotlinx.serialization. Ambos bloques usan `allowobfuscation` o apuntan a
# librerías de terceros, así que no reintroducen nombres del doble fondo.

# ── SQLCipher (net.zetetic / net.sqlcipher) ──────────────────────────────────
# La librería carga estas clases por nombre desde código nativo (JNI). Si R8 las
# renombra, el puente Java↔C no las encuentra y la BD cifrada del espacio interno
# no abre (RNF-01, P-03). Son nombres de terceros, no del doble fondo.
-keep class net.zetetic.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.zetetic.**
-dontwarn net.sqlcipher.**

# ── kotlinx.serialization ────────────────────────────────────────────────────
# Los @Serializable del clima (Open-Meteo: geocoding y pronóstico) resuelven su
# KSerializer vía el `Companion` generado. Se conserva el miembro, pero con
# `allowobfuscation` para que el nombre de la clase igual pueda renombrarse.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Room ─────────────────────────────────────────────────────────────────────
# room-runtime 2.6.1 ya trae consumer-rules que conservan las subclases de
# RoomDatabase y sus `_Impl` (Room las localiza por reflexión: `Class.forName`).
# No se añade nada extra para no ampliar la superficie legible; los nombres que
# Room deja visibles (`JournalDatabase`, `WeatherDatabase`, entidades) son
# neutros y no delatan el propósito.

# ── PdfBox-Android (RF-45) ───────────────────────────────────────────────────
# El AAR trae sus propias consumer-rules (instancia SecurityHandlers por
# reflexión). BouncyCastle queda EXCLUIDA del grafo (ADR RF-45): solo la
# referencia PublicKeySecurityHandler (cifrado por certificado, nunca ejecutado),
# así que R8 debe ignorar esas clases ausentes.
-dontwarn org.bouncycastle.**
# JPEG 2000 (JPXFilter) es una dependencia OPCIONAL de la librería que Aurora
# no declara: solo se incrustan JPEG convencionales (DCTDecode).
-dontwarn com.gemalto.jp2.**

# ── Sin logs en release (regla 4 de CLAUDE.md) ───────────────────────────────
# Aurora no llama android.util.Log en src/main, pero PdfBox-Android sí registra
# avisos técnicos en 141 archivos. R8 elimina esas llamadas del DEX del release:
# ningún rastro del contenido exportado ni de la generación en logcat.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
}
