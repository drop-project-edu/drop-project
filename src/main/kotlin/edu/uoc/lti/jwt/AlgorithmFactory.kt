/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2022 Pedro Alves
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
package edu.uoc.lti.jwt

import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1Sequence
import java.io.IOException
import java.math.BigInteger
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPrivateCrtKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*

/**
 * This shadows the class AlgorithmFactory in the lti-13-jwt library (1.0.0)
 * to remove the dependency on sun.security classes, whose access is forbidden since Java 1.7
 *
 * TODO: Remove this class after lti-13-jwt-1.X.X is released
 */
class AlgorithmFactory(publicKey: String?, privateKey: String?, algorithm: String?) {

    var publicKey: RSAPublicKey

    var privateKey: RSAPrivateKey

    init {
        val kf: KeyFactory
        try {
            kf = KeyFactory.getInstance(algorithm)
            val encodedPb = Base64.getDecoder().decode(publicKey)
            val keySpecPb = X509EncodedKeySpec(encodedPb)
            this.publicKey = kf.generatePublic(keySpecPb) as RSAPublicKey
            val derReader = ASN1InputStream(Base64.getDecoder().decode(privateKey))
            val seq = derReader.readObject() as ASN1Sequence

            if (seq.size() < 9) {
                throw GeneralSecurityException("Could not parse a PKCS1 private key.")
            }

            // skip version seq[0];
            val modulus = (seq.getObjectAt(1) as ASN1Integer).value as BigInteger
            val publicExp = (seq.getObjectAt(2) as ASN1Integer).value as BigInteger
            val privateExp = (seq.getObjectAt(3) as ASN1Integer).value as BigInteger
            val prime1 = (seq.getObjectAt(4) as ASN1Integer).value as BigInteger
            val prime2 = (seq.getObjectAt(5) as ASN1Integer).value as BigInteger
            val exp1 = (seq.getObjectAt(6) as ASN1Integer).value as BigInteger
            val exp2 = (seq.getObjectAt(7) as ASN1Integer).value as BigInteger
            val crtCoef = (seq.getObjectAt(8) as ASN1Integer).value as BigInteger
            val keySpecPv = RSAPrivateCrtKeySpec(modulus, publicExp, privateExp, prime1, prime2, exp1, exp2, crtCoef)
            this.privateKey = kf.generatePrivate(keySpecPv) as RSAPrivateKey
        } catch (e: GeneralSecurityException) {
            throw BadToolProviderConfigurationException(e)
        } catch (e: IOException) {
            throw BadToolProviderConfigurationException(e)
        }
    }
}
