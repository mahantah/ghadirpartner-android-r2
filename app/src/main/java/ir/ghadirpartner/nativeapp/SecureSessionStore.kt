package ir.ghadirpartner.nativeapp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Persistent session tokens are authenticated ciphertext, protected by Android Keystore. */
internal class SecureSessionStore(context:Context,scope:String=BuildConfig.APP_MODE) {
    private val prefs=context.getSharedPreferences("ghadir_sessions_$scope",Context.MODE_PRIVATE)
    private val alias="ghadir_sessions_$scope"
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let {return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    @Synchronized fun load():Set<String> = try {
        val encoded=prefs.getString("ciphertext",null)
        if(encoded==null)emptySet() else {
            require(encoded.length<=262144)
            val bytes=Base64.decode(encoded,Base64.NO_WRAP);require(bytes.size>28)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
            JSONArray(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)).strings().toSet()
        }
    }catch(_:Exception){clear();emptySet()}
    @Synchronized fun save(values:Set<String>):Boolean = try {
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val plaintext=JSONArray(values.toList()).toString().toByteArray(Charsets.UTF_8)
        val bytes=cipher.iv+cipher.doFinal(plaintext)
        prefs.edit().putString("ciphertext",Base64.encodeToString(bytes,Base64.NO_WRAP)).commit()
    }catch(_:Exception){clear();false}
    fun clear(){prefs.edit().clear().commit()}
}
