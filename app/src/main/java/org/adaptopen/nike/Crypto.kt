package org.adaptopen.nike

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/** Gruppi Diffie-Hellman (Oakley) estratti dall'APK originale. Indice 0..3 = 768/1024/1536/2048 bit, generatore 2. */
object Primes {
    val HEX: List<String> = listOf(
        (
        "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74" +
        "020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F1437" +
        "4FE1356D6D51C245E485B576625E7EC6F44C42E9A63A3620FFFFFFFFFFFFFFFF"
        ),
        (
        "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74" +
        "020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F1437" +
        "4FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
        "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE65381FFFFFFFFFFFFFFFF"
        ),
        (
        "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74" +
        "020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F1437" +
        "4FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
        "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF05" +
        "98DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB" +
        "9ED529077096966D670C354E4ABC9804F1746C08CA237327FFFFFFFFFFFFFFFF"
        ),
        (
        "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E088A67CC74" +
        "020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B302B0A6DF25F1437" +
        "4FE1356D6D51C245E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED" +
        "EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE45B3DC2007CB8A163BF05" +
        "98DA48361C55D39A69163FA8FD24CF5F83655D23DCA3AD961C62F356208552BB" +
        "9ED529077096966D670C354E4ABC9804F1746C08CA18217C32905E462E36CE3B" +
        "E39E772C180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF695581718" +
        "3995497CEA956AE515D2261898FA051015728E5A8AACAA68FFFFFFFFFFFFFFFF"
        )
    )
}

object Crypto {
    /** Chiave di default usata prima del pairing (16 byte a 0x01). */
    val DEFAULT_KEY = ByteArray(16) { 1 }

    fun md5(data: ByteArray): ByteArray = MessageDigest.getInstance("MD5").digest(data)

    /** AES-128 ECB senza padding: risposta alla challenge della scarpa. */
    fun aesEcb(data: ByteArray, key: ByteArray): ByteArray {
        require(data.size % 16 == 0) { "challenge non multipla di 16 byte (${data.size})" }
        val c = Cipher.getInstance("AES/ECB/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return c.doFinal(data)
    }

    /** Big-endian senza zero iniziale di segno (come fa l'app originale). */
    fun unsigned(v: BigInteger): ByteArray {
        val b = v.toByteArray()
        return if (b.size > 1 && b[0].toInt() == 0) b.copyOfRange(1, b.size) else b
    }
}

class Dh(groupIndex: Int) {
    private val p = BigInteger(Primes.HEX[groupIndex], 16)
    private val g = BigInteger.valueOf(2)
    private val x: BigInteger = run {
        val rnd = SecureRandom()
        var v: BigInteger
        do { v = BigInteger(256, rnd) } while (v < BigInteger.valueOf(2))
        v
    }

    val publicBytes: ByteArray = Crypto.unsigned(g.modPow(x, p))

    /** Segreto condiviso (big-endian senza zero iniziale). */
    fun shared(peerPublic: ByteArray): ByteArray {
        val y = BigInteger(1, peerPublic)
        return Crypto.unsigned(y.modPow(x, p))
    }

    /** authKey = MD5(segreto condiviso). */
    fun authKey(peerPublic: ByteArray): ByteArray = Crypto.md5(shared(peerPublic))
}
