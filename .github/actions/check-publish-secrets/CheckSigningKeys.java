import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.ArrayList;
import java.util.Optional;

/**
 * Reads GPG_PUBLIC_KEY and GPG_PRIVATE_KEY from the environment the way JReleaser 1.23.0 reads
 * armored keys in MEMORY mode, with the Bouncy Castle version it ships (1.83), so a key passes here
 * exactly when JReleaser can sign with it. gpg is no stand-in: the gpg on Ubuntu runners reads a
 * private key cut short that Bouncy Castle turns away.
 *
 * <p>The calls follow JReleaser's {@code Keyring} (initialize, readPublicKey, readSecretKey) and
 * {@code SigningUtils.initSignatureGenerator}, which unlocks the key with the configured passphrase;
 * the publish workflows set none. Prints one line per problem, as GitHub error annotations, and exits
 * 1; or prints the key id and exits 0. Never prints key material.
 */
public class CheckSigningKeys {

    public static void main(String[] args) {
        Security.addProvider(new BouncyCastleProvider());

        var publicId = publicSigningKey(System.getenv("GPG_PUBLIC_KEY")).map(PGPPublicKey::getKeyID);
        var secretKey = secretSigningKey(System.getenv("GPG_PRIVATE_KEY"));

        var errors = new ArrayList<String>();
        if (publicId.isEmpty()) {
            errors.add("GPG_PUBLIC_KEY is not an armored OpenPGP public key");
        }
        if (secretKey.isEmpty()) {
            errors.add("GPG_PRIVATE_KEY is not an armored OpenPGP private key");
        } else if (!unlocksWithoutPassphrase(secretKey.get())) {
            errors.add("GPG_PRIVATE_KEY needs a passphrase, and the release signs without one");
        }
        if (errors.isEmpty() && publicId.get() != secretKey.get().getKeyID()) {
            errors.add("GPG_PUBLIC_KEY is key %s but GPG_PRIVATE_KEY is key %s; they must be the two halves of one key"
                    .formatted(hex(publicId.get()), hex(secretKey.get().getKeyID())));
        }

        if (!errors.isEmpty()) {
            errors.forEach(error -> System.out.println("::error::" + error));
            System.exit(1);
        }
        System.out.println("The publishing secrets are set, and GPG_PUBLIC_KEY and GPG_PRIVATE_KEY are both key "
                + hex(publicId.get()));
    }

    /** The first signing-capable key in the block, as {@code Keyring.readPublicKey} picks it. */
    private static Optional<PGPPublicKey> publicSigningKey(String armored) {
        try (var decoded = PGPUtil.getDecoderStream(bytes(armored))) {
            var ring = new PGPPublicKeyRing(decoded, new BcKeyFingerprintCalculator());
            for (var keys = ring.getPublicKeys(); keys.hasNext(); ) {
                var key = keys.next();
                if (isSigningAlgorithm(key.getAlgorithm())) {
                    return Optional.of(key);
                }
            }
            return Optional.empty();
        } catch (Exception unreadable) {
            // JReleaser fails on the same input, with "Did not find public key for signing"
            return Optional.empty();
        }
    }

    /** The first signing key in the block, as {@code Keyring.readSecretKey} picks it. */
    private static Optional<PGPSecretKey> secretSigningKey(String armored) {
        try (var decoded = PGPUtil.getDecoderStream(bytes(armored))) {
            var ring = new PGPSecretKeyRing(decoded, new BcKeyFingerprintCalculator());
            for (var keys = ring.getSecretKeys(); keys.hasNext(); ) {
                var key = keys.next();
                if (key.isSigningKey()) {
                    return Optional.of(key);
                }
            }
            return Optional.empty();
        } catch (Exception unreadable) {
            return Optional.empty();
        }
    }

    private static boolean unlocksWithoutPassphrase(PGPSecretKey key) {
        try {
            key.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder()
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(new char[0]));
            return true;
        } catch (Exception locked) {
            return false;
        }
    }

    /** The algorithms {@code Keyring.isSigningKey} accepts in JReleaser 1.23.0, deprecated ones included. */
    @SuppressWarnings("deprecation")
    private static boolean isSigningAlgorithm(int algorithm) {
        return algorithm == PublicKeyAlgorithmTags.EDDSA
                || algorithm == PublicKeyAlgorithmTags.ECDSA
                || algorithm == PublicKeyAlgorithmTags.ELGAMAL_GENERAL
                || algorithm == PublicKeyAlgorithmTags.ELGAMAL_ENCRYPT
                || algorithm == PublicKeyAlgorithmTags.RSA_SIGN
                || algorithm == PublicKeyAlgorithmTags.RSA_GENERAL
                || algorithm == PublicKeyAlgorithmTags.DSA;
    }

    // JReleaser turns the configured key into bytes with UTF-8 (JReleaserContext.createKeyring)
    private static ByteArrayInputStream bytes(String armored) {
        return new ByteArrayInputStream(armored.getBytes(StandardCharsets.UTF_8));
    }

    /** As gpg prints a key id, so the messages match what `gpg --list-keys` shows. */
    private static String hex(long keyId) {
        return "%016X".formatted(keyId);
    }
}
