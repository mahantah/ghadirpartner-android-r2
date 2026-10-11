package ir.ghadirpartner.nativeapp

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal data class RememberedLogin(val identity:String,val password:String)

/** Opt-in device-local storage. Password bytes never enter plaintext preferences or backups. */
internal class SecureLoginStore(context:Context,mode:String=BuildConfig.APP_MODE) {
    private val prefs=context.getSharedPreferences("ghadir_remembered_$mode",Context.MODE_PRIVATE)
    private val alias="ghadir_remembered_$mode"
    @Synchronized private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let {return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    fun load():RememberedLogin? = try {
        val encoded=prefs.getString("ciphertext",null)
        if(encoded==null)null else {
            require(encoded.length<=32768)
            val bytes=Base64.decode(encoded,Base64.NO_WRAP)
            require(bytes.size>12)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
            val data=JSONObject(String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8))
            RememberedLogin(data.getString("identity"),data.getString("password"))
        }
    }catch(_:Exception){clear();null}
    fun save(identity:String,password:String) {
        require(identity.isNotBlank()&&password.isNotBlank()&&identity.length<=100&&password.length<=4096)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,key())
        val plain=JSONObject().put("identity",identity).put("password",password).toString().toByteArray(Charsets.UTF_8)
        val encrypted=cipher.iv+cipher.doFinal(plain)
        check(prefs.edit().putString("ciphertext",Base64.encodeToString(encrypted,Base64.NO_WRAP)).commit())
    }
    fun clear(){prefs.edit().clear().commit()}
}
