package com.applymate.ai;

/**
 * One structured model call. The only provider-specific seam in the app: prompts,
 * schemas and injection defences live in {@link AiService} and would port unchanged.
 */
public interface LlmClient {

    /**
     * @param label  short name for logs ("match-analysis")
     * @param system instructions; untrusted text must never be placed here
     * @param prompt the user turn, with any third-party text fenced by {@link Prompts#untrusted}
     * @param schema a record type; the response is constrained to its JSON shape
     */
    <T> Result<T> generate(String label, String system, String prompt, Class<T> schema);

    /** The parsed value and the model that actually answered. */
    record Result<T>(T value, String model) {}
}
