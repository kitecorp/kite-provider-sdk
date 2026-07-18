package cloud.kitelang.provider;

/**
 * Per-operation context carrying engine-stored state into a
 * {@link ResourceTypeHandler} CRUD call and provider-private bytes back out.
 *
 * <p>The engine persists two things per resource after every apply: the full
 * applied state and an opaque blob of provider-private bytes. On subsequent
 * operations it supplies both, so handlers no longer need in-memory fields
 * that die with the provider process:</p>
 *
 * <ul>
 *   <li>{@link #priorState()} — the state recorded at the last apply
 *       (null when none exists, e.g. on create)</li>
 *   <li>{@link #privateData()} — the private bytes returned by the provider
 *       on the previous operation (empty when none exist)</li>
 *   <li>{@link #returnPrivateData(byte[])} — hands updated private bytes back
 *       to the engine for persistence; when a handler never calls it, the
 *       incoming bytes are returned unchanged so state is never lost for
 *       handlers that ignore private data</li>
 * </ul>
 *
 * @param <T> the resource class handled by the owning {@link ResourceTypeHandler}
 */
public final class ResourceContext<T> {

    private static final byte[] EMPTY = new byte[0];

    private final T priorState;               // null when no prior state is stored
    private final byte[] privateData;
    private byte[] returnedPrivateData;

    private ResourceContext(T priorState, byte[] privateData) {
        this.priorState = priorState;
        this.privateData = privateData != null ? privateData : EMPTY;
        // Default: round-trip the incoming bytes unchanged
        this.returnedPrivateData = this.privateData;
    }

    /**
     * Creates a context with engine-supplied prior state and private bytes.
     *
     * @param priorState  the last applied state, or null when none is stored
     * @param privateData the stored provider-private bytes, or null/empty when none
     * @param <T>         the resource class
     * @return a new context
     */
    public static <T> ResourceContext<T> of(T priorState, byte[] privateData) {
        return new ResourceContext<>(priorState, privateData);
    }

    /**
     * Creates a context with no stored state, for callers that invoke handlers
     * directly without an engine (e.g. tests or standalone tools).
     *
     * @param <T> the resource class
     * @return a context with null prior state and empty private bytes
     */
    public static <T> ResourceContext<T> empty() {
        return new ResourceContext<>(null, null);
    }

    /**
     * The state the engine recorded at the last apply, or null when none exists.
     */
    public T priorState() {
        return priorState;
    }

    /**
     * The provider-private bytes stored by the engine, never null (empty when
     * no private data exists yet). Opaque to the engine — only the provider
     * interprets them.
     */
    public byte[] privateData() {
        return privateData;
    }

    /**
     * Hands updated private bytes back to the engine for persistence after
     * this operation completes.
     *
     * @param privateData the new private bytes; null is treated as empty
     */
    public void returnPrivateData(byte[] privateData) {
        this.returnedPrivateData = privateData != null ? privateData : EMPTY;
    }

    /**
     * The private bytes to persist after this operation: whatever the handler
     * passed to {@link #returnPrivateData(byte[])}, or the incoming
     * {@link #privateData()} unchanged when the handler never called it.
     */
    public byte[] privateDataToReturn() {
        return returnedPrivateData;
    }
}
