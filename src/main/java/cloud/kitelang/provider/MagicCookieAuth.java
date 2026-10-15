package cloud.kitelang.provider;

import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.stub.MetadataUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * The magic cookie on provider calls. The engine generates a random cookie for each
 * provider it launches and passes it in the {@code KITE_PLUGIN_MAGIC_COOKIE}
 * environment variable; afterwards it sends the same value as gRPC metadata under
 * {@link #METADATA_KEY} on every call, and the provider server refuses any call that
 * does not carry it. The server listens on 127.0.0.1 only, so without this check
 * the remaining risk is any other local process, including one run by another user,
 * that finds the port and calls a provider holding this user's cloud credentials.
 *
 * <p>Both halves live in this class so the engine and the SDK share one metadata key.
 * Providers written in other languages must check the same key.</p>
 */
public final class MagicCookieAuth {

    /** gRPC metadata key that carries the cookie on every call to a provider. */
    public static final Metadata.Key<String> METADATA_KEY =
            Metadata.Key.of("kite-plugin-magic-cookie", Metadata.ASCII_STRING_MARSHALLER);

    static final String MISSING = "Missing magic cookie: the call has no " + METADATA_KEY.name()
            + " metadata. Only the Kite engine that launched this provider may call it.";
    static final String WRONG = "Wrong magic cookie: the " + METADATA_KEY.name()
            + " metadata does not match the value this provider was launched with.";

    private MagicCookieAuth() {
    }

    /**
     * Client side: adds the cookie to every call made through a channel or stub
     * intercepted with the returned interceptor.
     */
    public static ClientInterceptor attach(String cookie) {
        var headers = new Metadata();
        headers.put(METADATA_KEY, cookie);
        return MetadataUtils.newAttachHeadersInterceptor(headers);
    }

    /**
     * Server side: closes every call that does not carry {@code expectedCookie} with
     * UNAUTHENTICATED before the service sees it, so a refused call neither runs nor
     * counts as activity for the idle timeout.
     */
    public static ServerInterceptor require(String expectedCookie) {
        var expected = expectedCookie.getBytes(StandardCharsets.UTF_8);
        return new ServerInterceptor() {
            @Override
            public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers,
                                                                         ServerCallHandler<ReqT, RespT> next) {
                var presented = headers.get(METADATA_KEY);
                if (presented == null) {
                    return refuse(call, MISSING);
                }
                // Constant-time comparison, so response timing does not reveal how much of a guess matched
                if (!MessageDigest.isEqual(expected, presented.getBytes(StandardCharsets.UTF_8))) {
                    return refuse(call, WRONG);
                }
                return next.startCall(call, headers);
            }
        };
    }

    private static <ReqT, RespT> ServerCall.Listener<ReqT> refuse(ServerCall<ReqT, RespT> call, String reason) {
        call.close(Status.UNAUTHENTICATED.withDescription(reason), new Metadata());
        return new ServerCall.Listener<>() {
        };
    }
}
